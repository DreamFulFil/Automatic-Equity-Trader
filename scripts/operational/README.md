# Operational Scripts

Scripts for running production operations: data population and backtesting.

## Overview

These scripts perform **operational tasks** (not development tasks):

1. **Historical Data Population**: Fetch 2 years of daily stock data from Shioaji API
2. **Combinatorial Backtests**: Test all strategies against all stocks

## Prerequisites

- PostgreSQL database running (Docker recommended)
- Shioaji API credentials configured
- Python 3.12+ with dependencies installed
- For backtests: Java application must be running

## Environment Variables

```bash
export JASYPT_PASSWORD="your_password"
export POSTGRES_DB="auto_equity_trader"
export POSTGRES_USER="dreamer"
export POSTGRES_PASSWORD="your_db_password"
```

## Quick Start

### Run Both Tasks (Recommended)

```bash
./scripts/operational/run_all_operational_tasks.sh <jasypt-password>
```

This script:
1. Fetches historical data for 10 Taiwan stocks (2 years)
2. Stores data in PostgreSQL `market_data` table
3. Starts Java application if not running
4. Runs backtests for all strategy × stock combinations
5. Stores results in `strategy_stock_mapping` table

**Duration**: Approximately 30-60 minutes depending on API rate limits and compute power.

---

## Individual Tasks

### Task 1: Populate Historical Data

Fetch and store historical stock data:

```bash
python scripts/operational/populate_historical_data.py \
    --jasypt-password <password> \
    --days 730
```

**Options:**
- `--jasypt-password`: Required. Encryption password for config
- `--days`: Optional. Number of days of history (default: 730 = 2 years)

**Output:**
- Inserts data into `market_data` table
- Approximately 5,000-7,000 records (10 stocks × 500-700 trading days)

**Stocks Fetched:**
- 2330.TW (TSMC)
- 2317.TW (Hon Hai)
- 2454.TW (MediaTek)
- 2308.TW (Delta Electronics)
- 2881.TW (Fubon Financial)
- 2882.TW (Cathay Financial)
- 2886.TW (Mega Financial)
- 2303.TW (United Microelectronics)
- 1303.TW (Nan Ya Plastics)
- 1301.TW (Formosa Plastics)

---

### Task 2: Run Combinatorial Backtests

Test all strategies against all stocks:

```bash
# Ensure Java application is running first
./start-auto-trader.fish <jasypt-password> &

# Run backtests
python scripts/operational/run_combinatorial_backtests.py \
    --port 16350 \
    --capital 80000 \
    --days 730
```

**Options:**
- `--port`: Java application port (default: 16350)
- `--host`: Java application host (default: localhost)
- `--capital`: Initial capital for backtests (default: 80000 TWD)
- `--days`: Days of history to test (default: 730)

**Output:**
- Updates `strategy_stock_mapping` table with:
  - Total return percentage
  - Sharpe ratio
  - Win rate
  - Max drawdown
  - Total trades

**Combinations Tested:**
- 50 strategies × 10 stocks = 500 combinations
- Each backtest runs against 2 years of daily data

---

## Database Tables

### market_data

Historical OHLCV data:

| Column | Type | Description |
|--------|------|-------------|
| symbol | VARCHAR | Stock symbol (e.g., "2330.TW") |
| timestamp | TIMESTAMP | Bar timestamp |
| open | DOUBLE | Opening price |
| high | DOUBLE | High price |
| low | DOUBLE | Low price |
| close | DOUBLE | Closing price |
| volume | BIGINT | Trading volume |
| timeframe | VARCHAR | Timeframe (DAY_1) |

### strategy_stock_mapping

Backtest results:

| Column | Type | Description |
|--------|------|-------------|
| symbol | VARCHAR | Stock symbol |
| strategy_name | VARCHAR | Strategy name |
| total_return_pct | DOUBLE | Total return % |
| sharpe_ratio | DOUBLE | Sharpe ratio |
| win_rate_pct | DOUBLE | Win rate % |
| max_drawdown_pct | DOUBLE | Max drawdown % |
| total_trades | INTEGER | Number of trades |
| updated_at | TIMESTAMP | Last update time |

---

## Viewing Results

### Query Best Performers

```sql
-- Top 10 by Sharpe Ratio
SELECT symbol, strategy_name, sharpe_ratio, total_return_pct, win_rate_pct
FROM strategy_stock_mapping
WHERE sharpe_ratio > 1.5
  AND total_return_pct > 10.0
  AND win_rate_pct > 55.0
ORDER BY sharpe_ratio DESC
LIMIT 10;
```

### Use AutoStrategySelector

After backtests complete, use Telegram:

```
/selectstrategy
```

This automatically selects the best strategy-stock combination based on:
- High return (>5%)
- Good Sharpe ratio (>1.0)
- High win rate (>50%)
- Acceptable drawdown (<20%)

---

## Troubleshooting

### "Failed to connect to Shioaji"

**Solution:** Verify credentials in encrypted config:
```bash
python -c "from app.core.config import load_config_with_decryption; \
    config = load_config_with_decryption('$JASYPT_PASSWORD'); \
    print(config['sinopac'])"
```

### "No data found for stock"

**Solution:** Run Task 1 first to populate historical data

### "Java application not running"

**Solution:** Start the application:
```bash
./start-auto-trader.fish <jasypt-password>
```

### "Database connection failed"

**Solution:** Start PostgreSQL:
```bash
docker start psql
# or
docker run -d --name psql -p 5432:5432 \
  -e POSTGRES_DB=auto_equity_trader \
  -e POSTGRES_USER=dreamer \
  -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
  postgres:15
```

---

## Performance Notes

- **Data Population**: ~5-10 minutes (depends on Shioaji API rate limits)
- **Combinatorial Backtests**: ~20-50 minutes (500 combinations)
- **Total Runtime**: ~30-60 minutes for complete pipeline

---

## Next Steps

After running operational tasks:

1. **Review Results**: Query `strategy_stock_mapping` table
2. **Auto-Select Strategy**: Use `/selectstrategy` in Telegram
3. **Monitor Performance**: Check daily P&L and metrics
4. **Adjust Risk**: Use `/risk` commands to tune parameters
5. **Go Live**: Enable trading when confident

---

**Note**: These are **operational tasks**, not development. The infrastructure
is complete and tested. Running these scripts populates data and generates
backtests for production use.
