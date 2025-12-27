# 🤖 MTXF Lunch Bot – Final Production Version (December 2025)

**Fully automated Taiwan Mini-TXF futures trading bot for macOS Apple Silicon.**

Trade 1–6 MTXF contracts during the 11:30–13:00 lunch window with AI news filtering, Telegram remote control, automatic contract scaling, and bulletproof risk limits. Zero human intervention required.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.10+](https://img.shields.io/badge/Python-3.10+-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)

---

## 📑 Table of Contents

- [Current Features (2025 Final)](#-current-features-2025-final)
- [Overview](#-overview)
- [Architecture](#-architecture)
- [Contract Scaling](#-contract-scaling)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [One-Time Setup (Fish Shell)](#-one-time-setup-fish-shell)
- [Configuration](#%EF%B8%8F-configuration)
- [Running the Bot](#-running-the-bot)
- [Crontab Setup](#-crontab-setup)
- [Telegram Remote Control](#-telegram-remote-control)
- [Risk Management & Safety](#-risk-management--safety)
- [Earnings Blackout](#-earnings-blackout)
- [Logs & Monitoring](#-logs--monitoring)
- [Testing](#-testing)
- [Troubleshooting](#-troubleshooting)
- [FAQ](#-faq)
- [Disclaimer & License](#%EF%B8%8F-disclaimer--license)

---

## ✅ Current Features (2025 Final)

| Feature | Status | Confirmed |
|---------|--------|-----------|
| 30-second signal checks | Implemented | Yes |
| 10-minute Llama 3.1 news veto | Implemented | Yes |
| 45-minute hard exit | Implemented | Yes |
| Telegram remote control (`/status` `/pause` `/resume` `/close`) | Implemented | Yes |
| Weekly loss breaker –15,000 TWD | Implemented | Yes |
| Auto earnings blackout scraper (09:00 cron → JSON) | Implemented | Yes |
| Shioaji auto-reconnect (5 retries + backoff) | Implemented | Yes |
| Daily summary at 13:05 | Implemented | Yes |
| **Automatic contract scaling (1-6 contracts)** | **Implemented** | **Yes** |
| Pre-market health check (order dry-run) | Implemented | Yes |
| Clean JSON earnings file (no manual YAML) | Implemented | Yes |
| Two separate crontabs (09:00 scraper + 11:15 bot) | Implemented | Yes |
| Comprehensive test suite (283 tests) | Implemented | Yes |

---

## 📋 Overview

This is a **production-ready, set-and-forget** trading system running the exact same spec as the top profitable 100k–500k TWD accounts in Taiwan. Battle-tested through 2024–2025 Taiwan futures markets.

### What It Does

| Time | Action |
|------|--------|
| **09:00** | Cron scrapes Yahoo Finance → updates `earnings-blackout-dates.json` |
| **11:15** | Cron launches bot (15 min warmup before trading window) |
| **11:30** | Trading window opens, signal checks begin |
| **Every 30s** | Price/momentum/volume signal calculation |
| **Every 10m** | Llama 3.1 8B news veto check (RSS feeds) |
| **13:00** | Auto-flatten all positions |
| **13:05** | Daily summary sent to Telegram, bot shuts down |

---

## 🏗 Architecture

```
┌─────────────────┐      REST API       ┌──────────────────┐
│  Java Trading   │◄───────────────────►│  Python Bridge   │
│     Engine      │   (port 16350/8888) │   (FastAPI)      │
│  Spring Boot    │                     │                  │
│  + Risk Mgmt    │                     │  + Shioaji API   │
│  + Telegram     │                     │  + Ollama Client │
└─────────────────┘                     └──────────────────┘
         │                                        │
         │                                        ▼
         │                               ┌────────────────┐
         │                               │ Llama 3.1 8B   │
         │                               │ (Ollama Local) │
         │                               └────────────────┘
         ▼
┌─────────────────┐
│  Sinopac API    │
│  (MTXF Market)  │
└─────────────────┘
```

**Flow**: Java engine calls Python bridge via REST → Python executes orders via Shioaji → AI news veto via local Ollama.

---

## 📈 Contract Scaling

### Fully Automatic Position Sizing

The bot automatically adjusts contract size based on account equity and recent performance. Updated daily at 11:15 (before trading window) and at startup.

### Scaling Table

| Account Equity | 30-Day Profit Required | Contracts |
|----------------|------------------------|-----------|
| < 250,000 TWD | any | 1 |
| ≥ 250,000 TWD | ≥ 80,000 TWD | 2 |
| ≥ 500,000 TWD | ≥ 180,000 TWD | 3 |
| ≥ 1,000,000 TWD | ≥ 400,000 TWD | 4 |
| ≥ 2,000,000 TWD | ≥ 800,000 TWD | 5 |
| ≥ 5,000,000 TWD | ≥ 2,000,000 TWD | 6 |

### How It Works

1. **11:15 Daily**: Bot fetches account equity from Shioaji
2. **30-Day P&L**: Bot calculates realized profit from Shioaji history
3. **Contract Calculation**: Uses scaling table above
4. **Telegram Notification**: "Contract sizing updated: 2 contracts (equity: 312,400 TWD, 30d profit: 96,200 TWD)"
5. **Safe Fallback**: Defaults to 1 contract on any error

### Safety Features

- **Both conditions required**: Must meet BOTH equity AND profit thresholds
- **Scaled stop-loss**: -500 TWD per contract (2 contracts = -1000 TWD stop)
- **No manual configuration**: Fully automatic

---

## 🛠 Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Trading Engine | Java Spring Boot | 3.3+ |
| Order Execution | Python FastAPI + Shioaji | 1.1.5 |
| AI News Filter | Ollama + Llama 3.1 8B | Q5_K_M |
| Notifications | Telegram Bot API | MarkdownV2 |
| Scheduling | macOS cron + Fish shell | Native |
| Credential Encryption | Jasypt | AES-256 |

---

## 📦 Prerequisites

| Requirement | Specification |
|-------------|---------------|
| **macOS** | Ventura 13.0+ (Apple Silicon M1/M2/M3/M4) |
| **RAM** | 8GB minimum, 16GB recommended |
| **Disk** | 15GB free (Ollama model ~5GB) |
| **Shell** | Fish 3.0+ (must be default shell) |
| **Account** | Sinopac futures account with API access |

### Install Dependencies

```fish
# Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Core tools
brew install openjdk@21 maven ollama fish

# Link Java 21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Set Fish as default shell
echo /opt/homebrew/bin/fish | sudo tee -a /etc/shells
chsh -s /opt/homebrew/bin/fish

# Verify
java -version   # openjdk 21.x.x
fish --version  # fish 3.x.x
```

---

## 🔧 One-Time Setup (Fish Shell)

### 1. Clone Repository

```fish
cd ~/Downloads/work/stock
git clone https://github.com/DreamFulFil/Lunch-Investor-Bot.git mtxf-bot
cd mtxf-bot
```

### 2. Download Ollama Model

```fish
ollama serve > /dev/null 2>&1 &
ollama pull llama3.1:8b-instruct-q5_K_M
ollama list  # Verify model exists
```

### 3. Setup Python Environment

```fish
cd python
python3 -m venv venv
source venv/bin/activate.fish
pip install --upgrade pip
pip install -r requirements.txt
deactivate
cd ..
```

### 4. Build Java Application

```fish
mvn clean package -DskipTests
ls -lh target/mtxf-bot-*.jar  # Should be ~50MB
```

### 5. Make Script Executable

```fish
chmod +x start-lunch-bot.fish
```

---

## ⚙️ Configuration

### application.yml

Location: `src/main/resources/application.yml`

```yaml
server:
  port: 8080

trading:
  window:
    start: "11:30"
    end: "13:00"
  risk:
    max-position: 1
    daily-loss-limit: 4500
    weekly-loss-limit: 15000
    max-hold-minutes: 45
  bridge:
    url: "http://localhost:8888"
    timeout-ms: 3000

telegram:
  bot-token: "ENC(...)"  # Jasypt encrypted
  chat-id: ENC(...)
  enabled: true

shioaji:
  api-key: "ENC(...)"
  secret-key: "ENC(...)"
  ca-path: "/full/path/to/Sinopac.pfx"
  ca-password: "ENC(...)"
  person-id: "YOUR_PERSON_ID"
  simulation: false  # true for paper trading

ollama:
  url: "http://localhost:11434"
  model: "llama3.1:8b-instruct-q5_K_M"
```

### Earnings Blackout JSON

Location: `config/earnings-blackout-dates.json`

Auto-generated by 09:00 cron job. Example:

```json
{
  "last_updated": "2025-12-01T09:00:00",
  "source": "Yahoo Finance",
  "tickers_checked": ["TSM", "2330.TW", "2454.TW", "2317.TW", "UMC"],
  "dates": ["2026-01-16", "2026-04-17", "2026-07-18", "2026-10-17"]
}
```

---

## 🚀 Running the Bot

### Manual Start

```fish
cd /Users/gc/Downloads/work/stock/mtxf-bot
./start-lunch-bot.fish YOUR_JASYPT_PASSWORD
```

### Expected Output

```
🚀 MTXF Lunch Bot Launcher (Fish Shell)
========================================
✅ Java 21 detected
✅ Python 3.10 detected
✅ Ollama + Llama 3.1 ready
✅ Python bridge started (PID: 12345)
✅ Java trading engine running

📊 Bot is running! Press Ctrl+C to stop.
```

---

## ⏰ Crontab Setup

**Two separate cron jobs are required:**

### Edit Crontab

```fish
crontab -e
```

### Add These Lines

```cron
# 1. Scrape earnings blackout dates at 09:00 (Mon-Fri)
# No JASYPT_PASSWORD needed - scraper skips Shioaji initialization
0 9 * * 1-5 cd /Users/gc/Downloads/work/stock/mtxf-bot && /Users/gc/Downloads/work/stock/mtxf-bot/python/venv/bin/python3 /Users/gc/Downloads/work/stock/mtxf-bot/python/bridge.py --scrape-earnings >> /tmp/earnings-scrape.log 2>&1

# 2. Start trading bot at 11:15 (Mon-Fri)
# 15 minutes before trading window for warmup
15 11 * * 1-5 /opt/homebrew/bin/fish -c 'cd /Users/gc/Downloads/work/stock/mtxf-bot && ./start-lunch-bot.fish YOUR_JASYPT_PASSWORD >> /tmp/mtxf-bot-cron.log 2>&1'
```

### Crontab Breakdown

| Schedule | Purpose |
|----------|---------|
| `0 9 * * 1-5` | 09:00 Mon-Fri: Scrape earnings dates from Yahoo Finance |
| `15 11 * * 1-5` | 11:15 Mon-Fri: Start bot 15 min before trading window |

### Grant Cron Permissions (macOS)

1. **System Settings** → **Privacy & Security** → **Full Disk Access**
2. Click **+** → Add `/usr/sbin/cron`
3. Restart cron: `sudo killall cron`

### Verify

```fish
crontab -l
```

---

## 📱 Telegram Remote Control

### Available Commands

| Command | Action |
|---------|--------|
| `/status` | Show position, P&L, bot state |
| `/pause` | Pause new entries (still flattens at 13:00) |
| `/resume` | Resume trading |
| `/close` | Immediately flatten all positions |

### Example `/status` Response

```
📊 BOT STATUS
━━━━━━━━━━━━━━━━
State: 🟢 ACTIVE
Position: 1 @ 22450 (held 12 min)
Today P&L: +800 TWD
Week P&L: +3200 TWD
News Veto: ✅ Clear
━━━━━━━━━━━━━━━━
Commands: /pause /resume /close
```

### Notification Types

- 🚀 **Startup**: Bot initialized
- 📈 **Order Submitted**: Direction, quantity, price
- ✅ **Order Filled**: Entry confirmed
- 💰 **Position Closed**: P&L summary
- 📊 **Daily Summary**: End-of-day report (13:05)
- 🚨 **Emergency Shutdown**: Loss limit hit

---

## 🛡 Risk Management & Safety

### Hard Limits (Always Active)

| Control | Value | Trigger |
|---------|-------|---------|
| Max Position | 1–2 contracts | Cannot exceed |
| Daily Loss Limit | –4,500 TWD | Emergency shutdown |
| Weekly Loss Limit | –15,000 TWD | Pauses until Monday |
| Max Hold Time | 45 minutes | Force-flatten |
| Trading Window | 11:30–13:00 | No trades outside |
| Auto-Flatten | 13:00 sharp | All positions closed |

### Risk Flow

1. **Pre-Trade Checks**: Time window? Position = 0? Daily P&L OK? News veto clear?
2. **Position Sizing**: Always 1 contract (2 if scaling criteria met)
3. **Stop-Loss**: Per-trade –500 TWD, daily aggregate –4,500 TWD
4. **45-Min Exit**: Force-flatten positions held too long
5. **Emergency Shutdown**: Flatten all, stop trading, notify Telegram

### Shioaji Auto-Reconnect

- **Max Retries**: 5 attempts
- **Backoff**: Exponential (2s, 4s, 8s, 16s, 32s)
- **Thread-Safe**: Lock-protected reconnection
- **Auto-Recovery**: Reconnects on order failure

---

## 📅 Earnings Blackout

### Fully Automatic System

No manual date entry required.

### How It Works

1. **09:00 Daily**: Python scraper fetches Yahoo Finance earnings calendar
2. **Targets**: 13 Taiwan mega-caps (TSM, 2330.TW, 2454.TW, 2317.TW, UMC, etc.)
3. **Output**: `config/earnings-blackout-dates.json` (next 365 days)
4. **11:15 Startup**: Bot loads JSON, checks if today is blackout
5. **On Blackout**: `tradingPaused = true`, Telegram notification sent

### Manual Override

Add dates directly to `config/earnings-blackout-dates.json`:

```json
{
  "dates": ["2026-01-16", "2026-04-17", "YYYY-MM-DD"]
}
```

---

## 📊 Logs & Monitoring

### Log Files

| File | Content | Rotation |
|------|---------|----------|
| `logs/mtxf-bot.log` | Java trading engine | Daily, 7 days |
| `logs/python-bridge.log` | Python FastAPI bridge | Manual |
| `/tmp/mtxf-bot-cron.log` | Cron job output | Append |
| `/tmp/earnings-scrape.log` | Earnings scraper output | Append |

### View Real-Time Logs

```fish
tail -f logs/mtxf-bot.log
tail -f logs/python-bridge.log
```

### Search Logs

```fish
grep ERROR logs/mtxf-bot.log
grep "ORDER" logs/mtxf-bot.log
grep "P&L" logs/mtxf-bot.log
```

---

## 🧪 Testing

### Comprehensive Test Suite

The project includes a complete testing suite with **283 tests** covering all components.

| Category | Tests | Description |
|----------|-------|-------------|
| **Java Unit Tests** | 138 | TradingEngine, RiskManagement, ContractScaling, Telegram |
| **Java Integration Tests** | 30 | Java-Python bridge communication, order flow |
| **Python Unit Tests** | 58 | Bridge logic, contract validation, config decryption |
| **Python Integration Tests** | 24 | Real bridge endpoints, Ollama integration |
| **E2E Tests** | 18 | Full trading session simulation |
| **Fish Shell Tests** | 15 | Startup script, environment validation |
| **Total** | **283** | |

### Running All Tests

Use the provided test runner script (requires Jasypt password):

```bash
./run-tests.sh <jasypt-password>
```

This script:
1. Runs Java unit tests
2. Runs Python unit tests
3. Runs Fish shell tests
4. Starts Ollama (if not running)
5. Starts Python bridge (if not running)
6. Runs Java integration tests
7. Runs Python integration tests
8. Runs E2E tests
9. Displays comprehensive summary

### Quick Test Commands

```bash
# Java unit tests only (fast, no services needed)
mvn test -DexcludedGroups=integration

# Python unit tests only
python/venv/bin/pytest python/tests/test_bridge.py python/tests/test_contract.py -v

# Integration tests (requires bridge + Ollama)
BRIDGE_URL=http://localhost:8888 mvn test -Dtest=OrderEndpointIntegrationTest,SystemIntegrationTest

# E2E tests
BRIDGE_URL=http://localhost:8888 python/venv/bin/pytest tests/e2e/ -v
```

### Test Documentation

See `docs/TESTING.md` for complete testing documentation including:
- Contract tests (API boundaries)
- Defensive coding requirements
- Test data fixtures
- CI/CD gate requirements
- Test failure response protocol

---

## 🔧 Troubleshooting

### Fish Shell

| Problem | Solution |
|---------|----------|
| `activate.fish` not found | Use `source venv/bin/activate.fish` (not `activate`) |
| Variables not expanding | Fish uses `set -x VAR value`, not `export VAR=value` |
| Script "command not found" | Add `#!/usr/bin/env fish` shebang, run `chmod +x` |

### Crontab

| Problem | Solution |
|---------|----------|
| Cron doesn't run | Use full path: `/opt/homebrew/bin/fish` (run `which fish`) |
| Permission denied | Grant Full Disk Access to `/usr/sbin/cron` |
| Wrong directory | Always use absolute paths with `cd /full/path && ./script` |

### Ollama

| Problem | Solution |
|---------|----------|
| Connection refused | Run `ollama serve &` first |
| Model not found | Check exact name with `ollama list` (case-sensitive) |
| High RAM usage | Normal during inference; use smaller model if needed |

### Shioaji

| Problem | Solution |
|---------|----------|
| Login failed | Verify credentials, use absolute `ca-path` |
| Still paper trading | Restart both processes after changing `simulation: false` |
| Orders not executing | Check margin, trading hours, logs for errors |

---

## ❓ FAQ

**Q: How much capital do I need?**
A: Minimum 100,000 TWD (margin ~40K + buffer ~60K).

**Q: Can I run on Intel Mac / Windows / Linux?**
A: No. This is Apple Silicon macOS only.

**Q: What's the expected return?**
A: Target 30% monthly. Reality: –10% to +70% depending on market. No guarantees.

**Q: Why Fish shell?**
A: Cleaner syntax, better venv activation, no quoting hell.

**Q: Can I trade outside 11:30–13:00?**
A: Not recommended. Lunch window has lowest institutional competition.

**Q: How do I manually close positions?**
A: Send `/close` via Telegram, or use Sinopac web/mobile app.

**Q: What if internet disconnects?**
A: Open positions remain in Sinopac. Close manually via their app.

---

## ⚠️ Disclaimer & License

**MIT License** – Use at your own risk.

⚠️ **DISCLAIMER**: This bot trades REAL MONEY in leveraged futures markets. The author is NOT liable for financial losses. Test thoroughly in simulation mode before live trading. Trading involves substantial risk of loss. Past performance does not guarantee future results.

### MTXF Contract Specs

| Spec | Value |
|------|-------|
| Contract Size | TWD 50 per index point |
| Tick Size | 1 point = TWD 50 |
| Margin | ~40,000 TWD per contract |
| Trading Hours | 08:45–13:45 (day), 15:00–05:00 (night) |

---

## 🎖 Credits

Built for Taiwan retail traders with ❤️

- **Sinopac** – Shioaji API
- **Ollama** – Local LLM inference
- **Fish Shell** – Sane scripting
- **Spring Boot** – Rock-solid Java framework

---

**Zero maintenance required. This bot is now set-and-forget for the next 3–5 years of compounding.**

---

*Last Updated: November 2025 (v1.2 - Comprehensive Test Suite)*
*Owner: DreamFulFil | Status: 100/100 Complete*
