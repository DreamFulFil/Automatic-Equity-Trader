# Performance Evaluation System

## Overview

The Automatic Equity Trader includes a comprehensive performance evaluation system that tracks, compares, and automatically optimizes strategy selection based on real-time and historical performance data.

---

## 📊 Data Collection & Storage

### Database Tables

#### 1. **strategy_performance**
Stores calculated performance metrics for all strategies (main + shadow mode).

**Key Columns:**
- `strategy_name`: Name of the strategy (e.g., "RSIStrategy", "Hull Moving Average")
- `symbol`: Stock/futures symbol (e.g., "2454.TW", "MTXF")
- `performance_mode`: `MAIN`, `SHADOW`, or `BACKTEST`
- `total_return_pct`: Total return percentage
- `sharpe_ratio`: Risk-adjusted return metric
- `max_drawdown_pct`: Maximum peak-to-trough decline
- `win_rate_pct`: Percentage of winning trades
- `total_trades`: Number of trades executed
- `total_pnl`: Total profit/loss in TWD
- `profit_factor`: Ratio of gross profit to gross loss
- `period_start` / `period_end`: Performance calculation window
- `calculated_at`: Timestamp of calculation

#### 2. **active_strategy_config**
Tracks the currently active main strategy.

**Key Columns:**
- `strategy_name`: Active strategy name
- `parameters_json`: Strategy parameters
- `auto_switched`: Whether automatically switched (true) or manual (false)
- `switch_reason`: Reason for strategy switch
- `total_return_pct`, `sharpe_ratio`, `max_drawdown_pct`, `win_rate_pct`: Snapshot metrics

#### 3. **shadow_mode_stocks**
Defines stocks being tracked in shadow mode with their assigned strategies.

**Key Columns:**
- `symbol`: Stock symbol (e.g., "2454.TW")
- `stock_name`: Human-readable name (e.g., "MediaTek")
- `strategy_name`: Assigned strategy (e.g., "RSI (14, 30/70)")
- `rank_position`: Priority ranking (1-10)
- `enabled`: Whether actively tracking (true/false)
- `expected_return_percentage`: Expected return from backtest

#### 4. **daily_statistics**
Aggregates daily performance data for the main strategy.

**Key Columns:**
- `strategy_name`: Strategy used that day
- `trade_date`: Trading date
- `total_pnl`: Daily profit/loss
- `total_trades`: Number of trades
- `win_rate`: Daily win rate percentage
- `max_drawdown`: Daily maximum drawdown
- `sharpe_ratio`: Daily Sharpe ratio
- `cumulative_pnl`: Cumulative profit/loss

---

## 🔍 How to Check Performance

### Method 1: PostgreSQL Database Queries

**Connect to database:**
```python
import psycopg2
conn = psycopg2.connect(
    host="localhost",
    database="auto_equity_trader",
    user="dreamer",
    password="WSYS1r0PE0Ig0iuNX2aNi5k7"
)
```

**Query Recent Strategy Performance:**
```sql
SELECT strategy_name, symbol, performance_mode,
       total_return_pct, sharpe_ratio, max_drawdown_pct, 
       win_rate_pct, total_trades, calculated_at
FROM strategy_performance
WHERE calculated_at > NOW() - INTERVAL '30 days'
ORDER BY calculated_at DESC;
```

**Query Main Strategy Performance:**
```sql
SELECT strategy_name, symbol,
       total_return_pct, sharpe_ratio, max_drawdown_pct, win_rate_pct
FROM strategy_performance
WHERE performance_mode = 'MAIN'
ORDER BY calculated_at DESC
LIMIT 1;
```

**Query Shadow Mode Performance:**
```sql
SELECT sp.strategy_name, sp.symbol, sp.total_return_pct, 
       sp.sharpe_ratio, sp.max_drawdown_pct, sp.win_rate_pct
FROM strategy_performance sp
WHERE sp.performance_mode = 'SHADOW'
  AND sp.calculated_at > NOW() - INTERVAL '7 days'
ORDER BY sp.sharpe_ratio DESC;
```

