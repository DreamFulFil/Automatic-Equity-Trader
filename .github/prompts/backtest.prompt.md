# Backtest prompt — concise guide

Purpose: Run and validate comprehensive backtests for configured stocks/strategies.

**Configured:** 50 stocks, 100 strategies

**Runtime logs:** All runtime logs and temporary outputs are written to the repository's `logs/` directory (not `/tmp`).

Quick steps (non-interactive)

This prompt is non-interactive and provides a ready-to-run fish script to verify prerequisites, trigger a backtest, and validate results. If the Java service is not running, the script will attempt to start it automatically (requires `JASYPT_PASSWORD` exported in the environment).

```fish
# Non-interactive backtest run (fish)
# Ensure JASYPT_PASSWORD is exported in fish (e.g. `set -x JASYPT_PASSWORD mysecret`)
if not set -q JASYPT_PASSWORD
    echo "❌ JASYPT_PASSWORD not set in environment; aborting non-interactive run"
    exit 1
end

# 1) Verify data present
set -l records (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COUNT(*) FROM bar;")
if test -z "$records" -o $records -lt 1
    echo "⚠️ No historical data found in 'bar' table: $records rows"
    exit 2
end

# 2) Ensure Java service is UP (timeout 60s); start automatically if needed
# Quick pre-check: if not UP, attempt to start Java once, then wait up to 60s for health
set -l health (curl -s http://localhost:16350/actuator/health 2>/dev/null | jq -r '.status' 2>/dev/null)
if test "$health" != "UP"
    echo "Java service not UP; attempting to start Java application..."
    if not set -q JASYPT_PASSWORD
        echo "❌ JASYPT_PASSWORD not set in environment; cannot start Java; aborting"
        exit 1
    end
    cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
    mkdir -p logs
    set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)
    nohup jenv exec java -Djasypt.encryptor.password=$JASYPT_PASSWORD -jar target/auto-equity-trader.jar > "logs/java-${LOG_TS}.log" 2>&1 &
    echo "Started Java (logs/java-${LOG_TS}.log)"
end

# Wait for Java (up to 60 seconds)
for i in (seq 1 30)
    sleep 2
    set health (curl -s http://localhost:16350/actuator/health 2>/dev/null | jq -r '.status' 2>/dev/null)
    if test "$health" = "UP"
        echo "✅ Java service UP"
        break
    end
end
if test "$health" != "UP"
    echo "❌ Java service did not become UP in time"; exit 3
end

# 3) Trigger backtest (non-interactive)
set -l LOG_TS (date -u +%Y%m%dT%H%M%SZ)
curl -s -X POST "http://localhost:16350/api/backtest/run" -o "logs/backtest-result-${LOG_TS}.json" &

# 4) Monitor progress briefly and look for completion (non-interactive check)
# Wait up to 120s and check for completion message in logs
set -l attempts 0
set -l max_attempts 40
while test $attempts -lt $max_attempts
    if tail -n 200 logs/java-*.log | rg -i "Parallelized backtest completed" >/dev/null
        echo "✅ Backtest completed"
        break
    end
    sleep 3
    set attempts (math $attempts + 1)
end
if test $attempts -ge $max_attempts
    echo "⚠️ Backtest did not complete within expected window. Check logs"; exit 4
end

# 5) Validate results
set -l result_file (ls -1 logs/backtest-result-*.json | sort | tail -n1)
if test -n "$result_file"
    echo "Result file: $result_file"
    jq -r '{file: $result_file, total_results: .total_results, duration_secs: .duration_secs} ' "$result_file" 2>/dev/null || echo "(could not parse JSON summary)"
end
set -l br_count (docker exec psql psql -U dreamer -d auto_equity_trader -t -A -c "SELECT COUNT(*) FROM backtest_result;")
echo "backtest_result rows: $br_count"
```

> Note: This script is non-interactive and intended to be invoked either directly or by the post-download verifier in `download-historical-data.prompt.md`.

Checks & expectations
- Total results ≈ 5,000 (50 stocks × 100 strategies)
- Execution time: typically 20–60s (parallelized)
- Ensure `total_trades > 10` filters for reliable stats

Troubleshooting (short)
- No data: run historical data download
- Slow/blocked: inspect Java logs for errors and restart service if needed
- Partial results: check for exceptions in logs and rerun

Notes
- Backtest is simulation only (no live trades)
- Results are persisted for downstream analysis and auto-selection

END