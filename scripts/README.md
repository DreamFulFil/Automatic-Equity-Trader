# Scripts

Utility scripts for the Automatic Equity Trader.

## Directory Structure

### [`automation/`](automation/) - Scheduled Tasks
- `automation_watchdog.py` - System health monitoring
- `ai_daily_insights.py` - AI-powered daily reports
- `daily_performance_report.py` - Daily performance summary
- `weekly_performance_report.py` - Weekly performance summary

### [`backtest/`](backtest/) - Data & Backtesting
- `download_taiwan_stock_history.py` - Download stock data
- `run_backtest_all_stocks.py` - Run comprehensive backtests
- `run_backtest_now.py` - Quick backtest runner
- `read_insights.py` - Read AI insights from database

### [`setup/`](setup/) - Setup & Configuration
- `install-python312.fish` - Install Python 3.12
- `force-fix-python.fish` - Fix Python environment
- `update_strategy_configs.py` - Update strategy configs

## Usage

```bash
# Run backtests
./scripts/backtest/run_backtest_all_stocks.py

# Download stock data
./scripts/backtest/download_taiwan_stock_history.py

# Start watchdog
nohup ./scripts/automation/automation_watchdog.py &
```