**Compare Main vs Shadow Strategies:**
```sql
WITH main_perf AS (
    SELECT strategy_name, total_return_pct, sharpe_ratio, max_drawdown_pct
    FROM strategy_performance
    WHERE performance_mode = 'MAIN'
    ORDER BY calculated_at DESC
    LIMIT 1
),
shadow_perf AS (
    SELECT strategy_name, symbol, total_return_pct, sharpe_ratio, max_drawdown_pct
    FROM strategy_performance
    WHERE performance_mode = 'SHADOW'
      AND calculated_at > NOW() - INTERVAL '7 days'
    ORDER BY sharpe_ratio DESC
)
SELECT 
    'MAIN' as mode, 
    m.strategy_name, 
    NULL as symbol,
    m.total_return_pct, 
    m.sharpe_ratio, 
    m.max_drawdown_pct
FROM main_perf m
UNION ALL
SELECT 
    'SHADOW' as mode,
    s.strategy_name,
    s.symbol,
    s.total_return_pct,
    s.sharpe_ratio,
    s.max_drawdown_pct
FROM shadow_perf s
ORDER BY sharpe_ratio DESC;
```

### Method 2: Telegram Bot Commands

#### `/status`
Shows current trading status including active strategy.

**Response includes:**
- Active strategy name
- Current position
- P&L (profit/loss)
- Trading mode
- Account equity

**Example:**
```
📊 TRADING STATUS
━━━━━━━━━━━━━━━━
Mode: STOCK (2454.TW)
Strategy: RSIStrategy
Position: 140 shares
P&L: +2,450 TWD
Equity: 85,200 TWD
```

#### `/ask` (without arguments)
Get AI-powered strategy recommendation based on recent performance.

**What it does:**
1. Queries `strategy_performance` table for best performer (last 30 days)
2. Analyzes Sharpe ratio, max drawdown, win rate, total return
3. Compares with current active strategy
4. Provides recommendation with metrics

**Example response:**
```
🤖 Strategy Recommendation
━━━━━━━━━━━━━━━━
Based on recent performance:

Current Strategy: RSIStrategy
Best Performer: Hull Moving Average (3711.TW)

Recommended Metrics:
📈 Sharpe Ratio: 1.85
📉 Max Drawdown: 8.3%
💰 Total Return: 12.4%
🎯 Win Rate: 68.5%

Consider switching with:
/set-main-strategy Hull Moving Average
```

#### `/insight`
Generate daily market insight (includes strategy performance summary).

**Response includes:**
- Market trend analysis
- Active strategy performance
- AI-generated insights

---

## 🔄 Automatic Strategy Switching

### DrawdownMonitorService

**Purpose:** Automatically switch strategies when main strategy exceeds maximum drawdown threshold.

**How it works:**

1. **Scheduled Monitoring**
   - Runs every 5 minutes during trading hours
   - Schedule: `0 */5 * * * MON-FRI` (Asia/Taipei timezone)

2. **Drawdown Calculation**
   - Monitors last 7 days of performance
   - Calculates Maximum Drawdown (MDD) percentage
   - Threshold: **15%**

3. **Breach Detection**
   - If MDD > 15%, triggers emergency procedure:
     - Flattens all positions immediately
     - Sends Telegram alert with current MDD
     - Queries best alternative strategy

4. **Strategy Selection**
   - Calls `StrategyPerformanceService.getBestPerformer(30)`
   - Evaluates strategies from last 30 days
   - Selects strategy with highest Sharpe ratio
   - Excludes current failing strategy

5. **Execution**
   - Updates `active_strategy_config` table
   - Sets `auto_switched = true`
   - Records `switch_reason` with MDD percentage
   - Sends Telegram notification with new strategy metrics

