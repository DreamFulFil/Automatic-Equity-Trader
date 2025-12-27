# Auto-Selection Execution Prompt

This guide walks through running the auto-selection process to dynamically select the best-performing strategy and stocks based on backtest results.

## Prerequisites

Before running auto-selection:

1. **Backtest Results Must Exist**
   - Auto-selection analyzes backtest_results table to rank strategies
   - Run backtest first: See [backtest.prompt.md](backtest.prompt.md)
   - Verify: `SELECT COUNT(*) FROM backtest_results;`
   - Expected: 2,700+ records (50 stocks × 99 strategies minimum)

2. **Java Application Running**
   - Health check: `curl http://localhost:16350/actuator/health`
   - Expected response: `{"status":"UP"}`

## Step 1: Verify Backtest Results Available

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \ I
  "SELECT COUNT(*) as total_results, 
          COUNT(DISTINCT symbol) as unique_stocks,
          COUNT(DISTINCT strategy_name) as unique_strategies
   FROM backtest_results
   WHERE total_trades > 10;"
```

**Expected Output:**
```
 total_results | unique_stocks | unique_strategies 
---------------+---------------+-------------------
          3000 |            50 |                99
```

## Step 2: Check Current Active Configuration

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \
  "SELECT stock_code, stock_name, strategy_name, is_active, confidence_score
   FROM strategy_stock_mapping
   WHERE is_active = true
   ORDER BY confidence_score DESC
   LIMIT 10;"
```

This shows the currently active strategy+stock combinations.

## Step 3: Trigger Auto-Selection

```bash
curl -X POST http://localhost:16350/api/auto-selection/run-now | jq '.'
```

**Expected Response:**
```json
{
  "status": "success",
  "message": "Auto-selection completed successfully"
}
```

**What Happens:**
1. `selectBestStrategyAndStock()`: Analyzes backtest results, ranks strategies by performance metrics (win rate, Sharpe ratio, profit factor), selects top performer
2. `selectShadowModeStrategies()`: Selects top 10 strategies for shadow mode monitoring
3. Updates `strategy_stock_mapping` table with new active configurations
4. Deactivates previous selections

## Step 4: Monitor Execution

```bash
tail -100 logs/java-*.log | rg -i "(auto.?selection|strategy.?selector)" | tail -20
```

**Expected Log Patterns:**
```
INFO  t.g.a.e.t.c.AutoSelectionController - 🎯 Manual trigger: Running auto-selection NOW
INFO  t.g.a.e.t.s.AutoStrategySelector - 🤖 AUTO-SELECTION: Starting daily strategy and stock selection...
INFO  t.g.a.e.t.s.AutoStrategySelector - 🏆 SELECTED: Strategy [Bollinger Band] + Stock [2330.TW]
INFO  t.g.a.e.t.s.AutoStrategySelector - 🌙 AUTO-SELECTION: Selecting top 10 shadow mode strategies...
INFO  t.g.a.e.t.s.AutoStrategySelector - ✅ Shadow mode strategies updated
```

**Warning: If no backtest results exist:**
```
WARN  t.g.a.e.t.s.AutoStrategySelector - ⚠️ No backtest results found. Skipping auto-selection.
```
→ Run backtest first (see [backtest.prompt.md](backtest.prompt.md))

## Step 5: Verify Selection Results

### Check Active Strategy+Stock Mapping

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \
  "SELECT stock_code, stock_name, strategy_name, is_active, confidence_score, created_at
   FROM strategy_stock_mapping
   WHERE is_active = true
   ORDER BY confidence_score DESC;"
```

**Expected Output:**
```
 stock_code | stock_name |     strategy_name      | is_active | confidence_score |        created_at         
------------+------------+------------------------+-----------+------------------+---------------------------
 2330.TW    | 台積電     | Bollinger Band         | t         |            0.850 | 2025-01-19 14:22:00
```

### Check Shadow Mode Strategies

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \
  "SELECT stock_code, strategy_name, confidence_score
   FROM strategy_stock_mapping
   WHERE is_active = true
   ORDER BY confidence_score DESC
   LIMIT 10;"
```

This shows the top 10 strategies selected for shadow mode monitoring.

## Step 6: Analyze Selection Quality

### View Performance Metrics of Selected Strategy

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \
  "SELECT symbol, strategy_name, win_rate_pct, sharpe_ratio, total_return_pct, profit_factor, total_trades
   FROM backtest_results
   WHERE symbol = '2330.TW' AND strategy_name = 'Bollinger Band Mean Reversion'
   LIMIT 1;"
```

### Compare Top 10 Strategies by Win Rate

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \
  "SELECT symbol, strategy_name, 
          ROUND(CAST(win_rate_pct AS numeric), 2) as win_rate,
          ROUND(CAST(sharpe_ratio AS numeric), 2) as sharpe,
          ROUND(CAST(total_return_pct AS numeric), 2) as return_pct,
          total_trades
   FROM backtest_results
   WHERE total_trades > 10
   ORDER BY win_rate_pct DESC
   LIMIT 10;"
```

### Check Stock Rankings

