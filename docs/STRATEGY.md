# MTXF Lunch Bot - Trading Strategy

## 🎯 Strategy Overview: Lunch-Break Scalping

**Goal**: Capture 10-20 point moves in Mini-TXF during low-liquidity lunch hour (11:30-13:00).

**Edge**: 
- Reduced competition (institutional desks at lunch)
- Mean-reversion tendencies during consolidation
- News-driven volatility spikes
- AI news sentiment filter prevents disaster trades

---

## 📊 Strategy Components

### 1. Fair Value Anchor (Overnight US Futures)
```python
# Fetch overnight NQ/ES performance
# If NQ up 1%+ → Taiwan bullish bias
# If NQ down 1%+ → Taiwan bearish bias
```

**Implementation**:
- Pull CME Nasdaq (NQ) / S&P (ES) data from Yahoo Finance API
- Calculate % change from previous close
- Adjust fair value: `TWSE_close + (NQ_change * beta_coefficient)`

### 2. Momentum Filter (3-min / 5-min)
```python
# 3-min momentum
price_3m_ago = get_price(now - 3min)
momentum_3m = (current_price - price_3m_ago) / price_3m_ago

# 5-min momentum
price_5m_ago = get_price(now - 5min)
momentum_5m = (current_price - price_5m_ago) / price_5m_ago

# Buy signal: both positive and accelerating
if momentum_3m > 0.001 and momentum_5m > 0.001:
    if momentum_3m > momentum_5m:  # Accelerating
        signal = "LONG"
```

### 3. Volume Imbalance Detection
```python
# Track bid/ask volume over 5-min window
bid_volume = sum(volume where price_change > 0)
ask_volume = sum(volume where price_change < 0)

imbalance_ratio = bid_volume / ask_volume

# Buy signal: strong buying pressure
if imbalance_ratio > 1.5:
    signal = "LONG"
elif imbalance_ratio < 0.67:
    signal = "SHORT"
```

### 4. News Sentiment Veto (Llama 3.1 8B)

Every 10 minutes, scrape latest 15 headlines from:
- MoneyDJ (台股即時): https://www.moneydj.com/rss/RssNews.djhtm
- UDN Finance: https://udn.com/rssfeed/news/2/6638

**Llama 3.1 8B Prompt**:
```
You are a Taiwan stock market news analyst. Analyze these headlines and decide if trading should be VETOED due to major negative news.

Headlines:
- 美股暴跌500點 台指期挫低
- 央行突襲升息2碼
- 聯發科法說利多
- ...

Respond ONLY with valid JSON:
{"veto": true/false, "score": 0.0-1.0, "reason": "brief explanation"}

Veto if: geopolitical crisis, major crash, regulatory halt, war.
Score: 0.0=very bearish, 0.5=neutral, 1.0=very bullish
```

**Veto Triggers**:
- War/military conflict
- Central bank emergency action
- Circuit breaker halt
- Major tech earnings miss
- Regulatory crackdown

---

## 🎲 Entry Rules

✅ **LONG Entry**:
1. Time: 11:30-12:45
2. Fair value: MTXF > fair_value + 10 points
3. Momentum: 3-min > 0.1% AND 5-min > 0.1%
4. Volume imbalance: bid/ask > 1.3
5. News veto: false
6. Confidence: > 65%

✅ **SHORT Entry**:
1. Time: 11:30-12:45
2. Fair value: MTXF < fair_value - 10 points
3. Momentum: 3-min < -0.1% AND 5-min < -0.1%
4. Volume imbalance: bid/ask < 0.77
5. News veto: false
6. Confidence: > 65%

---

## 🚪 Exit Rules

1. **Target Profit**: +20 points (1,000 TWD)
2. **Stop Loss**: -10 points (-500 TWD)
3. **Time Stop**: 12:50 (10 min before close)
4. **Auto-Flatten**: 13:00 sharp
5. **Reversal Signal**: Momentum flips + volume imbalance reverses

---

## 📈 Expected Performance

### Per Trade:
- Win: +20 points = +1,000 TWD
- Loss: -10 points = -500 TWD
- Reward:Risk = 2:1

### Daily Target:
- 2-3 trades/day
- 60% win rate
- Average: 1 win (+1,000) + 1 loss (-500) = +500 TWD/day
- Best case: 2 wins = +2,000 TWD/day
- Worst case: 2 losses = -1,000 TWD (still within daily limit)

### Monthly Target:
- 20 trading days
- 500 TWD/day × 20 = 10,000 TWD (conservative)
- 1,000 TWD/day × 20 = 20,000 TWD (realistic)
- 1,500 TWD/day × 20 = 30,000 TWD (aggressive target)

---

## 🛡️ Risk Management

### Position Sizing:
- **Always 1 contract** (no pyramiding)
- MTXF margin: 40,000 TWD
- Max loss per trade: -500 TWD (1.25% of margin)

### Daily Loss Limit:
- **Hard stop**: -4,500 TWD
- **Emergency shutdown**: Auto-flatten + disable trading
- **Recovery**: Manual restart next day

### News Veto Examples:
- ✅ "聯發科Q3營收創高" → No veto (earnings beat)
- ❌ "中國對台軍演" → VETO (geopolitical)
- ❌ "央行緊急升息3碼" → VETO (monetary shock)
- ✅ "台積電法說平淡" → No veto (neutral)

---

## 🔧 Strategy Tuning (Advanced)

### Backtesting Parameters:
```python
# Test different thresholds
MOMENTUM_THRESHOLD = [0.05%, 0.1%, 0.15%]
VOLUME_IMBALANCE = [1.2, 1.3, 1.5]
PROFIT_TARGET = [15, 20, 25] points
STOP_LOSS = [8, 10, 12] points
```

### Optimization Metrics:
- Sharpe Ratio (target: > 2.0)
- Max Drawdown (target: < 10%)
- Win Rate (target: > 58%)
- Profit Factor (target: > 1.8)

### Machine Learning Enhancement (Future):
- Train Llama 3.1 on Taiwan market-specific corpus
- Fine-tune entry thresholds via reinforcement learning
- Add support/resistance detection with computer vision

---

## 📚 References

- TAIFEX Mini-TXF Specs: https://www.taifex.com.tw/
- Shioaji Python SDK: https://sinotrade.github.io/
- Taiwan Market Hours: 08:45-13:45 (lunch 11:30-13:00 low volume)
- Historical Data: Use `shioaji.get_historical_data()` for backtesting

---

**💡 Pro Tip**: Run in simulation mode for 2-4 weeks. Track every signal in a spreadsheet. Only go live when paper trading shows 60%+ win rate with your actual fills.
