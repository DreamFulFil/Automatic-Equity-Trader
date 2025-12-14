# Crontab Setup Instructions

To setup the automatic trading system to run Monday-Friday at 08:55, follow these steps:

## 1. Edit your crontab

```bash
crontab -e
```

## 2. Add the following line

```cron
55 8 * * 1-5 cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader && /opt/homebrew/bin/fish start-auto-trader.fish dreamfulfil stock >> logs/shell/cron-$(date +\%Y\%m\%d).log 2>&1
```

### Explanation:
- `55 8 * * 1-5` - Runs at 08:55 Monday through Friday
- `cd /Users/gc/Downloads/work/stock/Automatic-Equity-Trader` - Change to project directory
- `/opt/homebrew/bin/fish` - Use absolute path to fish shell (required for cron)
- `start-auto-trader.fish dreamfulfil stock` - Start the trader in stock mode with jasypt password
- `>> logs/shell/cron-$(date +\%Y\%m\%d).log 2>&1` - Log everything to dated log file

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
