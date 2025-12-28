# Historical Data Download Prompt

## Context
This prompt guides the AI agent through downloading historical data for Taiwan stocks using the Automatic-Equity-Trader system.

**Non-interactive:** This document includes runnable, non-interactive fish scripts to automate the full download, verification of 5-year coverage, and auto-triggering of the backtest.

**Runtime logs:** All runtime logs and temporary outputs are written to the repository's `logs/` directory (not `/tmp`).

## Prerequisites
User must provide:
- **JASYPT Password**: Must be exported as environment variable `JASYPT_PASSWORD` (fish example: `set -x JASYPT_PASSWORD mysecret`) — required for decrypting database credentials
- **Years of data**: Number of years to download (default: 5)

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

**Start Java Application (fish):**
```fish
# Ensure JASYPT_PASSWORD is exported in fish (e.g. `set -x JASYPT_PASSWORD mysecret`)
if not set -q JASYPT_PASSWORD
    echo "❌ JASYPT_PASSWORD not set in environment; aborting non-interactive run"
    exit 1
end

cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
mkdir -p logs
set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)
nohup jenv exec java -Djasypt.encryptor.password=$JASYPT_PASSWORD -jar target/auto-equity-trader.jar > "logs/java-${LOG_TS}.log" 2>&1 &
```

**Start Python Bridge (fish):**
```fish
# Ensure JASYPT_PASSWORD is exported in fish (e.g. `set -x JASYPT_PASSWORD mysecret`)
if not set -q JASYPT_PASSWORD
    echo "❌ JASYPT_PASSWORD not set in environment; aborting non-interactive run"
    exit 1
end

cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader/python
set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)
# Export TRADING_MODE for the uvicorn process and run (JASYPT_PASSWORD should already be exported)
set -x TRADING_MODE stock
../python/venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8888 > "logs/bridge-${LOG_TS}.log" 2>&1 &
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

### 4. Trigger Historical Data Download (non-interactive)

Replace `<YEARS>` with desired number of years (e.g., 5):

```fish
# Non-interactive: trigger the historical download in background and save the response
set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)
curl -s -X POST "http://localhost:16350/api/history/download?years=<YEARS>" -o "logs/history-download-${LOG_TS}.json" &
```

#### 4b. Verify 5-year coverage and auto-trigger backtest (non-interactive)

This script polls the DB until it detects at least **5 years** of history (MIN(timestamp) <= CURRENT_DATE - INTERVAL '5 years') and at least **50 symbols** (expected coverage). If checks pass, it automatically triggers the backtest endpoint.

```fish
# Poll DB (up to max attempts) and on success trigger backtest automatically
set -l attempts 0
set -l max_attempts 40         # 40 * 30s = 20 minutes
set -l sleep_secs 30
set -l expected_symbols 50

while test $attempts -lt $max_attempts
    echo "Checking historical coverage (attempt $attempts)..."
    set -l row (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT (MIN(timestamp::date) <= (CURRENT_DATE - INTERVAL '5 years'))::text || '|' || COUNT(*) || '|' || COUNT(DISTINCT symbol) FROM bar;")
    if test -n "$row"
        set -l parts (string split '|' $row)
        set -l has_5yr $parts[1]
        set -l records $parts[2]
        set -l symbols $parts[3]
        echo "DB rows: $records, symbols: $symbols, has_5yr: $has_5yr"

        # Check if any symbol is missing 5 years of data
        set -l missing_count (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COUNT(*) FROM (SELECT symbol, MIN(timestamp::date) as earliest FROM bar GROUP BY symbol HAVING MIN(timestamp::date) > (CURRENT_DATE - INTERVAL '5 years')) t;")
        echo "Symbols missing 5-year coverage: $missing_count"

        if test "$has_5yr" = 't' -a $symbols -ge $expected_symbols -a $missing_count -eq 0
            echo "✅ 5-year coverage detected for all symbols (symbols: $symbols). Triggering backtest wrapper script..."
            set -l BT_TS (date -u +%Y%m%dT%H%M%SZ)
            # Launch the canonical non-interactive backtest script and detach; log output
            mkdir -p logs
            fish scripts/operational/run_backtest.fish > "logs/backtest-run-${BT_TS}.log" 2>&1 &
            echo "Backtest wrapper started: logs/backtest-run-${BT_TS}.log"
            exit 0
        else
            echo "⚠️ Coverage incomplete: has_5yr=$has_5yr, symbols=$symbols, missing_symbols=$missing_count — will retry"
        end
    end
    sleep $sleep_secs
    set attempts (math $attempts + 1)
end

echo "⚠️ Timeout waiting for 5-year coverage after (math $max_attempts * $sleep_secs) seconds. Check logs and retry."; exit 2
```

> Note: This check is non-interactive and intended for automated runs; adjust `expected_symbols` / `max_attempts` as needed.

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
tail -100 logs/bridge-*.log | rg -i "(error|fail|429|rate)"

# Check date ranges being fetched
tail -100 logs/bridge-*.log | rg "📅"

# Check data sources
tail -100 logs/bridge-*.log | rg "✅ Merged"
```

## Expected Results

- **Date Range**: Should see data from ~2018-2020 to current date (depending on stock availability)
  - Most stocks achieve 5+ years of historical coverage
  - Actual coverage depends on when each stock was listed
- **Coverage**: All 50 configured stocks should have data
- **Sources**: Data primarily from Yahoo Finance (multi-year historical), supplemented by Shioaji (recent data) and TWSE
- **Duration**: Full 5-year download takes approximately 5-10 minutes for 50 stocks
- **Expected Total**: ~130,000-140,000 records for 50 stocks over 5 years

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
2. Check Python logs: `tail -f logs/bridge-*.log`
3. Verify Shioaji connection: `curl http://localhost:8888/health | jq '.shioaji_connected'`
4. Restart Python bridge if disconnected

### Issue: Only 1 year of data despite requesting 10 years
**Symptoms**: All records show same 1-year date range

**Solutions**:
1. Check if date ranges are properly passed: `tail logs/bridge-*.log | rg "📅"`
2. Verify code uses explicit start_date/end_date (not just days from today)
3. Ensure `fetch_historical_data` receives start_date/end_date parameters from Java

### Issue: Duplicate data or repeated batches
**Symptoms**: Record count grows but date range doesn't expand

**Solutions**:
1. Database tables should be truncated at start of download
2. Check Java logs for batch download details: `tail logs/java-*.log`
3. Verify no concurrent downloads running

## Notes

- Downloads are batched by year (365 days per batch) to avoid overwhelming the APIs
- Tables (`bar`, `market_data`, `strategy_stock_mapping`) are truncated at the start of download for clean data window
- Yahoo Finance provides the most comprehensive historical data without rate limiting
- Shioaji provides reliable recent data and is kept as supplement for data validation
