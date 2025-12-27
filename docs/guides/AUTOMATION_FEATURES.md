# 🤖 Automation Features - Set It and Forget It

This document explains all the automation features designed to minimize your time investment while maximizing safety and returns.

## 🎯 Philosophy

**You have a day job. This system should work while you're busy.**

Target: Spend < 15 minutes per day on trading

## 📋 Fully Automated Features

### 1. Strategy Selection & Switching

**What it does:**
- Tests 50+ strategies continuously in shadow mode
- Automatically switches to best-performing strategy
- Sends you notification before switching (for approval)

**Your involvement:** 
- Reply "YES" to Telegram when notified (30 seconds)
- Or ignore - system won't switch without approval

**Frequency:** Weekly or when major performance gap detected

---

### 2. Capital Management

**What it does:**
- Fetches real-time equity from Shioaji API every trade
- Automatically calculates safe position size
- Scales positions up as your equity grows
- Never exceeds 10% risk per position

**Your involvement:**
- None. System handles everything
- You can manually adjust via `/change-share` command

**Frequency:** Every trade

---

### 3. Risk Monitoring

**What it does:**
- Monitors drawdown, volatility, position size
- Pauses trading if daily loss > 3%
- Sends alerts when risk exceeds thresholds
- AI explains risks in simple language

**Your involvement:**
- Read alerts (1 minute)
- Approve pause/resume if needed

**Frequency:** Continuous during market hours

---

### 4. Daily Performance Reports

**What it does:**
- Generates comprehensive daily summary
- AI analyzes performance in beginner-friendly language
- Identifies trends, risks, opportunities
- Stores in database for easy retrieval

**Your involvement:**
- Read Telegram summary (2 minutes)
- Optional: Run `./scripts/ai_daily_insights.py` for details

**Frequency:** Daily at 15:00 (after market close)

**Example Output:**
```
📊 Today's Summary
------------------
Strategy: RSI
Return: +1.2%
Trades: 3 (2 wins, 1 loss)

💡 AI Analysis:
"Good day! Your strategy captured a bounce in TSMC. 
Risk is still low. Continue current approach."

😊 Sentiment: POSITIVE
⚠️ Action Needed: No
```

---

### 5. Weekly Analysis

**What it does:**
- Compares your performance vs benchmarks
- Identifies best/worst performing stocks
- Suggests strategy changes if needed
- Projects monthly return based on trend

**Your involvement:**
- Review summary (5 minutes)
- Approve recommendations if any

**Frequency:** Every Sunday at 18:00

---

### 6. Backtest Automation

**What it does:**
- Runs nightly backtests on all 50 stocks
- Tests all strategies over 1-year period
- Updates strategy-stock performance mappings
- Identifies hidden opportunities

**Your involvement:**
- None. Results automatically used for strategy selection

**Frequency:** Daily at 02:00

---

### 7. Position Scaling Recommendations

**What it does:**
- Monitors your equity growth
- Calculates when to add more shares safely
- Sends notification when scaling threshold reached
- Explains the math in simple terms

**Your involvement:**
- Approve scaling (30 seconds)
- Or manually adjust base shares

**Frequency:** When equity grows by 20k TWD increments

**Example:**
```
📈 Scaling Recommendation

Your equity: 105,000 TWD (was 85,000)
Growth: 20,000 TWD ✅

Current: 70 shares
Recommended: 97 shares (+27)

Why: You've earned enough to safely add shares 
while staying under 10% risk per position.

Approve? Reply YES to /scale-up
```

---

### 8. Watchdog Service

**What it does:**
- Monitors all services (Java, Python, Database)
- Checks trading activity during market hours
- Verifies positions are within risk limits
- Restarts services if they crash
- Sends emergency alerts if issues detected

**Your involvement:**
- Respond to critical alerts only
- 99% of time: nothing needed

**Frequency:** Every 15 minutes

**To start:**
```bash
# Run in background
nohup ./scripts/automation_watchdog.py &
```

---

## 🕐 Daily Timeline (What Happens Automatically)

### 08:30 - Pre-Market
- ✅ Fetch latest prices
- ✅ Check overnight news
- ✅ Verify all services running
- ✅ Load today's strategy configuration

### 09:00 - Market Open
- ✅ Begin monitoring all 50 stocks
- ✅ Evaluate strategy signals
- ✅ Execute trades when triggered
- ✅ Update position sizes based on equity

### 09:00-13:30 - Market Hours
- ✅ Continuous price monitoring
- ✅ Real-time risk management
- ✅ Automatic position exits if stops hit
- ✅ Shadow strategies collecting data

### 13:30 - Market Close
- ✅ Close any open positions (if configured)
- ✅ Calculate day's P&L
- ✅ Update strategy performance metrics

