# 🐟 MTXF Bot - Fish Shell User Guide

## 🎯 Quick Start for Fish Shell Users

This bot is **fully optimized for Fish shell on macOS Apple Silicon**.

---

## 🚀 One-Time Setup (5 Minutes)

### Method 1: Double-Click Setup (Easiest)

1. **Make the script executable**:
```fish
cd /Users/gc/Downloads/work/stock/mtxf-bot
chmod +x scripts/setup-fish.fish
```

2. **Double-click** `setup-fish.fish` in Finder
   - Or run: `fish scripts/setup-fish.fish`

This will:
- ✅ Install Java 21, Maven, Ollama via Homebrew
- ✅ Download Llama 3.1 8B Instruct (q5_K_M)
- ✅ Create Python venv with Fish-specific `activate.fish`
- ✅ Install all Python dependencies

### Method 2: Manual Commands

```fish
# Install dependencies
brew install openjdk@21 maven ollama

# Download AI model
ollama pull llama3.1:8b-instruct-q5_K_M

# Setup Python environment (Fish-aware)
cd python
python3 -m venv venv
source venv/bin/activate.fish  # Fish-specific activation!
pip install -r requirements.txt
cd ..
```

---

## ⚙️ Configure Credentials (2 Minutes)

Edit `src/main/resources/application.yml`:

```yaml
shioaji:
  api-key: "YOUR_SHIOAJI_API_KEY"
  secret-key: "YOUR_SHIOAJI_SECRET_KEY"
  ca-path: "/Users/YOUR_USERNAME/Documents/Sinopac.pfx"
  ca-password: "A123456789"  # Your 身分證字號
  person-id: "A123456789"    # Same as ca-password
  simulation: true  # IMPORTANT: true = paper trading

telegram:
  bot-token: "123456789:ABCdefGHI..."
  chat-id: "987654321"
```

### Get Telegram Chat ID (Fish-friendly):

```fish
# 1. Send "Hello" to your bot
# 2. Get chat ID:
set BOT_TOKEN "YOUR_BOT_TOKEN"
curl "https://api.telegram.org/bot$BOT_TOKEN/getUpdates" | grep -o '"chat":{"id":[0-9]*' | grep -o '[0-9]*$'
```

---

## 🚀 Running the Bot

### Method 1: Double-Click (Easiest)

1. **Double-click** `start-lunch-bot.fish` in Finder
2. Terminal window opens automatically
3. Bot starts at 11:30, stops at 13:00
4. Press `Ctrl+C` to stop manually

### Method 2: Run in Terminal

```fish
cd /Users/gc/Downloads/work/stock/mtxf-bot
fish start-lunch-bot.fish
```

The script will:
1. ✅ Check Java 21
2. ✅ Activate Python venv with `activate.fish`
3. ✅ Start Ollama service
4. ✅ Build Java app (if needed)
5. ✅ Start Python bridge (port 8888)
6. ✅ Start Java trading engine (port 8080)

---

## 🤖 Auto-Start with launchd (Optional)

### Install the Launch Agent

```fish
# Copy plist to launch agents
cp scripts/tw.gc.mtxfbot.plist ~/Library/LaunchAgents/

# Load the agent
launchctl load ~/Library/LaunchAgents/tw.gc.mtxfbot.plist

# Check status
launchctl list | grep mtxfbot
```

Now the bot will **auto-start at 11:15 AM** every weekday!

### Uninstall Auto-Start

```fish
launchctl unload ~/Library/LaunchAgents/tw.gc.mtxfbot.plist
rm ~/Library/LaunchAgents/tw.gc.mtxfbot.plist
```

---

## 🐟 Fish Shell Features

### 1. Proper Virtual Environment Activation

