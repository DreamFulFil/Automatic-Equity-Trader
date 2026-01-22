# Backtest prompt — concise guide

Purpose: Run and validate comprehensive backtests for configured stocks/strategies.

**Configured:** 50 stocks, 100 strategies

**Runtime logs:** All runtime logs and temporary outputs are written to the repository's `logs/` directory (not `/tmp`).

Quick steps (non-interactive)

This prompt is non-interactive and provides a ready-to-run fish script to verify prerequisites, trigger a backtest, and validate results. If the Java service is not running, the script will attempt to start it automatically (requires `JASYPT_PASSWORD` exported in the environment).

```fish
# Backtest prompt

Purpose: Run and validate backtests for the configured stock universe and strategies.

## Prerequisites
- Historical data exists in `bar`.
- Java service is healthy: `curl -s http://localhost:16350/actuator/health | jq -r '.status'` returns `UP`.
- PostgreSQL container `psql` is running.

## Run (fish)
```fish
# 1) Verify data present
set -l records (docker exec psql psql -U $POSTGRES_USER -d $POSTGRES_DB -t -A -c "SELECT COUNT(*) FROM bar;")
if test -z "$records" -o $records -lt 1
    echo "❌ No historical data found in 'bar' table: $records rows"
    exit 2
end

# 2) Trigger backtest
set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)
curl -s -X POST "http://localhost:16350/api/backtest/run" -o "logs/backtest-result-${LOG_TS}.json" &

# 3) Wait for completion signal in logs (up to ~2 minutes)
set -l attempts 0
set -l max_attempts 40
while test $attempts -lt $max_attempts
    if tail -n 300 logs/java-*.log | rg -i "Parallelized backtest completed" >/dev/null
        echo "✅ Backtest completed"
        break
    end
    sleep 3
    set attempts (math $attempts + 1)
end
if test $attempts -ge $max_attempts
    echo "⚠️ Backtest did not complete within expected window. Check logs/java-*.log"
    exit 4
end
```

## Validate
```sql
SELECT MAX(backtest_run_id) AS latest_run_id
FROM backtest_results;
```

```sql
SELECT COUNT(*) AS results
FROM backtest_results
WHERE backtest_run_id = (SELECT MAX(backtest_run_id) FROM backtest_results);
```

> Tip: downstream workflows (like auto-selection) typically filter to `total_trades > 10`.

## Troubleshooting
- No data: run the historical data download prompt.
- Slow/blocked: inspect `logs/java-*.log` for exceptions and retry.