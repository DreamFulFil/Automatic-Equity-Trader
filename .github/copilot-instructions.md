# MTXF Lunch-Break Trading Bot - Instructions

## Project Overview
**Production Taiwan Mini-TXF (MTXF) day-trading bot** for 11:30-13:00 lunch session.
- **Owner:** DreamFulFil | **Live Account:** 100K TWD | **Status:** Production-ready (100/100)

## Architecture
```
Java (Spring Boot :16350) ◄──REST──► Python (FastAPI :8888) ──► Shioaji + Ollama
```

---

## 🚨 IMMUTABLE RULES

### Timing (DO NOT CHANGE)
| Component | Value | Code |
|-----------|-------|------|
| Signal check | **30s** | `@Scheduled(fixedRate = 30000)` |
| News veto | **10min** | `minute % 10 == 0 && second < 30` |
| Trading window | **11:30-13:00** | Asia/Taipei timezone |
| Auto-flatten | **13:00** | `cron = "0 0 13 * * MON-FRI"` |

### Risk Controls
- `max-position: 1` (single contract)
- `daily-loss-limit: 4500` TWD (emergency shutdown)
- **NO profit caps** - let winners run unlimited

### Security
- All credentials: `ENC(...)` Jasypt encryption
- Never commit plain-text tokens
- `Sinopac.pfx` is gitignored

---

## Coding Guidelines

### TradingEngine.java
- Keep `fixedRate = 30000` - NEVER change
- Always use `TAIPEI_ZONE` for time checks
- Never add profit caps

### bridge.py
- Keep 3-min + 5-min momentum alignment
- Keep `/signal/news` separate from `/signal`
- Resolve `ca-path` relative to project root

### application.yml
- All sensitive values: `ENC(...)`
- Keep `simulation: false` for live

---

## Shell Tools (Use Instead of Traditional Commands)
| Task | Use | Avoid |
|------|-----|-------|
| Find files | `fd` | `find`, `ls -R` |
| Search text | `rg` (ripgrep) | `grep`, `ag` |
| Code structure | `ast-grep` | `grep`, `sed` |
| JSON | `jq` | `python -m json.tool` |

## File Reading Efficiency
- **DO NOT** read entire files unnecessarily
- **DO** use targeted reads with `offset`/`limit` around specific lines
- Trust provided line numbers - be surgical, not exploratory

## Code Compilation Verification
- **ALWAYS** run build command after changes: `mvn compile` or `mvn clean compile`
- **FIX** compilation errors before marking complete
- Never consider task done without compilation verification

## Import Statement Management
🚨 **CRITICAL**: Add imports IMMEDIATELY when making code changes:
- **NEVER** add code without corresponding imports FIRST
- **ALWAYS** add imports in the SAME edit where you introduce new class usage
- Check BOTH standard library (`java.io.*`, `java.nio.charset.*`) AND project imports
- Forgetting imports wastes tokens on failed compilation cycles

---

## Quick Reference
```bash
# Build
mvn clean package -DskipTests

# Run
./start-lunch-bot.fish <jasypt-secret>

# Test endpoints
curl http://localhost:8888/health
curl http://localhost:8888/signal
```

## Crontab
```
15 11 * * 1-5 /opt/homebrew/bin/fish -c 'cd /path/to/mtxf-bot && ./start-lunch-bot.fish <secret>' >> /tmp/mtxf-bot-cron.log 2>&1
```

---

**Last Audit:** 2025-11-26 | **Score:** 100/100
