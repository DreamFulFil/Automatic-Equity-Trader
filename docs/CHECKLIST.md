# ✅ MTXF Lunch Bot - Production Checklist

## 🎯 Pre-Launch Checklist (Complete Before Live Trading)

### ☑️ Phase 1: Setup (Day 0)
- [ ] Install Java 21: `brew install openjdk@21`
- [ ] Install Maven: `brew install maven`
- [ ] Install Ollama: `brew install ollama`
- [ ] Download Llama 3.1 8B: `ollama pull llama3.1:8b-instruct-q5_K_M`
- [ ] Run setup script: `./scripts/setup.sh`
- [ ] Verify Python dependencies installed in `python/venv/`

### ☑️ Phase 2: Credentials (Day 0)
- [ ] Register Sinopac Shioaji account
- [ ] Obtain API key & secret from Sinopac
- [ ] Download CA certificate (.pfx file)
- [ ] Create Telegram bot via @BotFather
- [ ] Get Telegram chat ID
- [ ] Edit `application.yml` with all credentials
- [ ] Verify `simulation: true` is set

### ☑️ Phase 3: Paper Trading (Week 1-4)
- [ ] Run test: `./scripts/test-paper-trading.sh`
- [ ] Check logs for errors: `tail -f logs/mtxf-bot.log`
- [ ] Verify Telegram alerts received on phone
- [ ] Confirm Python bridge connects: `curl http://localhost:8888/health`
- [ ] Verify Shioaji login successful in logs
- [ ] Test news veto: Check logs for Llama 3.1 8B calls
- [ ] Run full day (11:30-13:00) in paper mode
- [ ] Track performance in spreadsheet:
  - [ ] Day 1-5: Record all signals, no execution issues
  - [ ] Day 6-10: Win rate > 55%
  - [ ] Day 11-20: Consistent profitability
  - [ ] Final check: 60%+ win rate, positive P&L

### ☑️ Phase 4: Pre-Live Validation (Week 4)
- [ ] Review 20+ paper trades in logs
- [ ] Calculate actual Sharpe ratio
- [ ] Verify max drawdown < 10%
- [ ] Test emergency shutdown: Manually trigger daily loss limit
- [ ] Test auto-flatten: Verify positions close at 13:00
- [ ] Test news veto: Inject fake crisis headline, verify block
- [ ] Confirm Telegram alerts sent for all events
- [ ] Check order slippage in paper trades

### ☑️ Phase 5: Live Trading Launch (Week 5)
- [ ] **BACKUP PAPER TRADING DATA**: Copy logs folder
- [ ] Fund Sinopac account with 100,000+ TWD
- [ ] Verify margin available: 40,000+ TWD
- [ ] Change `simulation: false` in application.yml
- [ ] **DOUBLE-CHECK**: Read application.yml line-by-line
- [ ] Start bot: `./scripts/start-lunch-bot.sh`
- [ ] Watch logs in real-time: `tail -f logs/mtxf-bot.log`
- [ ] Monitor first 3 days with manual override ready

### ☑️ Phase 6: Post-Launch Monitoring (Week 5-8)
- [ ] Daily log review at 13:30 (after market close)
- [ ] Track actual P&L vs. paper trading
- [ ] Monitor news veto accuracy
- [ ] Check for unexpected errors
- [ ] Review Telegram alerts for missing notifications
- [ ] Weekly performance report

---

## 🚨 Critical Safety Checks (Before Every Trading Day)

- [ ] Verify `simulation: false` (live mode)
- [ ] Check Ollama running: `curl http://localhost:11434/api/tags`
- [ ] Check Python bridge: `curl http://localhost:8888/health`
- [ ] Confirm margin balance > 50,000 TWD
- [ ] Test Telegram: Send manual alert
- [ ] Review previous day's P&L
- [ ] Check system time matches Taipei timezone

---

## 📊 Performance Tracking Template

Copy this to a spreadsheet:

```
Date       | Trades | Wins | Losses | P&L (TWD) | Max DD | Win% | News Vetos | Notes
-----------|--------|------|--------|-----------|--------|------|------------|-------
2025-01-02 | 3      | 2    | 1      | +1,200    | -500   | 66.7 | 1          | Good day
2025-01-03 | 2      | 1    | 1      | -200      | -500   | 50.0 | 0          | Choppy
2025-01-04 | 3      | 3    | 0      | +2,400    | 0      | 100  | 0          | Trending
```

**Weekly Review Questions**:
1. Is win rate ≥ 60%?
2. Is average daily P&L ≥ 1,000 TWD?
3. Are news vetos blocking bad trades?
4. Any unexpected errors in logs?
5. Telegram alerts working 100%?

---

## 🛠️ Maintenance Tasks

### Daily (13:05 after market close)
- [ ] Review logs: `tail -100 logs/mtxf-bot.log`
- [ ] Check daily P&L in Telegram
- [ ] Verify all positions closed

### Weekly (Friday 14:00)
- [ ] Calculate weekly P&L
- [ ] Review win rate vs. target
- [ ] Check log file size: `du -h logs/`
- [ ] Backup logs: `cp -r logs logs_backup_$(date +%Y%m%d)`

### Monthly (Last Friday)
- [ ] Calculate monthly P&L
- [ ] Compare to 30,000 TWD target
- [ ] Review news veto accuracy
- [ ] Optimize strategy parameters if needed
- [ ] Update Llama 3.1 model: `ollama pull llama3.1:8b-instruct-q5_K_M`
- [ ] Archive old logs: `tar -czf logs_archive_$(date +%Y%m).tar.gz logs/`

---

## 🚑 Emergency Procedures

