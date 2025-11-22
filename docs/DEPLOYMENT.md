# MTXF Lunch Bot - Deployment Guide

## 🎯 Production Deployment (macOS Apple Silicon)

### Prerequisites
1. **Java 21**: `brew install openjdk@21`
2. **Python 3.10+**: Built-in on macOS
3. **Ollama**: `brew install ollama`
4. **Maven**: `brew install maven`
5. **Shioaji Account**: Register at [Sinopac Securities](https://www.sinotrade.com.tw/)
6. **Telegram Bot**: Create via [@BotFather](https://t.me/botfather)

---

## 📋 Step 1: Configuration

Edit `src/main/resources/application.yml`:

```yaml
# Shioaji Credentials
shioaji:
  api-key: "YOUR_API_KEY_HERE"
  secret-key: "YOUR_SECRET_KEY_HERE"
  ca-path: "/Users/gc/Sinopac.pfx"
  ca-password: "YOUR_PASSWORD"
  simulation: true  # ⚠️ Set to false for LIVE trading

# Telegram Bot
telegram:
  bot-token: "123456789:ABCdefGHIjklMNOpqrsTUVwxyz"
  chat-id: "987654321"
  enabled: true
```

### Get Telegram Chat ID:
```bash
# 1. Send a message to your bot
# 2. Run:
curl https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates
# 3. Find "chat":{"id": YOUR_CHAT_ID}
```

---

## 🚀 Step 2: Manual Launch

```bash
cd /Users/gc/Downloads/work/stock/mtxf-bot
chmod +x scripts/start-lunch-bot.sh
./scripts/start-lunch-bot.sh
```

The script will:
1. ✅ Verify Java 21
2. ✅ Setup Python venv + dependencies
3. ✅ Download Llama 3.1 8B (if needed)
4. ✅ Build Java app with Maven
5. ✅ Start Python bridge (port 8888)
6. ✅ Start Java trading engine (port 8080)

---

## ⏰ Step 3: Auto-Start with launchd (Optional)

Install as macOS launch agent to auto-start at 11:15 AM weekdays:

```bash
# Copy plist
cp scripts/tw.gc.mtxfbot.plist ~/Library/LaunchAgents/

# Load agent
launchctl load ~/Library/LaunchAgents/tw.gc.mtxfbot.plist

# Check status
launchctl list | grep mtxfbot

# Unload (to disable)
launchctl unload ~/Library/LaunchAgents/tw.gc.mtxfbot.plist
```

---

## 📱 Step 4: Telegram Message Examples

You'll receive these alerts:

```
🚀 MTXF Lunch Bot started
Trading window: 11:30 - 13:00
Max position: 1 contract
Daily loss limit: 4500 TWD
```

```
✅ ORDER FILLED
BUY 1 MTXF @ 17850
Position: 1
```

```
💰 POSITION CLOSED
Reason: Exit signal / Target hit
P&L: 1200 TWD
Daily P&L: 1200 TWD
```

```
🛑 EMERGENCY SHUTDOWN
Daily loss: -4600 TWD
Flattening all positions!
```

```
📊 DAILY SUMMARY
Final P&L: 1200 TWD
Target: 1,000 TWD/day (30,000 TWD/month)
Status: ✅ Profitable
```

---

## 🔄 Paper Trading → Live Trading Switch

### Paper Trading (Simulation Mode)
```yaml
shioaji:
  simulation: true
```
- Uses Shioaji test environment
- NO REAL MONEY at risk
- Test strategy for 2-4 weeks

### Live Trading
```yaml
shioaji:
  simulation: false
```
- ⚠️ **REAL MONEY**
- Ensure account has 40,000+ TWD margin
- Start with 1-week observation before going full auto

---

## 📊 Strategy Configuration

The bot implements a **lunch-break scalping strategy**:

1. **Fair Value Anchor**: Uses overnight US futures (NQ/ES) to gauge Taiwan sentiment
2. **Momentum Filter**: 3-min and 5-min price momentum
3. **Volume Imbalance**: Detects institutional flow
4. **News Veto**: Llama 3.1 8B analyzes MoneyDJ + UDN headlines every 10 min

**Customize in `bridge.py`**:
```python
# Line 90-100: Add your strategy logic
if latest_tick["volume"] > 100:
    if price > fair_value:  # Your logic here
        direction = "LONG"
        confidence = 0.7
```

---

## 🛡️ Risk Management

Hardcoded safeguards:
- **Max Position**: 1 MTXF contract (configurable in application.yml)
- **Daily Loss Limit**: 4,500 TWD → auto-shutdown
- **Trading Window**: 11:30-13:00 only
- **Auto-Flatten**: All positions closed at 13:00
- **News Veto**: AI blocks trades during major events

MTXF margin: ~40,000 TWD/contract  
MTXF tick value: 50 TWD/point

---

## 📈 Performance Target

- **Initial Capital**: 100,000 TWD
- **Monthly Target**: 30,000 TWD (30% return)
- **Daily Target**: ~1,000 TWD (20 trading days/month)
- **Win Rate Required**: 60%+ with 2:1 reward:risk
- **Max Drawdown**: -10% (10,000 TWD)

---

## 🔍 Monitoring & Logs

```bash
# Java app logs
tail -f logs/mtxf-bot.log

# Python bridge logs
tail -f logs/python-bridge.log

# Real-time system stats
htop
```

---

## ⚠️ Troubleshooting

### Python bridge not connecting
```bash
cd python
source venv/bin/activate
python3 bridge.py
# Check error messages
```

### Ollama not responding
```bash
ollama list  # Check if model is installed
ollama pull llama3.1:8b-instruct-q5_K_M
```

### Shioaji login failed
- Verify API key/secret in application.yml
- Check CA certificate path (.pfx file)
- Ensure simulation mode matches account type

### Orders not executing
- Check account margin (need 40,000+ TWD)
- Verify trading hours (11:30-13:00 Taipei time)
- Check logs for risk vetos

---

## 📞 Support

- Shioaji API Docs: https://sinotrade.github.io/
- Telegram Bot API: https://core.telegram.org/bots/api
- Ollama: https://ollama.ai/

**⚠️ DISCLAIMER**: This bot trades REAL MONEY. Test thoroughly in simulation mode. Past performance does not guarantee future results. Use at your own risk.
