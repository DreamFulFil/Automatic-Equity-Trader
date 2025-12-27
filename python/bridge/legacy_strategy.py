"""
Legacy momentum strategy implementation.
This module contains the original bridge.py momentum strategy logic
for backward compatibility with LegacyBridge strategy.
"""

from collections import deque
from datetime import datetime, timedelta
import statistics
import threading


class LegacyMomentumStrategy:
    """
    Legacy momentum strategy for bridge.py compatibility.
    Implements the 3-min/5-min/10-min momentum alignment strategy.
    """
    
    def __init__(self):
        self.price_history = deque(maxlen=600)
        self.volume_history = deque(maxlen=600)
        self.session_open_price = None
        self.session_high = None
        self.session_low = None
        self.last_direction = "NEUTRAL"
        
        # Anti-whipsaw state
        self.consecutive_signal_count = 0
        self.last_signal_direction = "NEUTRAL"
        self.last_trade_time = None
        self.signal_confirmation_history = deque(maxlen=6)
        
        # Streaming data
        self.streaming_quotes = deque(maxlen=100)
        self.streaming_quotes_lock = threading.Lock()
        
        # Order book data
        self.order_book = {
            "bids": [],
            "asks": [],
            "timestamp": None,
            "symbol": None
        }
        self.order_book_lock = threading.Lock()
    
    def update_tick(self, price, volume, timestamp):
        """
        Update strategy with new tick data.
        
        Args:
            price: Current price
            volume: Current volume
            timestamp: Tick timestamp
        """
        if price > 0:
            self.price_history.append({"price": price, "time": timestamp, "volume": volume})
            self.volume_history.append(volume)
            
            # Update streaming quotes
            with self.streaming_quotes_lock:
                self.streaming_quotes.append({
                    "price": price,
                    "volume": volume,
                    "timestamp": timestamp.isoformat() if hasattr(timestamp, 'isoformat') else str(timestamp)
                })
            
            # Update session stats
            if self.session_open_price is None:
                self.session_open_price = price
                self.session_high = price
                self.session_low = price
            else:
                self.session_high = max(self.session_high, price)
                self.session_low = min(self.session_low, price)
    
    def generate_signal(self, current_price):
        """
        Generate trading signal based on momentum strategy.
        
        Args:
            current_price: Current market price
        
        Returns:
            dict: Signal data including direction, confidence, and metadata
        """
        direction = "NEUTRAL"
        confidence = 0.0
        exit_signal = False
        
        # Need minimum data
        if len(self.price_history) < 120:
            return {
                "current_price": current_price,
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
                "session_high": self.session_high if self.session_high is not None else current_price,
                "session_low": self.session_low if self.session_low is not None else current_price,
                "raw_direction": "NEUTRAL",
                "timestamp": datetime.now().isoformat()
            }
        
        # Calculate momentum indicators
        momentum_3min = self._calculate_momentum(180)
        momentum_5min = self._calculate_momentum(300)
        momentum_10min = self._calculate_momentum(600)
        
        # Calculate RSI
        rsi = self._calculate_rsi()
        
        # Calculate volume ratio
        volume_ratio = self._calculate_volume_ratio()
        
        # Check cooldown
        in_cooldown, cooldown_remaining = self._check_cooldown()
        
        # Generate direction
        raw_direction = self._determine_direction(momentum_3min, momentum_5min, momentum_10min, rsi, volume_ratio)
        
        # Apply anti-whipsaw logic
        self.signal_confirmation_history.append(raw_direction)
        
        if raw_direction == self.last_signal_direction and raw_direction != "NEUTRAL":
            self.consecutive_signal_count += 1
        else:
            self.consecutive_signal_count = 1 if raw_direction != "NEUTRAL" else 0
            self.last_signal_direction = raw_direction
        
        # Only emit signal if enough consecutive confirmations
        MIN_CONSECUTIVE_SIGNALS = 3
        VOLUME_ENTRY_THRESHOLD = 1.5
        
        if self.consecutive_signal_count >= MIN_CONSECUTIVE_SIGNALS and not in_cooldown and volume_ratio > VOLUME_ENTRY_THRESHOLD:
            direction = raw_direction
            confidence = self._calculate_confidence(momentum_3min, momentum_5min, volume_ratio)
        else:
            direction = "NEUTRAL"
            confidence = 0.0 if in_cooldown else 0.3
        
        # Check exit signal
        exit_signal = self._check_exit_signal(momentum_3min, momentum_5min)
        
        # Update last direction
        if direction != "NEUTRAL" and confidence >= 0.70:
            self.last_direction = direction
        
        return {
            "current_price": current_price,
            "direction": direction,
            "confidence": round(confidence, 3),
            "exit_signal": exit_signal,
            "momentum_3min": round(momentum_3min, 4),
            "momentum_5min": round(momentum_5min, 4),
            "momentum_10min": round(momentum_10min, 4),
            "volume_ratio": round(volume_ratio, 2),
            "rsi": round(rsi, 1),
            "consecutive_signals": self.consecutive_signal_count,
            "in_cooldown": in_cooldown,
            "cooldown_remaining": cooldown_remaining,
            "session_high": self.session_high,
            "session_low": self.session_low,
            "raw_direction": raw_direction,
            "timestamp": datetime.now().isoformat()
        }
    
    def _calculate_momentum(self, lookback):
        """Calculate momentum for given lookback period."""
        lookback = min(lookback, len(self.price_history))
        if lookback == 0:
            return 0.0
        
        prices = [p["price"] for p in list(self.price_history)[-lookback:]]
        if len(prices) < 2 or prices[0] == 0:
            return 0.0
        
        return (prices[-1] - prices[0]) / prices[0] * 100
    
    def _calculate_rsi(self):
        """Calculate RSI-like indicator."""
        if len(self.price_history) < 60:
            return 50.0
        
        prices = [p["price"] for p in list(self.price_history)[-60:]]
        gains = []
        losses = []
        
        for i in range(1, len(prices)):
            change = prices[i] - prices[i-1]
            if change > 0:
                gains.append(change)
            else:
                losses.append(abs(change))
        
        avg_gain = sum(gains) / 60 if gains else 0
        avg_loss = sum(losses) / 60 if losses else 0
        
        if avg_loss == 0:
            return 100.0 if avg_gain > 0 else 50.0
        
        rs = avg_gain / avg_loss
        return 100 - (100 / (1 + rs))
    
    def _calculate_volume_ratio(self):
        """Calculate recent volume ratio."""
        if len(self.volume_history) < 60:
            return 1.0
        
        recent_vol = sum(list(self.volume_history)[-30:])
        avg_vol = sum(list(self.volume_history)[-60:]) / 2
        
        return recent_vol / avg_vol if avg_vol > 0 else 1.0
    
    def _check_cooldown(self):
        """Check if in cooldown period after trade."""
        COOLDOWN_SECONDS = 180
        
        if self.last_trade_time is None:
            return False, 0
        
        elapsed = (datetime.now() - self.last_trade_time).total_seconds()
        if elapsed < COOLDOWN_SECONDS:
            return True, int(COOLDOWN_SECONDS - elapsed)
        
        return False, 0
    
    def _determine_direction(self, momentum_3min, momentum_5min, momentum_10min, rsi, volume_ratio):
        """Determine raw trading direction."""
        MOMENTUM_ENTRY_THRESHOLD = 0.05
        RSI_OVERBOUGHT = 70
        RSI_OVERSOLD = 30
        
        all_bullish = (momentum_3min > MOMENTUM_ENTRY_THRESHOLD and 
                       momentum_5min > MOMENTUM_ENTRY_THRESHOLD * 0.8 and
                       momentum_10min > 0)
        
        all_bearish = (momentum_3min < -MOMENTUM_ENTRY_THRESHOLD and 
                       momentum_5min < -MOMENTUM_ENTRY_THRESHOLD * 0.8 and
                       momentum_10min < 0)
        
        if all_bullish and rsi < RSI_OVERBOUGHT:
            return "LONG"
        elif all_bearish and rsi > RSI_OVERSOLD:
            return "SHORT"
        else:
            return "NEUTRAL"
    
    def _calculate_confidence(self, momentum_3min, momentum_5min, volume_ratio):
        """Calculate signal confidence."""
        VOLUME_ENTRY_THRESHOLD = 1.5
        MOMENTUM_STRONG_THRESHOLD = 0.08
        
        confidence = 0.4 + abs(momentum_3min) * 8 + abs(momentum_5min) * 4
        
        if volume_ratio > VOLUME_ENTRY_THRESHOLD:
            confidence += 0.2
        
        if abs(momentum_3min) > MOMENTUM_STRONG_THRESHOLD:
            confidence += 0.1
        
        return min(0.95, confidence)
    
    def _check_exit_signal(self, momentum_3min, momentum_5min):
        """Check if exit signal should be triggered."""
        MOMENTUM_EXIT_THRESHOLD = 0.06
        
        if self.last_direction == "LONG" and momentum_3min < -MOMENTUM_EXIT_THRESHOLD:
            if momentum_5min < -MOMENTUM_EXIT_THRESHOLD * 0.5:
                return True
        elif self.last_direction == "SHORT" and momentum_3min > MOMENTUM_EXIT_THRESHOLD:
            if momentum_5min > MOMENTUM_EXIT_THRESHOLD * 0.5:
                return True
        
        return False
    
    def set_exit_cooldown(self):
        """Set cooldown after exit order."""
        self.last_trade_time = datetime.now()
        self.consecutive_signal_count = 0
        self.last_signal_direction = "NEUTRAL"
    
    def get_streaming_quotes(self, limit=50):
        """Get recent streaming quotes."""
        with self.streaming_quotes_lock:
            return list(self.streaming_quotes)[-limit:]
    
    def get_order_book(self):
        """Get current order book."""
        with self.order_book_lock:
            return dict(self.order_book)
    
    def update_order_book(self, bids, asks, timestamp, symbol):
        """Update order book data."""
        with self.order_book_lock:
            self.order_book["bids"] = bids
            self.order_book["asks"] = asks
            self.order_book["timestamp"] = timestamp
            self.order_book["symbol"] = symbol
