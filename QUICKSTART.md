# 🚀 MTXF Lunch Bot - Quick Start Guide

## ⚡ 5-Minute Setup (Copy-Paste Ready)

### Step 1: Download & Setup (2 minutes)
```bash
cd /Users/gc/Downloads/work/stock/mtxf-bot
chmod +x scripts/*.sh
./scripts/setup.sh
```

### Step 2: Configure Credentials (2 minutes)
Edit `src/main/resources/application.yml`:

```yaml
shioaji:
  api-key: "PASTE_YOUR_API_KEY_HERE"
  secret-key: "PASTE_YOUR_SECRET_HERE"
  ca-path: "/Users/gc/Downloads/Sinopac.pfx"
  ca-password: "YOUR_PASSWORD"
  simulation: true  # ⚠️ MUST be true for testing

telegram:
  bot-token: "123456789:ABCdefGHI..."  # From @BotFather
  chat-id: "987654321"                 # Your chat ID
```

**Get Telegram Chat ID**:
```bash
# 1. Send "Hello" to your bot
# 2. Run:
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates" | grep -o '"chat":{"id":[0-9]*' | grep -o '[0-9]*$'
```

### Step 3: Launch Bot (1 minute)
```bash
./scripts/start-lunch-bot.sh
```

**Done!** Bot will:
- ✅ Trade MTXF during 11:30-13:00 (paper mode)
- ✅ Send Telegram alerts
- ✅ Auto-flatten at 13:00
- ✅ Emergency shutdown at -4,500 TWD loss

---

## 📱 What You'll See in Telegram

```
🚀 MTXF Lunch Bot started
Trading window: 11:30 - 13:00
Max position: 1 contract
Daily loss limit: 4500 TWD

✅ ORDER FILLED
BUY 1 MTXF @ 17850
Position: 1

💰 POSITION CLOSED
Reason: Target hit
P&L: 1200 TWD
Daily P&L: 1200 TWD

📊 DAILY SUMMARY
Final P&L: 1200 TWD
Status: ✅ Profitable
```

---

## 🧪 Testing Workflow (Recommended)

```bash
# Week 1-4: Paper trading
./scripts/test-paper-trading.sh  # 5-minute smoke test
./scripts/start-lunch-bot.sh     # Full day test

# Monitor logs
tail -f logs/mtxf-bot.log

# After 2-4 weeks of profitable paper trading:
# Change simulation: true → false in application.yml
# Then restart for LIVE trading
```

---

## 📊 Performance Target

| Metric | Value |
|--------|-------|
| Capital | 100,000 TWD |
| Monthly Target | 30,000 TWD (30%) |
| Daily Target | 1,000 TWD |
| Win Rate | 60%+ |
| Max Daily Loss | -4,500 TWD (auto-stop) |

---

## 🛠️ Common Commands

```bash
# Start bot
./scripts/start-lunch-bot.sh

# Test paper trading (5 min)
./scripts/test-paper-trading.sh

# View logs
tail -f logs/mtxf-bot.log

# Stop bot
pkill -f "mtxf-bot"
pkill -f "bridge.py"

# Check Python bridge
curl http://localhost:8888/health

# Check Ollama
curl http://localhost:11434/api/tags
```

---

## 📚 Documentation

| Guide | Purpose |
|-------|---------|
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | Production setup, paper → live switch |
| [STRATEGY.md](docs/STRATEGY.md) | Trading logic, entry/exit rules |
| [CHECKLIST.md](docs/CHECKLIST.md) | Pre-launch verification, daily tasks |
| [MANIFEST.md](docs/MANIFEST.md) | File structure, troubleshooting |

---

## ⚠️ Critical Safety Rules

1. **ALWAYS start with `simulation: true`**
2. **Test for 2-4 weeks before live trading**
3. **Never increase position size until 3 months profitable**
4. **Review logs daily at 13:30**
5. **If 3 losing days in a row → stop and review**

---

## 🏗️ Project Structure

```
mtxf-bot/
├── pom.xml                           # Maven build
├── src/main/
│   ├── java/tw/gc/mtxfbot/
│   │   ├── MtxfBotApplication.java  # Main entry
│   │   ├── TradingEngine.java       # Core logic (270 lines)
│   │   ├── TelegramService.java     # Alerts
│   │   └── AppConfig.java           # Spring config
│   └── resources/
│       └── application.yml           # ⚠️ YOUR CREDENTIALS HERE
├── python/
│   ├── bridge.py                    # Shioaji + Ollama (150 lines)
│   └── requirements.txt
└── scripts/
    ├── setup.sh                     # One-time setup
    ├── start-lunch-bot.sh           # Main launcher
    └── test-paper-trading.sh        # 5-min test
```

**Total code**: ~600 lines (ultra-lightweight!)

---

## 🎯 Next Steps

1. ✅ Run `./scripts/setup.sh`
2. ✅ Configure `application.yml`
3. ✅ Test: `./scripts/test-paper-trading.sh`
4. ✅ Paper trade for 2-4 weeks
5. ✅ Track performance in spreadsheet
6. ✅ Switch to live when consistently profitable

---

## 📞 Need Help?

- **Shioaji Login Issues**: Check API key, .pfx path, password
- **Telegram Not Working**: Verify bot token and chat ID
- **Ollama Timeout**: Restart `brew services restart ollama`
- **Orders Not Executing**: Check margin balance (need 40K+ TWD)

**Read full docs**: [DEPLOYMENT.md](docs/DEPLOYMENT.md)

---

## 🎓 Llama 3.1 8B News Veto Prompt (Included)

The bot uses this exact prompt every 10 minutes:

```
You are a Taiwan stock market news analyst. Analyze these headlines 
and decide if trading should be VETOED due to major negative news.

Headlines:
- 美股暴跌500點 台指期挫低
- 央行突襲升息2碼
- 聯發科法說利多
...

Respond ONLY with valid JSON:
{"veto": true/false, "score": 0.0-1.0, "reason": "brief explanation"}

Veto if: geopolitical crisis, major crash, regulatory halt, war.
Score: 0.0=very bearish, 0.5=neutral, 1.0=very bullish
```

This prevents disaster trades during black swan events! 🛡️

---

## 🚀 Launch Checklist (Final Review)

Before switching to live trading:

- [ ] Paper traded 20+ days profitably
- [ ] Win rate ≥ 60%
- [ ] Max drawdown < 10%
- [ ] Telegram alerts working 100%
- [ ] Emergency shutdown tested
- [ ] Auto-flatten verified at 13:00
- [ ] Sinopac account funded with 100K+ TWD
- [ ] `simulation: false` set in application.yml

**Ready to launch?** 🎯

```bash
./scripts/start-lunch-bot.sh
# Watch logs: tail -f logs/mtxf-bot.log
# Monitor Telegram alerts on phone
# First 3 days: Watch closely with manual override ready
```

---

**💡 Pro Tip**: Keep a trading journal in Notion/Obsidian. Record every signal, your thoughts, market conditions. This is invaluable for improving the strategy.

**⚠️ Legal Disclaimer**: This is educational software. Trading involves risk of loss. Test thoroughly. Use at your own risk. Not financial advice.

**🎯 Good luck and may the odds be ever in your favor!** 🚀📈
