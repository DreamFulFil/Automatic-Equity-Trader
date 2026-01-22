# Auto-selection prompt

Purpose: Run and validate the auto-selection workflow that picks the best strategy + stock from backtest results and updates shadow-mode candidates.

## Prerequisites
- Backtest results exist (run backtest first if needed).
- Java service is healthy: `curl -s http://localhost:16350/actuator/health | jq -r '.status'` returns `UP`.
- PostgreSQL container `psql` is running.

## Run (fish)
1) Verify backtest results exist
```fish
docker exec -i psql psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT COUNT(*) AS results FROM backtest_results WHERE total_trades > 10;"
```

2) Trigger auto-selection
```fish
curl -s -X POST http://localhost:16350/api/auto-selection/run-now | jq '.'
```

## Validate
Look for `"status":"success"` in the response and `AUTO-SELECTION` entries in `logs/java-*.log`.

### Active selection
```sql
SELECT strategy_name, stock_symbol, stock_name, updated_at
FROM active_strategy_config
ORDER BY updated_at DESC
LIMIT 1;
```

### Shadow-mode candidates (top 10)
```sql
SELECT stock_symbol, strategy_name, expected_return_percentage, rank_position, updated_at
FROM shadow_mode_stocks
ORDER BY rank_position ASC
LIMIT 10;
```

### Optional: legacy mapping table (if enabled)
```sql
SELECT stock_code, stock_name, strategy_name, is_active, confidence_score, created_at
FROM strategy_stock_mapping
WHERE is_active = true
ORDER BY confidence_score DESC
LIMIT 10;
```

## Expectations
- Selection time: typically < 5s.
- Active selection updated and shadow candidates refreshed.

## Troubleshooting
- No results: run backtest and verify `backtest_results` has rows.
- Shadow candidates not updating: check `logs/java-*.log` for exceptions and confirm the `shadow_mode_stocks` table exists.
- Health check not `UP`: start the Java service and re-run.

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
