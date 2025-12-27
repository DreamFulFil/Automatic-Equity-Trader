# Backtest Prompt

## Context
This prompt guides the AI agent through running comprehensive backtesting against historical data for all Taiwan stocks using the Automatic-Equity-Trader system.

## Prerequisites
User must provide:
- **JASYPT Password**: Required for decrypting database credentials
- **Historical Data**: Must have historical data loaded (use download-historical-data.prompt.md first)

## Execution Steps

### 1. Verify Historical Data is Loaded

Check if database has sufficient historical data:

```bash
docker exec psql psql -U dreamer -d auto_equity_trader -c "SELECT COUNT(*) as records, COUNT(DISTINCT symbol) as stocks, MIN(timestamp::date) as earliest, MAX(timestamp::date) as latest FROM bar;"
```

Expected:
- **Records**: ~80,000-140,000 for 50 stocks over 7-10 years
- **Stocks**: 50 (all configured stocks)
- **Date Range**: Should span multiple years (e.g., 2015-2025)

If data is insufficient, run the download prompt first.

### 2. Verify Java Application is Running

Check if Java application is healthy:

```bash
curl -s http://localhost:16350/actuator/health | jq -r '.status'
```

Expected output: `UP`

If not running, start the Java application:

```bash
cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
mkdir -p logs
LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
nohup jenv exec java -Djasypt.encryptor.password=<JASYPT_PASSWORD> -jar target/auto-equity-trader.jar > "logs/java-${LOG_TS}.log" 2>&1 &
```

Wait for health check to pass:

```bash
for i in (seq 1 30)
    sleep 2
    set health (curl -s http://localhost:16350/actuator/health 2>/dev/null | jq -r '.status' 2>/dev/null)
    if test "$health" = "UP"
        echo "✅ Java service UP after "(math "$i * 2")" seconds"
        break
    end
end
```

### 3. Trigger Backtest Execution

Run backtesting against all historical data:

```bash
LOG_TS=$(date -u +%Y%m%dT%H%M%SZ)
curl -s -X POST "http://localhost:16350/api/backtest/run" -o "logs/backtest-result-${LOG_TS}.json" &
```

This endpoint will:
- Load all historical bar data from the database
- Test each stock against each strategy (50 stocks × 54 strategies = 2,700 combinations)
- Calculate performance metrics (win rate, profit/loss, trade count)
- Persist results to `backtest_result` table

### 4. Monitor Backtest Progress

Monitor Java logs for progress:

```bash
tail -f logs/java-*.log | rg -i "(backtest|strategy|stock)"
```

Key log patterns to watch for:
- `🎯 Running backtest` - Backtest started
- `📊 Testing stock` - Individual stock being tested
- `✅ Parallelized backtest completed` - All tests finished
- `✅ Backtesting completed` - Final summary

### 5. Check Backtest Results

Once backtest completes, check the results:

```bash
# Wait for completion (typically 20-60 seconds)
sleep 60

# Check result file
ls -1 logs/backtest-result-*.json | tail -n1 | xargs -I {} sh -c 'cat "{}" | jq "{stocksTested, strategiesTested, totalResults, duration: .completionTime}"' 

# Verify results in database
docker exec psql psql -U dreamer -d auto_equity_trader -c "SELECT COUNT(*) as total_results, COUNT(DISTINCT stock_code) as stocks_tested, COUNT(DISTINCT strategy_name) as strategies_tested FROM backtest_result;"
```

Expected results:
- **Total Results**: ~2,700 (50 stocks × 54 strategies)
- **Stocks Tested**: 50
- **Strategies Tested**: 54
- **Duration**: 20-60 seconds (parallelized execution)

### 6. Analyze Top Performing Strategies

Query for best performing strategies:

```bash
docker exec psql psql -U dreamer -d auto_equity_trader -c "
SELECT 
    strategy_name,
    stock_code,
    win_rate,
    total_trades,
    total_pnl
FROM backtest_result
WHERE total_trades > 10
ORDER BY win_rate DESC
LIMIT 20;
"
```

### 7. Verify Data Integrity

Ensure backtest covered the expected date range:

```bash
docker exec psql psql -U dreamer -d auto_equity_trader -c "
SELECT 
    stock_code,
    COUNT(*) as result_count,
    MIN(total_trades) as min_trades,
    MAX(total_trades) as max_trades
FROM backtest_result
GROUP BY stock_code
ORDER BY stock_code
LIMIT 10;
"
```

## Expected Results

- **Execution Time**: 20-60 seconds for 50 stocks × 54 strategies
- **Total Results**: ~2,700-3,000 backtest records
- **Coverage**: All 50 configured stocks tested against all strategies
- **Win Rate Distribution**: Typically ranges from 0% to 30% depending on strategy and stock
- **Data Persisted**: Results saved to `backtest_result` table for later analysis

## Performance Characteristics

- **Parallelization**: Uses virtual threads for concurrent stock processing
- **Memory Efficient**: Streams historical data, batch inserts results
- **Database Writer**: Buffered writer with final flush for optimal insert performance
- **Typical Output**: 
  ```
  ✅ Parallelized backtest completed. 50 stocks tested, 2700 results persisted.
  ✅ Backtesting completed (took 25000ms)
  ```

## Troubleshooting

### Issue: "No historical data found"
**Symptoms**: Backtest returns 0 results or errors about missing data

**Solutions**:
1. Verify database has data: `SELECT COUNT(*) FROM bar;`
2. If empty, run historical data download first
3. Check date ranges match: `SELECT MIN(timestamp), MAX(timestamp) FROM bar;`

### Issue: Backtest takes too long (>5 minutes)
**Symptoms**: Backtest hangs or takes excessive time

**Solutions**:
1. Check if database has excessive records: `SELECT COUNT(*) FROM bar;`
2. Monitor Java logs for stuck threads: `tail -f logs/java-*.log`
3. Restart Java application if frozen

### Issue: Partial results (less than expected)
**Symptoms**: Only some stocks/strategies tested

**Solutions**:
1. Check Java logs for errors: `tail -100 logs/java-*.log | rg -i error`
2. Verify all stocks have data: `SELECT symbol, COUNT(*) FROM bar GROUP BY symbol;`
3. Re-run backtest if incomplete

## Notes

- Backtest is **historical simulation** only - does not execute real trades
- Results are cached in database for quick retrieval
- Can be re-run at any time to test new strategies or updated data
- Win rate alone doesn't guarantee profitability - consider trade frequency and P&L
- Strategies with very few trades (<10) may have unreliable win rates
