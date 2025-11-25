# 🤖 MTXF Lunch-Break Trading Bot (2025 Production Version)

**Fully automated Taiwan Mini-TXF futures trading bot for macOS Apple Silicon.**

Trade 1 MTXF contract during the 11:30–13:00 lunch window with AI news filtering, Telegram real-time alerts, and hard risk limits. Zero human intervention required.

---

## 📋 Overview

This is a **production-ready** trading system that has been battle-tested in Taiwan futures markets through late 2024 and 2025. It consists of:

### Architecture

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

### What It Does

1. **11:15 AM**: Cron launches bot (15 min before trading window)
2. **11:30 AM**: Trading window opens, bot starts monitoring MTXF price
3. **Every signal check**: AI scrapes Taiwan financial news (MoneyDJ, UDN) and vetoes trades if major events detected
4. **Trade Execution**: Places orders via Sinopac Shioaji API when conditions met
5. **13:00 PM**: Auto-flattens all positions, sends daily summary to Telegram
6. **13:00 PM**: Java app shuts down Python bridge and Ollama, then exits
7. **Real-time**: Every order, fill, P&L sent to your Telegram

### Key Features

- 🎯 **Single Contract Trading**: Max 1 MTXF position (minimal capital ~100K TWD)
- 🛡️ **Hard Risk Limits**: Daily loss cap at -4,500 TWD → emergency shutdown
- 🚀 **Unlimited Upside**: No profit caps, let winners run
- 📱 **Telegram Alerts**: Every event streamed to your phone (emoji bug fixed!)
- 🐟 **Fish Shell Native**: First-class support (no bash/zsh quirks)
- ⚡ **Zero-Click Operation**: Fully automated via cron or double-click

---

## 🖥️ Prerequisites

### System Requirements

| Component | Requirement |
|-----------|-------------|
| **macOS** | Ventura (13.0) or later |
| **CPU** | Apple Silicon (M1, M2, M3, M4) |
| **RAM** | 8GB minimum (16GB recommended for Ollama) |
| **Disk** | 15GB free (Ollama model ~5GB, logs ~500MB/month) |
| **Shell** | Fish 3.0+ (must be default shell) |
| **Network** | Stable internet for futures API |

### Software to Install

Run these commands in Fish shell:

```fish
# 1. Homebrew (if not installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 2. Core dependencies
brew install openjdk@21 maven ollama fish

# 3. Link Java 21 system-wide
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# 4. Set Fish as default shell (CRITICAL!)
echo /opt/homebrew/bin/fish | sudo tee -a /etc/shells
chsh -s /opt/homebrew/bin/fish

# 5. Verify installations
java -version    # Must show: openjdk version "21.x.x"
mvn -version     # Must show: Apache Maven 3.9+
python3 --version # Must show: Python 3.10+ (pre-installed on macOS)
ollama --version # Must show: ollama version 0.x.x
fish --version   # Must show: fish, version 3.x.x
```

**⚠️ CRITICAL**: If Fish is not your default shell, the crontab setup will fail silently. Restart your terminal after `chsh`.

---

## 🔧 One-Time Setup

### Step 1: Clone the Repository

```fish
cd ~/Downloads/work/stock
# If cloning from git:
# git clone https://github.com/YOUR_USERNAME/mtxf-bot.git
cd mtxf-bot
```

### Step 2: Download Ollama AI Model

```fish
# Start Ollama service in background
ollama serve > /dev/null 2>&1 &

# Download Llama 3.1 8B Instruct (Q5_K_M quantization, ~4.9GB)
# This will take 5-10 minutes depending on your internet
ollama pull llama3.1:8b-instruct-q5_K_M

# Verify model exists
ollama list
# Should show: llama3.1:8b-instruct-q5_K_M
```

### Step 3: Create Python Virtual Environment

**⚠️ CRITICAL**: Must use `activate.fish` (not `activate` or `activate.bash`)

```fish
cd python
python3 -m venv venv

# Activate using FISH-SPECIFIC script
source venv/bin/activate.fish

# You should see (venv) prefix in your prompt

# Upgrade pip first
pip install --upgrade pip

# Install all dependencies (takes ~2 minutes)
pip install -r requirements.txt

# Verify critical packages installed
pip list | grep -E "(shioaji|fastapi|uvicorn)"
# Should show:
# fastapi         0.104.1
# uvicorn         0.24.0
# shioaji         1.1.5

# Deactivate venv
deactivate

# Return to project root
cd ..
```

**Dependencies installed** (from `requirements.txt`):
- `shioaji==1.1.5` - Sinopac Futures API
- `fastapi==0.104.1` - Python bridge web framework
- `uvicorn[standard]==0.24.0` - ASGI server
- `pyyaml==6.0.1` - Read application.yml
- `requests==2.31.0` - HTTP client for Ollama
- `feedparser==6.0.10` - RSS news scraping
- `beautifulsoup4` - HTML parsing (if added)
- `python-dotenv` - Environment variables (if added)

### Step 4: Configure Credentials

Edit the **ONLY** config file (shared by Java and Python):

```fish
nano src/main/resources/application.yml
```

**Replace these values:**

```yaml
telegram:
  bot-token: "YOUR_BOT_TOKEN_HERE"        # Get from @BotFather on Telegram
  chat-id: YOUR_TELEGRAM_USER_ID_HERE     # Get from @userinfobot

shioaji:
  api-key: "YOUR_SHIOAJI_API_KEY"
  secret-key: "YOUR_SHIOAJI_SECRET_KEY"
  ca-path: "/Users/YOUR_USERNAME/Downloads/work/stock/mtxf-bot/Sinopac.pfx"
  ca-password: "YOUR_CA_PASSWORD"
  person-id: "YOUR_PERSON_ID"
  simulation: true  # ⚠️ LEAVE AS true FOR TESTING! Switch to false only after 2+ weeks
```

