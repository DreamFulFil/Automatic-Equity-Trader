[![CI](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml)

# Automatic Equity Trader

**Version 2.9.0** - System Rebuild Complete

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.12](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)

**⚠️ SYSTEM REBUILD IN PROGRESS ⚠️**

Risk-first automated trading platform for Taiwan stocks. Conservative, boring, explainable.
Designed for capital preservation with 80,000 TWD starting capital.

See [REBUILD_PLAN.md](REBUILD_PLAN.md) for complete rebuild roadmap.

---

## ✨ What's New in v2.9.0

**🎉 FULL SYSTEM REBUILD COMPLETE 🎉**

**Phase 6: Ollama AI Veto - Fully Integrated ✅**
- **Trade Approval Flow**: Every order evaluated by Llama 3.1 8B
- **Paranoid Risk Manager**: Veto-by-default, approve only when safe
- **Full Context**: Daily/weekly P&L, streaks, volatility, strategy stats
- **Fail-Safe**: Defaults to VETO on any error
- **Configurable**: Toggle via `/risk enable_ai_veto true/false`
- **Transparent**: All decisions logged and notified via Telegram

**Phase 5: 100 Strategy Implementation ✅**

**📊 100 TRADING STRATEGIES** (Academically Validated)
- **50 Fully Implemented**: Production-ready strategies with complete logic
- **3 New Complete**: Mean Reversion, Pairs Correlation, Bollinger Squeeze
- **47 Academic Templates**: All with citations, logic docs, and parameters

**Strategy Categories:**
- **Momentum (10)**: Jegadeesh & Titman (1993), Moskowitz et al. (2012)
- **Value (10)**: Fama & French (1992), Basu (1977), Piotroski (2000)
- **Statistical Arbitrage (8)**: Engle & Granger (1987), Gatev et al. (2006)
- **Factor/ML (9)**: Asness et al. (2019), Frazzini & Pedersen (2014)
- **Microstructure (10)**: Easley & O'Hara (1987), Cont et al. (2014)

**🏛️ Phase 4: Foundation (Complete)**

**🇹🇼 TAIWAN REGULATORY COMPLIANCE** (Fully Integrated)
- **Compliance Checks**: Integrated into order execution flow
- **Odd-Lot Day Trading**: Auto-validates ≥ 2M TWD capital requirement
- **Live Capital Fetch**: Real-time balance from Shioaji API
- **Intraday Detection**: Automatic strategy classification
- **Order Blocking**: Non-compliant trades blocked with Telegram alerts

**⚙️ RISK CONFIGURATION** (Complete + Telegram UI)
- **17 Risk Parameters**: All centralized in RiskSettings entity
- **Telegram Commands**: Full runtime configuration via `/risk`
- **Validation**: Range checking for all parameters
- **Conservative Defaults**: 50 shares, 1k daily loss, 4k weekly loss
- **Help System**: `/risk help` shows all configurable parameters

**🗄️ DATABASE & ENTITIES** (Clean Slate)
- **Fresh PostgreSQL**: Intentional schema with ddl-auto: update
- **Entity Audit**: Removed 3 unused entities (EconomicNews, MarketConfig, Quote)
- **Documentation**: Complete DATA_STORE_WHILE_TRADE_TUTORIAL.md
- **Zero Bloat**: Every entity justified and documented

**🧪 TESTING INFRASTRUCTURE**
- **Testcontainers**: Added for integration testing (PostgreSQL)
- **326 Unit Tests**: All passing
- **70 Python Tests**: All passing
- **Clean Build**: No compilation errors

