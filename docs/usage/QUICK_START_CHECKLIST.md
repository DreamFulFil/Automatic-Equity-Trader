# ✅ Quick Start Checklist

Use this checklist to get your system running in 30 minutes.

## Prerequisites Check

- [ ] macOS with Docker installed
- [ ] Java 17+ (check: `jenv version`)
- [ ] Python 3.12+ (check: `python3 --version`)
- [ ] Shioaji/Sinopac account with API access
- [ ] Telegram bot token (from @BotFather)
- [ ] Ollama installed (check: `ollama --version`)

## Initial Setup (One Time Only)

### 1. Database Setup (2 minutes)
```bash
# Start PostgreSQL in Docker
docker run -d --name auto-trader-db \
  -p 5432:5432 \
  -e POSTGRES_DB=auto_equity_trader \
  -e POSTGRES_USER=dreamer \
  -e POSTGRES_PASSWORD=WSYS1r0PE0Ig0iuNX2aNi5k7 \
  postgres:15

# Verify it's running
docker ps | grep auto-trader-db
```
- [ ] PostgreSQL running on port 5432

### 2. Environment Variables (1 minute)
```bash
# Add to your ~/.zshrc or ~/.bashrc
export JASYPT_PASSWORD=dreamfulfil
export POSTGRES_DB=auto_equity_trader
export POSTGRES_USER=dreamer
export POSTGRES_PASSWORD=WSYS1r0PE0Ig0iuNX2aNi5k7
export TELEGRAM_BOT_TOKEN=your_token_here
export TELEGRAM_CHAT_ID=your_chat_id_here

# Reload
source ~/.zshrc  # or ~/.bashrc
```
- [ ] Environment variables set

### 3. Ollama Setup (3 minutes)
```bash
# Start Ollama service
ollama serve &

# Pull the AI model (1-2 min download)
ollama pull mistral:7b-instruct-v0.2-q5_K_M

# Verify
curl http://localhost:11434/api/tags
```
- [ ] Ollama running on port 11434
- [ ] Model downloaded

### 4. Python Dependencies (2 minutes)
```bash
cd python
pip3 install -r requirements.txt
```
- [ ] Python packages installed

### 5. Configuration File (2 minutes)
```bash
# Edit config (use your actual credentials)
nano src/main/resources/application.yml
```

Update these sections:
```yaml
shioaji:
  api-key: "YOUR_API_KEY"
  secret-key: "YOUR_SECRET_KEY"
  
telegram:
  bot-token: "${TELEGRAM_BOT_TOKEN}"
  chat-id: "${TELEGRAM_CHAT_ID}"
  
ollama:
  url: "http://localhost:11434"
  model: "mistral:7b-instruct-v0.2-q5_K_M"
```
- [ ] Configuration file updated

