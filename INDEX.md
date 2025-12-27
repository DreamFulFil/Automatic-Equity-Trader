# 📁 MTXF Lunch Bot - Complete File Index

## 🎯 Start Here (New Users)

1. **[SUMMARY.txt](SUMMARY.txt)** - Quick overview of entire system
2. **[QUICKSTART.md](QUICKSTART.md)** - 5-minute setup guide
3. **[docs/CHECKLIST.md](docs/CHECKLIST.md)** - Pre-launch verification

## 📚 Core Documentation

| File | Purpose | When to Read |
|------|---------|--------------|
| [README.md](README.md) | Project overview, features, target metrics | First time |
| [QUICKSTART.md](QUICKSTART.md) | Copy-paste setup commands | Setup phase |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design, data flow, components | Understanding internals |
| [SUMMARY.txt](SUMMARY.txt) | One-page reference card | Quick lookup |

## 📖 Detailed Guides

| File | Purpose | When to Read |
|------|---------|--------------|
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production setup, paper→live switch | Before launching |
| [docs/STRATEGY.md](docs/STRATEGY.md) | Trading logic, entry/exit rules | Strategy tuning |
| [docs/CHECKLIST.md](docs/CHECKLIST.md) | Daily tasks, verification steps | Daily routine |
| [docs/MANIFEST.md](docs/MANIFEST.md) | File inventory, troubleshooting | When debugging |

## 💻 Source Code

### Java (293 lines total)

| File | Lines | Purpose |
|------|-------|---------|
| [MtxfBotApplication.java](src/main/java/tw/gc/mtxfbot/MtxfBotApplication.java) | ~20 | Spring Boot entry point |
| [TradingEngine.java](src/main/java/tw/gc/mtxfbot/TradingEngine.java) | ~200 | Core trading logic, risk management |
| [TelegramService.java](src/main/java/tw/gc/mtxfbot/TelegramService.java) | ~50 | Alert system |
| [AppConfig.java](src/main/java/tw/gc/mtxfbot/AppConfig.java) | ~23 | Spring configuration |

### Python (165 lines total)

| File | Lines | Purpose |
|------|-------|---------|
| [python/bridge.py](python/bridge.py) | 165 | FastAPI + Shioaji + Ollama integration |
| [python/requirements.txt](python/requirements.txt) | 6 | Python dependencies |

### Configuration

| File | Purpose |
|------|---------|
| [pom.xml](pom.xml) | Maven build config (Java 21 + Spring Boot 3.3) |
| [src/main/resources/application.yml](src/main/resources/application.yml) | ⚠️ **Credentials go here** |

## 🚀 Scripts (Ready to Run)

| Script | Purpose | When to Run |
|--------|---------|-------------|
| [scripts/setup.sh](scripts/setup.sh) | One-time installation | First setup |
| [scripts/start-lunch-bot.sh](scripts/start-lunch-bot.sh) | Main launcher | Every trading day |
| [scripts/test-paper-trading.sh](scripts/test-paper-trading.sh) | 5-min smoke test | After changes |
| [scripts/verify.sh](scripts/verify.sh) | Verify all files present | Before launch |
| [scripts/tw.gc.mtxfbot.plist](scripts/tw.gc.mtxfbot.plist) | macOS auto-start config | Optional |

## 📊 Statistics

```
Total Source Code:      ~458 lines
  - Java:               293 lines
  - Python:             165 lines

Total Documentation:    ~1,000 lines
  - Markdown files:     8 files
  - Code comments:      Minimal (self-documenting)

Total Scripts:          ~200 lines
  - Setup & deployment: 4 scripts
  - launchd config:     1 plist

Total Project Size:     ~1,700 lines (ultra-lightweight!)
```

## 🗂️ Directory Structure

```
mtxf-bot/
│
├── 📄 README.md                    ← Start here (overview)
├── 📄 QUICKSTART.md                ← 5-minute setup
├── 📄 ARCHITECTURE.md              ← System design
├── 📄 SUMMARY.txt                  ← Quick reference
├── 📄 INDEX.md                     ← This file
├── 📄 .gitignore                   ← Git exclusions
├── 📄 pom.xml                      ← Maven build
│
├── 📁 src/main/
│   ├── 📁 java/tw/gc/mtxfbot/
│   │   ├── MtxfBotApplication.java      [Entry point]
│   │   ├── TradingEngine.java           [Core logic - 200 lines]
│   │   ├── TelegramService.java         [Alerts]
│   │   └── AppConfig.java               [Spring config]
│   └── 📁 resources/
│       └── application.yml              [⚠️ CREDENTIALS HERE]
│
├── 📁 python/
│   ├── bridge.py                   [Shioaji + Ollama - 165 lines]
│   └── requirements.txt            [Dependencies]
│
├── 📁 scripts/
│   ├── setup.sh                    [One-time setup]
│   ├── start-lunch-bot.sh          [Main launcher]
│   ├── test-paper-trading.sh       [Smoke test]
│   ├── verify.sh                   [Pre-flight check]
│   └── tw.gc.mtxfbot.plist         [macOS auto-start]
│
└── 📁 docs/
    ├── DEPLOYMENT.md               [Production guide]
    ├── STRATEGY.md                 [Trading logic]
    ├── CHECKLIST.md                [Daily tasks]
    └── MANIFEST.md                 [File inventory]
```