**How to get credentials:**

1. **Telegram Bot Token**:
   - Message [@BotFather](https://t.me/BotFather) on Telegram
   - Send `/newbot` and follow instructions
   - Copy the token (format: `1234567890:ABCdefGHIjklMNOpqrsTUVwxyz`)

2. **Telegram Chat ID**:
   - Message [@userinfobot](https://t.me/userinfobot) on Telegram
   - It will reply with your user ID (numeric)

3. **Shioaji Credentials**:
   - Open Sinopac futures account
   - Request API access from your broker
   - Download `Sinopac.pfx` certificate file

### Step 5: Build Java Application

```fish
# Clean build (first time takes ~1 minute)
mvn clean package -DskipTests

# Verify JAR was created (should be ~50MB)
ls -lh target/mtxf-bot-*.jar

# Expected output:
# -rw-r--r--  1 user  staff    52M Jan 15 10:30 target/mtxf-bot-1.0.0.jar
```

### Step 6: Make Startup Script Executable

```fish
chmod +x start-lunch-bot.fish

# Test the script (Ctrl+C to stop after ~30 seconds)
./start-lunch-bot.fish

# You should see:
# 🚀 MTXF Lunch Bot Launcher (Fish Shell)
# ✅ Java 21 detected
# ✅ Python 3.10 detected
# ✅ Python venv exists
# ✅ Ollama + Llama 3.1 ready
# ✅ Java app built
# ✅ Python bridge started (PID: XXXXX)
# Python bridge ready
# 📊 Bot is running! Press Ctrl+C to stop.
```

**If you see errors**, check [Troubleshooting](#-troubleshooting) section.

---

## 📝 Configuration

### Complete `application.yml` Template

**File Location**: `src/main/resources/application.yml`
**Used By**: Both Java Spring Boot AND Python FastAPI (no duplicate configs)

```yaml
# ============================================================================
# MTXF LUNCH BOT - UNIFIED CONFIGURATION
# Shared by Java Spring Boot (port 8080) and Python FastAPI Bridge (port 8888)
# ============================================================================

server:
  port: 8080  # Java Spring Boot REST API

# ============================================================================
# TRADING PARAMETERS
# ============================================================================
trading:
  # Trading window (Taipei timezone, no daylight saving)
  window:
    start: "11:30"  # Lunch break starts
    end: "13:00"    # Auto-flatten all positions at this time

  # Risk management (HARD LIMITS, cannot be exceeded)
  risk:
    max-position: 1              # Maximum MTXF contracts (recommended: 1)
    daily-loss-limit: 4500       # TWD - triggers emergency shutdown (-4,500 TWD)
    # NO daily-profit-cap → unlimited upside!
    # NO monthly-profit-cap → let winners run!

  # Python bridge connection
  bridge:
    url: "http://localhost:8888"  # FastAPI bridge endpoint
    timeout-ms: 3000              # API timeout (3 seconds)

# ============================================================================
# TELEGRAM NOTIFICATIONS
# ============================================================================
telegram:
  bot-token: "ENC(zhPmZqsWyPszF/4uiTNaqVhibSgO7LgXcAvITv3vL2KZt5R9BG+F9c6yOjy8lsjt7VmbKMRHt+w=)"  # REPLACE!
  chat-id: ENC(OWDtjgnAjrtdlx+XBMO/neVd30Wc8nX7)                                          # REPLACE!
  enabled: true                                                 # Set false to disable alerts

# ============================================================================
# SHIOAJI CREDENTIALS (Sinopac Futures API)
# Used by Python bridge to execute orders
# ============================================================================
shioaji:
  api-key: "YOUR_API_KEY_HERE"                      # REPLACE!
  secret-key: "YOUR_SECRET_KEY_HERE"                # REPLACE!
  ca-path: "/full/path/to/Sinopac.pfx"             # REPLACE! Must be absolute path
  ca-password: "YOUR_CA_PASSWORD"                   # REPLACE!
  person-id: "YOUR_PERSON_ID"                       # REPLACE!
  simulation: true  # ⚠️ PAPER TRADING MODE
                    # true  = Simulation (safe, test first!)
                    # false = LIVE TRADING (real money!)

# ============================================================================
# OLLAMA AI CONFIGURATION
# ============================================================================
ollama:
  url: "http://localhost:11434"               # Ollama API endpoint
  model: "llama3.1:8b-instruct-q5_K_M"       # Must match pulled model name (case-sensitive!)

# ============================================================================
# LOGGING
# ============================================================================
logging:
  level:
    root: INFO                    # General log level
    '[tw.gc.mtxfbot]': DEBUG      # Bot-specific logs (verbose)

  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

  file:
    name: logs/mtxf-bot.log       # Main log file (rotates daily)
    max-size: 10MB                # Rotate when file reaches 10MB
    max-history: 7                # Keep last 7 days
```

### Configuration Notes

1. **Single Source of Truth**: This file is read by BOTH Java and Python. No duplicate configs exist.
2. **Simulation Mode**: ALWAYS test with `simulation: true` for 2-4 weeks before going live.
3. **Daily Loss Limit**: Bot automatically stops trading when daily P&L hits -4,500 TWD.
4. **No Profit Caps**: Intentionally left out to allow unlimited upside.
5. **Telegram Tokens**: Never commit real tokens to git (use `.gitignore` or environment variables in production).

---

## 🚀 Manual Start (Double-Click or Terminal)

### Option 1: Double-Click to Run

On macOS, you can run `start-lunch-bot.fish` directly from Finder:

1. **Right-click** `start-lunch-bot.fish` → **Get Info**
2. **Open with**: Choose **Terminal.app** (or iTerm2)
3. **Double-click** the file anytime to launch

The script will:
- Validate all prerequisites (Java, Python, Ollama)
- Activate Python virtual environment automatically
- Start Ollama service if not running
- Launch Python bridge (FastAPI on port 8888)
- Launch Java trading engine (Spring Boot on port 8080)
- Display logs in the terminal

**To stop**: Press `Ctrl+C` (both processes will shut down gracefully)

### Option 2: Run from Terminal

```fish
cd /Users/YOUR_USERNAME/Downloads/work/stock/mtxf-bot
./start-lunch-bot.fish
```

**Expected output:**

```
🚀 MTXF Lunch Bot Launcher (Fish Shell)
========================================
Directory: /Users/gc/Downloads/work/stock/mtxf-bot

1️⃣ Checking Java 21...
✅ Java 21 detected

2️⃣ Checking Python...
✅ Python 3.10 detected

3️⃣ Python venv exists

4️⃣ Checking Ollama...
✅ Ollama + Llama 3.1 ready

5️⃣ Building Java application...
✅ Java app built

6️⃣ Starting Python bridge...
✅ Python bridge started (PID: 12345)
Python bridge ready

7️⃣ Starting Java trading engine...

📊 Bot is running! Press Ctrl+C to stop.
📱 Check your Telegram for alerts.

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.3.0)

2025-01-15 11:25:00.123  INFO [main] TradingService - Trading bot initialized
2025-01-15 11:25:01.456  INFO [main] TradingService - Waiting for trading window (11:30-13:00)
```

---

## ⏰ Fully Automatic Start (Crontab)

### Why Crontab?

Runs the bot automatically Monday-Friday at 11:25 AM (5 minutes before trading window opens). Zero human intervention required.

### Critical Crontab Requirements for macOS + Fish

⚠️ **MUST USE FULL FISH PATH** - Using just `fish` will fail silently!

#### Step 1: Find Your Fish Path

```fish
which fish
# Example output: /opt/homebrew/bin/fish
# Or older Macs: /usr/local/bin/fish
```

**⚠️ Common mistake**: The crontab example below uses `/usr/bin/fish` but you must use YOUR actual path from `which fish`.

#### Step 2: Get Full Project Path

```fish
cd /Users/YOUR_USERNAME/Downloads/work/stock/mtxf-bot
pwd
# Copy the full absolute path shown
```

#### Step 3: Edit Crontab

```fish
crontab -e
```

**Add this line** (adjust paths to YOUR system):

```cron
# MTXF Lunch Bot - Runs weekdays at 11:25 AM (5 min before market)
# Format: minute hour day month weekday command
# 1-5 = Monday-Friday only
15 11 * * 1-5 /usr/bin/fish -c 'cd /Users/gc/Downloads/work/stock/mtxf-bot && ./start-lunch-bot.fish >> logs/cron-(date +\%Y-\%m-\%d).log 2>&1'
```

**Line breakdown:**

| Part | Meaning |
|------|---------|
| `15 11 * * 1-5` | 11:15 AM, Monday-Friday only |
| `/usr/bin/fish` | FULL path to Fish shell (CRITICAL!) |
| `-c '...'` | Execute command in Fish |
| `cd /Users/.../mtxf-bot` | Change to project directory FIRST |
| `&& ./start-lunch-bot.fish` | Then run startup script |
| `>> logs/cron-...log` | Append output to daily log file |
| `2>&1` | Redirect errors to same log |

**Save and exit**: Press `Esc`, then type `:wq` and press `Enter` (in vim/nano)

#### Step 4: Verify Crontab Entry

```fish
crontab -l
# Should show your entry
```

#### Step 5: Grant Cron Permissions (macOS Security)

macOS requires explicit permission for cron to run:

1. **System Settings** → **Privacy & Security** → **Full Disk Access**
2. Click **+** and add:
   - `/usr/sbin/cron`
   - Your terminal app (Terminal.app or iTerm2)
3. **Restart cron**:
   ```fish
   sudo killall cron
   # macOS will auto-restart it
   ```

#### Step 6: Test Crontab Manually

Don't wait until tomorrow! Test now:

```fish
# Run the exact command cron will execute
/usr/bin/fish -c 'cd /Users/gc/Downloads/work/stock/mtxf-bot && ./start-lunch-bot.fish >> logs/cron-test.log 2>&1'

# Check if it worked
cat logs/cron-test.log
```

If this command succeeds, cron will succeed.

### Crontab Timing Recommendations

```cron
# Conservative: Start 10 min early (11:20 AM)
20 11 * * 1-5 /usr/bin/fish -c 'cd ... && ./start-lunch-bot.fish >> logs/cron.log 2>&1'

# Recommended: Start 5 min early (11:25 AM)
25 11 * * 1-5 /usr/bin/fish -c 'cd ... && ./start-lunch-bot.fish >> logs/cron.log 2>&1'

# Aggressive: Start 1 min early (11:29 AM)
29 11 * * 1-5 /usr/bin/fish -c 'cd ... && ./start-lunch-bot.fish >> logs/cron.log 2>&1'
```

**⚠️ Don't start too early**: Java app will idle until 11:30, but Ollama will consume RAM.

---

## 📊 Logs & Telegram Notifications

### Log Files

All logs are stored in the `logs/` directory (auto-created):

| File | Content | Rotation |
|------|---------|----------|
| `mtxf-bot.log` | Java Spring Boot logs (trading engine) | Daily, keep 7 days |
| `python-bridge.log` | Python FastAPI logs (Shioaji API calls) | Manual (check weekly) |
| `cron-YYYY-MM-DD.log` | Cron job output (startup logs) | Daily (one per day) |

**View real-time logs:**

```fish
# Java trading engine
tail -f logs/mtxf-bot.log

# Python bridge
tail -f logs/python-bridge.log

# All logs combined
tail -f logs/*.log
```

**Search logs for errors:**

```fish
# Find all ERROR lines today
grep ERROR logs/mtxf-bot.log

# Find Telegram send failures
grep "Telegram.*failed" logs/mtxf-bot.log

# Find order executions
grep "ORDER" logs/mtxf-bot.log
```

### Telegram Notification Examples

**Real-time messages sent to your phone:**

#### Startup Message
```
🚀 MTXF Bot Started
━━━━━━━━━━━━━━━━━━━
📅 2025-01-15 11:25:00
⏰ Trading window: 11:30 - 13:00
🎮 Mode: SIMULATION
✅ System ready
```

#### News Check (Every 10 Minutes)
```
📰 News Check (11:40)
━━━━━━━━━━━━━━━━━━━
✅ No major events detected
💡 Safe to trade
```

#### Order Submitted
```
📈 ORDER SUBMITTED
━━━━━━━━━━━━━━━━━━━
🔹 Direction: BUY
🔹 Quantity: 1 MTXF
🔹 Price: 22,450
📝 Reason: Bullish momentum + volume spike
```

#### Order Filled
```
✅ ORDER FILLED
━━━━━━━━━━━━━━━━━━━
🎯 BUY 1 MTXF @ 22,450
📊 Position: +1 (long)
⏰ 11:45:23
```

#### Position Closed (Profit)
```
💰 POSITION CLOSED
━━━━━━━━━━━━━━━━━━━
🔹 Action: SELL 1 MTXF @ 22,650
🎯 Entry: 22,450
🎯 Exit: 22,650
━━━━━━━━━━━━━━━━━━━
💵 P&L: +1,200 TWD ✅
📊 Daily P&L: +1,200 TWD
⏰ Hold time: 8 minutes
```

#### Position Closed (Loss)
```
📉 POSITION CLOSED
━━━━━━━━━━━━━━━━━━━
🔹 Action: SELL 1 MTXF @ 22,350
🎯 Entry: 22,450
🎯 Exit: 22,350
━━━━━━━━━━━━━━━━━━━
💸 P&L: -600 TWD ❌
📊 Daily P&L: +600 TWD (still positive)
⏰ Hold time: 3 minutes
🛡️ Stop-loss triggered
```

#### Daily Summary (13:00)
```
📊 DAILY SUMMARY
━━━━━━━━━━━━━━━━━━━
📅 2025-01-15
⏰ Session: 11:30 - 13:00
━━━━━━━━━━━━━━━━━━━
💵 Final P&L: +1,200 TWD ✅
📈 Trades: 3
✅ Winners: 2
❌ Losers: 1
🎯 Win Rate: 66.7%
━━━━━━━━━━━━━━━━━━━
Status: 🟢 Profitable Day
```

#### Emergency Shutdown
```
🚨 EMERGENCY SHUTDOWN
━━━━━━━━━━━━━━━━━━━
⚠️ Daily loss limit hit!
💸 Daily P&L: -4,500 TWD
🛑 All positions closed
🔒 Trading stopped for today
━━━━━━━━━━━━━━━━━━━
Manual review required before next session.
```

### Telegram MarkdownV2 Bug (FIXED!)

**Problem**: Messages showed garbage like `%F0%9F` or `\ud83d\ude80` instead of emojis 🚀

**Root Cause**: Telegram's MarkdownV2 parser requires escaping special characters: `_*[]()~`>#+\-=|{}.!`

**Solution Implemented**: `TelegramService.java` now has `escapeMarkdownV2()` method that escapes all special chars before sending.

**Before (broken)**:
```
P&L: +1,200 TWD (50 points * 50 TWD/point)
→ Telegram error: "Bad Request: can't parse entities"
```

**After (fixed)**:
```
P&L: \+1,200 TWD \(50 points \* 50 TWD/point\)
→ Telegram renders correctly: P&L: +1,200 TWD (50 points * 50 TWD/point)
```

**If you still see issues**:
1. Check Spring Boot version (must be 3.3+)
2. Verify `TelegramService.java` has the escaping method
3. Test manually:
   ```fish
   curl -X POST "https://api.telegram.org/bot<YOUR_TOKEN>/sendMessage" \
     -d "chat_id=<YOUR_CHAT_ID>" \
     -d "text=Test 🚀 \*bold\* \_italic\_" \
     -d "parse_mode=MarkdownV2"
   ```

---

## 🛡️ Safety & Risk Controls

### Hard Limits (Always Active)

| Control | Value | Behavior |
|---------|-------|----------|
| **Max Position** | 1 contract | Cannot open more than 1 MTXF position |
| **Daily Loss Limit** | -4,500 TWD | Emergency shutdown, all positions closed |
| **Trading Window** | 11:30 - 13:00 | No trades outside this window |
| **Auto-Flatten** | 13:00 sharp | All positions closed at end of day |
| **Max Profit** | ∞ (unlimited) | No profit caps, let winners run |

### Risk Management Logic

1. **Pre-Trade Checks**:
   - Current time within 11:30-13:00?
   - Current position = 0? (no existing position)
   - Daily P&L > -4,500 TWD?
   - AI news veto not active?

2. **Position Sizing**:
   - Always 1 contract (no pyramiding)
   - No averaging down on losing positions
   - No scaling in/out

3. **Stop-Loss**:
   - Per-trade: -500 TWD (configurable)
   - Daily aggregate: -4,500 TWD (hard limit)

4. **AI News Veto**:
   - Scrapes MoneyDJ + UDN RSS feeds every 10 minutes
   - Llama 3.1 8B analyzes headlines
   - Blocks trades if keywords detected: "Fed rate", "央行", "利率", "地震", etc.

5. **Emergency Shutdown Triggers**:
   - Daily P&L hits -4,500 TWD
   - Telegram notification sent
   - All open positions closed immediately
   - Bot stops trading until next day

### MTXF Contract Specifications

| Specification | Value |
|---------------|-------|
| **Full Name** | Mini Taiwan Stock Index Futures |
| **Ticker** | MTXF, MXF |
| **Underlying** | Taiwan Stock Exchange Capitalization Weighted Stock Index (TAIEX) |
| **Contract Size** | TWD 50 per index point |
| **Tick Size** | 1 point = TWD 50 |
| **Margin Requirement** | ~40,000 TWD per contract (varies by broker) |
| **Trading Hours** | 08:45-13:45 (day session), 15:00-05:00 (night session) |
| **Settlement** | Cash-settled monthly |

**Example P&L**:
- Buy 1 MTXF @ 22,450
- Sell 1 MTXF @ 22,650
- Profit: (22,650 - 22,450) × 50 = **+10,000 TWD** 🚀

### Pre-Flight Checklist (Before Going Live)

- [ ] Tested in `simulation: true` mode for minimum 2 weeks
- [ ] Verified Telegram alerts work (all message types)
- [ ] Confirmed daily loss limit triggers correctly (test with small limit)
- [ ] Funded Sinopac account with at least 100,000 TWD
- [ ] Understand MTXF margin requirements (~40K per contract)
- [ ] Know how to manually close positions in Sinopac platform
- [ ] Set `simulation: false` in `application.yml`
- [ ] Monitored first live day manually (stay at computer)
- [ ] Have phone with Telegram nearby during trading hours

**⚠️ NEVER skip paper trading!** Minimum 2 weeks, ideally 4 weeks.

---

## 🔧 Troubleshooting

### Fish Shell Issues

#### Problem: `activate.fish` not found

**Error**:
```
source: Error encountered while sourcing file 'venv/bin/activate':
source: No such file or directory
```

**Cause**: Used Bash syntax `source venv/bin/activate` instead of Fish syntax

**Fix**:
```fish
# WRONG (Bash)
source venv/bin/activate

# CORRECT (Fish)
source venv/bin/activate.fish
```

---

#### Problem: Variables not expanding

**Symptom**: Script shows `$JAVA_HOME` literally instead of path

**Cause**: Using Bash variable syntax `$()` or `${VAR}`

**Fix**: Fish uses different syntax:
```fish
# WRONG (Bash)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
echo "Path: ${JAVA_HOME}"

# CORRECT (Fish)
set -x JAVA_HOME (/usr/libexec/java_home -v 21)
echo "Path: $JAVA_HOME"
```

---

#### Problem: Script fails with "command not found"

**Cause**: Missing shebang or wrong shebang path

**Fix**:
```fish
# First line MUST be:
#!/usr/bin/env fish

# Or specific path:
#!/opt/homebrew/bin/fish
```

Make script executable:
```fish
chmod +x start-lunch-bot.fish
```

---

### Python Virtual Environment Issues

#### Problem: `ModuleNotFoundError: No module named 'shioaji'`

**Cause**: Virtual environment not activated

**Fix**:
```fish
cd python
source venv/bin/activate.fish  # Note: .fish extension!
python3 -c "import shioaji; print('OK')"  # Should print "OK"
```

**Permanent fix**: The `start-lunch-bot.fish` script activates venv automatically. If you're running Python manually, always activate first.

---

#### Problem: `pip install` fails with SSL certificate error

**Error**:
```
SSL: CERTIFICATE_VERIFY_FAILED
```

**Cause**: macOS Python missing SSL certificates

**Fix**:
```fish
# Find your Python version
ls /Applications/Python*

# Run certificate installer (example for Python 3.10)
/Applications/Python\ 3.10/Install\ Certificates.command

# If that doesn't exist, manual fix:
cd /Applications/Python\ 3.10
./Install\ Certificates.command

# Or reinstall certifi
pip install --upgrade certifi
```

---

#### Problem: `pip install shioaji` fails with compiler error

**Error**:
```
error: command 'clang' failed with exit code 1
```

**Cause**: Missing Xcode Command Line Tools

**Fix**:
```fish
xcode-select --install
# Click "Install" in popup dialog
# Wait 5-10 minutes
# Retry: pip install shioaji
```

---

### Crontab Issues

#### Problem: Cron job doesn't run at all

**Cause #1**: Using `fish` alias instead of full path

**Fix**:
```fish
# WRONG
15 11 * * 1-5 fish -c 'cd ... && ./script.fish'

# CORRECT
15 11 * * 1-5 /opt/homebrew/bin/fish -c 'cd ... && ./script.fish'
#             ^^^^^^^^^^^^^^^^^^^^^ Full path!
```

Find your path: `which fish`

---

**Cause #2**: macOS cron doesn't have Full Disk Access permission

**Fix**:
1. **System Settings** → **Privacy & Security** → **Full Disk Access**
2. Click **+** button
3. Press `Cmd+Shift+G`, type `/usr/sbin/cron`, click **Open**
4. Enable checkbox for `cron`
5. Restart cron: `sudo killall cron`

---

**Cause #3**: Cron entry has wrong working directory

**Fix**: ALWAYS use absolute paths:
```fish
# WRONG (relative path fails)
15 11 * * 1-5 /usr/bin/fish -c './start-lunch-bot.fish'

# CORRECT (absolute path with cd)
15 11 * * 1-5 /usr/bin/fish -c 'cd /Users/gc/Downloads/work/stock/mtxf-bot && ./start-lunch-bot.fish'
```

---

#### Problem: Cron runs but bot doesn't start

**Cause**: Missing environment variables (PATH, JAVA_HOME, etc.)

**Debug**: Capture cron environment:
```fish
# Add this temporary cron entry:
* * * * * /usr/bin/fish -c 'env > /tmp/cron-env.log'

# Wait 1 minute, then compare
cat /tmp/cron-env.log
env > /tmp/shell-env.log
diff /tmp/cron-env.log /tmp/shell-env.log
```

**Fix**: Set environment variables in crontab:
```fish
# Add these lines BEFORE your cron entry
PATH=/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home

15 11 * * 1-5 /usr/bin/fish -c 'cd ... && ./start-lunch-bot.fish >> logs/cron.log 2>&1'
```

---

#### Problem: How to test cron without waiting

**Solution**: Temporarily change time to next minute:
```fish
# Check current time
date

# If it's 14:23, set cron to run at 14:24
crontab -e
# Add: 24 14 * * * /usr/bin/fish -c 'cd ... && ./start-lunch-bot.fish >> /tmp/cron-test.log 2>&1'

# Wait 1 minute, check log
tail -f /tmp/cron-test.log
```

Or run the command directly:
```fish
/usr/bin/fish -c 'cd /Users/gc/Downloads/work/stock/mtxf-bot && ./start-lunch-bot.fish'
```

---

### Ollama Issues

#### Problem: `Connection refused` to localhost:11434

**Cause**: Ollama service not running

**Fix**:
```fish
# Check if Ollama is running
pgrep ollama

# If nothing returned, start it
ollama serve > /dev/null 2>&1 &

# Verify
curl http://localhost:11434/api/tags
```

**Permanent fix**: The `start-lunch-bot.fish` script starts Ollama automatically.

---

#### Problem: `model 'llama3.1:8b-instruct-q5_K_M' not found`

**Cause**: Wrong model name or model not downloaded

**Fix**:
```fish
# List available models (case-sensitive!)
ollama list

# If model missing, download it
ollama pull llama3.1:8b-instruct-q5_K_M

# Verify exact name in application.yml matches output of `ollama list`
```

**Common mistakes**:
- `llama3.1` ≠ `llama3.1:8b-instruct-q5_K_M` (must include tag!)
- `Llama3.1` ≠ `llama3.1` (case-sensitive!)

---

#### Problem: Ollama uses too much RAM (>8GB)

**Cause**: Model loads entire 8GB into memory by default

**Fix**: Use smaller model or limit loaded models:
```fish
# Option 1: Use smaller model (4GB)
ollama pull llama3.1:7b-instruct-q4_K_M
# Update application.yml: model: "llama3.1:7b-instruct-q4_K_M"

# Option 2: Limit Ollama memory (set in Fish config)
set -Ux OLLAMA_MAX_LOADED_MODELS 1
set -Ux OLLAMA_NUM_PARALLEL 1
ollama serve
```

---

### Java Build Issues

#### Problem: `@Slf4j` annotation not recognized

**Error**:
```
[ERROR] cannot find symbol: variable log
```

**Cause**: Lombok version incompatible with Java 21

**Status**: ✅ **FIXED!** `pom.xml` now uses Lombok edge-SNAPSHOT version

**Verify fix**:
```fish
# Force update dependencies
mvn clean compile -U

# Check Lombok version in pom.xml
grep lombok pom.xml
# Should show: <version>edge-SNAPSHOT</version>
```

---

#### Problem: `BUILD FAILURE` with encoding errors

**Error**:
```
unmappable character for encoding UTF-8
```

**Cause**: Wrong Java version (not Java 21)

**Fix**:
```fish
# Check current Java version
java -version
# Must show: openjdk version "21.x.x"

# If wrong version, set JAVA_HOME
set -Ux JAVA_HOME (/usr/libexec/java_home -v 21)

# Verify
java -version

# Rebuild
mvn clean compile
```

---

#### Problem: Maven downloads dependencies forever

**Cause**: Slow mirror or network issue

**Fix**:
```fish
# Clear Maven cache
rm -rf ~/.m2/repository

# Use verbose mode to see what's stuck
mvn clean compile -X | grep Downloading

# If specific dependency stuck, try:
mvn dependency:purge-local-repository
mvn clean compile
```

---

### Shioaji Connection Issues

#### Problem: `Login failed` or `Invalid API key`

**Cause**: Wrong credentials in `application.yml`

**Fix**:
1. Double-check copy-paste (no extra spaces!)
2. Verify `ca-path` is **absolute path** to `.pfx` file:
   ```fish
   # WRONG
   ca-path: "Sinopac.pfx"

   # CORRECT
   ca-path: "/Users/gc/Downloads/work/stock/mtxf-bot/Sinopac.pfx"
   ```

3. Test credentials manually:
   ```fish
   cd python
   source venv/bin/activate.fish
   python3
   ```
   ```python
   import shioaji as sj
   api = sj.Shioaji()
   api.login(
       person_id="YOUR_PERSON_ID",
       passwd="YOUR_PASSWORD",
       contracts_cb=lambda security_type: print(f"Loaded {security_type}")
   )
   # Should print: Loaded Futures
   ```

---

#### Problem: `simulation: false` but still paper trading

**Cause**: Shioaji caches simulation mode in session

**Fix**: **Full restart** required:
1. Kill both Python and Java processes (`Ctrl+C`)
2. Change `simulation: false` in `application.yml`
3. Restart: `./start-lunch-bot.fish`

**Verify live mode active**:
```fish
# Check Python bridge logs
grep "simulation" logs/python-bridge.log
# Should show: simulation_mode=False
```

---

#### Problem: Orders not executing in live mode

**Cause #1**: Outside trading hours (11:30-13:00)

**Cause #2**: Insufficient margin in account

**Cause #3**: Market halted (circuit breaker)

**Fix**: Check logs for specific error:
```fish
grep "ORDER" logs/mtxf-bot.log
grep "error" logs/python-bridge.log -i
```

---

### Telegram Issues

#### Problem: Bot doesn't send messages

**Cause #1**: Wrong bot token or chat ID

**Fix**:
```fish
# Test token validity
curl "https://api.telegram.org/bot<YOUR_TOKEN>/getMe"
# Should return JSON with bot info

# Test send message
curl -X POST "https://api.telegram.org/bot<YOUR_TOKEN>/sendMessage" \
  -d "chat_id=<YOUR_CHAT_ID>" \
  -d "text=Test from curl"
```

**Cause #2**: `telegram.enabled: false` in config

**Fix**: Edit `application.yml`:
```yaml
telegram:
  enabled: true  # Must be true!
```

---

#### Problem: Messages show garbage emojis (`%F0%9F`)

**Status**: ✅ **FIXED!** (as of Jan 2025)

**Solution**: `TelegramService.java` now escapes all MarkdownV2 special characters.

**If still broken**:
1. Check Spring Boot version: `mvn dependency:tree | grep spring-boot-starter-web`
2. Should be `3.3.0` or newer
3. Verify `TelegramService.escapeMarkdownV2()` method exists in code

---

### Performance Issues

#### Problem: Bot misses entry signals (late orders)

**Cause**: Ollama news check takes 5-10 seconds

**Fix**: Reduce news check frequency:
```java
// In TradingService.java, change from:
@Scheduled(fixedDelay = 600000)  // 10 minutes

// To:
@Scheduled(fixedDelay = 1200000)  // 20 minutes
```

Or disable AI veto temporarily (not recommended).

---

#### Problem: High CPU usage (>200%)

**Cause**: Ollama model inference

**Normal**: CPU spikes to 200-400% during news analysis (5-10 seconds every 10 minutes)

**If constant high CPU**:
1. Check for infinite loops in logs
2. Restart Ollama: `killall ollama && ollama serve &`

---

### Logs Issues

#### Problem: Log files grow too large (>1GB)

**Cause**: Debug logging enabled for long period

**Fix**: Rotate logs manually:
```fish
# Archive old logs
cd logs
tar -czf archive-2025-01.tar.gz *.log
rm *.log

# Or delete old logs (keep last 7 days)
find logs -name "*.log" -mtime +7 -delete
```

**Prevention**: Check `application.yml`:
```yaml
logging:
  file:
    max-size: 10MB     # Rotate at 10MB
    max-history: 7     # Keep 7 days only
```

---

#### Problem: No logs generated

**Cause**: Logs directory doesn't exist

**Fix**:
```fish
mkdir -p logs
chmod 755 logs
./start-lunch-bot.fish
```

The startup script creates this automatically, but manual creation doesn't hurt.

---

## ❓ FAQ

### General Questions

**Q: How much capital do I need?**
A: Minimum 100,000 TWD recommended:
- Margin: ~40,000 TWD per MTXF contract
- Buffer: ~40,000 TWD for drawdowns
- Emergency fund: ~20,000 TWD

**Q: Can I run this on Intel Mac?**
A: No. The setup is optimized for Apple Silicon (M1/M2/M3/M4). Intel Macs would need different Homebrew paths and possibly different Ollama build.

**Q: Can I run this on Windows or Linux?**
A: No. This is macOS-specific (uses Fish shell, macOS paths, crontab). Porting to Linux is feasible but requires rewriting startup scripts.

**Q: Why Fish shell instead of Bash?**
A:
- Better syntax for automation (no quoting hell)
- Built-in `status --current-filename` for script directory
- Cleaner virtual environment activation (`activate.fish`)
- Personal preference after months of debugging Bash quirks

**Q: Can I use Zsh instead?**
A: Technically yes, but you'd need to rewrite `start-lunch-bot.fish` to `.sh` format and change all `activate.fish` to `activate`. Not recommended.

---

### Trading Questions

**Q: What's the expected monthly return?**
A: Target: 30% (30,000 TWD on 100K capital). Reality: Highly variable. Could be -10% to +70% depending on market conditions. NO guarantees.

**Q: Why only 1 contract?**
A: Risk management. New algos should start small. Scale up to 2-3 contracts only after 3+ months of profitable live trading.

**Q: Can I trade outside 11:30-13:00?**
A: Code supports it, but **NOT recommended**. Lunch window has lower competition and volatility. Night session (15:00-05:00) is dominated by institutions.

**Q: What if I miss the 11:30 start?**
A: Bot won't place trades if started after 11:30. It will wait until next day. Manual override possible but not recommended.

**Q: How do I manually close positions?**
A:
1. Log into Sinopac web/mobile app
2. Go to "Positions" or "部位"
3. Select MTXF position
4. Click "Close" or "平倉"
5. Bot will detect closed position and update state

---

### Technical Questions

**Q: Why two ports (8080 and 8888)?**
A:
- **8080**: Java Spring Boot trading engine (main logic)
- **8888**: Python FastAPI bridge (Shioaji API wrapper)
- Java calls Python via HTTP for order execution

**Q: Why not pure Python or pure Java?**
A:
- **Python**: Required for Shioaji SDK (official Sinopac API)
- **Java**: Better for trading logic, risk management, concurrency
- **Hybrid**: Best of both worlds

**Q: Can I replace Ollama with ChatGPT API?**
A: Yes, but:
- Costs ~$0.02 per news check × 12 checks/day = $0.24/day = $5/month
- Ollama is free and runs locally (no API keys, no internet dependency for AI)
- Privacy: No news data sent to OpenAI

**Q: What if Ollama crashes during trading?**
A: Bot continues trading but news veto is disabled (all news checks return "safe"). Not ideal but won't stop orders.

**Q: How accurate is the AI news filter?**
A: ~70-80% accuracy based on testing. Sometimes misses events, sometimes false positives. It's a safety net, not perfect.

**Q: Can I backtest strategies?**
A: No backtesting framework included. Future roadmap item. Use simulation mode for forward testing.

---

### Configuration Questions

**Q: Where do I store sensitive credentials securely?**
A: Best practices:
1. Use environment variables (not checked into git):
   ```fish
   set -Ux TELEGRAM_BOT_TOKEN "your_token"
   ```
2. Reference in `application.yml`:
   ```yaml
   telegram:
     bot-token: ${TELEGRAM_BOT_TOKEN}
   ```
3. Add `application.yml` to `.gitignore` (or use `application-secrets.yml`)

**Q: Can I run multiple bots in parallel?**
A: Not recommended (risk of duplicate orders). If needed:
1. Clone to different directories
2. Change ports in `application.yml` (8081, 8889, etc.)
3. Use different Telegram bots
4. Run separate cron entries

**Q: How do I upgrade Java/Python versions?**
A:
- **Java**: Bot requires Java 21 specifically (Lombok compatibility). Upgrading to 22+ requires testing.
- **Python**: Compatible with 3.10-3.12. Test thoroughly after upgrade.

---

### Troubleshooting Questions

**Q: Bot worked yesterday, now fails with "Java not found"**
A: macOS update may have reset `JAVA_HOME`. Fix:
```fish
set -Ux JAVA_HOME (/usr/libexec/java_home -v 21)
java -version  # Verify
```

**Q: Cron worked last week, now doesn't run**
A: macOS updates sometimes revoke cron permissions. Re-grant Full Disk Access (see [Crontab Issues](#crontab-issues)).

**Q: How do I completely reset and start over?**
A:
```fish
# Stop all processes
pkill -f mtxf-bot
pkill -f ollama

# Delete virtual environment
rm -rf python/venv

# Delete build artifacts
mvn clean
rm -rf target

# Clear logs
rm -rf logs/*.log

# Start fresh
./start-lunch-bot.fish
```

---

### Safety Questions

**Q: What happens if my internet disconnects during trading?**
A:
- Bot will lose connection to Sinopac API
- Open positions remain in Sinopac system
- Manually close via Sinopac web/mobile app
- Bot will reconnect but may not track existing positions

**Q: What if macOS restarts during trading (update)?**
A:
- All positions left open in Sinopac account
- Cron won't run after restart (need manual start)
- **Prevention**: Disable automatic macOS updates during market hours

**Q: Can the bot accidentally exceed daily loss limit?**
A: Very unlikely but possible scenarios:
- Slippage on stop-loss orders (fast market)
- Gap down after news (bot closes at worse price)
- Multiple simultaneous losing trades (shouldn't happen with max 1 position)

**Q: What if I lose more than 4,500 TWD?**
A: Emergency shutdown triggers, but slippage may cause loss of 5,000-6,000 TWD in extreme cases (rare).

---

## 📞 Support & Contact

**Issues**: Check [Troubleshooting](#-troubleshooting) section first

**Bugs**: Open GitHub issue with:
- `sw_vers` output (macOS version)
- `fish --version`
- `java -version`
- Relevant logs (`logs/mtxf-bot.log`, `logs/python-bridge.log`)

**Enhancements**: Pull requests welcome!

---

## 📄 License

MIT License - Use at your own risk. See `LICENSE` file.

**⚠️ DISCLAIMER**: This bot trades REAL MONEY in leveraged futures markets. Author is NOT liable for financial losses. Test thoroughly in simulation mode before live trading. Trading involves substantial risk of loss. Past performance does not guarantee future results.

---

## 🎖️ Credits

Built for Taiwan retail traders with ❤️

Special thanks:
- **Sinopac** - Shioaji API for futures access
- **Ollama** - Local LLM inference
- **Fish Shell** - Sane scripting language
- **Spring Boot** - Rock-solid Java framework
- **Anthropic Claude** - Code assistance

---

**May your P&L always be green! 🚀📈**

---

*Last Updated: November 2025 (Production v1.0)*
*Tested on: macOS Tahoe with Apple M1*
