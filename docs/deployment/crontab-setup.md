# Crontab Setup Instructions

To setup the automatic trading system with **automated strategy selection** to run Monday-Friday at 08:25 (before market opens at 09:00), follow these steps:

## Why 08:25?
The system performs auto-selection at 08:30. Starting at 08:25 gives:
- 5 minutes for system startup
- Auto-selection runs at 08:30
- Main and shadow strategies are selected
- System is ready before market opens at 09:00

## 1. Edit your crontab

```bash
crontab -e
```

## 2. Add the following line

```cron
25 8 * * 1-5 cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader && /opt/homebrew/bin/fish start-auto-trader.fish dreamfulfil stock >> logs/shell/cron-$(date +\%Y\%m\%d).log 2>&1
```

### Explanation:
- `25 8 * * 1-5` - Runs at 08:25 Monday through Friday
- `cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader` - Change to project directory
- `/opt/homebrew/bin/fish` - Use absolute path to fish shell (required for cron)
- `start-auto-trader.fish dreamfulfil stock` - Start the trader in stock mode with jasypt password
- `>> logs/shell/cron-$(date +\%Y\%m\%d).log 2>&1` - Log everything to dated log file

### What Happens Automatically:
1. **08:25** - System starts via cron
2. **08:30** - AutoStrategySelector analyzes all backtest results
3. **08:30** - Best performing strategy+stock selected as main trading pair
4. **08:30** - Top 5 strategies selected for shadow mode
5. **08:30** - Telegram notification sent with selections
6. **09:00** - Market opens, system starts trading with selected strategies

### Log Locations:
- **Shell/Cron logs**: `logs/shell/cron-YYYYMMDD.log`
- **Java application**: Logs are managed by the application itself
- **Python scripts**: `logs/python/` (created by the application)

## 3. Verify crontab is set

```bash
crontab -l
```

## 4. Monitor logs

```bash
# Watch today's cron log
tail -f logs/shell/cron-$(date +%Y%m%d).log

# Check Java logs
tail -f logs/java/*.log

# Check Python logs  
tail -f logs/python/*.log
```

## Important Notes:

1. **Environment Variables**: Cron has a limited PATH. The script uses absolute paths to avoid issues.

2. **JASYPT Password**: The password "dreamfulfil" is hardcoded in the crontab. Keep your crontab secure.

3. **Trading Mode**: Set to "stock" by default. Change to "futures" if needed.

4. **Database Mode**: Currently set to SIMULATION. The system will NOT execute real trades.

5. **Time Zone**: Ensure your system timezone is set to Asia/Taipei for Taiwan market hours.

6. **Auto-Selection**: The system automatically selects the best strategy and stock daily at 08:30 based on:
   - Expected return > 5%
   - Sharpe ratio > 1.0
   - Win rate > 50%
   - Max drawdown < 20%

7. **Backtest Data**: Run backtests regularly to keep performance data fresh. The system uses the latest results.

## To manually run (for testing):

```bash
cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader
./start-auto-trader.fish dreamfulfil stock
```

## To disable the cron job:

```bash
crontab -e
# Comment out the line by adding # at the beginning
# 55 8 * * 1-5 cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader && ...
```

or

```bash
crontab -r  # WARNING: This removes ALL cron jobs
```
