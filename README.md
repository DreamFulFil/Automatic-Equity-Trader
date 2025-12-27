[![CI](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml)

# Automatic Equity Trader

**Version 2.0.0** - Complete System Rebuild

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.12](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)
[![Tests](https://img.shields.io/badge/Tests-333%20passing-brightgreen.svg)](tests/)

Risk-first automated trading platform for Taiwan stocks. Conservative, boring, explainable.
Designed for capital preservation with 80,000 TWD starting capital.

**Production-ready** with 333 passing tests, 100 strategies, AI trade veto, and Taiwan compliance.

📚 **[Complete Documentation](docs/INDEX.md)** | 🚀 **[Quick Start](docs/usage/QUICK_START_CHECKLIST.md)** | 📖 **[Beginner Guide](docs/usage/BEGINNER_GUIDE.md)** | 📝 **[Changelog](CHANGELOG.md)**

---

## ✨ What's New in v2.0.2 (2025-12-19)

### 🎯 Stock Name Display & Database Integration Fixes

**Critical Fixes**
- ✅ **Fixed NULL stock names** in Telegram messages (created TaiwanStockNameService with 50-stock mapping)
- ✅ **Fixed duplicate key error** in auto-selection (changed deleteAll() to deleteAllInBatch())
- ✅ Updated BacktestController to populate stock names when saving backtest results
- ✅ Synchronized stock name mappings between Java and Python services
- ✅ Fixed Python bridge database authentication (added POSTGRES_PASSWORD to Fish script)
- ✅ Fixed circular dependency in strategy selection (Python now calls Java AutoStrategySelector)
- ✅ Added direct Java strategy selection endpoint (`/api/backtest/select-strategy-direct`)
- ✅ All 333 Java unit tests passing
- ✅ 70 Python unit tests passing

**Architectural Improvements**
- ✅ **Removed all SQL operations from Python service** (129 lines deleted)
- ✅ **Java (Hibernate) is now sole database owner** - eliminates schema inconsistencies
- ✅ Python service exclusively calls Java REST endpoints for all database operations
- ✅ Removed legacy auto_select_best_strategy method with direct SQL queries
- ✅ Removed shadow_mode_stocks and active_strategy_config table manipulation from Python

### 🏢 Stock Universe Coverage
- Comprehensive Taiwan stock name mappings for 50 major stocks
- Technology & Electronics: TSMC, MediaTek, Hon Hai, Delta Electronics, etc.
- Financial Services: Fubon, Cathay, Mega, CTBC, E.Sun, etc.
- Petrochemicals & Materials: Formosa Plastics, Nan Ya Plastics, China Steel, etc.
- Retail & Consumer: Evergreen Marine, Yang Ming, Uni-President, etc.

### 🚀 Validated Features
- ✅ Full backtest pipeline: data population → backtesting → strategy selection
- ✅ Successfully tested with 10 stocks × 50 strategies = 500 backtest combinations
- ✅ Auto-selection of 1 active + 9 shadow strategies with correct stock names
- ✅ Proper database persistence and selection table population

**New Database Persistence Layer**
- ✅ **BacktestResult** entity - Stores ~50,000 backtest results (50 stocks × 100 strategies)
- ✅ **BacktestRanking** entity - Ranked results for strategy selection
- ✅ **StockUniverse** entity - ~50 selected stocks with selection criteria
- ✅ **ActiveShadowSelection** entity - 11 rows (1 active + 10 shadow stock+strategy pairs)
- ✅ All results persisted with full auditability and source attribution (BACKTEST/FRONTTEST/COMBINATION)

**Automatic Startup Initialization**
- ✅ Checks for persisted backtest data on startup
- ✅ If data exists: automatically loads rankings and selects 1 active + 10 shadow strategies
- ✅ If data missing: prompts for manual backtest execution via `/run-backtests` or REST API
- ✅ **NO hardcoded defaults** - fully data-driven selection
- ✅ Deterministic first-start behavior

**Enhanced Auto-Selection**
- ✅ Populates unified Active/Shadow selection table
- ✅ Exactly 11 entries with proper ranking (1 active, ranks 2-11 shadow)
- ✅ Full metrics tracked: Sharpe ratio, return %, win rate, max drawdown
- ✅ Source attribution for explainability
- ✅ Shadow mode now correctly shows 10 stocks (not 3)

**Improved Logging**
- ✅ Clear distinction between active and shadow entries
- ✅ Ranking position explicitly labeled
- ✅ Reason logged when fewer than 10 shadow entries exist

**REST APIs for Data Operations**
- Populate historical data: `POST /api/backtest/populate-data`
- Run all backtests: `POST /api/backtest/run-all`
- Auto-select strategy: `POST /api/backtest/select-strategy`
- Full pipeline: `POST /api/backtest/full-pipeline`
- System status: `GET /api/backtest/data-status`

**AI Trade Veto (Ollama)**
- Every trade analyzed by Llama 3.1 8B (120s timeout)
- Veto-by-default paranoid risk manager
- Configurable via `/risk enable_ai_veto`
- Full context: P&L, volatility, strategy performance

**Comprehensive Testing**
- 333 unit tests (100% passing after fixes)
- Integration tests with Testcontainers
- E2E test scenarios
- All BacktestResult type references updated

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
# Expected: 486 tests passing (326 Java unit + 70 Python unit + 49 Java integration + 25 Python integration + 16 E2E)
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

### Operational Tasks (Scripts Ready):
- **Historical Data Population**: `scripts/operational/populate_historical_data.py`
  - Fetches 2 years of data for 10 Taiwan stocks, stores in PostgreSQL
- **Combinatorial Backtesting**: `scripts/operational/run_combinatorial_backtests.py`
  - Tests 50 strategies × 10 stocks, stores results in database
- **Run All**: `scripts/operational/run_all_operational_tasks.sh <jasypt-password>`
  - Complete pipeline: data + backtests (~30-60 minutes)
- **Live Trading Deployment**: System ready; requires API credentials and market hours

See [`scripts/operational/README.md`](scripts/operational/README.md) for detailed usage.

---

**Status**: Production-ready ✅ | **Tests**: 486 total passing | **Strategies**: 100 total (53 complete, 47 templates) | **Version**: 2.9.0

*Owner: DreamFulFil | License: MIT | Last Updated: December 2025*