### 15:00 - After Hours
- ✅ Generate daily performance report
- ✅ AI analyzes the day
- ✅ Send Telegram summary to you
- ✅ Save insights to database

### 18:00 - Evening
- ✅ Update strategy rankings
- ✅ Check if strategy switch recommended
- ✅ Prepare tomorrow's configuration

### 02:00 - Overnight
- ✅ Run comprehensive backtests
- ✅ Update stock-strategy mappings
- ✅ Optimize parameters
- ✅ Database maintenance

---

## 💬 Telegram Notifications

### Critical (Immediate Action Required)
- 🚨 Daily loss > 5%
- 🚨 Service crashed
- 🚨 Position risk > 15%

### Important (Review Soon)
- ⚠️ Strategy switch recommended
- ⚠️ Position scaling available
- ⚠️ Unusual trading pattern detected

### Informational (Read When Convenient)
- 📊 Daily performance summary
- 📈 Weekly analysis ready
- 💡 AI insights available

---

## 🔧 Automation Configuration

### Enable/Disable Features

```bash
# Edit configuration
nano config/automation-config.yml
```

```yaml
automation:
  strategy_auto_switch: true         # Auto-switch strategies
  position_auto_scale: true          # Auto-scale positions
  risk_auto_pause: true              # Auto-pause on high risk
  daily_reports: true                # Send daily summaries
  weekly_analysis: true              # Send weekly reports
  
  thresholds:
    daily_loss_pause: 3.0            # Pause if lose 3% in day
    position_max_risk: 10.0          # Max 10% per position
    strategy_switch_days: 7          # Min days before switch
    
  notifications:
    telegram_enabled: true
    critical_only: false             # Set true for minimal alerts
    quiet_hours: [22, 7]             # No alerts 10PM-7AM
```

### Telegram Commands

```
/status - Current state
/pause - Pause all trading
/resume - Resume trading
/scale-up - Approve position scaling
/switch-strategy - Approve strategy switch
/daily-report - Get today's report
/weekly-report - Get week summary
/talk <question> - Ask AI anything
```

---

## 📊 What You Actually Need to Do

### Daily (5 minutes)
1. Check Telegram for alerts (2 min)
2. Read AI daily summary (2 min)
3. Approve any actions if requested (1 min)

### Weekly (10 minutes)
1. Review weekly performance (5 min)
2. Check if strategy switch recommended (3 min)
3. Approve scaling if suggested (2 min)

### Monthly (30 minutes)
1. Review monthly returns vs goal (10 min)
2. Adjust risk parameters if needed (10 min)
3. Plan next month's approach (10 min)

---

## 🎓 Learning Mode

**First Month:** System runs in full shadow mode
- All strategies tested
- No real money at risk
- You learn how everything works
- AI teaches you gradually

**Second Month:** Transition to hybrid mode
- Best strategy goes live with minimal capital
- Others stay in shadow mode
- Build confidence gradually

**Third Month+:** Fully automated
- System proven over 60 days
- You trust the automation
- Minimal intervention needed

---

## 🆘 Emergency Override

If you need to take manual control:

```bash
# Pause everything immediately
curl -X POST http://localhost:16350/api/trading/pause

# Via Telegram
/pause

# Close all positions
/close

# Switch back to simulation mode
/backtosim
```

---

## 💡 Automation Best Practices

1. **Trust but verify** - Let system run but check alerts
2. **Start conservative** - Begin with low position sizes
3. **Read AI insights** - They're designed to teach you
4. **Don't override often** - System has more discipline than humans
5. **Review weekly** - Ensure automation is meeting goals

---

## 📈 Success Metrics

**Good Automation (System Working Well):**
- ✅ Daily time investment < 15 min
- ✅ Alerts are actionable and rare
- ✅ Monthly returns tracking toward 5% goal
- ✅ You understand what's happening
- ✅ Stress level low

**Needs Tuning:**
- ⚠️ Too many alerts (alert fatigue)
- ⚠️ Returns below 3% monthly
- ⚠️ You're confused about decisions
- ⚠️ Spending > 30 min/day on system

---

## 🔮 Future Enhancements

Planned automation features:

- ⏳ Auto-rebalance portfolio quarterly
- ⏳ Tax-loss harvesting automation
- ⏳ Round lot readiness (when equity > 500k)
- ⏳ Multi-stock portfolio automation
- ⏳ Voice alerts (Telegram voice messages)

---

## 📚 Related Documents

- [Beginner's Guide](./BEGINNER_GUIDE.md) - Start here
- [Risk Management](./misc/guides/SHADOW_MODE.md) - Safety features
- [AI Insights](./misc/guides/AI_INSIGHTS.md) - Understanding AI analysis

---

**Remember:** The goal is 5% monthly returns with minimal stress and time investment. If automation isn't achieving this, tune the settings or ask for help!

Your time is valuable. Let the system work for you. 🚀
