# Scripts

Utility scripts for the Automatic Equity Trader.

## Directory Structure

### [`automation/`](automation/) - Scheduled Tasks
- `automation_watchdog.py` - System health monitoring
- `ai_daily_insights.py` - AI-powered daily reports
- `daily_performance_report.py` - Daily performance summary
- `weekly_performance_report.py` - Weekly performance summary

### [`setup/`](setup/) - Setup & Configuration
- `install-python312.fish` - Install Python 3.12
- `force-fix-python.fish` - Fix Python environment
- `update_strategy_configs.py` - Update strategy configs

## Deprecated Scripts (Removed in v2.4.0)

The following Python scripts have been ported to Java and removed:
- `backtest/download_taiwan_stock_history.py` → `HistoryDataService.java`
- `backtest/run_backtest_all_stocks.py` → `BacktestService.java` + REST API
- `backtest/run_backtest_now.py` → `BacktestService.java` + REST API
- `backtest/read_insights.py` → Database queries via JPA

**Use Java/REST equivalents:**
- `/download-history <symbol> [years]` (Telegram)
- `/backtest <symbol> <days>` (Telegram)
- `GET /api/backtest/run` (REST API)
- `POST /api/history/download` (REST API)

## Usage

```bash
# Start watchdog
nohup ./scripts/automation/automation_watchdog.py &

# Generate daily report
./scripts/automation/daily_performance_report.py

# Generate weekly report
./scripts/automation/weekly_performance_report.py
```
