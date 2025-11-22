# MTXF Lunch Bot - Complete File Manifest

## 📁 Project Structure

```
mtxf-bot/
├── pom.xml                                 # Maven build configuration
├── README.md                               # Main documentation
├── .gitignore                              # Git ignore rules
│
├── src/main/
│   ├── java/tw/gc/mtxfbot/
│   │   ├── MtxfBotApplication.java        # Spring Boot entry point
│   │   ├── TradingEngine.java             # Core trading logic (270 lines)
│   │   ├── TelegramService.java           # Telegram alerts (50 lines)
│   │   └── AppConfig.java                 # Spring configuration
│   │
│   └── resources/
│       └── application.yml                 # Configuration (credentials here!)
│
├── python/
│   ├── bridge.py                          # FastAPI + Shioaji + Ollama (150 lines)
│   └── requirements.txt                   # Python dependencies
│
├── scripts/
│   ├── setup.sh                           # One-time setup script
│   ├── start-lunch-bot.sh                 # Main launcher
│   ├── test-paper-trading.sh              # Paper trading test
│   └── tw.gc.mtxfbot.plist                # macOS launchd config
│
└── docs/
    ├── DEPLOYMENT.md                      # Production deployment guide
    ├── STRATEGY.md                        # Trading strategy details
    └── MANIFEST.md                        # This file
```

---

## 🔑 Critical Files (Must Configure)

### 1. `src/main/resources/application.yml`
**Purpose**: All configuration and credentials  
**Action Required**: Add your API keys before running

```yaml
shioaji:
  api-key: "YOUR_SHIOAJI_API_KEY"        # ⚠️ REQUIRED
  secret-key: "YOUR_SHIOAJI_SECRET_KEY"  # ⚠️ REQUIRED
  ca-path: "/path/to/Sinopac.pfx"        # ⚠️ REQUIRED
  ca-password: "YOUR_CA_PASSWORD"        # ⚠️ REQUIRED
  simulation: true                        # ⚠️ Set false for live trading

telegram:
  bot-token: "YOUR_TELEGRAM_BOT_TOKEN"   # ⚠️ REQUIRED
  chat-id: "YOUR_TELEGRAM_CHAT_ID"       # ⚠️ REQUIRED
```

---

## 🚀 Execution Files

### 1. `scripts/setup.sh`
**Run once** - Installs dependencies, downloads Llama 3.1 8B, sets up Python venv

```bash
chmod +x scripts/setup.sh
./scripts/setup.sh
```

### 2. `scripts/start-lunch-bot.sh`
**Main launcher** - Starts Python bridge + Java engine

```bash
./scripts/start-lunch-bot.sh
```

### 3. `scripts/test-paper-trading.sh`
**Safety check** - Runs bot for 5 minutes to verify setup

```bash
./scripts/test-paper-trading.sh
```

---

## 🤖 Core Logic Files

### 1. `TradingEngine.java` (270 lines)
**Responsibilities**:
- Trading window enforcement (11:30-13:00)
- Position management (max 1 contract)
- Risk limits (daily loss -4,500 TWD)
- Auto-flatten at 13:00
- Order execution via Python bridge
- Telegram notifications

**Key Methods**:
- `tradingLoop()` - Runs every 60 seconds during trading window
- `checkRiskLimits()` - Emergency shutdown on loss limit
- `evaluateEntry()` - Fetch signal from Python bridge, place order
- `evaluateExit()` - Monitor unrealized P&L, flatten on target/stop
- `autoFlatten()` - Cron job at 13:00

### 2. `python/bridge.py` (150 lines)
**Responsibilities**:
- Shioaji connection + MTXF subscription
- Market data streaming (tick-by-tick)
- News scraping (MoneyDJ + UDN RSS)
- Llama 3.1 8B news sentiment analysis
- FastAPI endpoints for Java

**API Endpoints**:
- `GET /health` - Health check
- `GET /signal` - Trading signal + news veto
- `POST /order` - Execute order via Shioaji

**Llama 3.1 8B Prompt** (lines 45-58):
```python
"""You are a Taiwan stock market news analyst. Analyze these headlines 
and decide if trading should be VETOED due to major negative news.

Veto if: geopolitical crisis, major crash, regulatory halt, war.
Score: 0.0=very bearish, 0.5=neutral, 1.0=very bullish

Respond ONLY with valid JSON:
{"veto": true/false, "score": 0.0-1.0, "reason": "brief explanation"}"""
```

### 3. `TelegramService.java` (50 lines)
**Responsibilities**:
- Send alerts via Telegram Bot API
- Format messages for mobile readability

**Message Types**:
- 🚀 Bot startup
- ✅ Order filled
- 💰 Position closed
- 🛑 Emergency shutdown
- 📊 Daily summary

---

## 📊 Strategy Implementation

### Entry Logic (TradingEngine.java lines 110-140)
```java
// 1. Check news veto
if (newsVeto) return;

// 2. Check confidence threshold
if (confidence < 0.65) return;

// 3. Execute order
if ("LONG".equals(direction)) {
    executeOrder("BUY", 1, currentPrice);
}
```

### News Veto (bridge.py lines 45-75)
```python
# Every 60 seconds (called by Java)
headlines = fetch_news_headlines()  # MoneyDJ + UDN
news_analysis = call_llama_news_veto(headlines)

return {
    "news_veto": news_analysis.get("veto", False),
    "news_score": news_analysis.get("score", 0.5)
}
```

