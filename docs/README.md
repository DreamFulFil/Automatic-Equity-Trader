# 📚 Documentation Index

Welcome to the Automatic Equity Trader documentation.

## 🚀 Quick Start

**New to the system?** Start here:
1. [Beginner's Guide](usage/BEGINNER_GUIDE.md) - Complete walkthrough
2. [Quick Start Checklist](usage/QUICK_START_CHECKLIST.md) - 30-minute setup
3. [Automation Features](usage/AUTOMATION_FEATURES.md) - What runs automatically

---

## 📂 Directory Structure

### [`usage/`](usage/) - User Guides
- [BEGINNER_GUIDE.md](usage/BEGINNER_GUIDE.md) - Start here!
- [QUICK_START_CHECKLIST.md](usage/QUICK_START_CHECKLIST.md) - Setup checklist
- [AUTOMATION_FEATURES.md](usage/AUTOMATION_FEATURES.md) - Scheduled tasks
- [AUTO_SELECTION.md](usage/AUTO_SELECTION.md) - Daily strategy auto-selection
- [SHADOW_MODE.md](usage/SHADOW_MODE.md) - Paper trading strategies
- [FAQ.md](usage/FAQ.md) - Frequently asked questions

### [`architecture/`](architecture/) - System Design
- [STRATEGY_PATTERN.md](architecture/STRATEGY_PATTERN.md) - Strategy implementation
- [DYNAMIC_STOCK_SELECTION.md](architecture/DYNAMIC_STOCK_SELECTION.md) - Stock selection logic

### [`deployment/`](deployment/) - Deployment
- [CRONTAB_SETUP.md](deployment/CRONTAB_SETUP.md) - Cron scheduling

### [`reference/`](reference/) - Reference
- [TESTING.md](reference/TESTING.md) - Testing guide

### [`reports/`](reports/) - Performance Reports
- [DECEMBER_2024_IMPROVEMENTS.md](reports/DECEMBER_2024_IMPROVEMENTS.md) - Major update
- [BACKTEST_2024_12_14.md](reports/BACKTEST_2024_12_14.md) - Backtest results
- [PERFORMANCE_EVALUATION.md](reports/PERFORMANCE_EVALUATION.md) - Analysis

### [`archive/`](archive/) - Historical
Old release notes, implementation summaries, and system prompts

---

## 🔍 Quick Reference

**Starting the system:**
```bash
./start-auto-trader.fish $JASYPT_PASSWORD
```

**Running backtests:**
```bash
./scripts/backtest/run_backtest_all_stocks.py
```

**Telegram commands:**
```
/status - Current state
/help - Command list
/talk <question> - Ask AI
```

---

**Ready to start?** → [Beginner's Guide](usage/BEGINNER_GUIDE.md)

**Questions?** → [FAQ](usage/FAQ.md)
