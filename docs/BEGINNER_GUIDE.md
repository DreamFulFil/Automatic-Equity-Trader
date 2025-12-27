# 🎓 Beginner's Guide to Automatic Equity Trader

## Welcome! 👋

This system is designed to help you trade stocks automatically while you focus on your day job. This guide explains everything in simple terms.

## 📋 Table of Contents

1. [What Does This System Do?](#what-does-this-system-do)
2. [How to Start](#how-to-start)
3. [Understanding Your Dashboard](#understanding-your-dashboard)
4. [Risk Management](#risk-management)
5. [Position Sizing](#position-sizing)
6. [Daily Routine](#daily-routine)
7. [Common Questions](#common-questions)

---

## What Does This System Do?

Think of this as your personal trading assistant that:

- **Monitors 50+ Taiwan stocks** continuously
- **Tests 50+ different trading strategies** in shadow mode
- **Automatically trades** using the best-performing strategy
- **Manages your risk** to protect your capital
- **Sends you Telegram alerts** for important events
- **Uses AI to explain** what's happening in simple terms

### Your Goal: 5% Monthly Returns

The system is optimized for **steady, low-risk** returns. We aim for 5% per month, not get-rich-quick schemes.

---

## How to Start

### Step 1: Verify System is Running

```bash
# Check if everything is running
docker ps
# You should see: PostgreSQL database running
# Port 8888 should have the Python bridge active
# Port 16350 should have the Java application active
```

### Step 2: Check Your Telegram

- Send `/status` to your trading bot
- You should see:
  - Current equity
  - Today's P&L
  - Active strategy
  - Position size

### Step 3: View AI Insights

```bash
# Run daily insights (after market close at 14:30)
./scripts/ai_daily_insights.py
```

This will show you in SIMPLE LANGUAGE:
- How did you do today?
- What's the risk level?
- Should you change anything?

---

## Understanding Your Dashboard

### Key Metrics Explained

| Metric | What It Means | Good Value |
|--------|---------------|------------|
| **Sharpe Ratio** | Risk-adjusted returns (higher = better) | > 1.0 |
| **Max Drawdown** | Biggest loss from peak (lower = better) | < 10% |
| **Win Rate** | % of profitable trades | > 55% |
| **Daily Return** | Today's profit/loss | Positive! |

### Strategy Status

- **MAIN**: Currently trading with real money
- **SHADOW**: Testing in background (no real trades)

The system automatically switches to the best-performing shadow strategy when appropriate.

---

## Risk Management

### Your Safety Settings

1. **Maximum 10% per position**
   - Never risk more than 10% of your capital on one stock
   - System enforces this automatically

2. **Odd lot trading only**
   - Trading in small quantities (< 1000 shares)
   - Lower risk, easier to manage

3. **Daily loss limits**
   - If you lose 3% in a day, system pauses
   - Prevents emotional decisions

4. **AI risk warnings**
   - Get alerts when risk is too high
   - Simple explanations of what to do

### Position Sizing Formula

```
Base Capital: 80,000 TWD
Per 20,000 TWD growth: Add 27 shares

Example:
- At 80,000 TWD: Trade 70 shares
- At 100,000 TWD: Trade 97 shares (70 + 27)
- At 120,000 TWD: Trade 124 shares (70 + 27 + 27)
```

This is AUTOMATIC. The system calculates it for you.

---

## Position Sizing

### How the System Decides How Much to Buy

Every strategy has its own settings:

- **Conservative strategies** (DCA, Dividends): 50 base shares, +20 increment
- **Balanced strategies** (RSI, Bollinger): 70 base shares, +27 increment
- **Aggressive strategies** (Momentum): 65 base shares, +30 increment

The system picks the right size based on:
1. Your current equity (from Shioaji API)
2. Stock price
3. Strategy risk profile
4. Your 10% max risk rule

### When to Scale Up?

The system tells you when to increase position size:

```bash
# Check scaling recommendation
curl -X POST http://localhost:8888/ai/position-sizing \
  -H "Content-Type: application/json" \
  -d '{
    "capital": 80000,
    "stock_price": 1100,
    "risk_level": "LOW",
    "equity": 100000
  }'
```

Response explains in simple terms:
- "Your equity grew by 20,000 TWD. Safe to add 27 more shares."
- "This keeps you at 8% risk - still safe!"

---

## Daily Routine

### What the System Does Automatically

**09:00 - Market Opens**
- Fetches latest prices
- Evaluates strategies
- Places trades if signals trigger

**14:30 - Market Closes**
- Generates daily performance report
- AI analyzes the day
- Sends Telegram summary

**15:00 - After Hours**
- Updates strategy rankings
- Prepares for next day
- Backtests continue

### What You Need to Do

**Once a Day (5 minutes)**
1. Check Telegram for alerts
2. Read AI insights
3. Approve any strategy switches if asked

**Once a Week (10 minutes)**
1. Review weekly performance report
2. Check if position scaling is recommended
3. Ask questions to AI if confused

**Once a Month (30 minutes)**
1. Review monthly returns
2. Adjust base shares if system recommends
3. Rebalance if needed

---

## Common Questions

### Q: "I don't understand Sharpe ratio!"

**A:** Think of it as a score:
- 0-0.5: Not great, high risk for the returns
- 0.5-1.0: Okay, reasonable risk
- 1.0-2.0: Good! Nice returns for the risk
- 2.0+: Excellent! (rare)

### Q: "When should I switch strategies?"

**A:** The system does this automatically, but asks your approval. Look for:
- Shadow strategy has higher Sharpe than MAIN for 7+ days
- Risk level is same or lower
- AI recommendation says "APPROVE"

### Q: "My equity isn't growing. What's wrong?"

**A:** Check these:
1. Are you in a drawdown period? (normal)
2. Is win rate below 45%? (may need strategy change)
3. Is market trending down? (all strategies struggle)

Run AI insights - it will explain what's happening.

### Q: "How do I know which stocks to trade?"

**A:** Check the strategy-stock mapping:

```bash
# Get best stocks for your current strategy
curl http://localhost:16350/api/strategy-stock-mapping/top-combinations
```

AI will explain which stocks work best with your strategy.

### Q: "I want to be more aggressive/conservative"

**A:** Edit your strategy settings:

```bash
# For more conservative (5% → 3% target)
./scripts/update_strategy_configs.py

# Then adjust base shares via Telegram
/change-share 50  # More conservative
/change-increment 20
```

### Q: "Can I trade round lots (1000+ shares)?"

**A:** Yes, but:
1. Your equity needs to be much higher (500k+ TWD)
2. Risk increases significantly
3. System will warn you if position size is too big

Start with odd lots until consistent profits.

---

## 🆘 When to Ask for Help

**Telegram Commands:**

- `/status` - Current state
- `/help` - Command list
- `/talk <question>` - Ask AI tutor anything
- `/insight` - Get current market insights
- `/pause` - Stop trading temporarily
- `/resume` - Resume trading

**Red Flags (Contact Support):**

- Daily loss > 5%
- System stopped responding
- Multiple failed trades
- Can't understand AI explanations

---

## 📊 Reading Your Reports

### Daily Report Location

Reports are saved in the database and can be accessed via:

```bash
# View today's insights
./scripts/ai_daily_insights.py

# View weekly summary
./scripts/weekly_performance_report.py
```

### What to Look For

**Green Flags (Good!):**
- ✅ Win rate > 55%
- ✅ Sharpe ratio > 1.0
- ✅ Drawdown < 8%
- ✅ Steady daily gains

**Yellow Flags (Watch closely):**
- ⚠️ Win rate 45-55%
- ⚠️ Sharpe ratio 0.5-1.0
- ⚠️ Drawdown 8-12%
- ⚠️ Choppy performance

**Red Flags (Take action):**
- 🚨 Win rate < 45%
- 🚨 Sharpe ratio < 0.5
- 🚨 Drawdown > 12%
- 🚨 3+ consecutive losing days

---

## 🎯 Monthly Goals Tracking

| Month | Target Return | Actual Return | Status |
|-------|---------------|---------------|--------|
| Jan   | 5%            | __%           | ⏳     |
| Feb   | 5%            | __%           | ⏳     |
| Mar   | 5%            | __%           | ⏳     |

**Update this yourself** or let the system track it automatically.

---

## 💡 Pro Tips

1. **Don't panic on red days** - drawdowns are normal
2. **Trust the system** - it's tested on 1 year of data
3. **Read AI insights daily** - they're written for you
4. **Start small** - let profits compound naturally
5. **Ask questions** - use `/talk` command liberally

---

## 📚 Next Steps

1. ✅ Read this guide thoroughly
2. ✅ Run your first day in shadow mode
3. ✅ Check Telegram alerts
4. ✅ Review AI insights after market close
5. ✅ Gradually increase position size as equity grows

**Remember:** This is a marathon, not a sprint. Steady 5% monthly returns compound to 80% annually!

---

## 🔗 Quick Links

- [Technical Documentation](./README.md)
- [Architecture Overview](./misc/architecture/)
- [Setup Guide](./misc/setup/)
- [Performance Reports](./misc/reports/)

---

**Questions? Issues?**

- Telegram: Use `/talk` command
- Email: [Your support email]
- GitHub Issues: [Repository link]

Happy Trading! 🚀
