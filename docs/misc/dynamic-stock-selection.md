# Dynamic Stock Selection System

## Overview

The system now supports dynamic stock selection instead of hardcoded MediaTek (2454.TW). The active trading stock can be changed at runtime via Telegram command or database update.

## Key Changes

### 1. Active Stock Service
**File:** `ActiveStockService.java`

New service that manages the currently active stock symbol for trading in stock mode.

**Key Methods:**
- `getActiveStock()` - Get currently active stock from database
- `setActiveStock(symbol)` - Update active stock in database
- `getActiveSymbol(tradingMode)` - Get symbol based on trading mode (stock/futures)

**Storage:** PostgreSQL `system_config` table
- Key: `CURRENT_ACTIVE_STOCK`
- Value: Stock symbol (e.g., "2330.TW", "2454.TW")
- Default: "2454.TW" (MediaTek)

### 2. Services Updated
All services that previously had hardcoded `"2454.TW"` now use `ActiveStockService`:

- `TradingEngineService` ✅
- `StrategyManager` ✅  
- `TelegramCommandHandler` ✅
- `ReportingService` ✅
- `DrawdownMonitorService` ✅
- `EndOfDayStatisticsService` ✅

### 3. New Telegram Command

**Command:** `/change-stock <symbol>`

**Purpose:** Change the active trading stock in stock mode

**Usage:**
```
/change-stock 2330.TW    # Switch to TSMC
/change-stock 2317.TW    # Switch to Hon Hai
/change-stock 3711.TW    # Switch to ASE Technology
```

**Features:**
- Only works in STOCK mode (not FUTURES)
- Validates symbol format (XXXX.TW)
- Flattens existing position before switching
- Sends confirmation message with old/new stock
- Updates database configuration
- Error handling with fallback

**Example Response:**
```
✅ Active stock changed
━━━━━━━━━━━━━━━━
From: 2454.TW
To: 2330.TW
━━━━━━━━━━━━━━━━
System will now trade 2330.TW on next signal
```

## How It Works

### Database Configuration

```sql
-- View current active stock
SELECT config_key, config_value 
FROM system_config 
WHERE config_key = 'CURRENT_ACTIVE_STOCK';

-- Manually change active stock (alternative to Telegram command)
UPDATE system_config 
SET config_value = '2330.TW' 
WHERE config_key = 'CURRENT_ACTIVE_STOCK';
```

### Trading Flow

1. **Startup:** System loads active stock from database
2. **Signal Generation:** Strategies generate signals for active stock
3. **Order Execution:** Orders placed for active stock
4. **Position Management:** Tracks position for active stock
5. **Stock Change:** 
   - User sends `/change-stock 2330.TW`
   - System flattens position in old stock
   - Database updated with new stock
   - Future signals generated for new stock

### Failsafe Behavior

If database query fails:
- Returns default stock: "2454.TW" (MediaTek)
- Logs warning message
- System continues operating normally

## Strategy Optimization Recommendations

Since you asked **"What should I do at what time to get better profits?"**, here's the optimal approach:

### Daily Workflow

**Morning (Before 9:00 AM)**
1. Review shadow mode performance from previous day
2. Check which stock+strategy had best Sharpe ratio
3. If significantly better than current main stock, consider switching

**During Trading Hours (9:00-13:30)**
1. Let automated system run
2. Monitor Telegram notifications
3. **Automatic switching happens at:**
   - Every 5 minutes: Drawdown monitor checks MDD
   - If MDD > 15%: Auto-switches to best strategy
   - You get notified via Telegram

**End of Day (14:00)**
1. Daily statistics calculated automatically
2. Review performance report
3. Check which shadow stocks performed well

### Weekly Strategy

**Monday Morning:**
1. Review weekly performance data
2. Query database for best performers:
```sql
SELECT sp.symbol, sp.strategy_name, sp.total_return_pct, 
       sp.sharpe_ratio, sp.max_drawdown_pct, sp.win_rate_pct
FROM strategy_performance sp
WHERE sp.performance_mode = 'SHADOW'
  AND sp.calculated_at > NOW() - INTERVAL '7 days'
ORDER BY sp.sharpe_ratio DESC
LIMIT 5;
```
3. If a shadow stock consistently outperforms, switch to it:
   `/change-stock <symbol>`

### Optimal Decision Triggers

**When to Switch Stocks:**

1. **Performance Gap:** Shadow stock Sharpe ratio > Main stock by 0.5+
2. **Consistency:** Shadow stock wins 3+ days in a row
3. **Risk-Adjusted:** Shadow stock has lower MDD and higher return
4. **Win Rate:** Shadow stock win rate > Main stock by 10%+

**Example Decision Matrix:**

