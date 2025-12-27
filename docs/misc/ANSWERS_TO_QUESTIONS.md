# Answers to System Architecture Questions

**Date:** 2025-12-15  
**Version:** 2.1.2

---

## Table of Contents

1. [Stock vs Strategy Changes: Are They Coupled?](#1-stock-vs-strategy-changes-are-they-coupled)
2. [Performance Reporting System](#2-performance-reporting-system)
3. [Backtest Data Ranges](#3-backtest-data-ranges)
4. [Capital Management](#4-capital-management)
5. [Lot Trading Types](#5-lot-trading-types)

---

## 1. Stock vs Strategy Changes: Are They Coupled?

### Question
> Doesn't changing stock also means strategy change? But I see you only do one at a time?

### Answer

**No, they are independent but complementary dimensions:**

```
┌─────────────────────────────────────────────────────────┐
│  Trading Configuration = Stock × Strategy               │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Stock Dimension:    2454.TW, 2330.TW, 2317.TW, etc.   │
│  Strategy Dimension: RSI, Bollinger, MACD, etc.        │
│                                                          │
│  You can change:                                        │
│  - Stock only:     2454.TW + RSI → 2330.TW + RSI      │
│  - Strategy only:  2454.TW + RSI → 2454.TW + MACD     │
│  - Both:           2454.TW + RSI → 2330.TW + MACD     │
└─────────────────────────────────────────────────────────┘
```

### Why They Are Separate

**1. Stock Selection (`/change-stock`)**
- **What:** Changes the underlying instrument being traded
- **When:** Based on fundamental factors, liquidity, volatility profile
- **Frequency:** Typically weekly or less frequent
- **Telegram Command:** `/change-stock 2330.TW`
- **Storage:** `system_config` table, key `CURRENT_ACTIVE_STOCK`

**2. Strategy Selection (`/set-main-strategy`)**
- **What:** Changes the trading algorithm/logic
- **When:** Based on recent performance metrics
- **Frequency:** Can change multiple times per day (automated on MDD > 15%)
- **Telegram Command:** `/set-main-strategy RSI (14, 30/70)`
- **Storage:** `active_strategy` table

### Workflow Example

```
Morning (Before 9:00 AM):
├─ Review weekly report: scripts/weekly_performance_report.py
├─ Decision: "2330.TW has been outperforming 2454.TW all week"
└─ Action: /change-stock 2330.TW

During Trading (9:00-13:30):
├─ System monitors: Max Drawdown (MDD)
├─ Trigger: MDD exceeds 15% on current strategy
├─ Automatic action: Switch to best Sharpe ratio strategy
└─ Telegram notification sent

End of Day (14:30):
├─ Run daily report: scripts/daily_performance_report.py
├─ Review: Which stock+strategy combos performed best
└─ Plan for tomorrow
```

### One at a Time vs Simultaneous

**Current Implementation (One at a Time):**
- ✅ Safer: Less risk of compounding errors
- ✅ Traceable: Clear cause-effect relationship
- ✅ Controllable: User has explicit control

**Why Not Automatic Simultaneous Changes?**
1. **Risk amplification:** Changing both dimensions simultaneously is higher risk
2. **Attribution confusion:** Hard to know if results are due to stock or strategy
3. **User preference:** Strategic decisions should have human oversight
4. **Regulatory:** Taiwan stock market has specific odd-lot rules per stock

### Commands Summary

| Command | Changes | Frequency | Automation |
|---------|---------|-----------|------------|
| `/change-stock 2330.TW` | Stock only | Weekly | Manual |
| `/set-main-strategy RSI` | Strategy only | Daily | Manual + Auto (MDD trigger) |

---

## 2. Performance Reporting System

### Question
> Read docs/misc/dynamic-main-strategy-selection.md and write python scripts to generate a summary report (both daily and weekly), and suggestions to make a stock/strategy change (before market starts).

### Answer

**✅ IMPLEMENTED:** Two Python reporting scripts created:

### Daily Performance Report

**File:** `scripts/daily_performance_report.py`

**Features:**
- 📊 Main strategy performance (last 24 hours)
- 👥 Top 5 shadow strategies comparison
- 💡 Actionable recommendations with Telegram commands
- ⚠️ Risk warnings (MDD, win rate, consistency)

**Usage:**
```bash
# Run manually
python3 scripts/daily_performance_report.py

# Run with custom DB credentials
POSTGRES_PASSWORD=your_password python3 scripts/daily_performance_report.py

# Schedule with cron (daily at 14:35 after market close)
35 14 * * 1-5 cd /path/to/project && python3 scripts/daily_performance_report.py
```

**Output Example:**
```
======================================================================
📊 DAILY PERFORMANCE REPORT
======================================================================
Generated: 2025-12-15 14:35:00
======================================================================

🎯 Current Active Stock: 2454.TW

📈 MAIN STRATEGY PERFORMANCE (Last 24h)
============================================================
Strategy:        RSI (14, 30/70)
Symbol:          2454.TW
Total Return:      3.45%
Sharpe Ratio:      1.82
Max Drawdown:     -2.30%
Win Rate:         68.5%
Total Trades:       12
Winning Trades:      8

======================================================================
👥 TOP 5 SHADOW STRATEGIES (Last 24h)
======================================================================

#1 - Bollinger Band Mean Reversion (2330.TW)
    Sharpe:   2.15 | Return:   4.80% | Win Rate:  72.0% | MDD:  -1.80%

#2 - MACD Crossover (2454.TW)
    Sharpe:   1.95 | Return:   4.20% | Win Rate:  70.0% | MDD:  -2.10%

======================================================================
💡 RECOMMENDATIONS
======================================================================

🔄 STOCK CHANGE RECOMMENDED
   Current: 2454.TW (Sharpe: 1.82)
   Suggested: 2330.TW (Sharpe: 2.15)
   Command: /change-stock 2330.TW
```

### Weekly Performance Report

**File:** `scripts/weekly_performance_report.py`

**Features:**
- 📅 7-day aggregated metrics
- 📈 Performance trends (IMPROVING ⬆️ / DECLINING ⬇️ / STABLE ➡️)
- 🎯 Consistency scoring
- 💡 Strategic recommendations with rationale

**Usage:**
```bash
# Run manually
python3 scripts/weekly_performance_report.py

# Schedule with cron (Monday 8:30 AM before market opens)
30 8 * * 1 cd /path/to/project && python3 scripts/weekly_performance_report.py
```

**Output Example:**
```
======================================================================
📅 WEEKLY PERFORMANCE REPORT
======================================================================
Generated: 2025-12-15 08:30:00
Period: Last 7 days
======================================================================

📈 MAIN STRATEGY - WEEKLY AGGREGATE
======================================================================

Strategy:         RSI (14, 30/70)
Symbol:           2454.TW
Avg Return:         2.80%
Avg Sharpe:         1.65
Worst Drawdown:    -5.20%
Avg Win Rate:      65.3%
Total Trades:        85
Consistency:        0.72
Trend:            STABLE ➡️
Data Points:         7 days

======================================================================
💡 WEEKLY RECOMMENDATIONS
======================================================================

🎯 STOCK CHANGE RECOMMENDED FOR WEEK
   Current: 2454.TW (Sharpe: 1.65, Consistency: 0.72)
   Suggested: 2330.TW (Sharpe: 2.05, Consistency: 0.81)
   Expected improvement: 0.40 Sharpe points
   Command: /change-stock 2330.TW
```

### Recommendation Algorithm

Both scripts use a **multi-factor scoring system**:

```python
score = (sharpe_ratio * 0.35) + 
        (total_return / 10.0 * 0.25) + 
        (win_rate / 100.0 * 0.20) + 
        ((1 - abs(drawdown) / 20.0) * 0.20)
```

**Recommendation Triggers:**

| Condition | Action |
|-----------|--------|
| Sharpe diff > 0.5 | Stock change recommended |
| Sharpe diff > 0.3 | Strategy change recommended |
| MDD < -15% | High drawdown alert |
| Win rate < 45% (>10 trades) | Low win rate warning |
| Consistency < 0.4 | High variance warning |

---

## 3. Backtest Data Ranges

### Question
> Do we need to re-run the backtests? What were the backtests data date range?

### Answer

**Current Backtest Configuration:**

**Date Range:** Last **90 days** from execution date

**Evidence:**
```
File: docs/misc/backtest-result-20251214.md
Generated: 2025-12-14 19:52:59
Capital: 80,000 TWD
Period: Last 90 days
```

**Script:** `scripts/run_backtest_all_stocks.py`

### Do We Need to Re-run?

**✅ YES, re-run backtests if:**
1. ✅ Market conditions changed significantly (>30 days since last run)
2. ✅ New strategies added to the system
3. ✅ Parameter optimization needed for existing strategies
4. ✅ Stock universe expanded (currently 18 major Taiwan stocks)

**❌ NO, skip backtests if:**
1. ❌ Less than 1 week since last run
2. ❌ Only minor code refactoring (no strategy changes)
3. ❌ Shadow mode data is sufficient for decision-making

### How to Re-run Backtests

```bash
# Run full backtest suite (18 stocks × ~20 strategies)
cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
python3 scripts/run_backtest_all_stocks.py

# Results saved to:
# - docs/misc/backtest-result-YYYYMMDD.md
```

**Processing Time:** ~10-15 minutes for full suite

**Stocks Tested (18 major Taiwan stocks):**
```
1301.TW  - Formosa Plastics      2317.TW  - Hon Hai Precision
1303.TW  - Nan Ya Plastics       2330.TW  - TSMC
2002.TW  - China Steel           2454.TW  - MediaTek
2303.TW  - UMC                   2882.TW  - Cathay Financial
2412.TW  - Chunghwa Telecom      3008.TW  - LARGAN Precision
3711.TW  - ASE Technology        ... and more
```

### Backtest vs Shadow Mode

| Aspect | Backtest | Shadow Mode |
|--------|----------|-------------|
| **Data** | Historical (90 days) | Real-time live |
| **Purpose** | Strategy validation | Live comparison |
| **Frequency** | Weekly/monthly | Continuous |
| **Use case** | Initial strategy selection | Daily optimization |
| **Cost** | Free (historical data) | Real-time data fees |

**Recommendation:**
- **Backtests:** Re-run monthly or when major changes occur
- **Shadow mode:** Use for daily/weekly decision-making

---

## 4. Capital Management

### Question
> Currently all strategies are based on my capital fetched by shioaji API, right? So if I change capital manually, will the strategies be affected?

### Answer

**Complex Answer:** Capital comes from **multiple sources** with fallback mechanism.

### Capital Flow Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  Capital Determination Hierarchy                             │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  1. Shioaji API (Live)                                       │
│     ├─ Stock mode: api.account_balance().acc_balance        │
│     └─ Futures mode: api.list_accounts() → margin           │
│                                                               │
│  2. Database Settings (Fallback)                             │
│     ├─ stock_settings table: base capital for share calc    │
│     └─ system_config table: custom overrides                │
│                                                               │
│  3. Strategy-Level Defaults (Last resort)                    │
│     └─ Hardcoded: 80,000 TWD                                │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### Answer: Yes and No

**YES - Strategies ARE affected if you change:**

1. **Actual Shioaji Account Balance**
   ```python
   # In python/app/services/shioaji_service.py
   balance = self.api.account_balance()
   equity = float(balance.acc_balance)
   ```
   - **Impact:** ALL strategies adjust position sizes immediately
   - **Mechanism:** Next API call fetches new balance
   - **Timing:** Real-time (next market data refresh)

2. **Database Stock Settings**
   ```java
   // StockSettingsService.java
   public int getBaseStockQuantity(double equity) {
       StockSettings settings = getSettings();
       int baseShares = settings.getShares();  // Default: 70
       int increment = settings.getShareIncrement();  // Default: 27
       
       double baseCapital = 80000.0;
       double incrementCapital = 30000.0;
       
       int additionalShares = 0;
       if (equity > baseCapital) {
           additionalShares = (int) ((equity - baseCapital) / incrementCapital) * increment;
       }
       return baseShares + additionalShares;
   }
   ```
   - **Impact:** Changes share calculation formula
   - **Mechanism:** Database update propagates to Java service
   - **Timing:** Next calculation cycle

**NO - Strategies NOT affected if:**

1. You manually edit database but Shioaji API still returns old value (API takes precedence)
2. You change backtest capital (only affects backtest results, not live trading)

### How to Manually Override Capital

**Method 1: Change Actual Trading Capital (Recommended)**
- Deposit/withdraw funds in your Sinopac/Shioaji brokerage account
- System automatically detects on next balance check

**Method 2: Update Database Settings (Affects Calculation)**
```sql
-- Change base shares (for stock mode)
UPDATE stock_settings 
SET shares = 100, 
    share_increment = 30 
WHERE id = 1;

-- System will recalculate position sizes based on:
-- shares = 100 + (equity - 80000) / 30000 * 30
```

**Method 3: Temporary Override (Not Implemented)**
- **Future feature:** Could add `CAPITAL_OVERRIDE` in `system_config`
- Would allow testing without changing actual account balance

### Capital Usage in Different Modes

**1. Backtest Mode**
```java
// BacktestService.java - Line 24
public Map<String, BacktestResult> runBacktest(
    List<IStrategy> strategies, 
    List<MarketData> history, 
    double initialCapital  // Passed as parameter, default 80000
) {
    // ...
}
```
- **Fixed:** 80,000 TWD (default)
- **Configurable:** Can pass different value when calling backtest endpoint
- **Impact:** Only affects backtest results, not live trading

**2. Live/Simulation Trading**
```python
# ShioajiWrapper.get_portfolio()
balance = self.api.account_balance()
equity = float(balance.acc_balance)  # Fetched from API
```
- **Dynamic:** Fetched in real-time from Shioaji API
- **Updated:** Every market data tick (~1-5 seconds)
- **Impact:** Directly affects order quantities

**3. Shadow Mode**
- Uses same capital as main strategy
- Simulates trades without executing
- Position sizes calculated based on API capital

### Capital Scaling Example

**Scenario:** Your capital grows from 80,000 → 140,000 TWD

```
Initial Capital: 80,000 TWD
├─ Base shares: 70
└─ Price per share: ~1,143 TWD (80,000 / 70)

New Capital: 140,000 TWD
├─ Additional capital: 60,000 TWD
├─ Increment units: 60,000 / 30,000 = 2 units
├─ Additional shares: 2 × 27 = 54 shares
└─ Total shares: 70 + 54 = 124 shares

Result: Position size automatically scales up by 77%
```

### Important Notes

⚠️ **Capital synchronization:**
- Java backend calls Python bridge every market data tick
- Python bridge queries Shioaji API
- Fresh balance returned with each portfolio update
- **Latency:** ~100-500ms

⚠️ **Database vs API precedence:**
- API balance takes precedence for live trading
- Database settings only affect **calculation logic**
- To truly change capital, change account balance, not database

---

## 5. Lot Trading Types

### Question
> Are we only doing odd lot trading? What about round/even lot trading?

### Answer

**Currently: ODD LOT ONLY for stocks**

### Lot Types Explained

**Taiwan Stock Market Lot Rules:**

```
┌────────────────────────────────────────────────────────────┐
│  Taiwan Stock Exchange Lot Types                           │
├────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Round Lot (整股)                                        │
│     - Unit: 1000 shares                                    │
│     - Trading hours: 9:00-13:30                           │
│     - Liquidity: HIGH                                      │
│     - Spread: TIGHT                                        │
│     - Min capital: ~NT$ 1,000,000 for major stocks        │
│                                                             │
│  2. Odd Lot (零股)                                          │
│     - Unit: 1-999 shares                                   │
│     - Trading hours: 9:00-13:30 (since 2020 reform)       │
│     - Liquidity: LOWER                                     │
│     - Spread: WIDER                                        │
│     - Min capital: ~NT$ 50,000-200,000                    │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

### Current Implementation

**Stock Mode: ODD LOT ONLY**

**Evidence from code:**

```python
# python/app/services/shioaji_service.py - Line 33
# Modes:
# - "stock": Uses api.Contracts.Stocks.TSE["2454"] for 2454.TW odd lots

# python/app/services/shioaji_service.py - Line 302
order_lot=sj.constant.StockOrderLot.Odd,  # Regular odd lot trading
```

```java
// TradingEngineService.java - Line 67
"stock".equals(tradingStateService.getTradingMode()) 
    ? activeStockService.getActiveStock() + " odd lots" 
    : "MTXF futures"
```

**Futures Mode: Contract-based (not lot-based)**
- Uses MTXF (Mini Taiwan Index Futures)
- Each contract = 1 unit
- Different mechanism from stock lots

### Why Odd Lot Only?

**1. Capital Constraints**
```
Your typical capital: ~80,000-120,000 TWD

Stock prices:
├─ 2330.TW (TSMC): ~1,000 TWD/share
├─ 2454.TW (MediaTek): ~1,200 TWD/share
└─ Round lot: 1000 shares

Round lot cost:
├─ TSMC: 1000 × 1,000 = 1,000,000 TWD (too expensive!)
└─ MediaTek: 1000 × 1,200 = 1,200,000 TWD (too expensive!)

Odd lot allows:
├─ 70-100 shares = 70,000-120,000 TWD ✅
└─ Matches your capital perfectly
```

**2. Risk Management**
- Smaller position sizes = lower risk per trade
- Better diversification possible with limited capital
- Easier to scale gradually

**3. Automation Strategy**
- Current system targets retail/small investors
- Odd lot is more suitable for algo-trading at small scale
- Lower minimum capital requirement

### Can We Add Round Lot Trading?

**YES, but requires:**

**1. Code Changes**
```python
# In shioaji_service.py
order_lot=sj.constant.StockOrderLot.Common,  # Round lot (1000 shares)

# Quantity must be multiples of 1000
if quantity < 1000:
    raise ValueError("Round lot requires min 1000 shares")

quantity = (quantity // 1000) * 1000  # Round down to nearest 1000
```

**2. Capital Requirements**
- Minimum: ~1,000,000 TWD for major stocks
- Recommended: 3,000,000+ TWD for diversification

**3. Strategy Adjustments**
```java
// StockSettingsService.java
public int getBaseStockQuantity(double equity) {
    if (equity >= 1000000) {
        // Round lot: quantities in 1000s
        return ((int) (equity / averagePrice) / 1000) * 1000;
    } else {
        // Odd lot: current logic
        return baseShares + additionalShares;
    }
}
```

**4. Configuration Flag**
```sql
-- Add to system_config table
INSERT INTO system_config (config_key, config_value)
VALUES ('STOCK_LOT_TYPE', 'ODD');  -- or 'ROUND'
```

### Feature Comparison

| Aspect | Odd Lot (Current) | Round Lot (Potential) |
|--------|-------------------|----------------------|
| **Min Capital** | 50,000 TWD | 1,000,000 TWD |
| **Flexibility** | High (any quantity 1-999) | Low (1000 multiples only) |
| **Liquidity** | Lower | Higher |
| **Spread** | Wider (0.2-0.5%) | Tighter (0.05-0.1%) |
| **Slippage** | Higher risk | Lower risk |
| **Implementation** | ✅ Done | ❌ Not implemented |
| **Suitable for** | Retail, algo-testing | Institutions, large capital |

### Hybrid Approach (Future Enhancement)

**Proposed feature:**
```java
// Dynamic lot type selection
public LotType selectLotType(double equity, double price) {
    double requiredCapital = 1000 * price;
    
    if (equity >= requiredCapital * 2) {
        // Use round lot if we have 2x the capital needed
        return LotType.ROUND;
    } else {
        // Use odd lot for flexibility
        return LotType.ODD;
    }
}
```

**Benefits:**
- Automatically switch to round lot when capital grows
- Better execution quality at scale
- Still flexible for small accounts

### Recommendation

**Current situation (capital ~80,000-120,000 TWD):**
- ✅ **Keep odd lot** - It's the right choice for your capital level
- ❌ **Don't implement round lot yet** - Insufficient capital

**Future (when capital > 1,500,000 TWD):**
- Consider implementing round lot support
- Use round lot for main positions
- Keep odd lot for fractional adjustments

---

## Summary Table

| Question | Short Answer | Implementation Status |
|----------|--------------|----------------------|
| **Stock vs Strategy coupling?** | Independent dimensions | ✅ Implemented separately |
| **Daily/Weekly reports?** | Python scripts created | ✅ Implemented + tested |
| **Backtest date range?** | Last 90 days | ✅ Documented |
| **Re-run backtests?** | Yes, if >30 days old | ⚠️ Manual process |
| **Capital source?** | Shioaji API (real-time) | ✅ Dynamic fetching |
| **Manual capital change?** | Changes database, but API takes precedence | ℹ️ Complex behavior |
| **Lot type?** | Odd lot only | ✅ Implemented |
| **Round lot support?** | Not implemented | ❌ Not needed yet |

---

## Quick Reference Commands

```bash
# Daily reporting (run after market close 14:30)
python3 scripts/daily_performance_report.py

# Weekly reporting (run Monday 8:30 before market)
python3 scripts/weekly_performance_report.py

# Change stock
Telegram: /change-stock 2330.TW

# Change strategy
Telegram: /set-main-strategy RSI (14, 30/70)

# Get recommendation
Telegram: /ask

# Re-run backtests (monthly)
python3 scripts/run_backtest_all_stocks.py

# Check current capital
Telegram: /status
```

---

**Last Updated:** 2025-12-15  
**Next Review:** 2026-01-15  
**Status:** ✅ Complete and tested
