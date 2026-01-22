# Historical Data Download Prompt

Purpose: Download historical Taiwan stock data into PostgreSQL for backtesting and auto-selection.

## Required input: `JASYPT_PASSWORD`
This workflow requires `JASYPT_PASSWORD` to decrypt runtime secrets.

Fish example:
```fish
set -x JASYPT_PASSWORD '<your-secret>'
test -n "$JASYPT_PASSWORD"; and echo "✅ JASYPT_PASSWORD is set"; or echo "❌ JASYPT_PASSWORD is missing"
```

## Notes
- Non-interactive: use the operational runner script.
- Logs: all runtime logs and temporary outputs are written to `logs/`.

## Prerequisites
- Export `JASYPT_PASSWORD` in the environment.
- PostgreSQL container `psql` is running.
- Python bridge is available (default port `8888`).

## Run (recommended)
Use `scripts/operational/run_download_and_monitor.fish` to start services (if needed), trigger the history download, and monitor DB inserts.

```fish
set -x JASYPT_PASSWORD '<your-secret>'
fish scripts/operational/run_download_and_monitor.fish
```

Exit codes:
- 0: Success
- 1: Missing `JASYPT_PASSWORD` (or early abort)
- 2: Health check failed after startup
- 3: No inserts observed during monitoring

Artifacts:
- `logs/history-download-*.json`
- `logs/java-*.log`
- `logs/bridge-*.log`

## Quick checks

### Health
```fish
curl -s http://localhost:16350/actuator/health | jq -r '.status'
curl -s http://localhost:8888/health | jq '.'
```

### DB coverage (high level)
```fish
docker exec psql psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT COUNT(*) AS records, COUNT(DISTINCT symbol) AS symbols, MIN(timestamp::date) AS earliest FROM bar;"
```

## Data source priority
Current configuration (as of 2025-12-27):
1. Yahoo Finance (primary)
2. Shioaji (supplement)
3. TWSE (supplement)

## Troubleshooting
- 0 records inserted: verify Python bridge health and check `logs/bridge-*.log`.
- Short date range: confirm the bridge logs show the requested date window.
- Repeated batches: ensure no concurrent downloads are running; check `logs/java-*.log`.
# Historical Data Download Prompt

## Context
This prompt guides the AI agent through downloading historical data for Taiwan stocks using the Automatic-Equity-Trader system.

## Required Input: `JASYPT_PASSWORD`

This prompt assumes you will provide `JASYPT_PASSWORD` in the shell environment before running any scripts.

- Why: the Java service decrypts secrets in `application.yml` via Jasypt at runtime.
- If missing: the non-interactive runner aborts early with a clear error message.

Fish example:
```fish
# Provide the password for this shell session only
set -x JASYPT_PASSWORD '<your-secret>'
```

Verify it is set:
```fish
test -n "$JASYPT_PASSWORD"; and echo "✅ JASYPT_PASSWORD is set"; or echo "❌ JASYPT_PASSWORD is missing"
```

**Non-interactive:** This document includes runnable, non-interactive fish scripts to automate the full download, verification of 5-year coverage, and auto-triggering of the backtest.

**Runtime logs:** All runtime logs and temporary outputs are written to the repository's `logs/` directory (not `/tmp`).

## Prerequisites
- You MUST export **JASYPT_PASSWORD** in the environment before running this prompt or any scripts here.
    - The runner checks for it and aborts if it is missing.
- **Years of data**: Number of years to download (default: 5; the script uses 5 if not specified)
- Ensure Telegram credentials (bot token & chat id) are configured and enabled so the Java service can send notifications.

## Execution Steps

### 1. Verify Services Status

First, check if Java application and Python bridge are running:

```bash
# Check Java app (should be on port 16350)
lsof -i :16350

# Check Python bridge (should be on port 8888)
lsof -i :8888
```

### 2. Run the non-interactive runner (recommended)

Use the provided executable fish script which kills/starts services, triggers the history download, and monitors DB inserts for progress. It is located at `scripts/operational/run_download_and_monitor.fish` and writes all runtime logs to `logs/`.

Usage:
```fish
# REQUIRED: ensure JASYPT_PASSWORD is exported in this shell (example):
set -x JASYPT_PASSWORD '<your-secret>'
# Run the script (non-interactive):
fish scripts/operational/run_download_and_monitor.fish
```

Exit codes and meaning:
- 0: Success — monitoring observed inserts and services were left running ✅
- 1: Missing `JASYPT_PASSWORD` or other early abort ❌
- 2: Health check failed after startup; script tailed logs and stopped services ⚠️
- 3: No inserts observed during monitoring; script tailed logs and stopped services ⚠️

Check `logs/history-download-*.json`, `logs/java-*.log`, and `logs/bridge-*.log` for details and troubleshooting.

### 3. Wait for health (brief)
```fish
for i in (seq 1 30)
    sleep 2
    if test (curl -s http://localhost:16350/actuator/health 2>/dev/null | jq -r '.status') = "UP"
        echo "✅ Java UP"; break
    end
end
curl -s http://localhost:8888/health | jq '.'
```

### Monitor & troubleshoot (brief)
- Logs: `tail -f logs/java-*.log` and `tail -f logs/bridge-*.log`.
- DB checks: `docker exec psql psql -U dreamer -d auto_equity_trader -c "SELECT COUNT(*) as records, COUNT(DISTINCT symbol) as symbols, MIN(timestamp::date) as earliest FROM bar;"` 

### Expected
- Java sends Telegram messages only twice: a summary at the start (symbols being downloaded) and a final summary when all stocks are finished and inserted. If you do not receive these messages, verify Telegram config and credentials.

---
Concise non-interactive: kills apps, starts services (if needed), triggers backtest, monitors DB insert activity every 30s for up to 10 checks, stops the apps on no activity, and uses Telegram for notifications when configured.

### Monitor & troubleshoot (brief)
- Logs: `tail -f logs/java-*.log` and `tail -f logs/bridge-*.log`.
- DB checks: `docker exec psql psql -U dreamer -d auto_equity_trader -c "SELECT COUNT(*) as records, COUNT(DISTINCT symbol) as symbols, MIN(timestamp::date) as earliest FROM bar;"` 

### Expected
- Java sends Telegram messages only twice: a summary at the start (symbols being downloaded) and a final summary when all stocks are finished and inserted. If you do not receive these messages, verify Telegram config and credentials.

---
Concise non-interactive: starts services (if needed), triggers download, polls DB for 5-year coverage, auto-triggers backtest, and uses Telegram for notifications.

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
