#!/usr/bin/env python3
"""
Check Yahoo Finance rate limits by downloading historical data for a list of Taiwan stocks.
This script is safe for repository inclusion (no secrets); tests are included to avoid network calls.

Usage:
  python scripts/operational/check_yahoo_rate_limits.py --years 7 --throttle-ms 500 --output results/yahoo_rate_limit.json

"""
from __future__ import annotations

import argparse
import json
import time
from datetime import datetime, timedelta
from pathlib import Path
from typing import List

import yfinance as yf
import pandas as pd

DEFAULT_STOCKS = [
    "2330", "2317", "2454", "2308", "3711", "2303", "1301", "2382", "2881",
    "2412", "2882", "2891", "2886", "2884", "2885", "6505", "2892", "2883",
    "2887", "2357", "2379", "2395", "2002", "2301", "2603", "1303", "1216",
    "2207", "2344", "2345", "2347", "2353", "2356", "2408", "2409", "2498",
    "2610", "2615", "2801", "2880", "2888", "2890", "2912", "3008", "3034",
    "3037", "3045", "4938", "5880", "9910",
]


def fetch_one(symbol: str, start_date, end_date) -> dict:
    start_time = time.time()
    try:
        df: pd.DataFrame = yf.download(symbol, start=start_date, end=end_date, progress=False)
        elapsed = time.time() - start_time

        if df.empty:
            return {
                "symbol": symbol,
                "status": "no_data",
                "records": 0,
                "elapsed_seconds": round(elapsed, 2),
            }

        return {
            "symbol": symbol,
            "status": "success",
            "records": len(df),
            "earliest": str(df.index.min().date()),
            "latest": str(df.index.max().date()),
            "elapsed_seconds": round(elapsed, 2),
        }

    except Exception as e:
        elapsed = time.time() - start_time
        err = str(e)
        status = "rate_limited" if "429" in err or "rate" in err.lower() else "error"
        return {
            "symbol": symbol,
            "status": status,
            "error": err,
            "elapsed_seconds": round(elapsed, 2),
        }


def run(stocks: List[str], years: int, throttle_ms: int, output: Path) -> dict:
    end_date = datetime.now()
    start_date = end_date - timedelta(days=years * 365)

    results = {
        "test_date": datetime.now().isoformat(),
        "date_range": {
            "start": start_date.strftime('%Y-%m-%d'),
            "end": end_date.strftime('%Y-%m-%d'),
            "years": years,
        },
        "total_stocks": len(stocks),
        "stocks_tested": 0,
        "successful": 0,
        "failed": 0,
        "rate_limited": 0,
        "details": [],
    }

    for i, code in enumerate(stocks, 1):
        symbol = f"{code}.TW"
        detail = fetch_one(symbol, start_date.date(), end_date.date())
        if detail["status"] == "success":
            results["successful"] += 1
        elif detail["status"] == "rate_limited":
            results["rate_limited"] += 1
        else:
            results["failed"] += 1

        results["details"].append(detail)
        results["stocks_tested"] += 1

        time.sleep(throttle_ms / 1000.0)

    output.parent.mkdir(parents=True, exist_ok=True)
    with open(output, "w") as f:
        json.dump(results, f, indent=2)

    return results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stocks-file", type=str, help="Optional newline-separated file of stock codes (without .TW)")
    parser.add_argument("--years", type=int, default=7)
    parser.add_argument("--throttle-ms", type=int, default=500)
    parser.add_argument("--output", type=str, default="scripts/operational/results/yahoo_rate_limit.json")
    args = parser.parse_args()

    if args.stocks_file:
        p = Path(args.stocks_file)
        if not p.exists():
            raise SystemExit(f"Stocks file not found: {p}")
        stocks = [line.strip() for line in p.read_text().splitlines() if line.strip()]
    else:
        stocks = DEFAULT_STOCKS

    out = Path(args.output)
    results = run(stocks, args.years, args.throttle_ms, out)

    # Print compact summary
    print(f"Tested {results['stocks_tested']}/{results['total_stocks']} stocks")
    print(f"Success: {results['successful']}, Failed: {results['failed']}, Rate limited: {results['rate_limited']}")
    print(f"Full results saved to {out}")


if __name__ == "__main__":
    main()