**Example Telegram notification:**
```
🚨 EMERGENCY: DRAWDOWN BREACH
━━━━━━━━━━━━━━━━
Strategy: RSIStrategy
Max Drawdown: 16.75%
Threshold: 15.00%
━━━━━━━━━━━━━━━━
⚠️ Flattening all positions...

✅ Strategy Switched
━━━━━━━━━━━━━━━━
From: RSIStrategy
To: Hull Moving Average
━━━━━━━━━━━━━━━━
New Strategy Metrics:
📈 Sharpe Ratio: 1.92
📉 Max Drawdown: 7.80%
💰 Total Return: 15.30%
🎯 Win Rate: 72.40%
```

---

## 🎯 Shadow Mode Strategy Selection

### How Strategies & Stocks Were Chosen

The 10 shadow mode configurations were selected based on **backtest results** from `docs/misc/backtest-result-20251214.md`.

**Selection Criteria:**
1. **Top Performing Stocks:** Selected 10 stocks with highest backtest returns
2. **Best Strategy per Stock:** Assigned each stock its best-performing strategy
3. **Diversification:** Included variety of strategy types (RSI, momentum, mean reversion, etc.)
4. **Risk-Adjusted:** Considered Sharpe ratio and maximum drawdown, not just returns

**Current Configuration:**

| Rank | Symbol | Stock Name | Strategy | Backtest Return |
|------|--------|------------|----------|----------------|
| 1 | 3711.TW | ASE Technology | Hull Moving Average | +32,048 TWD |
| 2 | 2317.TW | Hon Hai (Foxconn) | Aggressive RSI (7, 80/20) | +31,536 TWD |
| 3 | 1303.TW | Nan Ya Plastics | Price Volume Rank | +30,993 TWD |
| 4 | 1301.TW | Formosa Plastics | Aggressive RSI (7, 80/20) | +15,671 TWD |
| 5 | 2382.TW | Quanta Computer | Stochastic (14,3) | +15,562 TWD |
| 6 | 2357.TW | Asustek Computer | CCI Oscillator | +12,595 TWD |
| 7 | 2330.TW | TSMC | Aggressive RSI (7, 80/20) | +9,412 TWD |
| 8 | 2454.TW | MediaTek | RSI (14, 30/70) | +9,280 TWD ⭐ MAIN |
| 9 | 2882.TW | Cathay Financial | Pivot Points (Intraday) | +7,582 TWD |
| 10 | 2891.TW | CTBC Financial | Price Volume Rank | +6,857 TWD |

**Note:** MediaTek (2454.TW) is currently the main strategy because the system is hardcoded to trade this symbol in stock mode.

---

## 📱 Performance Notifications

### Automatic Notifications

**1. Strategy Switch Alerts**
- Sent when automatic strategy switching occurs
- Includes old strategy, new strategy, reason, and metrics
- Triggered by `DrawdownMonitorService`

**2. Daily Performance Reports**
- Sent at end of trading day
- Includes P&L, trades, win rate
- Triggered by `ReportingService`

**3. Emergency Alerts**
- Sent on drawdown breach (MDD > 15%)
- Sent on emergency shutdown
- Sent on risk limit breaches

### Manual Query Commands

**Get Performance:**
- `/status` - Current position and P&L
- `/ask` - AI strategy recommendation based on recent performance
- `/insight` - Daily market insight with performance summary

**Modify Strategy:**
- `/set-main-strategy <name>` - Manually switch main strategy
- `/strategy <name>` - (Deprecated, use `/set-main-strategy`)

---

## 🔧 Services & Components

### StrategyPerformanceService
**Purpose:** Calculate and store strategy performance metrics

**Key Methods:**
- `calculatePerformance()` - Compute metrics for a strategy/symbol/period
- `getBestPerformer(days)` - Find best strategy from last N days
- `recordPerformance()` - Store performance record in database