## 🎯 Recommended Reading Order

### For First-Time Setup:
1. [SUMMARY.txt](SUMMARY.txt) (2 min) - Get the big picture
2. [QUICKSTART.md](QUICKSTART.md) (5 min) - Setup commands
3. [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) (10 min) - Detailed setup
4. Run: `./scripts/verify.sh` - Check all files present
5. Run: `./scripts/setup.sh` - Install dependencies
6. Edit: `application.yml` - Add your credentials
7. Run: `./scripts/test-paper-trading.sh` - 5-min test

### For Understanding the System:
1. [ARCHITECTURE.md](ARCHITECTURE.md) - System design
2. [docs/STRATEGY.md](docs/STRATEGY.md) - Trading logic
3. [TradingEngine.java](src/main/java/tw/gc/mtxfbot/TradingEngine.java) - Core code
4. [bridge.py](python/bridge.py) - Python integration

### For Daily Operations:
1. [docs/CHECKLIST.md](docs/CHECKLIST.md) - Daily routine
2. `tail -f logs/mtxf-bot.log` - Monitor logs
3. Telegram app - Real-time alerts

### For Troubleshooting:
1. [docs/MANIFEST.md](docs/MANIFEST.md) - Common issues
2. Check logs: `logs/mtxf-bot.log`
3. Test bridge: `curl http://localhost:8888/health`
4. Verify Ollama: `curl http://localhost:11434/api/tags`

## 🔧 Configuration Files Priority

1. **⚠️ MUST EDIT**: `src/main/resources/application.yml`
   - Shioaji API key & secret
   - Telegram bot token & chat ID
   - Set `simulation: true` for testing

2. **Optional**: `scripts/tw.gc.mtxfbot.plist`
   - Only if you want auto-start at 11:15 AM

3. **Tune Later**: `python/bridge.py` (lines 90-110)
   - Strategy parameters
   - After 2-4 weeks of paper trading

## 🚨 Critical Files (Never Delete)

- ✅ `application.yml` - All configuration
- ✅ `TradingEngine.java` - Core trading logic
- ✅ `bridge.py` - Market data integration
- ✅ `start-lunch-bot.sh` - Main launcher

## 🔐 Security Notes

**Files with credentials** (NEVER commit to Git):
- `src/main/resources/application.yml`
- Any `.pfx` or `.p12` certificate files

**Protected by .gitignore**:
- `logs/`
- `python/venv/`
- `target/`
- `*.pfx`, `*.p12`

## 📱 Quick Commands Reference

```bash
# First-time setup
./scripts/setup.sh

# Verify installation
./scripts/verify.sh

# Test (5 minutes)
./scripts/test-paper-trading.sh

# Start trading
./scripts/start-lunch-bot.sh

# Monitor logs
tail -f logs/mtxf-bot.log

# Check Python bridge
curl http://localhost:8888/health

# Stop everything
pkill -f "mtxf-bot"
pkill -f "bridge.py"
```

## 📞 Getting Help

1. **Setup Issues**: Read [QUICKSTART.md](QUICKSTART.md)
2. **Trading Questions**: Read [docs/STRATEGY.md](docs/STRATEGY.md)
3. **Errors**: Read [docs/MANIFEST.md](docs/MANIFEST.md) → Troubleshooting
4. **Architecture**: Read [ARCHITECTURE.md](ARCHITECTURE.md)

## 📈 Performance Tracking

Create a spreadsheet with these columns (see [docs/CHECKLIST.md](docs/CHECKLIST.md)):
- Date
- Trades
- Wins
- Losses
- P&L (TWD)
- Max Drawdown
- Win %
- News Vetos
- Notes

## ✅ Next Actions

- [ ] Read [SUMMARY.txt](SUMMARY.txt) (2 min)
- [ ] Read [QUICKSTART.md](QUICKSTART.md) (5 min)
- [ ] Run `./scripts/verify.sh`
- [ ] Run `./scripts/setup.sh`
- [ ] Edit `application.yml`
- [ ] Run `./scripts/test-paper-trading.sh`
- [ ] Read [docs/CHECKLIST.md](docs/CHECKLIST.md)
- [ ] Paper trade 2-4 weeks
- [ ] Go live!

---

**🎯 Remember**: This is a complete, production-ready system. All files are copy-paste deployable. Start with paper trading, verify profitability, then go live.

**⚠️ Legal**: Trading involves risk. Test thoroughly. Use at your own risk. Not financial advice.

**🚀 Good luck and profitable trading!**
