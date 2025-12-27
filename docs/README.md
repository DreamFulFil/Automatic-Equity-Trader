# 📚 Documentation Index

Source of truth for the Automatic Equity Trader system.

---

## Quick Start

| Guide | Purpose |
|-------|---------|
| [Beginner's Guide](usage/BEGINNER_GUIDE.md) | Full setup walkthrough |
| [Quick Start Checklist](usage/QUICK_START_CHECKLIST.md) | 30-minute setup |
| [FAQ](usage/FAQ.md) | Common questions |

---

## Usage

- [AUTOMATION_FEATURES.md](usage/AUTOMATION_FEATURES.md) — Scheduled tasks
- [AUTO_SELECTION.md](usage/AUTO_SELECTION.md) — Daily strategy auto-selection
- [SHADOW_MODE.md](usage/SHADOW_MODE.md) — Paper trading
- [DATA_STORE_WHILE_TRADE_TUTORIAL.md](usage/DATA_STORE_WHILE_TRADE_TUTORIAL.md) — Data persistence

## Architecture

- [STRATEGY_PATTERN.md](architecture/STRATEGY_PATTERN.md) — Strategy framework
- [DYNAMIC_STOCK_SELECTION.md](architecture/DYNAMIC_STOCK_SELECTION.md) — Stock selection logic

## Deployment

- [CRONTAB_SETUP.md](deployment/CRONTAB_SETUP.md) — Cron job configuration

## Reference

- [TESTING.md](reference/TESTING.md) — Testing guide
- [DATA_OPERATIONS_API.md](reference/DATA_OPERATIONS_API.md) — Data operations API

## Archive

- [prompts/](archive/prompts/) — AI reconstruction prompts (PROMPT_1–5.md)

---

## Quick Commands

```bash
# Start system
./start-auto-trader.fish $JASYPT_PASSWORD

# Run tests
./run-tests.sh $JASYPT_PASSWORD

# Telegram
/status  /help  /talk <question>
```