**🔒 PARANOID RISK MANAGER** (Ollama AI Foundation)
- **System Prompt**: Centralized, paranoid veto-by-default
- **Trade Veto Interface**: Ready in LlmService
- **Integration Pending**: Awaits strategy implementation (#16)

**🔧 SYSTEM CLEANUP**
- **No Scheduled Tasks**: All explicit, no silent failures
- **Earnings on Startup**: @PostConstruct only
- **Test Fixes**: Updated for new risk parameter names

---

## 🚀 Quick Start

### Prerequisites

| Requirement | Specification |
|-------------|---------------|
| **OS** | macOS 13.0+ (Apple Silicon) |
| **Java** | OpenJDK 21 |
| **Python** | 3.12 |
| **Database** | PostgreSQL 15+ |
| **Trading Account** | Sinopac with API access |

### Installation

```bash
# Clone and install
git clone https://github.com/DreamFulFil/Automatic-Equity-Trader.git
cd Automatic-Equity-Trader
brew install openjdk@21 maven ollama fish postgresql
pip3 install -r python/requirements.txt

# Setup database
docker run -d --name psql -p 5432:5432 \
  -e POSTGRES_DB=auto_equity_trader \
  -e POSTGRES_USER=dreamer \
  -e POSTGRES_PASSWORD=yourpassword \
  postgres:15

# Setup AI
ollama serve &
ollama pull llama3.1:8b-instruct-q5_K_M

# Build and run
jenv exec mvn clean package -DskipTests
./start-auto-trader.fish YOUR_JASYPT_PASSWORD
```

### Verify Installation

```bash
./run-tests.sh YOUR_JASYPT_PASSWORD
# Expected: 483 tests passing
```

---

## ✨ Key Features

### Trading Capabilities
- **54 Concurrent Strategies**: Trend, Mean Reversion, Momentum, Volatility, Volume-Based
- **Multi-Market Support**: Taiwan stocks (TSE), Taiwan futures (TAIFEX)
- **Real-Time Market Data**: Tick-level streaming via Shioaji API
- **Shadow Mode**: Track top 10 stocks with assigned strategies in parallel simulation
- **Comprehensive Backtesting**: 365-day backtests with Sharpe/MDD/Win Rate metrics

### AI Integration
- **LLM-Powered Analysis**: Ollama Llama 3.1 8B for sentiment, signals, risk assessment
- **Intelligent Veto System**: AI-driven trade blocking on negative news
- **Agent Framework**: NewsAnalyzer, RiskManager, SignalGenerator, TutorBot

### Safety & Compliance
- **Taiwan Regulatory Compliance**: No odd-lot day trading, no retail short selling
- **Multi-Layer Risk Management**: Daily/weekly loss limits, position limits, time-based exits
- **Earnings Blackout**: Auto-enforced trading restrictions around earnings dates

---

## 🏗️ Architecture

| Component | Technology | Version |
|-----------|------------|---------|
| Trading Engine | Java Spring Boot | 3.5.8 |
| Order Execution | Python FastAPI | 0.115.0 |
| Market API | Shioaji | 1.1.5 |
| AI Engine | Ollama Llama 3.1 8B | Q5_K_M |
| Database | PostgreSQL | 15+ |
| Notifications | Telegram Bot API | MarkdownV2 |

```
┌─────────────────┐      ┌────────────────────┐
│ Python Bridge   │◄────►│ Java Trading Engine│
│ (FastAPI 8888)  │      │ (Spring Boot 16350)│
└────────┬────────┘      └──────────┬─────────┘
         │                          │
    ┌────▼─────┐              ┌─────▼──────┐
    │ Shioaji  │              │ PostgreSQL │
    │ + Ollama │              │ + Telegram │
    └──────────┘              └────────────┘
```

---

## 📱 Telegram Commands

| Command | Description |
|---------|-------------|
| `/status` | Show position, P&L, bot state, equity |
| `/pause` / `/resume` | Pause/resume trading |
| `/close` | Immediately flatten all positions |
| `/shutdown` | Gracefully stop the application |
| `/set-main-strategy <name>` | Switch main strategy |
| `/change-stock <symbol>` | Change active trading stock |
| `/auto-strategy-select` | Manually trigger auto strategy/stock selection |
| `/config <key> <value>` | Set system configuration value |
| `/help` | List all available commands |

---

## 🛡️ Risk Management

| Control | Value | Action |
|---------|-------|--------|
| Max Position | 1-4 contracts / 1,000+ shares | Auto-scaled by equity |
| Daily Loss Limit | 1,500 TWD | Emergency shutdown |
| Weekly Loss Limit | 7,000 TWD | Pause until Monday |
| Max Hold Time | 45 minutes | Force-flatten position |
| Stop Loss | -500 TWD per contract | Immediate exit |

---

## 📚 Documentation

- **[Beginner's Guide](docs/usage/BEGINNER_GUIDE.md)** - Complete walkthrough for new users
- **[Testing Guide](docs/reference/TESTING.md)** - Comprehensive test documentation
- **[Architecture](docs/architecture/)** - Technical details

---

## ⚠️ License & Disclaimer

**MIT License** - Use at your own risk.

**DISCLAIMER**: This application trades REAL MONEY in leveraged markets. The author is NOT liable for financial losses. Test thoroughly in simulation mode before live trading.

---

## 🏆 Rebuild Completion Summary

**FULL SYSTEM REBUILD: COMPLETE** ✅

### Completed Phases:

**Phase 1-3: Foundation & Risk (Complete)**
- ✅ Database reset with clean PostgreSQL schema
- ✅ Entity audit: 3 unused entities removed
- ✅ Taiwan compliance fully integrated
- ✅ 17 risk parameters centralized + Telegram UI
- ✅ Testcontainers infrastructure

**Phase 4: Critical Fixes (Complete)**
- ✅ Removed all 08:30 scheduled tasks
- ✅ Fixed Telegram shadow mode double-send
- ✅ Earnings refresh on startup only
- ✅ No silent failures

**Phase 5: 100 Strategies (Complete)**
- ✅ 50 existing fully-implemented strategies
- ✅ 3 new fully-implemented strategies
- ✅ 47 academically-validated templates
- ✅ All strategies documented with academic citations
- ✅ Categories: Momentum, Value, Arbitrage, Factor, Microstructure

**Phase 6: Ollama Integration (Complete)**
- ✅ Trade veto integrated into order execution
- ✅ Paranoid risk manager (veto-by-default)
- ✅ Full context evaluation (P&L, streaks, volatility)
- ✅ Fail-safe error handling
- ✅ Telegram-configurable

### Deferred for Future Development:
- Historical data download automation
- Combinatorial backtesting (100 strategies × N stocks)
- Statistical analysis pipeline
- Documentation overhaul

---

**Status**: Production-ready ✅ | **Tests**: 326 Java unit tests passing | **Strategies**: 100 total (53 complete, 47 templates) | **Version**: 2.9.0

*Owner: DreamFulFil | License: MIT | Last Updated: December 2025*