**Bash/Zsh (won't work in Fish)**:
```bash
source venv/bin/activate  # ❌ Doesn't work in Fish!
```

**Fish (correct way)**:
```fish
source venv/bin/activate.fish  # ✅ Fish-specific!
```

Our scripts use `activate.fish` automatically.

### 2. Fish-Friendly Environment Variables

```fish
# Set variables (Fish syntax)
set -x JAVA_HOME /opt/homebrew/opt/openjdk@21
set -x PATH $JAVA_HOME/bin $PATH

# Check variables
echo $JAVA_HOME
echo $PATH
```

### 3. Background Jobs in Fish

```fish
# Start background process
nohup python3 bridge.py > logs/bridge.log 2>&1 &
set PYTHON_PID $last_pid  # Fish uses $last_pid, not $!

# Kill background job
kill $PYTHON_PID
```

### 4. Loops in Fish

```fish
# Wait for service (Fish syntax)
for i in (seq 1 30)
    if curl -s http://localhost:8888/health > /dev/null 2>&1
        echo "✅ Service ready"
        break
    end
    sleep 1
end
```

---

## 📊 Monitoring (Fish Commands)

```fish
# View logs (live tail)
tail -f logs/mtxf-bot.log

# Check Python bridge
curl http://localhost:8888/health | jq

# Check Ollama
curl http://localhost:11434/api/tags | jq

# Find process IDs
pgrep -f "mtxf-bot"
pgrep -f "bridge.py"

# Stop everything
pkill -f "mtxf-bot"
pkill -f "bridge.py"
```

---

## 🔧 Troubleshooting (Fish Shell)

### Issue: "source: Error encountered while sourcing file 'venv/bin/activate'"

**Problem**: Using Bash activation script in Fish

**Solution**: Always use `activate.fish`
```fish
source venv/bin/activate.fish  # NOT venv/bin/activate
```

### Issue: "command not found: java"

**Solution**: Add Homebrew Java to PATH
```fish
# Add to ~/.config/fish/config.fish
set -x PATH /opt/homebrew/opt/openjdk@21/bin $PATH
```

### Issue: Background jobs not working

**Problem**: Using Bash `$!` syntax in Fish

**Solution**: Use `$last_pid` in Fish
```fish
nohup python3 bridge.py &
set PID $last_pid  # Fish-specific
```

### Issue: Ollama not starting

```fish
# Check if running
pgrep -x "ollama"

# Start manually
ollama serve &

# Or use Homebrew services
brew services start ollama
```

---

## 📱 Telegram Alerts

You'll receive on your phone:

```
🚀 MTXF Lunch Bot started
Trading window: 11:30 - 13:00

✅ LONG ENTRY
BUY 1 MTXF @ 17850
Target: UNLIMITED
Stop: -500 TWD

💰 POSITION UPDATE
Unrealized: +3,500 TWD
Letting it run! 🚀

🔄 EXIT - TREND REVERSAL
P&L: +4,750 TWD
Daily P&L: +8,200 TWD 🔥

📊 DAILY SUMMARY
Final P&L: +8,200 TWD
Status: ✅ EXCEPTIONAL! 🚀
```

---

## 🎓 Fish Shell Resources

- **Config file**: `~/.config/fish/config.fish`
- **Functions**: `~/.config/fish/functions/`
- **Documentation**: https://fishshell.com/docs/current/
- **Tutorial**: `fish_config` (opens web UI)

### Useful Fish Functions

```fish
# Add to ~/.config/fish/config.fish

# Quick bot start
function mtxf
    cd /Users/gc/Downloads/work/stock/mtxf-bot
    fish start-lunch-bot.fish
end

# View logs
function mtxf-logs
    tail -f /Users/gc/Downloads/work/stock/mtxf-bot/logs/mtxf-bot.log
end

# Stop bot
function mtxf-stop
    pkill -f "mtxf-bot"
    pkill -f "bridge.py"
end
```

Then just run:
```fish
mtxf        # Start bot
mtxf-logs   # View logs
mtxf-stop   # Stop bot
```

---

## 🚀 Paper Trading → Live Trading

### Step 1: Test Paper Trading (2-4 weeks)

```fish
# Verify simulation mode
grep "simulation:" src/main/resources/application.yml
# Should show: simulation: true

# Run bot
fish start-lunch-bot.fish
```

### Step 2: Switch to Live Trading

**⚠️ WARNING: This uses REAL MONEY!**

```fish
# 1. Edit config
nano src/main/resources/application.yml

# 2. Change:
simulation: true  →  simulation: false

# 3. Verify account funded (100,000+ TWD)

# 4. Restart bot
fish start-lunch-bot.fish
```

### Step 3: Monitor First 3 Days

```fish
# Watch logs closely
tail -f logs/mtxf-bot.log

# Check Telegram alerts
# Verify all orders execute correctly
```

---

## 📈 Performance Tracking

```fish
# Create tracking alias
function mtxf-stats
    echo "Date       | Trades | P&L      | Notes"
    echo "-----------|--------|----------|-------"
    tail -20 logs/mtxf-bot.log | grep "DAILY SUMMARY"
end
```

Track in spreadsheet:
- Date
- Trades count
- Biggest win
- Daily P&L
- Notes

---

## 🆘 Emergency Procedures

```fish
# Force stop everything
pkill -9 -f "mtxf-bot"
pkill -9 -f "bridge.py"
pkill -9 -f "ollama"

# Restart services
brew services restart ollama

# Clear logs
rm logs/*.log

# Rebuild from scratch
mvn clean package -DskipTests
```

---

## ✅ Fish Shell Checklist

- [x] Use `activate.fish` (not `activate`)
- [x] Use `$last_pid` (not `$!`)
- [x] Use `set VAR value` (not `VAR=value`)
- [x] Use `(seq 1 10)` (not `{1..10}`)
- [x] Use `if test -f file` (not `if [ -f file ]`)
- [x] Scripts start with `#!/usr/bin/env fish`

---

## 🎯 Quick Command Reference

```fish
# Setup (once)
fish scripts/setup-fish.fish

# Run bot
fish start-lunch-bot.fish

# View logs
tail -f logs/mtxf-bot.log

# Stop bot
pkill -f "mtxf-bot"

# Check services
pgrep -f "bridge.py"
curl http://localhost:8888/health

# Install auto-start
cp scripts/tw.gc.mtxfbot.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/tw.gc.mtxfbot.plist
```

---

**🐟 Happy trading with Fish shell! 🚀**
