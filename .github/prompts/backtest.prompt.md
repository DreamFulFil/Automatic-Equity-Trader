# Backtest prompt — concise guide

Purpose: Run and validate comprehensive backtests for configured stocks/strategies.

Prerequisites
- Provide JASYPT password for DB creds
- Ensure historical data is loaded (use `download-historical-data.prompt.md` first)
- Java service running and healthy (`http://localhost:16350/actuator/health`)

Quick steps
1) Verify data
- SQL: `SELECT COUNT(*) FROM bar;` (expect large dataset for configured stocks)

2) Ensure Java service is UP (fish example)
- fish: `curl -s http://localhost:16350/actuator/health | jq -r '.status'`

3) Trigger backtest
- fish: 
  set LOG_TS (date -u +%Y%m%dT%H%M%SZ)
  curl -s -X POST "http://localhost:16350/api/backtest/run" -o "logs/backtest-result-${LOG_TS}.json" &

4) Monitor progress
- `tail -f logs/java-*.log | rg -i "(backtest|strategy|stock)"`
- Watch for `Parallelized backtest completed` and final summary

5) Validate results
- Inspect latest result file: `ls -1 logs/backtest-result-*.json | tail -n1` then `jq` summary
- DB check: `SELECT COUNT(*) FROM backtest_result;`

Checks & expectations
- Total results ≈ 2,700 (50 stocks × ~54 strategies)
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