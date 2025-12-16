# 📚 Documentation Index

Welcome to the Automatic Equity Trader documentation! This index helps you find what you need quickly.

## 🚀 Quick Start

**New to the system?** Start here:
1. [Beginner's Guide](guides/BEGINNER_GUIDE.md) - Complete walkthrough for non-traders
2. [Quick Start Checklist](guides/QUICK_START_CHECKLIST.md) - 30-minute setup guide
3. [Automation Features](guides/AUTOMATION_FEATURES.md) - Understanding what runs automatically

## 📂 Documentation Structure

### [`guides/`](guides/) - User Guides
Essential reading for understanding and using the system:
- **[BEGINNER_GUIDE.md](guides/BEGINNER_GUIDE.md)** - Start here! Complete beginner walkthrough
- **[QUICK_START_CHECKLIST.md](guides/QUICK_START_CHECKLIST.md)** - 30-minute setup checklist
- **[AUTOMATION_FEATURES.md](guides/AUTOMATION_FEATURES.md)** - What runs automatically
- **[SHADOW_MODE.md](guides/SHADOW_MODE.md)** - How strategy testing works
- **[FAQ.md](guides/FAQ.md)** - Frequently asked questions

### [`architecture/`](architecture/) - System Design
Technical documentation for developers:
- **[strategy-pattern.md](architecture/strategy-pattern.md)** - Strategy pattern implementation
- **[dynamic-stock-selection.md](architecture/dynamic-stock-selection.md)** - How stocks are selected

### [`deployment/`](deployment/) - Deployment Guides
Setup and deployment instructions:
- **[crontab-setup.md](deployment/crontab-setup.md)** - Automated scheduling with cron

### [`api/`](api/) - API Documentation
API endpoints and usage (to be added):
- Trading API endpoints
- AI Insights API
- Strategy-Stock Mapping API

### [`reports/`](reports/) - Performance Reports
Backtest results and performance analysis:
- **[DECEMBER_2024_IMPROVEMENTS.md](reports/DECEMBER_2024_IMPROVEMENTS.md)** - Latest major update
- **[backtest-2024-12-14.md](reports/backtest-2024-12-14.md)** - Comprehensive backtest results
- **[performance-evaluation.md](reports/performance-evaluation.md)** - Performance analysis

### [`tests/`](tests/) - Testing Documentation
Test guides and coverage reports:
- **[TESTING.md](tests/TESTING.md)** - Complete testing guide

### [`prompts/`](prompts/) - System Recreation Prompts
5-prompt series to rebuild the entire system from scratch

### [`release/`](release/) - Release Notes
Version history and release documentation

### [`misc/`](misc/) - Miscellaneous
Additional documentation and archived files

---

## 📖 Reading Paths

### For Beginners (Day 1-7)
1. Day 1: [Beginner's Guide](guides/BEGINNER_GUIDE.md)
2. Day 1: [Quick Start Checklist](guides/QUICK_START_CHECKLIST.md)
3. Day 2-3: [Automation Features](guides/AUTOMATION_FEATURES.md)
4. Day 4-5: [Shadow Mode](guides/SHADOW_MODE.md)
5. Day 6-7: [FAQ](guides/FAQ.md)

### For Developers
1. [Architecture Overview](architecture/)
2. [Strategy Pattern](architecture/strategy-pattern.md)
3. [Testing Guide](tests/TESTING.md)
4. [API Documentation](api/)

### For Deployment
1. [Quick Start Checklist](guides/QUICK_START_CHECKLIST.md)
2. [Crontab Setup](deployment/crontab-setup.md)
3. [Release Notes](release/)

---

## 🔍 Quick Reference

### Common Tasks

**Starting the system:**
```bash
# See Quick Start Checklist
./start-auto-trader.fish $JASYPT_PASSWORD
```

**Running backtests:**
```bash
./scripts/run_backtest_all_stocks.py
```

**Daily insights:**
```bash
./scripts/ai_daily_insights.py
```

**Telegram commands:**
```
/status - Current state
/help - Command list
/talk <question> - Ask AI
```

### Configuration Files

- `src/main/resources/application.yml` - Main configuration
- `config/` - Encrypted configuration files
- `scripts/` - Python automation scripts

### Logs

- `logs/application.log` - Java service logs
- `python/logs/bridge.log` - Python bridge logs
- `shioaji.log` - Trading API logs

---

## 📊 Performance Metrics

Target metrics for risk-averse trading:
- **Monthly Return:** 5% (60% annually)
- **Max Drawdown:** < 10%
- **Win Rate:** > 55%
- **Sharpe Ratio:** > 1.0
- **Daily Time:** < 15 minutes

See [Performance Reports](reports/) for actual results.

---

## 🆘 Getting Help

### Documentation
1. Search this index for your topic
2. Check [FAQ](guides/FAQ.md) for common questions
3. Review [Beginner's Guide](guides/BEGINNER_GUIDE.md) for basics

### Interactive
- **Telegram:** `/talk <question>` - Ask AI anything
- **Telegram:** `/help` - List all commands

### Troubleshooting
- Check logs in `logs/` directory
- Review [Quick Start Checklist](guides/QUICK_START_CHECKLIST.md) troubleshooting section
- Search GitHub issues

---

## 🔄 Updates

This documentation is actively maintained. Major updates:
- **December 2024:** AI-powered insights, 50+ stocks, 1-year backtests
- See [DECEMBER_2024_IMPROVEMENTS.md](reports/DECEMBER_2024_IMPROVEMENTS.md) for details

---

## 📝 Contributing

Documentation improvements are welcome! Please:
1. Keep beginner-friendly language
2. Add examples where helpful
3. Update this index when adding new docs
4. Follow the existing structure

---

**Ready to start?** 📖 Go to [Beginner's Guide](guides/BEGINNER_GUIDE.md)

**Questions?** 💬 Check [FAQ](guides/FAQ.md) or use `/talk` in Telegram