```bash
docker exec -i psql psql -U postgres -d auto_equity_trader -c \
  "SELECT symbol, 
          COUNT(*) as strategy_count,
          ROUND(CAST(AVG(win_rate_pct) AS numeric), 2) as avg_win_rate,
          ROUND(CAST(AVG(sharpe_ratio) AS numeric), 2) as avg_sharpe
   FROM backtest_results
   WHERE total_trades > 10
   GROUP BY symbol
   ORDER BY avg_win_rate DESC
   LIMIT 10;"
```

## Step 7: Verify System is Trading Selected Strategy

Check that the system starts using the newly selected strategy:

```bash
tail -50 logs/java-*.log | rg -i "(strategy.*signal|trade)" | tail -10
```

**Expected Pattern:**
```
INFO  t.g.a.e.t.services.StrategyManager - 💡 Strategy [Bollinger Band Mean Reversion] Signal: LONG
INFO  t.g.a.e.t.services.StrategyManager - 👻 Shadow Trade [Bollinger Band]: BUY 1 @ 635.0
```

## Expected Results

- **Selection Time:** < 5 seconds
- **Active Strategy:** 1 best-performing strategy+stock combination
- **Shadow Strategies:** Top 10 strategies for monitoring
- **Confidence Score:** 0.7-0.95 (based on win rate, Sharpe ratio, profit factor)
- **Database Updates:** `strategy_stock_mapping` table updated with `is_active = true`

## Troubleshooting

### Issue: "No backtest results found"

**Cause:** `backtest_results` table is empty or filtered out all records

**Solution:**
1. Run backtest first: `curl -X POST http://localhost:16350/api/backtest/run`
2. Wait for completion (check logs)
3. Verify results: `SELECT COUNT(*) FROM backtest_results WHERE total_trades > 10;`
4. Re-run auto-selection

### Issue: Selection keeps choosing same strategy

**Cause:** Only one strategy has acceptable performance metrics

**Solution:**
1. Check diversity of backtest results:
   ```bash
   docker exec -i psql psql -U postgres -d auto_equity_trader -c \
     "SELECT strategy_name, COUNT(*) as stock_count, AVG(win_rate_pct) as avg_win_rate
      FROM backtest_results
      WHERE total_trades > 10
      GROUP BY strategy_name
      ORDER BY avg_win_rate DESC
      LIMIT 20;"
   ```
2. Review strategy parameter configurations in `strategy_stock_mapping`
3. Consider adjusting selection criteria in `AutoStrategySelector.java`

### Issue: No active strategies after auto-selection

**Cause:** All strategies failed minimum performance criteria

**Solution:**
1. Check selection thresholds:
   - Minimum win rate (typically 40-50%)
   - Minimum Sharpe ratio (typically 0.5)
   - Minimum total trades (typically 10)
2. Review logs for selection criteria:
   ```bash
   tail -200 logs/java-*.log | rg -i "(selection|threshold|criteria)"
   ```
3. Lower thresholds in configuration if backtest results are poor across the board

### Issue: Shadow strategies not updating

**Cause:** `selectShadowModeStrategies()` failing silently

**Solution:**
1. Check for errors in logs:
   ```bash
   tail -200 logs/java-*.log | rg -i "(shadow|error|exception)"
   ```
2. Verify database connection: `curl http://localhost:16350/actuator/health`
3. Check `strategy_stock_mapping` table structure:
   ```bash
   docker exec -i psql psql -U postgres -d auto_equity_trader -c "\d strategy_stock_mapping"
   ```

## Data Verification Checklist

✅ Backtest results exist (3,000+ records)  
✅ At least 10 strategies have total_trades > 10  
✅ At least 10 stocks have been backtested  
✅ Auto-selection API responds with "success"  
✅ Java logs show "AUTO-SELECTION" completed  
✅ `strategy_stock_mapping` has is_active = true records  
✅ Confidence scores are reasonable (0.7-0.95)  
✅ System logs show new strategy being used  

## Scheduled Auto-Selection

Auto-selection runs automatically via scheduled task:

```java
@Scheduled(cron = "${app.auto-selection.schedule:0 0 1 * * *}")  // 1 AM daily
public void scheduledAutoSelection() {
    selectBestStrategyAndStock();
    selectShadowModeStrategies();
}
```

**Manual trigger is useful for:**
- Testing after backtest completion
- Immediate strategy update after market close
- Testing configuration changes
- Ad-hoc performance analysis

## Related Documentation

- [Backtest Execution](backtest.prompt.md) - Run backtests to generate data for auto-selection
- [Download Historical Data](download-historical-data.prompt.md) - Populate historical data needed for backtests
- [Dynamic Stock Selection](../docs/architecture/DYNAMIC_STOCK_SELECTION.md) - Architecture overview
- [Strategy Pattern](../docs/architecture/STRATEGY_PATTERN.md) - Strategy design and implementation

## Summary

Auto-selection dynamically chooses the best-performing strategy and stocks based on backtest results, enabling the system to adapt to changing market conditions. Always run backtest first, verify results, then trigger auto-selection. Monitor logs to confirm successful selection and verify the system starts using the new configuration.
