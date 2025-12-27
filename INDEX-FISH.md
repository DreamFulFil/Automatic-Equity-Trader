# 🐟 MTXF Bot - Fish Shell User Index

## 🎯 Start Here (Fish Users Only)

**You're using Fish shell** - this bot is fully optimized for you!

### Quick Start (3 Steps)

```fish
# 1. Setup (once)
fish scripts/setup-fish.fish

# 2. Edit config
nano src/main/resources/application.yml
# Add: Shioaji credentials + Telegram bot token
# Set: ca-password to your 身分證字號

# 3. Run
fish start-lunch-bot.fish
```

---

## 📚 Fish-Specific Documentation

| File | Purpose | When to Read |
|------|---------|--------------|
| **FISH-QUICKSTART.txt** | Entry point for Fish users | First! |
| **FISH-SHELL-GUIDE.md** | Complete Fish guide | Setup phase |
| **FISH-FINAL-SUMMARY.txt** | Delivery confirmation | Reference |

---

## 🐟 Fish Scripts (Ready to Run)

| Script | Purpose | Usage |
|--------|---------|-------|
| `start-lunch-bot.fish` | Main launcher | `fish start-lunch-bot.fish` or double-click! |
| `scripts/setup-fish.fish` | One-time setup | `fish scripts/setup-fish.fish` |
| `scripts/tw.gc.mtxfbot.plist` | Auto-start config | For launchd |

**All scripts use Fish syntax and `activate.fish`!**

---

## ⚡ Double-Click to Run

1. Make executable:
```fish
chmod +x start-lunch-bot.fish
```

2. Double-click `start-lunch-bot.fish` in Finder
3. Terminal opens, bot starts automatically!

---

## 🔑 Critical Configuration

**File**: `src/main/resources/application.yml`

```yaml
shioaji:
  ca-password: "A123456789"  # 🔑 Your 身分證字號 (National ID)
  person-id: "A123456789"    # Same as ca-password
  simulation: true           # Paper trading mode
```

⚠️ **IMPORTANT**:
- `ca-password` = Your National ID (身分證字號)
- Must be valid Taiwan National ID format
- Same value for `person-id`

---

## 🐟 Fish vs Bash/Zsh

### What's Different?

| Bash/Zsh | Fish | Status |
|----------|------|--------|
| `source venv/bin/activate` | `source venv/bin/activate.fish` | ✅ Fixed |
| `PID=$!` | `set PID $last_pid` | ✅ Fixed |
| `VAR=value` | `set VAR value` | ✅ Fixed |
| `for i in {1..10}` | `for i in (seq 1 10)` | ✅ Fixed |
| `if [ -f file ]` | `if test -f file` | ✅ Fixed |

**All our scripts use Fish syntax!**

---

## 📦 Complete File List

### Fish-Specific (New!)
```
start-lunch-bot.fish          ← Double-click to run!
scripts/setup-fish.fish       ← One-time setup
scripts/tw.gc.mtxfbot.plist   ← Auto-start config
FISH-QUICKSTART.txt           ← Start here
FISH-SHELL-GUIDE.md           ← Complete guide
FISH-FINAL-SUMMARY.txt        ← Delivery doc
INDEX-FISH.md                 ← This file
```

### Core Application
```
pom.xml                       Maven build
src/main/java/...             Java code (293 lines)
python/bridge.py              Python bridge (165 lines)
application.yml               Configuration
```

### Documentation
```
README.md                     Project overview
UNLIMITED-STRATEGY.md         Trading strategy
DEPLOYMENT.md                 Production setup
ARCHITECTURE.md               System design
```

---

## 🚀 Usage Examples

### Start Bot (3 Ways)

```fish
# Method 1: Double-click in Finder
# → Just click start-lunch-bot.fish

# Method 2: Terminal
fish start-lunch-bot.fish

# Method 3: Custom function
function mtxf
    cd /Users/gc/Downloads/work/stock/mtxf-bot
    fish start-lunch-bot.fish
end
# Then: mtxf
```

### Monitor Bot

```fish
# View logs (live)
tail -f logs/mtxf-bot.log

# Check services
curl http://localhost:8888/health
pgrep -f "bridge.py"

# Stop bot
pkill -f "mtxf-bot"
pkill -f "bridge.py"
```

### Auto-Start Setup

