# MTXF Lunch-Break Trading Bot - Copilot Instructions

## Project Overview
This is a **production Taiwan Mini-TXF (MTXF) day-trading bot** for the 11:30-13:00 lunch session.
- **Owner:** DreamFulFil
- **Live Account:** 100K TWD
- **Status:** Production-ready (Score: 100/100)

## Architecture
```
┌─────────────────┐      REST API       ┌──────────────────┐
│  Java Trading   │◄───────────────────►│  Python Bridge   │
│     Engine      │   (port 16350/8888) │   (FastAPI)      │
│  Spring Boot    │                     │  + Shioaji API   │
│  + Risk Mgmt    │                     │  + Ollama Client │
└─────────────────┘                     └──────────────────┘
```

## Critical Timing Rules (DO NOT CHANGE)
| Component | Interval | Rationale |
|-----------|----------|-----------|
| Signal calculation | **30 seconds** | `@Scheduled(fixedRate = 30000)` |
| News veto (Ollama) | **10 minutes** | `minute % 10 == 0 && second < 30` |
| Trading window | **11:30-13:00** | Asia/Taipei timezone |
| Auto-flatten | **13:00** | `@Scheduled(cron = "0 0 13 * * MON-FRI")` |

## Risk Controls (IMMUTABLE)
- `max-position: 1` - Single contract only
- `daily-loss-limit: 4500` TWD - Emergency shutdown trigger
- **NO profit caps** - Let winners run unlimited
- Auto-flatten at 13:00 regardless of P&L

## Security Requirements
- All credentials MUST use Jasypt encryption: `ENC(...)`
- Never commit plain-text tokens/passwords
- `Sinopac.pfx` is gitignored (certificate file)
- Jasypt password passed via CLI argument or env var

## File Structure
```
├── src/main/java/tw/gc/mtxfbot/
│   ├── TradingEngine.java    # Core trading loop (30s signals, 10min news)
│   ├── TelegramService.java  # Alerts (plain JSON, no MarkdownV2)
│   └── config/               # Properties classes
├── python/
│   └── bridge.py             # FastAPI + Shioaji + Ollama
├── src/main/resources/
│   └── application.yml       # Config with ENC() values
├── start-lunch-bot.fish      # Launcher script
└── logs/                     # Daily rolling logs
```

## Coding Guidelines

### When modifying TradingEngine.java:
1. Keep `@Scheduled(fixedRate = 30000)` - NEVER change to 60000 or other
2. Keep news veto logic: `now.getMinute() % 10 == 0 && now.getSecond() < 30`
3. Always use `TAIPEI_ZONE` for time checks
4. Never add profit caps or take-profit logic

### When modifying bridge.py:
1. Keep momentum strategy: 3-min + 5-min alignment
2. Keep volume surge detection: `volume_ratio > 1.3`
3. Keep `/signal/news` separate from `/signal`
4. Resolve `ca-path` relative to project root

### When modifying application.yml:
1. All sensitive values MUST be `ENC(...)`
2. Keep `simulation: false` for live trading
3. Logging pattern: `logs/mtxf-bot-%d{yyyy-MM-dd}.log`

## Crontab Setup
```cron
15 11 * * 1-5 /opt/homebrew/bin/fish -c 'cd /path/to/mtxf-bot && ./start-lunch-bot.fish <secret>' >> /tmp/mtxf-bot-cron.log 2>&1
```

## Common Commands
```bash
# Build
mvn clean package -DskipTests

# Run manually
./start-lunch-bot.fish dreamfulfil

# Test Python bridge
curl http://localhost:8888/health
curl http://localhost:8888/signal

# Encrypt a value with Jasypt
echo -n "secret" | java -cp target/mtxf-bot-1.0.0.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI password=<key> algorithm=PBEWithMD5AndDES input=<value>
```

## Do NOT:
- Change signal interval from 30 seconds
- Add profit caps or take-profit logic
- Commit plain-text credentials
- Remove Jasypt encryption
- Change timezone from Asia/Taipei
- Skip the 13:00 auto-flatten

## Last Audit: 2025-11-26
- Score: 100/100
- Status: Production-ready
- All blockers resolved