### ActiveStrategyService
**Purpose:** Manage active strategy configuration

**Key Methods:**
- `getActiveStrategyName()` - Get current main strategy
- `getActiveStrategyParameters()` - Get current strategy params
- `switchStrategy()` - Change active strategy (manual or auto)

### DrawdownMonitorService
**Purpose:** Monitor MDD and trigger automatic strategy switching

**Key Methods:**
- `monitorDrawdown()` - Scheduled check every 5 minutes
- `handleDrawdownBreach()` - Emergency switching logic

### ShadowModeStockService
**Purpose:** Manage shadow mode stock configurations

**Key Methods:**
- `getEnabledStocks()` - List all enabled shadow stocks
- `addOrUpdateStock()` - Add/modify shadow configuration
- `configureStocks()` - Bulk configure shadow stocks

---

## 📈 Performance Metrics Explained

### Sharpe Ratio
**Formula:** `(Return - Risk-Free Rate) / Standard Deviation`
- Measures risk-adjusted return
- Higher is better (> 1.0 is good, > 2.0 is excellent)
- Used as primary metric for strategy selection

### Maximum Drawdown (MDD)
**Formula:** `(Peak Value - Trough Value) / Peak Value × 100`
- Measures largest peak-to-trough decline
- Lower is better
- **Threshold:** 15% (triggers automatic switch)

### Win Rate
**Formula:** `(Winning Trades / Total Trades) × 100`
- Percentage of profitable trades
- Higher is better
- **Note:** High win rate doesn't guarantee profitability (consider avg win vs avg loss)

### Total Return
**Formula:** `(Final Equity - Initial Equity) / Initial Equity × 100`
- Overall percentage gain/loss
- Higher is better
- **Note:** Should be considered with MDD for risk-adjusted view

### Profit Factor
**Formula:** `Gross Profit / Gross Loss`
- Ratio of winning to losing trades
- > 1.0 means profitable
- > 2.0 is excellent

---

## ⚙️ Configuration

### Drawdown Threshold
**Location:** `DrawdownMonitorService.java`
```java
private static final double MAX_DRAWDOWN_THRESHOLD = 15.0; // 15%
```

### Performance Calculation Period
**Default:** Last 7 days for drawdown monitoring, 30 days for best performer selection

**Customization:** Modify calls to:
```java
strategyPerformanceService.calculatePerformance(
    strategyName,
    mode,
    periodStart,  // ← Customize this
    periodEnd,    // ← Customize this
    symbol,
    parameters
);
```

### Shadow Mode Stocks
**Location:** Database table `shadow_mode_stocks`

**Modify via:**
1. Direct SQL update
2. REST API (planned feature)
3. Telegram command (planned feature)

---

## 🔍 Troubleshooting

### No Performance Data
**Symptom:** `strategy_performance` table is empty

**Causes:**
- System hasn't executed trades yet
- Performance calculation service not running
- Insufficient historical data

**Solution:** Wait for trading day to complete, or manually trigger calculation

### Automatic Switching Not Working
**Symptom:** Strategy doesn't switch despite MDD > 15%

**Checks:**
1. Verify `DrawdownMonitorService` is enabled
2. Check logs for `@Scheduled` execution
3. Confirm `strategy_performance` table has recent data
4. Verify `StrategyPerformanceService.getBestPerformer()` returns valid results

### Telegram Notifications Not Received
**Checks:**
1. Verify Telegram bot token is correct in `application.yml`
2. Check `telegram.enabled = true`
3. Confirm chat ID is correct
4. Check logs for Telegram API errors

---

## 📚 Related Documentation

- [Release Notes](../RELEASE-20251213.md) - Feature overview
- [Testing Guide](../tests/TESTING.md) - Test coverage
- [Backtest Results](backtest-result-20251214.md) - Strategy selection rationale

---

**Last Updated:** December 15, 2025  
**Version:** 2.1.1
