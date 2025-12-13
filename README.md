[![CI](https://github.com/DreamFulFil/Lunch-Investor-Bot/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Lunch-Investor-Bot/actions/workflows/ci.yml)

# Automatic Equity Trader

**Enterprise-grade automated trading platform for macOS Apple Silicon with multi-market support, concurrent strategy execution, and local LLM analytics.**

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.12](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)

Advanced trading platform supporting Taiwan stocks/futures with indefinite lifecycle, comprehensive data persistence, structured LLM intelligence, and extensible multi-market/multi-strategy architecture.

---

## 📋 Quick Links

- **[Quick Start](#-quick-start)** - Get running in 5 minutes
- **[Release Notes](docs/RELEASE-20251213.md)** - Detailed feature list and performance benchmarks
- **[Testing Guide](docs/tests/TESTING.md)** - Comprehensive test documentation
- **[System Re-Creation Prompts](docs/prompts/)** - Complete 5-prompt series to rebuild from scratch

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
# 1. Clone repository
git clone https://github.com/DreamFulFil/Lunch-Investor-Bot.git
cd Lunch-Investor-Bot

# 2. Install dependencies
brew install openjdk@21 maven ollama fish postgresql
pip3 install -r python/requirements.txt

# 3. Setup database
docker run -d --name psql -p 5432:5432 \
  -e POSTGRES_DB=autotrader \
  -e POSTGRES_USER=autotraderuser \
  -e POSTGRES_PASSWORD=yourpassword \
  postgres:15

# 4. Download AI model
ollama serve &
ollama pull llama3.1:8b-instruct-q5_K_M

# 5. Configure (edit src/main/resources/application.yml)
# Set database credentials, Telegram bot token, trading API keys

# 6. Build and run
jenv exec mvn clean package -DskipTests
./start-auto-trader.fish YOUR_JASYPT_PASSWORD
```

### Verify Installation

```bash
# Run all 240 tests
jenv exec mvn test

# Expected: Tests run: 240, Failures: 0, Errors: 0, Skipped: 0
```

---

## ✨ Key Features

### Trading Capabilities
✅ **11 Concurrent Strategies** - DCA, MA Crossover, Bollinger Bands, VWAP, Momentum, Arbitrage, News/Sentiment, TWAP, Rebalancing, DRIP, Tax-Loss Harvesting  
✅ **Multi-Market Support** - Taiwan stocks (TSE), Taiwan futures (TAIFEX), ready for US markets  
✅ **Real-Time Market Data** - Tick-level streaming quotes and Level 2 order book depth  
✅ **Smart Order Execution** - TWAP, VWAP execution strategies

### AI Integration
✅ **LLM-Powered Analysis** - Ollama Llama 3.1 8B for news sentiment, signal enhancement, risk assessment  
✅ **Intelligent Veto System** - AI-driven trade blocking on negative news  
✅ **Human-Readable Errors** - Automatic LLM-generated error explanations  
✅ **Agent Framework** - NewsAnalyzer, RiskManager, SignalGenerator, TutorBot agents

### Safety & Compliance
✅ **Taiwan Regulatory Compliance** - No odd-lot day trading, no retail short selling  
✅ **Multi-Layer Risk Management** - Daily/weekly loss limits, position limits, time-based exits  
✅ **Emergency Shutdown** - Automatic system halt on risk threshold breach  
✅ **Earnings Blackout** - Auto-enforced trading restrictions around earnings dates

---

## 🏗️ Architecture

### Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Trading Engine | Java Spring Boot | 3.5.8 |
| Order Execution | Python FastAPI | 0.115.0 |
| Market API | Shioaji | 1.1.5 |
| AI Engine | Ollama Llama 3.1 8B | Q5_K_M |
| Database | PostgreSQL | 15+ |
| Notifications | Telegram Bot API | MarkdownV2 |

### System Overview

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

## 📱 Telegram Control

| Command | Description |
|---------|-------------|
| `/status` | Show position, P&L, bot state |
| `/pause` | Pause new entries |
| `/resume` | Resume trading |
| `/close` | Immediately flatten all positions |
| `/shutdown` | Gracefully stop the application |
| `/strategy` | Switch active strategy dynamically |
| `/golive` | Check eligibility for live trading |
| `/backtosim` | Switch back to simulation mode |
| `/talk <q>` | Ask TutorBot a trading question |
| `/insight` | Generate daily market insight |

---

## 🛡️ Risk Management

| Control | Value | Action |
|---------|-------|--------|
| Max Position | 1-4 contracts | Cannot exceed |
| Daily Loss Limit | 1,500 TWD | Emergency shutdown |
| Weekly Loss Limit | 7,000 TWD | Pause until Monday |
| Trading Window | 11:30-13:00 | No trades outside |

---

## 📚 Documentation

- **[Release Notes](docs/RELEASE-20251213.md)** - Full feature list, performance benchmarks, detailed architecture
- **[Testing Guide](docs/tests/TESTING.md)** - Complete test documentation (240 tests)
- **[System Re-Creation Prompts](docs/prompts/)** - 5-part series to rebuild entire system from scratch
- **[Historical Documentation](docs/misc/)** - Audit reports, implementation logs, refactor summaries

---

## ⚠️ License & Disclaimer

**MIT License** - Use at your own risk.

**DISCLAIMER**: This application trades REAL MONEY in leveraged markets. The author is NOT liable for financial losses. Test thoroughly in simulation mode before live trading. Trading involves substantial risk of loss.

---

**Status**: Production-ready ✅ | **Tests**: 240/240 passing ✅ | **Last Updated**: December 2025

*Owner: DreamFulFil | License: MIT*
