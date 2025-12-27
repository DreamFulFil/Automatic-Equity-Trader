[![CI](https://github.com/DreamFulFil/Lunch-Investor-Bot/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Lunch-Investor-Bot/actions/workflows/ci.yml)

# 🤖 Lunch Investor Bot – Dual-Mode Production Version (December 2025)

**Fully automated Taiwan trading bot for macOS Apple Silicon supporting BOTH stock (2454.TW) and futures (MTXF) trading.**

Trade during the 11:30–13:00 lunch window with AI news filtering, Telegram remote control, automatic scaling, and bulletproof risk limits. Switch between stock and futures mode with a single command-line argument.

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.12](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)

---

## 🔥 NEW: Dual-Mode Trading (December 2025)

The bot now supports **two trading modes** via command-line, with **zero config changes** required:

| Mode | Instrument | Default Quantity | Scaling |
|------|------------|------------------|---------|
| **stock** (default) | 2454.TW (MediaTek odd lots) | 70 shares (≈77k NTD) | +27 shares per 20k equity |
| **futures** | MTXF (Mini-TXF) | 1-4 contracts | Based on equity + 30d profit |

### Usage

```bash
# Stock mode (default) - trades 2454.TW odd lots (perfect for 80k capital)
./start-lunch-bot.fish YOUR_PASSWORD

# Futures mode - trades MTXF contracts
./start-lunch-bot.fish YOUR_PASSWORD futures
```

### Stock Mode Sizing (for 80k Capital)
- **Base:** 70 shares at NT$1,100/share = ~77,000 TWD (fits 80k capital perfectly)
- **Scaling:** +27 shares for every additional 20k equity above 80k
- **Example:** 100k equity → 70 + (20k÷20k)×27 = 97 shares

### Key Features
- **One command switches everything** - no config edits, no rebuild
- **Strict mode separation** - NO futures calls in stock mode and vice versa
- **Same signals, veto, risk limits** - identical strategy for both modes
- **Mode shown in Telegram** - "Bot started — Mode: STOCK (2454.TW odd lots, 70 shares)"
- **Crontab stays safe** - default (no argument) = stock mode

---

## 📑 Table of Contents

- [Quickstart](#-quickstart)
- [Current Features (2025 Final)](#-current-features-2025-final)
- [Dual-Mode Trading](#-new-dual-mode-trading-december-2025)
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
- [Signal Strategy (Anti-Whipsaw)](#-signal-strategy-anti-whipsaw-v2---december-2025)
- [Risk Management & Safety](#-risk-management--safety)
- [Earnings Blackout](#-earnings-blackout)
- [Logs & Monitoring](#-logs--monitoring)
- [Testing](#-testing)
- [Troubleshooting](#-troubleshooting)
- [FAQ](#-faq)
- [Disclaimer & License](#%EF%B8%8F-disclaimer--license)

---

## 🚀 Quickstart

### Simulation Mode (Safe Testing)

```bash
# 1. Clone and setup
git clone https://github.com/DreamFulFil/Lunch-Investor-Bot.git
cd Lunch-Investor-Bot

# 2. Install dependencies
brew install openjdk@21 maven ollama fish
python3 -m venv python/venv
python/venv/bin/pip install -r python/requirements.txt

# 3. Download AI model
ollama pull llama3.1:8b-instruct-q5_K_M

# 4. Configure credentials in src/main/resources/application.yml
#    Use Jasypt encryption for sensitive values (ENC(...))

# 5. Build and run (simulation mode is default)
mvn clean package -DskipTests
./start-lunch-bot.fish YOUR_JASYPT_PASSWORD
```

### Live Trading Mode

**⚠️ WARNING: Uses real money! Test thoroughly in simulation first.**

```bash
# 1. Verify simulation results are profitable
#    Check database: data/bot.db → trades table
#    Run: sqlite3 data/bot.db "SELECT COUNT(*), SUM(realized_pnl) FROM trades"

# 2. Meet go-live criteria via /golive Telegram command:
#    - Win rate > 55%
#    - Max drawdown < 5%
#    - 20+ trades completed

# 3. Switch to live mode via Telegram:
/golive

# 4. Or set database directly:
sqlite3 data/bot.db "UPDATE shioaji_settings SET simulation = false"

# 5. Restart bot
./start-lunch-bot.fish YOUR_JASYPT_PASSWORD
```

### Quick Test

```bash
# Run all tests (requires Jasypt password)
./run-tests.sh YOUR_JASYPT_PASSWORD

# Run only Java unit tests (no password needed)
mvn test -DexcludedGroups=integration -Dspring.profiles.active=ci

# Run only Python unit tests
python/venv/bin/pytest python/tests/test_bridge.py python/tests/test_contract.py -v
```

---

## ✅ Current Features (December 2025)

| Feature | Status |
|---------|--------|
| **Dual-mode trading (stock/futures via CLI)** | **Implemented** |
| 30-second signal checks | Implemented |
| 10-minute Llama 3.1 news veto | Implemented |
| 45-minute hard exit | Implemented |
| Telegram remote control (`/status` `/pause` `/resume` `/close`) | Implemented |
| Weekly loss breaker –15,000 TWD | Implemented |
| Auto earnings blackout scraper (09:00 cron → JSON) | Implemented |
| Shioaji auto-reconnect (5 retries + backoff) | Implemented |
| Daily summary at 13:05 | Implemented |
| **Automatic contract scaling (1-6 contracts)** | **Implemented** |
| Pre-market health check (order dry-run) | Implemented |
| Clean JSON earnings file (no manual YAML) | Implemented |
| Two separate crontabs (09:00 scraper + 11:15 bot) | Implemented |
| **Intra-day self-healing supervisor (Phase 2)** | **Implemented** |
| **Agent framework with simulation mode** | **Implemented** |
| AI agents (News, Tutor, Signal, Risk) | Implemented |
| Database layer (SQLite + JPA) | Implemented |
| Bot mode management (simulation/live) | Implemented |
| Extended Telegram commands (/agent, /talk, /insight, /golive, /backtosim) | Implemented |
| **Database-driven configuration (stock/risk settings)** | **Implemented** |
| Comprehensive test suite (202 tests) | Implemented |

---

## 🤖 Agent Framework and Simulation Mode (December 2025)

The bot now includes a comprehensive AI agent framework with built-in simulation mode for safe testing and development.

### Key Components

- **AI Agents**: Modular agents for news analysis, tutoring, signal generation, and risk management
- **Database Layer**: SQLite-based persistence for agent metadata, bot settings, trades, and interactions
- **Simulation Mode**: Bot starts in simulation by default, with eligibility checks for live mode transition
- **Telegram Integration**: New commands for agent interaction and mode switching

### New Telegram Commands

- `/agent` - List available agents with status
- `/talk <question>` - Ask trading questions (rate limited)
- `/insight` - Get daily trading insight
- `/golive` - Check and switch to live trading mode
- `/backtosim` - Switch back to simulation mode

### Simulation to Live Transition

- Requires >55% win rate, <5% drawdown, 20+ trades
- Automatic eligibility checks via `/golive` command

This framework enables phased development and safe deployment of new features.

---

## 📋 Overview

This is a **production-ready, set-and-forget** trading system running the exact same spec as the top profitable 100k–500k TWD accounts in Taiwan. Battle-tested through 2024–2025 Taiwan futures markets.

### What It Does

| Time | Action |
|------|--------|
| **09:00** | Java scheduler refreshes earnings blackout dates → stores in DB |
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
| Trading Engine | Java Spring Boot | 3.4.0 |
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
| **Python** | 3.12 (Apple Silicon) |
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

### Database-Driven Settings (December 2025)

**Stock and risk settings are now stored in SQLite database for dynamic configuration without restarts.**

#### Stock Settings (Database Table: `stock_settings`)

| Setting | Default | Description |
|---------|---------|-------------|
| `initial_shares` | 70 | Base shares for stock mode (2454.TW) |
| `share_increment` | 27 | Additional shares per 20k equity above base |

#### Risk Settings (Database Table: `risk_settings`)

| Setting | Default | Description |
|---------|---------|-------------|
| `max_position` | 1 | Maximum contracts/shares per position |
| `daily_loss_limit` | 1500 | Daily loss limit in TWD (scaled by position size) |
| `weekly_loss_limit` | 7000 | Weekly loss limit in TWD |
| `max_hold_minutes` | 45 | Maximum position hold time before forced exit |

#### Database Initialization

After first startup, run the initialization script:

```fish
sqlite3 data/bot.db < scripts/init_settings.sql
```

This creates default settings that can be modified dynamically without application restarts.

### application.yml

Location: `src/main/resources/application.yml`

**Note:** Stock and risk settings have been moved to database. Only static configuration remains.

```yaml
server:
  port: 8080

# Stock/risk settings moved to database (see above)
# trading:
#   stock:
#     initial-shares: 70
#     share-increment: 27
#   risk:
#     max-position: 1
#     daily-loss-limit: 4500
#     weekly-loss-limit: 15000
#     max-hold-minutes: 45

trading:
  window:
    start: "11:30"
    end: "13:00"
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

spring:
  datasource:
    url: "jdbc:sqlite:data/bot.db"
    driver-class-name: "org.sqlite.JDBC"
  jpa:
    hibernate:
      ddl-auto: "update"
    show-sql: false
    database-platform: "org.hibernate.community.dialect.SQLiteDialect"
```

### Earnings Blackout Persistence

- Managed by `EarningsBlackoutService` (Java) with daily 09:00 Asia/Taipei refresh
- Stored in DB tables: `earnings_blackout_meta` (metadata + TTL) and `earnings_blackout_date` (dates)
- Default TTL: 7 days; stale data disables blackout enforcement
- Retry/backoff: 3 attempts (2s, 4s, 8s); last-good data retained on failure
- Legacy file `config/earnings-blackout-dates.json` is read **once** for migration via seed endpoint

**Admin endpoints (require `X-Admin-Token` / `ADMIN_TOKEN`):**
- `POST /admin/earnings-blackout/seed` — load legacy JSON payload
- `POST /admin/earnings-blackout/refresh` — trigger immediate refresh
- `GET /admin/earnings-blackout/status` — latest meta, dates count, stale flag

---

## 🚀 Running the Bot

### Manual Start

```fish
cd /Users/gc/Downloads/work/stock/Lunch-Investor-Bot
./start-lunch-bot.fish YOUR_JASYPT_PASSWORD
```

**The script will:**
1. Clean up any leftover processes from previous runs
2. Check Java 21, Python 3.10+, and Ollama installation
3. Build the Java JAR if not present
4. Start Python bridge with auto-restart supervisor
5. Start Java trading engine
6. Auto-flatten positions at 13:00 and shut down gracefully

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

**CRITICAL:** Use absolute paths to avoid cron environment issues!

### Current Working Directory

Replace `/Users/gc/Downloads/work/stock/Lunch-Investor-Bot` with your actual bot directory path.

To find it:
```fish
cd /path/to/your/bot
pwd  # Copy this full path
```

### Two Separate Cron Jobs Required

Edit crontab:

```fish
crontab -e
```

Add these lines (replace `YOUR_ACTUAL_PATH` and `YOUR_JASYPT_PASSWORD`):

```cron
# 1. Scrape earnings blackout dates at 09:00 (Mon-Fri) with Telegram notifications
0 9 * * 1-5 cd /Users/gc/Downloads/work/stock/Lunch-Investor-Bot && /Users/gc/Downloads/work/stock/Lunch-Investor-Bot/python/venv/bin/python3 /Users/gc/Downloads/work/stock/Lunch-Investor-Bot/python/bridge.py --scrape-earnings --jasypt-password YOUR_JASYPT_PASSWORD >> /tmp/earnings-scrape.log 2>&1

# 2. Start trading bot at 11:15 (Mon-Fri) - 15 minutes before trading window for warmup
15 11 * * 1-5 /opt/homebrew/bin/fish -c 'cd /Users/gc/Downloads/work/stock/Lunch-Investor-Bot && ./start-lunch-bot.fish YOUR_JASYPT_PASSWORD stock >> /tmp/mtxf-bot-cron.log 2>&1'
```

### ⚠️ Critical Crontab Requirements

1. **Use ABSOLUTE paths everywhere** - `cd /full/path && /full/path/to/python3 /full/path/to/script`
2. **DO NOT use** `~` or relative paths - cron doesn't expand them
3. **Fish shell path:** Use `/opt/homebrew/bin/fish` (check with `which fish`)
4. **Python venv path:** Use full path to venv python3 binary
5. **Log output:** Redirect to `/tmp/` for debugging (tail `/tmp/mtxf-bot-cron.log`)

### Crontab Breakdown

| Schedule | Purpose | Telegram | Logs |
|----------|---------|----------|------|
| `0 9 * * 1-5` | 09:00 Mon-Fri: Scrape earnings dates | ✅ Start/End | `/tmp/earnings-scrape.log` |
| `15 11 * * 1-5` | 11:15 Mon-Fri: Start bot (15min warmup) | ✅ Full notifications | `/tmp/mtxf-bot-cron.log` |

### Testing Crontab Before Production

**ALWAYS test cron execution before relying on it!**

1. Create a test cron job that runs 2 minutes from now:
```fish
# Get current time
date +"%H:%M"

# Edit crontab and add a test job
# Example: if it's 10:30, add a job for 10:32
crontab -e

# Add this test line (adjust time):
# 32 10 * * * /opt/homebrew/bin/fish -c 'cd /YOUR/PATH && ./start-lunch-bot.fish YOUR_PASSWORD >> /tmp/mtxf-test-cron.log 2>&1'
```

2. Wait for the job to run and check the log:
```fish
# Monitor log file
tail -f /tmp/mtxf-test-cron.log

# After job runs, verify processes started
ps aux | grep -E "(java.*mtxf|python3 bridge)" | grep -v grep
```

3. If successful, remove test job and keep only production jobs

### Grant Cron Permissions (macOS)

1. **System Settings** → **Privacy & Security** → **Full Disk Access**
2. Click **+** → Add `/usr/sbin/cron`
3. Restart cron: `sudo killall cron`

### Verify Crontab is Installed

```fish
crontab -l
```

---

## 😴 macOS Sleep & Wake Management

**⚠️ CRITICAL: Cron jobs do NOT run when your Mac is asleep!**

If your Mac is sleeping at 09:00 or 11:15, the cron jobs will be **skipped entirely** — they won't run when the Mac wakes up.

### Solution: Auto-Wake with `pmset`

Configure your Mac to automatically wake before the cron jobs run:

```bash
# Wake at 08:55 every weekday (5 min before 09:00 scraper)
sudo pmset repeat wakeorpoweron MTWRF 08:55:00
```

### `pmset` Commands Reference

| Command | Description |
|---------|-------------|
| `pmset -g sched` | List all scheduled wake/sleep events |
| `pmset -g` | View all current power settings |
| `sudo pmset repeat wakeorpoweron MTWRF 08:55:00` | Wake at 08:55 Mon-Fri |
| `sudo pmset repeat cancel` | Cancel all scheduled wake events |
| `sudo pmset repeat wake MTWRF 08:55:00` | Wake only (not power on) |
| `sudo pmset repeat poweron MTWRF 08:55:00` | Power on only (if shutdown) |
| `sudo pmset repeat wakeorpoweron MTWRFSU 08:55:00` | Wake every day including weekends |

### Day Codes

| Code | Day |
|------|-----|
| M | Monday |
| T | Tuesday |
| W | Wednesday |
| R | Thursday |
| F | Friday |
| S | Saturday |
| U | Sunday |

### Recommended Setup

```bash
# Set wake schedule (5 min before first cron job)
sudo pmset repeat wakeorpoweron MTWRF 08:55:00

# Verify it's set
pmset -g sched
```

Expected output:
```
Repeating power events:
  wakepoweron at 8:55AM Monday Tuesday Wednesday Thursday Friday
```

### Alternative: Prevent Sleep During Trading Hours

If you prefer to keep your Mac awake during market hours:

```bash
# Keep awake while a command runs
caffeinate -i -w $PID

# Keep awake for 6 hours (21600 seconds)
caffeinate -t 21600 &

# Prevent sleep entirely (not recommended)
sudo pmset -a disablesleep 1

# Re-enable sleep
sudo pmset -a disablesleep 0
```

### Troubleshooting Wake Issues

1. **Wake not working?** Check System Settings → Battery → Options → "Wake for network access"
2. **Verify schedule is set:** `pmset -g sched`
3. **Check power logs:** `pmset -g log | grep -i wake`

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

## 🔄 System Robustness & Self-Healing

### Phase 2: Intra-Day Self-Healing (December 2025)

The bot includes a **process supervisor** that automatically restarts the Python bridge if it crashes during trading hours, ensuring continuous operation without manual intervention.

#### How It Works

1. **Supervisor Loop**: The `start-lunch-bot.fish` script wraps the Python bridge in a supervision loop
2. **Crash Detection**: If the Python process terminates unexpectedly, the supervisor detects it immediately
3. **Auto-Restart**: The bridge is automatically restarted after a 5-second delay
4. **Logging**: All restarts are logged to `logs/supervisor.log` with timestamps and exit codes
5. **Graceful Shutdown**: Stop-file mechanism (`logs/supervisor.stop`) ensures clean shutdown at 13:00

#### Robustness Timeline

| Phase | Feature | Status |
|-------|---------|--------|
| **Phase 1** | Daily resilience (startup cleanup, resource limits) | ✅ Deployed Nov 2025 |
| **Phase 2** | Intra-day self-healing supervisor | ✅ Deployed Dec 2025 |
| **Future** | Circuit breaker for repeated crashes | Planned |

#### Key Benefits

- **Zero Downtime**: Python bridge crashes no longer kill the entire trading session
- **Automatic Recovery**: Restarts happen within seconds without human intervention
- **Visibility**: All crash events and restarts are logged and visible
- **Tested**: 12 new supervisor tests ensure reliability (403 total tests)

### Process Lifecycle

```
11:15 → Bot starts
     ├─ Cleanup old processes
     ├─ Set ulimit -n 16384
     ├─ Start Ollama
     └─ Start supervisor loop
          └─ while [no stop file]:
               ├─ Run Python bridge
               ├─ If crash: log + wait 5s + restart
               └─ If stop file: exit cleanly

13:00 → Java exits
     ├─ Create stop file
     ├─ POST /shutdown to bridge
     ├─ Wait for supervisor exit
     └─ Kill Ollama
```

---

## 📊 Signal Strategy (Anti-Whipsaw v2 - December 2025)

### Problem Solved

The original strategy had **0.02% momentum thresholds** that triggered on tiny price movements, causing:
- **9+ consecutive losing trades** from entering/exiting on noise
- **Whipsaw losses** of ~275 TWD per trade as positions closed immediately after entry
- **Daily losses** of 2,475+ TWD due to over-trading

### Solution: Multi-Layer Anti-Whipsaw Filters

| Filter | Old Value | New Value | Purpose |
|--------|-----------|-----------|---------|
| **Entry Momentum** | 0.02% | **0.05%** | 2.5x higher threshold filters noise |
| **Exit Momentum** | 0.02% | **0.06%** | Exit requires *stronger* reversal than entry |
| **Volume Confirmation** | 1.3x | **1.5x** | Stricter volume surge requirement |
| **Consecutive Signals** | 1 | **3** | Need 3 aligned signals before entry |
| **Post-Exit Cooldown** | None | **3 min** | No re-entry immediately after closing |
| **Min Hold Time** | None | **3 min** | Position must mature before exit evaluation |

### RSI Filter (New)

- **RSI < 70**: Allow LONG entry (avoid buying overbought)
- **RSI > 30**: Allow SHORT entry (avoid selling oversold)
- **Calculated**: 60-period RSI from 3-min price history

### Multi-Timeframe Momentum Alignment

Entry requires **all three timeframes to agree**:

| Timeframe | LONG Condition | SHORT Condition |
|-----------|----------------|-----------------|
| 3-minute | momentum > 0.05% | momentum < -0.05% |
| 5-minute | momentum > 0.04% | momentum < -0.04% |
| 10-minute | momentum > 0 | momentum < 0 |

### Signal Response Fields (New)

```json
{
  "direction": "LONG|SHORT|NEUTRAL",
  "confidence": 0.75,
  "exit_signal": false,
  "momentum_3min": 0.0012,
  "momentum_5min": 0.0008,
  "momentum_10min": 0.0004,
  "volume_ratio": 1.62,
  "rsi": 45.3,
  "consecutive_signals": 3,
  "in_cooldown": false,
  "cooldown_remaining": 0,
  "raw_direction": "LONG"
}
```

### Exit Signal Logic

Exits require **stronger reversal** than entry:
- Must breach 0.06% reversal (vs 0.05% entry)
- Both 3-min AND 5-min must confirm reversal direction
- Still respects 3-min minimum hold time on Java side

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

### How It Works (Java-only)

1. **09:00 Daily**: `EarningsBlackoutService` refreshes via Yahoo Finance (3 retries, 2s/4s/8s backoff) and stores a snapshot in DB
2. **TTL**: 7 days; stale data pauses enforcement and triggers a one-time Telegram warning
3. **Risk Management**: `RiskManagementService` enforces blackout only when data is fresh; label derived from the first three tickers in `tickers_checked`

### Admin / Migration

- `POST /admin/earnings-blackout/seed` — ingest legacy `earnings-blackout-dates.json` payload (migration only)
- `POST /admin/earnings-blackout/refresh` — trigger immediate refresh
- `GET /admin/earnings-blackout/status` — latest meta, dates count, stale flag
- Header: `X-Admin-Token` (value from `ADMIN_TOKEN` env/system property)

Legacy JSON editing is deprecated; file remains only for one-time migration.

---

## 📊 Logs & Monitoring

### Log Files

| File | Content | Rotation |
|------|---------|----------|
| `logs/mtxf-bot.log` | Java trading engine | Daily, 7 days |
| `logs/python-bridge.log` | Python FastAPI bridge | Manual |
| `/tmp/mtxf-bot-cron.log` | Cron job output | Append |

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

The project includes a complete testing suite with **403 tests** covering all components.

| Category | Tests | Description |
|----------|-------|-------------|
| **Java Integration Tests** | 108 | Java-Python bridge communication, order flow |
| **Python Unit Tests** | 59 | Bridge logic, contract validation, config decryption |
| **Python Integration Tests** | 24 | Real bridge endpoints, Ollama integration |
| **E2E Tests** | 18 | Full trading session simulation |
| **Fish Shell Tests** | 27 | Startup script, supervisor, environment validation |
| **Total** | **403** | |

### Running All Tests

Use the provided test runner script (requires Jasypt password):

```bash
./run-tests.sh <jasypt-password>    # Run all tests
./run-tests.sh help                 # Show help and usage info
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

E2E prerequisites:
- Java trading engine running on `JAVA_URL` (default `http://localhost:16350`)
- Python bridge running on `BRIDGE_URL` (default `http://localhost:8888`)
- Ollama running on `http://localhost:11434` for news veto checks (optional; tests skip if unavailable)

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

*Last Updated: December 2025 (v2.2 - Dual-Mode with 70-Share MediaTek for 80k Capital)*
*Owner: DreamFulFil | Status: 100/100 Complete*