---

## 🛡️ Risk Management Implementation

### Daily Loss Limit (TradingEngine.java lines 90-105)
```java
if (currentPnL <= -dailyLossLimit) {
    log.error("🛑 DAILY LOSS LIMIT HIT: {} TWD", currentPnL);
    flattenPosition("Daily loss limit");
    emergencyShutdown = true;  // Stop trading
}
```

### Auto-Flatten (TradingEngine.java lines 180-185)
```java
@Scheduled(cron = "0 0 13 * * MON-FRI")  // 13:00 sharp
public void autoFlatten() {
    flattenPosition("End of trading window");
    sendDailySummary();
}
```

---

## 📦 Dependencies

### Java (pom.xml)
- Spring Boot 3.3.5
- Jackson (JSON)
- Lombok (boilerplate reduction)

### Python (requirements.txt)
- FastAPI + Uvicorn (web server)
- Shioaji 1.1.5 (market data + orders)
- PyYAML (config parsing)
- Requests (Ollama API calls)
- Feedparser (RSS scraping)

---

## 🔄 Data Flow

```
1. User starts bot → setup.sh → start-lunch-bot.sh
                         ↓
2. Python bridge launches → Shioaji login → MTXF subscription
                         ↓
3. Java engine launches → REST client → calls /health
                         ↓
4. At 11:30, tradingLoop() starts (every 60 sec)
                         ↓
5. Java calls GET /signal → Python scrapes news → Llama 3.1 8B
                         ↓
6. Python returns {direction, confidence, news_veto}
                         ↓
7. Java evaluates entry → POST /order → Python → Shioaji → Market
                         ↓
8. Telegram alert sent: "✅ ORDER FILLED BUY 1 MTXF @ 17850"
                         ↓
9. Monitor position → evaluateExit() → flatten on target/stop
                         ↓
10. At 13:00 → autoFlatten() → close all positions
                         ↓
11. Daily summary sent to Telegram: "📊 DAILY SUMMARY P&L: +1200 TWD"
```

---

## 🧪 Testing Workflow

1. **Setup**: `./scripts/setup.sh`
2. **Configure**: Edit `application.yml` with credentials
3. **Verify**: Set `simulation: true`
4. **Test**: `./scripts/test-paper-trading.sh` (5-min run)
5. **Monitor**: Check `logs/mtxf-bot.log` for errors
6. **Paper Trade**: Run for 2-4 weeks, track performance
7. **Go Live**: Set `simulation: false`, restart bot

---

## 📝 Logging

### Log Files
- `logs/mtxf-bot.log` - Java app logs (rotated at 10MB)
- `logs/python-bridge.log` - Python bridge logs
- `logs/launchd-stdout.log` - launchd output (if using auto-start)

### Log Levels
```yaml
logging:
  level:
    root: INFO
    tw.gc.mtxfbot: DEBUG  # Detailed trading logic
```

---

## 🔐 Security Notes

**NEVER commit these to Git**:
- `application.yml` with real credentials
- `*.pfx` certificate files
- Log files with account numbers

**Included in .gitignore**:
- `application-prod.yml`
- `*.pfx`, `*.p12`
- `logs/`
- `python/venv/`

---

## 📈 Performance Monitoring

Track these metrics daily:
- Win rate (target: 60%+)
- Average P&L per trade
- Max drawdown
- Sharpe ratio
- Number of news vetos
- Slippage (expected fill vs. actual)

**Spreadsheet Template** (manual tracking recommended):
```
Date | Trades | Wins | Losses | P&L | Max DD | News Vetos
2025-01-02 | 3 | 2 | 1 | +1200 | -500 | 1
2025-01-03 | 2 | 2 | 0 | +2000 | 0 | 0
```

---

## 🚨 Emergency Procedures

### Force Stop
```bash
pkill -f "mtxf-bot"  # Kill Java process
pkill -f "bridge.py"  # Kill Python process
```

### Manual Flatten (if bot crashes)
1. Login to Sinopac web platform
2. Navigate to Futures → Positions
3. Close all MTXF positions manually

### Restart After Emergency Shutdown
```bash
# Reset daily P&L counter (edit TradingEngine.java)
# Then restart
./scripts/start-lunch-bot.sh
```

---

## 📞 Troubleshooting

| Issue | Solution |
|-------|----------|
| Python bridge won't start | Check `python/venv/bin/python3 bridge.py` directly |
| Ollama timeout | Restart Ollama: `brew services restart ollama` |
| Shioaji login failed | Verify API key in application.yml |
| Orders not executing | Check margin balance (need 40,000+ TWD) |
| Telegram not sending | Test token: `curl https://api.telegram.org/bot<TOKEN>/getMe` |

---

## 📚 Additional Resources

- Shioaji Docs: https://sinotrade.github.io/
- TAIFEX Futures: https://www.taifex.com.tw/
- Ollama API: https://github.com/ollama/ollama/blob/main/docs/api.md
- Telegram Bot API: https://core.telegram.org/bots/api

---

**Total Lines of Code**: ~600 lines  
**Build Time**: 30 seconds  
**Setup Time**: 10 minutes (including Llama 3.1 8B download)  
**Ready for**: Paper trading immediately, live trading after 2-4 week verification

🚀 **Good luck and profitable trading!**
