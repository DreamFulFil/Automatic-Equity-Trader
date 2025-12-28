# Backtest prompt — concise guide

Purpose: Run and validate comprehensive backtests for configured stocks/strategies.

**Runtime logs:** All runtime logs and temporary outputs are written to the repository's `logs/` directory (not `/tmp`).

Quick steps (non-interactive)

This prompt is non-interactive and provides a ready-to-run fish script to verify prerequisites, trigger a backtest, and validate results. If the Java service is not running, the script will attempt to start it automatically (requires `JASYPT_PASSWORD` exported in the environment).

Run the canonical non-interactive backtest script:

```fish
# Run the canonical backtest wrapper (non-interactive)
fish scripts/operational/run_backtest.fish &
```

> Note: This script is non-interactive and intended to be invoked either directly or by the post-download verifier in `download-historical-data.prompt.md`. The canonical script is located at `scripts/operational/run_backtest.fish`.

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