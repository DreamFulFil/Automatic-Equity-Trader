# Historical Data Download Prompt

## Context
This prompt guides the AI agent through downloading historical data for Taiwan stocks using the Automatic-Equity-Trader system.

## Prerequisites
User must provide:
- **JASYPT Password**: Required for decrypting database credentials
- **Years of data**: Number of years to download (default: 10)

## Execution Steps

### 1. Verify Services Status

First, check if Java application and Python bridge are running:

```bash
# Check Java app (should be on port 16350)
lsof -i :16350

# Check Python bridge (should be on port 8888)
lsof -i :8888
```

### 2. Start Services (if not running)

**Start Java Application:**
```bash
cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
nohup jenv exec java -Djasypt.encryptor.password=<JASYPT_PASSWORD> -jar target/auto-equity-trader.jar > /tmp/java-bot.log 2>&1 &
```

**Start Python Bridge:**
```bash
cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader/python
JASYPT_PASSWORD=<JASYPT_PASSWORD> TRADING_MODE=stock ../python/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8888 > /tmp/bridge.log 2>&1 &
```

### 3. Wait for Services to be Healthy

Monitor health endpoints until both services respond:

```bash
# Wait for Java (up to 60 seconds)
for i in (seq 1 30)
    sleep 2
    set health (curl -s http://localhost:16350/actuator/health 2>/dev/null | jq -r '.status' 2>/dev/null)
    if test "$health" = "UP"
        echo "✅ Java service UP"
        break
    end
end

# Check Python bridge
curl -s http://localhost:8888/health | jq '.'
```

### 4. Trigger Historical Data Download

Replace `<YEARS>` with desired number of years (e.g., 10):

```bash
curl -s -X POST "http://localhost:16350/api/history/download?years=<YEARS>" &
```

### 5. Monitor Download Progress

Check database every 30 seconds to monitor progress:

```bash
# Monitor overall progress
docker exec psql psql -U dreamer -d auto_equity_trader -c "SELECT COUNT(*) as records, COUNT(DISTINCT symbol) as symbols, MIN(timestamp::date) as earliest, MAX(timestamp::date) as latest FROM bar;"

# Check per-symbol breakdown
docker exec psql psql -U dreamer -d auto_equity_trader -c "SELECT symbol, COUNT(*) as records, MIN(timestamp::date) as earliest, MAX(timestamp::date) as latest FROM bar GROUP BY symbol ORDER BY symbol LIMIT 20;"
```

### 6. Check Python Bridge Logs (if issues occur)

```bash
# Check for errors or rate limiting
tail -100 /tmp/bridge.log | rg -i "(error|fail|429|rate)"

# Check date ranges being fetched
tail -100 /tmp/bridge.log | rg "📅"

# Check data sources
tail -100 /tmp/bridge.log | rg "✅ Merged"
```

## Expected Results

- **Date Range**: Should see data from ~2018-2020 to current date (depending on stock availability)
  - Most stocks achieve 7+ years of historical coverage
  - Actual coverage depends on when each stock was listed
- **Coverage**: All 50 configured stocks should have data
- **Sources**: Data primarily from Yahoo Finance (multi-year historical), supplemented by Shioaji (recent data) and TWSE
- **Duration**: Full 10-year download takes approximately 5-10 minutes for 50 stocks
- **Expected Total**: ~130,000-140,000 records for 50 stocks over 7 years

## Data Source Priority

Current configuration (as of 2025-12-27):
1. **Yahoo Finance** (Primary): Best for multi-year historical data, no rate limiting observed in normal usage
2. **Shioaji** (Supplement): Limited to 2020-03-02 onwards for stocks, used to validate/fill recent data
3. **TWSE** (Supplement): Taiwan Stock Exchange direct data

This priority ensures:
- Maximum historical depth (back to 2015-2018 for most stocks)
- Reliable data coverage without hitting API limitations
- Yahoo Finance provides the broadest date range despite Shioaji being the official broker API

## Troubleshooting

### Issue: 0 records inserted
**Symptoms**: Download triggers but database count remains at 0

**Solutions**:
1. Check Python bridge is running: `lsof -i :8888`
2. Check Python logs: `tail -f /tmp/bridge.log`
3. Verify Shioaji connection: `curl http://localhost:8888/health | jq '.shioaji_connected'`
4. Restart Python bridge if disconnected

### Issue: Only 1 year of data despite requesting 10 years
**Symptoms**: All records show same 1-year date range

**Solutions**:
1. Check if date ranges are properly passed: `tail /tmp/bridge.log | rg "📅"`
2. Verify code uses explicit start_date/end_date (not just days from today)
3. Ensure `fetch_historical_data` receives start_date/end_date parameters from Java

### Issue: Duplicate data or repeated batches
**Symptoms**: Record count grows but date range doesn't expand

**Solutions**:
1. Database tables should be truncated at start of download
2. Check Java logs for batch download details: `tail /tmp/java-bot.log`
3. Verify no concurrent downloads running

## Notes

- Downloads are batched by year (365 days per batch) to avoid overwhelming the APIs
- Tables (`bar`, `market_data`, `strategy_stock_mapping`) are truncated at the start of download for clean data window
- Yahoo Finance provides the most comprehensive historical data without rate limiting
- Shioaji provides reliable recent data and is kept as supplement for data validation