```fish
# Install launchd agent
cp scripts/tw.gc.mtxfbot.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/tw.gc.mtxfbot.plist

# Verify
launchctl list | grep mtxfbot

# Uninstall
launchctl unload ~/Library/LaunchAgents/tw.gc.mtxfbot.plist
```

---

## 🎓 Fish Shell Helpers

Add to `~/.config/fish/config.fish`:

```fish
# MTXF Bot shortcuts
function mtxf
    cd /Users/gc/Downloads/work/stock/mtxf-bot
    fish start-lunch-bot.fish
end

function mtxf-logs
    tail -f /Users/gc/Downloads/work/stock/mtxf-bot/logs/mtxf-bot.log
end

function mtxf-stop
    pkill -f "mtxf-bot"
    pkill -f "bridge.py"
end

function mtxf-status
    pgrep -f "bridge.py" > /dev/null; and echo "✅ Bridge"; or echo "❌ Bridge"
    pgrep -f "mtxf-bot" > /dev/null; and echo "✅ Bot"; or echo "❌ Bot"
end
```

Then:
```fish
mtxf         # Start
mtxf-logs    # View logs
mtxf-stop    # Stop
mtxf-status  # Check
```

---

## 🔧 Common Issues (Fish)

### Issue: "source: Error encountered"
**Cause**: Using Bash activate  
**Fix**: Use `activate.fish`
```fish
source venv/bin/activate.fish  # ✅ Correct
```

### Issue: "$! not found"
**Cause**: Bash syntax in Fish  
**Fix**: Use `$last_pid`
```fish
nohup python3 script.py &
set PID $last_pid  # ✅ Correct
```

### Issue: "java: command not found"
**Fix**: Add to `~/.config/fish/config.fish`
```fish
set -x PATH /opt/homebrew/opt/openjdk@21/bin $PATH
```

---

## 📈 Performance

| Metric | Target |
|--------|--------|
| Capital | 100,000 TWD |
| Monthly Baseline | 30,000 TWD (30%) |
| **Monthly Best** | **50,000-70,000+ TWD** |
| Daily Loss Limit | -4,500 TWD (enforced) |
| Max Position | 1 MTXF contract |

**No profit caps - let winners run unlimited!**

---

## 📱 Telegram Alerts

Example:
```
🚀 Bot started (11:30-13:00)

✅ LONG @ 17850
Target: UNLIMITED
Stop: -500 TWD

💰 Position: +3,500 TWD 🚀

🔄 EXIT @ 17945
P&L: +4,750 TWD

📊 Daily: +8,200 TWD
Status: ✅ EXCEPTIONAL! 🔥
```

---

## ⚠️ Critical Notes

1. **ca-password MUST be your 身分證字號** (Taiwan National ID)
2. **person-id = same as ca-password**
3. **simulation: true** = paper trading (safe)
4. **simulation: false** = LIVE trading (real money!)
5. Test 2-4 weeks before going live

---

## ✅ Setup Checklist

- [ ] Run `fish scripts/setup-fish.fish`
- [ ] Edit `application.yml` (add credentials)
- [ ] Set `ca-password` to your 身分證字號
- [ ] Verify `simulation: true`
- [ ] Run `fish start-lunch-bot.fish`
- [ ] Check Telegram alerts received
- [ ] Monitor logs: `tail -f logs/mtxf-bot.log`
- [ ] Paper trade 2-4 weeks
- [ ] Switch to `simulation: false` for live

---

## 🎯 Next Steps

1. **Read** [FISH-QUICKSTART.txt](FISH-QUICKSTART.txt) (5 min)
2. **Setup** `fish scripts/setup-fish.fish` (10 min)
3. **Configure** Edit `application.yml` (2 min)
4. **Run** `fish start-lunch-bot.fish`
5. **Monitor** Check Telegram + logs
6. **Trade** Paper trade 2-4 weeks
7. **Review** [FISH-SHELL-GUIDE.md](FISH-SHELL-GUIDE.md)
8. **Go Live** When consistently profitable

---

## 📞 Support

- Fish shell issues → [FISH-SHELL-GUIDE.md](FISH-SHELL-GUIDE.md)
- Trading strategy → [UNLIMITED-STRATEGY.md](docs/UNLIMITED-STRATEGY.md)
- Setup issues → [DEPLOYMENT.md](docs/DEPLOYMENT.md)
- Architecture → [ARCHITECTURE.md](ARCHITECTURE.md)

---

**🐟 Built specifically for Fish shell users on macOS Apple Silicon!**

Good luck and maximum gains! 🚀📈🇹🇼
