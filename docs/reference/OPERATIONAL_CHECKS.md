# Operational Checks

This reference documents small operational scripts useful for maintenance and data-source validation.

## Scripts added

1. `scripts/operational/check_yahoo_rate_limits.py` 🔧
   - Purpose: Validate downloading historical data from Yahoo Finance for a configurable stock list and date range.
   - Usage: `python scripts/operational/check_yahoo_rate_limits.py --years 7 --throttle-ms 500 --output scripts/operational/results/yahoo_rate_limit.json`
   - Behavior: Produces a JSON result file with counts of successes, failures, and rate-limited requests. Designed to be safe and non-credentialed.

2. `scripts/operational/check_shioaji_config.py` 🔒
   - Purpose: Validate the `shioaji` configuration block and mask sensitive values in a human-friendly report.
   - Usage:
     - Static schema check from YAML: `python scripts/operational/check_shioaji_config.py --config python/config.yml`
     - Decrypt & check (requires `JASYPT_PASSWORD` env var): `JASYPT_PASSWORD=... python scripts/operational/check_shioaji_config.py --decrypt`
     - *Optional* live check (explicit opt-in): `--live-check` (will attempt a live login; only use with caution)
   - Safety: Does not print full secrets; prints masked values and optionally performs a live check only when explicitly requested.

## Tests
- Unit tests added under `python/tests/test_operational_scripts.py` mock network and decryption behavior so CI can run fast without external dependencies.

## Notes
- These scripts are intended for operator use and automation. They are written to be safe for inclusion in the repository (no secrets checked in).
- Always use `--live-check` sparingly and only when you have valid credentials and are prepared for the script to attempt an external login.
