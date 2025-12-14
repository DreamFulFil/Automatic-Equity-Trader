from datetime import datetime, timedelta
from collections import deque
from app.services.shioaji_service import latest_tick, price_history, volume_history, session_high, session_low

# ============================================================================
# ANTI-WHIPSAW STATE TRACKING
# ============================================================================
# Track consecutive signals to avoid false entries
consecutive_signal_count = 0
last_signal_direction = "NEUTRAL"
last_trade_time = None  # Cooldown tracking
signal_confirmation_history = deque(maxlen=6)  # Last 6 signals (3 minutes at 30s intervals)
last_direction = "NEUTRAL"

def get_signal_legacy():
    """
    Generate trading signal using improved momentum + volume strategy.
    
    Anti-Whipsaw Improvements (December 2025):
    - Higher momentum thresholds (0.05% entry, 0.08% strong)
    - Requires 3 consecutive aligned signals before entry
    - 3-minute cooldown after closing a position
    - Exit requires stronger reversal (0.06%) to avoid premature exits
    - RSI-like overbought/oversold filter
    - Volume must confirm direction (ratio > 1.5 for entries)
    """
    global last_direction, consecutive_signal_count, last_signal_direction
    global last_trade_time, signal_confirmation_history
    
    price = latest_tick["price"]
    direction = "NEUTRAL"
    confidence = 0.0
    exit_signal = False
    
    # Need minimum data for calculations
    if len(price_history) < 120:  # Increased warmup period (2 minutes of ticks)
        return {
            "current_price": price,
            "direction": "NEUTRAL",
            "confidence": 0.0,
            "exit_signal": False,
            "reason": "Insufficient data (warming up)",
            "momentum_3min": 0.0,
            "momentum_5min": 0.0,
            "momentum_10min": 0.0,
            "volume_ratio": 1.0,
            "rsi": 50.0,
            "consecutive_signals": 0,
            "in_cooldown": False,
            "cooldown_remaining": 0,
            "session_high": session_high if session_high is not None else price,
            "session_low": session_low if session_low is not None else price,
            "raw_direction": "NEUTRAL",
            "timestamp": datetime.now().isoformat()
        }
    
    # ========================================================================
    # MOMENTUM CALCULATIONS
    # ========================================================================
    
    # 3-minute momentum (short-term)
    lookback_3min = min(180, len(price_history))
    prices_3min = [p["price"] for p in list(price_history)[-lookback_3min:]]
    momentum_3min = (prices_3min[-1] - prices_3min[0]) / prices_3min[0] * 100 if prices_3min[0] > 0 else 0
    
    # 5-minute momentum (medium-term confirmation)
    lookback_5min = min(300, len(price_history))
    prices_5min = [p["price"] for p in list(price_history)[-lookback_5min:]]
    momentum_5min = (prices_5min[-1] - prices_5min[0]) / prices_5min[0] * 100 if prices_5min[0] > 0 else 0
    
    # 10-minute momentum (trend context)
    lookback_10min = min(600, len(price_history))
    prices_10min = [p["price"] for p in list(price_history)[-lookback_10min:]]
    momentum_10min = (prices_10min[-1] - prices_10min[0]) / prices_10min[0] * 100 if prices_10min[0] > 0 else 0
    
    # ========================================================================
    # RSI-LIKE CALCULATION (Relative Strength Index)
    # ========================================================================
    rsi = 50.0  # Default neutral
    if len(prices_3min) >= 60:
        gains = []
        losses = []
        for i in range(1, min(60, len(prices_3min))):
            change = prices_3min[-i] - prices_3min[-i-1]
            if change > 0:
                gains.append(change)
            else:
                losses.append(abs(change))
        
        avg_gain = sum(gains) / 60 if gains else 0
        avg_loss = sum(losses) / 60 if losses else 0
        
        if avg_loss == 0:
            if avg_gain == 0:
                rsi = 50.0
            else:
                rsi = 100.0
        else:
            rs = avg_gain / avg_loss
            rsi = 100 - (100 / (1 + rs))
    
    # ========================================================================
    # VOLUME ANALYSIS
    # ========================================================================
    if len(volume_history) >= 60:
        recent_vol = sum(list(volume_history)[-30:])
        avg_vol = sum(list(volume_history)[-60:]) / 2
        volume_ratio = recent_vol / avg_vol if avg_vol > 0 else 1.0
    else:
        volume_ratio = 1.0
    
    # ========================================================================
    # IMPROVED THRESHOLDS (Anti-Whipsaw)
    # ========================================================================
    MOMENTUM_ENTRY_THRESHOLD = 0.05      # Increased from 0.02 (2.5x higher)
    MOMENTUM_STRONG_THRESHOLD = 0.08     # Strong signal threshold
    MOMENTUM_EXIT_THRESHOLD = 0.06       # Exit requires stronger reversal
    VOLUME_ENTRY_THRESHOLD = 1.5         # Increased from 1.3 (stricter volume confirmation)
    RSI_OVERBOUGHT = 70                  # Don't buy when overbought
    RSI_OVERSOLD = 30                    # Don't sell when oversold
    MIN_CONSECUTIVE_SIGNALS = 3          # Need 3 aligned signals before entry
    COOLDOWN_SECONDS = 180               # 3-minute cooldown after closing position
    
    # ========================================================================
    # COOLDOWN CHECK
    # ========================================================================
    in_cooldown = False
    cooldown_remaining = 0
    if last_trade_time is not None:
        elapsed = (datetime.now() - last_trade_time).total_seconds()
        if elapsed < COOLDOWN_SECONDS:
            in_cooldown = True
            cooldown_remaining = int(COOLDOWN_SECONDS - elapsed)
    
    # ========================================================================
    # SIGNAL GENERATION
    # ========================================================================
    
    # Check if momentum aligns across timeframes
    all_bullish = (momentum_3min > MOMENTUM_ENTRY_THRESHOLD and 
                   momentum_5min > MOMENTUM_ENTRY_THRESHOLD * 0.8 and
                   momentum_10min > 0)  # 10min just needs to be positive
    
    all_bearish = (momentum_3min < -MOMENTUM_ENTRY_THRESHOLD and 
                   momentum_5min < -MOMENTUM_ENTRY_THRESHOLD * 0.8 and
                   momentum_10min < 0)  # 10min just needs to be negative
    
    # Strong signals (higher thresholds)
    strong_bullish = momentum_3min > MOMENTUM_STRONG_THRESHOLD and momentum_5min > MOMENTUM_ENTRY_THRESHOLD
    strong_bearish = momentum_3min < -MOMENTUM_STRONG_THRESHOLD and momentum_5min < -MOMENTUM_ENTRY_THRESHOLD
    
    # Volume confirmation (stricter)
    volume_confirms = volume_ratio > VOLUME_ENTRY_THRESHOLD
    
    # RSI filter (avoid buying overbought, selling oversold)
    rsi_allows_long = rsi < RSI_OVERBOUGHT
    rsi_allows_short = rsi > RSI_OVERSOLD
    
    # ========================================================================
    # DIRECTION DETERMINATION
    # ========================================================================
    raw_direction = "NEUTRAL"
    
    if all_bullish and rsi_allows_long:
        raw_direction = "LONG"
        confidence = min(0.95, 0.4 + abs(momentum_3min) * 8 + abs(momentum_5min) * 4)
        if volume_confirms:
            confidence = min(0.95, confidence + 0.2)
        if strong_bullish:
            confidence = min(0.95, confidence + 0.1)
            
    elif all_bearish and rsi_allows_short:
        raw_direction = "SHORT"
        confidence = min(0.95, 0.4 + abs(momentum_3min) * 8 + abs(momentum_5min) * 4)
        if volume_confirms:
            confidence = min(0.95, confidence + 0.2)
        if strong_bearish:
            confidence = min(0.95, confidence + 0.1)
    else:
        raw_direction = "NEUTRAL"
        confidence = 0.3
    
    # ========================================================================
    # CONSECUTIVE SIGNAL TRACKING (Anti-Whipsaw)
    # ========================================================================
    signal_confirmation_history.append(raw_direction)
    
    if raw_direction == last_signal_direction and raw_direction != "NEUTRAL":
        consecutive_signal_count += 1
    else:
        consecutive_signal_count = 1 if raw_direction != "NEUTRAL" else 0
        last_signal_direction = raw_direction
    
    # Only emit non-NEUTRAL direction if we have enough consecutive signals
    # AND we're not in cooldown AND volume confirms
    if consecutive_signal_count >= MIN_CONSECUTIVE_SIGNALS and not in_cooldown and volume_confirms:
        direction = raw_direction
    else:
        direction = "NEUTRAL"
        if in_cooldown:
            confidence = 0.0  # Zero confidence during cooldown
    
    # ========================================================================
    # EXIT SIGNAL (Stronger reversal required)
    # ========================================================================
    if last_direction == "LONG" and momentum_3min < -MOMENTUM_EXIT_THRESHOLD:
        # Require both short-term AND medium-term to confirm reversal
        if momentum_5min < -MOMENTUM_EXIT_THRESHOLD * 0.5:
            exit_signal = True
    elif last_direction == "SHORT" and momentum_3min > MOMENTUM_EXIT_THRESHOLD:
        if momentum_5min > MOMENTUM_EXIT_THRESHOLD * 0.5:
            exit_signal = True
    
    # Update last_direction only on confirmed entries
    if direction != "NEUTRAL" and confidence >= 0.70:  # Increased from 0.65
        last_direction = direction
    
    return {
        "current_price": price,
        "direction": direction,
        "confidence": round(confidence, 3),
        "exit_signal": exit_signal,
        "momentum_3min": round(momentum_3min, 4),
        "momentum_5min": round(momentum_5min, 4),
        "momentum_10min": round(momentum_10min, 4),
        "volume_ratio": round(volume_ratio, 2),
        "rsi": round(rsi, 1),
        "consecutive_signals": consecutive_signal_count,
        "in_cooldown": in_cooldown,
        "cooldown_remaining": cooldown_remaining,
        "session_high": session_high,
        "session_low": session_low,
        "raw_direction": raw_direction,
        "timestamp": datetime.now().isoformat()
    }

def notify_exit_order():
    """Notify strategy that an exit order was placed"""
    global last_trade_time, consecutive_signal_count, last_signal_direction
    last_trade_time = datetime.now()
    consecutive_signal_count = 0
    last_signal_direction = "NEUTRAL"
    return {
        "cooldown_started": True,
        "cooldown_until": (last_trade_time + timedelta(seconds=180)).isoformat()
    }