### 6. Build Application (3 minutes)
```bash
jenv exec mvn clean package -DskipTests
```
- [ ] Build successful (target/*.jar created)

## Data Preparation (One Time, ~10 minutes)

### 7. Download Stock History (5-8 minutes)
```bash
./scripts/download_taiwan_stock_history.py
```
This downloads 1 year of data for 50+ stocks.
- [ ] Stock data downloaded (~18,250 data points)

### 8. Initialize Strategy Configs (1 minute)
```bash
./scripts/update_strategy_configs.py
```
- [ ] Strategy configurations initialized

### 9. Run Initial Backtests (10-15 minutes)
```bash
./scripts/run_backtest_all_stocks.py
```
This tests all strategies on all stocks. Go get coffee! ☕
- [ ] Backtests complete
- [ ] Strategy-stock mappings created

## First Run

### 10. Start Trading System (1 minute)
```bash
# In one terminal
./start-auto-trader.fish $JASYPT_PASSWORD

# In another terminal  
cd python && python3 -m app.main
```

Wait for:
```
🚀 Java service started on port 16350
🐍 Python bridge started on port 8888
```
- [ ] Java service running (port 16350)
- [ ] Python bridge running (port 8888)

### 11. Verify Telegram (30 seconds)
Send to your bot:
```
/status
```

Expected response:
```
📊 Trading Status
Mode: SIMULATION
Equity: 80,000 TWD
Strategy: [Best performing]
Position: None (shadow mode)
```
- [ ] Telegram bot responding

### 12. Run First AI Insights (1 minute)
```bash
./scripts/ai_daily_insights.py
```

You should see:
```
📊 Today's Summary
Strategy: [Name]
Risk Level: [LOW/MEDIUM/HIGH]

💡 AI Analysis:
[Beginner-friendly explanation]
```
- [ ] AI insights working

## Enable Automation (Optional but Recommended)

### 13. Start Watchdog Service
```bash
nohup ./scripts/automation_watchdog.py >> logs/watchdog.log 2>&1 &
```
- [ ] Watchdog running in background

### 14. Setup Cron Jobs (Optional)
```bash
crontab -e
```

Add:
```cron
# AI Daily Insights (3:00 PM after market close)
0 15 * * 1-5 cd /path/to/Automatic-Equity-Trader && ./scripts/ai_daily_insights.py

# Nightly Backtests (2:00 AM)
0 2 * * * cd /path/to/Automatic-Equity-Trader && ./scripts/run_backtest_all_stocks.py

# Weekly Report (Sunday 6 PM)
0 18 * * 0 cd /path/to/Automatic-Equity-Trader && ./scripts/weekly_performance_report.py
```
- [ ] Cron jobs configured (optional)

## Daily Operation

### Morning Routine (2 minutes)
1. [ ] Check Telegram for overnight summary
2. [ ] Verify services running: `docker ps && jps`
3. [ ] Quick status check: Send `/status` to Telegram bot

### During Market Hours (Automatic)
- System monitors 50+ stocks automatically
- Executes trades based on strategy signals
- Sends alerts for important events
- **No action needed from you!**

### After Market Close (5 minutes)
1. [ ] Review daily performance in Telegram
2. [ ] Run AI insights: `./scripts/ai_daily_insights.py`
3. [ ] Check for any alerts or recommendations
4. [ ] Approve strategy switches if prompted

## First Week Goals

- [ ] Day 1: System running, verified via Telegram
- [ ] Day 2-3: Understand basic metrics (Sharpe, drawdown, win rate)
- [ ] Day 4-5: Review which strategies performing best
- [ ] Day 6-7: Read BEGINNER_GUIDE.md thoroughly
- [ ] Week end: Comfortable with daily routine

## First Month Goals

- [ ] Week 1: Shadow mode data collection
- [ ] Week 2: Understand AI insights and recommendations
- [ ] Week 3: Learn position scaling concepts
- [ ] Week 4: Review monthly performance, plan next month

## When Things Go Wrong

### Services Not Starting
```bash
# Check ports
lsof -i :16350  # Java
lsof -i :8888   # Python
lsof -i :11434  # Ollama
lsof -i :5432   # PostgreSQL

# Check logs
tail -f logs/application.log
tail -f python/logs/bridge.log
```

### Database Issues
```bash
# Restart PostgreSQL
docker restart auto-trader-db

# Check connection
psql -h localhost -U dreamer -d auto_equity_trader
```

### AI Not Working
```bash
# Restart Ollama
pkill ollama
ollama serve &

# Verify model
ollama list
```

### Emergency Stop
```bash
# Stop everything
pkill -f "java.*auto-equity"
pkill -f "python.*app.main"
docker stop auto-trader-db

# Or via Telegram
/pause
/close
```

## Support Resources

### Documentation
- [ ] Read `docs/BEGINNER_GUIDE.md` - Essential for new users
- [ ] Read `docs/AUTOMATION_FEATURES.md` - Understand what's automated
- [ ] Review `docs/DECEMBER_2024_IMPROVEMENTS.md` - Latest features

### Interactive Help
- Telegram: `/help` - List all commands
- Telegram: `/talk <question>` - Ask AI anything
- Logs: `tail -f logs/*.log` - Debug issues

### Performance Monitoring
```bash
# Daily insights
./scripts/ai_daily_insights.py

# Weekly summary
./scripts/weekly_performance_report.py

# Check strategy-stock mappings
curl http://localhost:16350/api/strategy-stock-mapping/top-combinations
```

## Success Criteria

After completing this checklist, you should have:

✅ All services running and healthy  
✅ 50+ stocks with 1 year of historical data  
✅ All strategies backtested and ranked  
✅ Telegram bot responding to commands  
✅ AI insights generating daily reports  
✅ Watchdog monitoring system 24/7  
✅ Understanding of basic metrics  
✅ Confidence in automated operation

**Congratulations!** 🎉 Your automated trading system is now running.

## Next Steps

1. **Learn More:**
   - Read the `BEGINNER_GUIDE.md` thoroughly
   - Experiment with Telegram commands
   - Ask AI questions via `/talk`

2. **Monitor Performance:**
   - Check daily AI insights
   - Review weekly summaries
   - Track progress toward 5% monthly goal

3. **Optimize:**
   - After 2 weeks, review strategy performance
   - Adjust position sizing if needed
   - Consider switching strategies if AI recommends

4. **Relax:**
   - System works while you're busy with your day job
   - Check Telegram for alerts
   - Spend < 15 minutes daily

**Welcome to automated trading!** 🚀

Questions? Use `/talk` in Telegram or review the documentation.