| Metric | Main (2454.TW) | Shadow (3711.TW) | Action |
|--------|---------------|------------------|---------|
| Sharpe Ratio | 1.2 | 1.85 | ✅ Switch |
| Max Drawdown | 12% | 7% | ✅ Switch |
| Win Rate | 62% | 74% | ✅ Switch |
| Total Return | 8% | 15% | ✅ Switch |

**Command:** `/change-stock 3711.TW`

### Automation Strategy

**Current Auto-Switching:**
- ✅ Strategy switches automatically when MDD > 15%
- ✅ Best strategy selected based on 30-day Sharpe ratio
- ✅ Telegram notification sent

**Stock Selection (Manual):**
- ⚠️ Stock must be changed manually via `/change-stock`
- ⚠️ User reviews shadow mode performance
- ⚠️ User makes informed decision

**Why Not Auto-Switch Stocks?**
1. **Risk Management:** Changing instruments mid-day riskier than strategy swap
2. **User Control:** Stock selection is strategic, not tactical
3. **Capital Efficiency:** One stock at a time minimizes capital requirements
4. **Regulatory:** Taiwan odd-lot rules differ per stock

### Best Practices

**DO:**
- ✅ Review shadow performance daily
- ✅ Switch stocks during non-trading hours (after 14:00 or before 9:00)
- ✅ Let automatic strategy switching handle intraday optimization
- ✅ Focus on top 3-5 stocks from shadow mode
- ✅ Consider transaction costs when switching
- ✅ Use /ask command for AI recommendation

**DON'T:**
- ❌ Switch stocks multiple times per day
- ❌ Switch during active positions (system flattens automatically, but avoid unnecessary trades)
- ❌ Ignore MDD warnings
- ❌ Chase yesterday's winners without analysis
- ❌ Override automatic strategy switches without good reason

### Performance Query Scripts

**Check Recent Shadow Performance:**
```python
import psycopg2

conn = psycopg2.connect(
    host="localhost",
    database="auto_equity_trader",
    user="dreamer",
    password="WSYS1r0PE0Ig0iuNX2aNi5k7"
)

cursor = conn.cursor()
cursor.execute("""
    SELECT symbol, strategy_name, 
           total_return_pct, sharpe_ratio, 
           max_drawdown_pct, win_rate_pct
    FROM strategy_performance
    WHERE performance_mode = 'SHADOW'
      AND calculated_at > NOW() - INTERVAL '7 days'
    ORDER BY sharpe_ratio DESC
    LIMIT 10;
""")

for row in cursor.fetchall():
    print(f"{row[0]} ({row[1]}): Return {row[2]:.2f}%, "
          f"Sharpe {row[3]:.2f}, MDD {row[4]:.2f}%, WR {row[5]:.2f}%")
```

**Compare Main vs Best Shadow:**
```python
# Get main performance
cursor.execute("""
    SELECT symbol, total_return_pct, sharpe_ratio, max_drawdown_pct
    FROM strategy_performance
    WHERE performance_mode = 'MAIN'
    ORDER BY calculated_at DESC LIMIT 1;
""")
main = cursor.fetchone()

# Get best shadow
cursor.execute("""
    SELECT symbol, total_return_pct, sharpe_ratio, max_drawdown_pct
    FROM strategy_performance
    WHERE performance_mode = 'SHADOW'
      AND calculated_at > NOW() - INTERVAL '7 days'
    ORDER BY sharpe_ratio DESC LIMIT 1;
""")
shadow = cursor.fetchone()

if shadow and main:
    sharpe_diff = shadow[2] - main[2]
    if sharpe_diff > 0.5:
        print(f"⚠️ Consider switching from {main[0]} to {shadow[0]}")
        print(f"   Sharpe improvement: {sharpe_diff:.2f}")
```

## Testing

**Unit Tests:** `ActiveStockServiceTest.java` - 11 test cases
**Integration:** Updated all service tests with ActiveStockService mock

**Test Coverage:**
- ✅ Get active stock from database
- ✅ Fallback to default on error
- ✅ Set active stock with validation
- ✅ Symbol format validation
- ✅ Trading mode detection
- ✅ Database error handling

## Migration

**Existing Systems:**
- Default stock remains: 2454.TW (MediaTek)
- No breaking changes
- Gradual migration supported

**To Switch Stock:**
1. Via Telegram: `/change-stock 2330.TW`
2. Via Database: `UPDATE system_config SET config_value = '2330.TW' WHERE config_key = 'CURRENT_ACTIVE_STOCK';`
3. Via API (future): `POST /api/config/active-stock {"symbol": "2330.TW"}`

---

**Version:** 2.1.2  
**Last Updated:** December 15, 2025  
**Status:** Production-Ready ✅