### Scenario 1: Bot Crashes During Trading Hours
```bash
# 1. Force stop everything
pkill -f "mtxf-bot"
pkill -f "bridge.py"

# 2. Login to Sinopac web platform
# 3. Manually close all MTXF positions

# 4. Check logs for error
tail -100 logs/mtxf-bot.log

# 5. Fix issue, restart
./scripts/start-lunch-bot.sh
```

### Scenario 2: Daily Loss Limit Hit
- [ ] Bot auto-shuts down (verify in logs)
- [ ] Receive Telegram: "🛑 EMERGENCY SHUTDOWN"
- [ ] All positions auto-flattened
- [ ] **DO NOT RESTART TODAY**
- [ ] Review trades, adjust strategy if needed
- [ ] Restart next trading day

### Scenario 3: Unexpected Large Loss
- [ ] Check if news veto failed
- [ ] Review trade logs for entry/exit prices
- [ ] Verify Shioaji fills match logs
- [ ] Check for slippage issues
- [ ] Consider adjusting confidence threshold

### Scenario 4: Telegram Alerts Stop
- [ ] Check bot token: `curl https://api.telegram.org/bot<TOKEN>/getMe`
- [ ] Verify chat ID correct
- [ ] Check internet connection
- [ ] Restart bot if network issue resolved

---

## 📝 Configuration Reference

### Key Parameters (application.yml)

```yaml
# Risk - Adjust carefully
trading:
  risk:
    max-position: 1          # Start with 1, increase after 3 months
    daily-loss-limit: 4500   # 4.5% of 100K capital

# Strategy - Tune after paper trading
strategy:
  confidence-threshold: 0.65  # Higher = fewer but better trades
  momentum-period: 180        # 3 minutes in seconds
  volume-lookback: 300        # 5 minutes in seconds

# Ollama - Model settings
ollama:
  temperature: 0.3            # Lower = more conservative news veto
  timeout-ms: 5000            # Max time for AI response
```

---

## 🎓 Learning Resources

### Recommended Reading
1. **Taiwan Futures Market**: TAIFEX official guide
2. **Shioaji API**: Complete SDK documentation
3. **Risk Management**: Van Tharp - "Trade Your Way to Financial Freedom"
4. **Algorithmic Trading**: Ernest Chan - "Algorithmic Trading"

### Backtesting (Future Enhancement)
- Download 6 months of MTXF tick data
- Replay strategy in `simulation: true` mode
- Calculate Sharpe ratio, max DD, win rate
- Optimize entry/exit thresholds

### Strategy Improvements (After 3 Months)
- Add support/resistance levels
- Implement order flow imbalance detection
- Use limit orders instead of market orders
- Add multiple timeframe confirmation
- Train custom Llama 3.1 8B on Taiwan news corpus

---

## ✅ Launch Day Final Checklist

**1 Hour Before Market Open (10:30 AM)**
- [ ] Verify system time: `date` (must match Taipei)
- [ ] Check Ollama: `ollama list`
- [ ] Check Python bridge: `curl localhost:8888/health`
- [ ] Test Telegram: Send manual message
- [ ] Review application.yml: **simulation: false**
- [ ] Confirm margin: Login to Sinopac, check balance
- [ ] Clear old logs: `rm logs/*.log`

**15 Minutes Before Trading (11:15 AM)**
- [ ] Start bot: `./scripts/start-lunch-bot.sh`
- [ ] Watch startup logs: `tail -f logs/mtxf-bot.log`
- [ ] Verify Shioaji connection: Look for "✅ Subscribed to TXFR1"
- [ ] Confirm trading window: "Trading window: 11:30 - 13:00"
- [ ] Open Telegram on phone
- [ ] Open Sinopac web platform (backup manual control)

**During Trading (11:30-13:00)**
- [ ] Monitor Telegram alerts
- [ ] Check logs every 15 minutes
- [ ] Verify positions in Sinopac platform match bot logs
- [ ] **DO NOT INTERFERE** unless emergency

**After Trading (13:05)**
- [ ] Verify all positions closed
- [ ] Check daily summary in Telegram
- [ ] Review full logs: `cat logs/mtxf-bot.log`
- [ ] Record P&L in spreadsheet
- [ ] Backup logs: `cp logs/mtxf-bot.log logs/archive/$(date +%Y%m%d).log`

---

## 🏆 Success Metrics (Review Monthly)

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Monthly P&L | +30,000 TWD | | |
| Win Rate | ≥60% | | |
| Max Drawdown | <10,000 TWD | | |
| Sharpe Ratio | >2.0 | | |
| Avg Trade | +350 TWD | | |
| News Veto Accuracy | >80% | | |
| System Uptime | 100% | | |

**If 3 consecutive losing days**:
- [ ] Stop live trading
- [ ] Switch to `simulation: true`
- [ ] Review strategy parameters
- [ ] Analyze market regime change
- [ ] Consult trading journal

**If monthly target missed**:
- [ ] Don't increase position size
- [ ] Review entry quality (false signals?)
- [ ] Check news veto effectiveness
- [ ] Consider pausing trading for re-optimization

---

## 📞 Support & Community

- GitHub Issues: Report bugs and feature requests
- Telegram Group: Join Taiwan algo trading community
- Shioaji Forum: API technical support
- Trading Journal: Keep detailed notes in Notion/Obsidian

---

**🎯 Remember**: Consistency beats home runs. Target 1,000 TWD/day × 20 days = 20,000 TWD/month is already excellent (20% monthly return).

**⚠️ Final Warning**: This bot trades REAL MONEY. Start small, track everything, never risk more than you can afford to lose.

**🚀 Good luck and may your P&L always be green!**
