[![CI](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml)

# Automatic Equity Trader

**Version 2.7.0** - Full System Rebuild (In Progress)

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.12](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)

**⚠️ SYSTEM REBUILD IN PROGRESS ⚠️**

Risk-first automated trading platform for Taiwan stocks. Conservative, boring, explainable.
Designed for capital preservation with 80,000 TWD starting capital.

See [REBUILD_PLAN.md](REBUILD_PLAN.md) for complete rebuild roadmap.

---

## ✨ What's New in v2.7.0 (Current Phase)

**🔒 PARANOID RISK MANAGER (Ollama AI)**
- **VETO by Default**: AI rejects trades unless ALL 10 safety criteria met
- **Capital Preservation**: Profitability irrelevant - avoiding losses is everything
- **Strict Parsing**: APPROVE or VETO: reason format enforced
- **System Prompt**: Centralized, identical in Python and Java
- **Trade Proposal**: Full context (P&L, news, volatility, streaks)

**🇹🇼 TAIWAN REGULATORY COMPLIANCE**
- **Odd-Lot Day Trading**: Requires ≥ 2,000,000 TWD capital (TSE rules)
- **Round Lot Validation**: 1000 shares per round lot
- **Live Capital Check**: Fetches account balance from Shioaji API
- **Intraday Detection**: Automatically flags day-trading strategies
- **Fail-Safe Defaults**: Conservative 80k TWD if API unavailable

**⚙️ CENTRALIZED RISK CONFIGURATION**
- **17 Risk Parameters**: All in one RiskSettings entity
- **Conservative Defaults**: 50 shares/trade, 1k daily loss limit
- **Strategy Quality**: Min Sharpe 1.5, win rate 55%, max drawdown 15%
- **Telegram Configurable**: Runtime parameter updates (in development)
- **Database-Backed**: Persistent, versioned, auditable

**📝 DATA STORAGE AUDIT**
- **Complete Documentation**: [DATA_STORE_WHILE_TRADE_TUTORIAL.md](docs/usage/DATA_STORE_WHILE_TRADE_TUTORIAL.md)
- **20 Entities Documented**: Purpose, storage, lifecycle, retention
- **Zero Unused Data**: Everything earns its right to exist
- **No "Just in Case"**: Intentional schema design only

**🔧 SYSTEM CLEANUP**
- **No 08:30 Schedules**: Manual triggers only (explicit behavior)
- **Earnings on Startup**: @PostConstruct, not scheduled
- **Backward Compatibility**: Legacy methods deprecated but functional
- **Compile-Time Safety**: All changes verified before commit

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

**Status**: Production-ready ✅ | **Tests**: 483 passing (326 Java unit, 67 Python unit, 49 Java integration, 25 Python integration, 16 E2E) | **Last Updated**: 2025

*Owner: DreamFulFil | License: MIT*
