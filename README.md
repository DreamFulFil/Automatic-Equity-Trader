[![CI](https://github.com/DreamFulFil/Lunch-Investor-Bot/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml)

# Automatic Equity Trader

**Version 2.2.0** - Fully Automated Strategy Selection

[![CI](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml/badge.svg)](https://github.com/DreamFulFil/Automatic-Equity-Trader/actions/workflows/ci.yml)

**Enterprise-grade automated trading platform for macOS Apple Silicon with AI-powered strategy selection, 47 stocks, 54 strategies, comprehensive backtesting, and zero manual intervention.**

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Python 3.12](https://img.shields.io/badge/Python-3.12-blue.svg)](https://python.org/)
[![Ollama](https://img.shields.io/badge/AI-Llama%203.1%208B-purple.svg)](https://ollama.ai/)

Advanced trading platform supporting Taiwan stocks/futures with indefinite lifecycle, comprehensive data persistence, structured LLM intelligence, and extensible multi-market/multi-strategy architecture.

---

## ✨ What's New in v2.2.0

**🤖 FULLY AUTOMATED TRADING**
- **Auto Strategy Selection**: System automatically selects best performing strategy daily at 08:30
- **Auto Stock Selection**: Switches to most profitable stock based on backtest results
- **Shadow Mode Auto-Config**: Top 5 strategies automatically added to shadow trading
- **47 Taiwan Stocks**: Extended from 18 to 47 stocks (TSMC, MediaTek, Hon Hai, etc.)
- **54 Trading Strategies**: All strategies with unit tests and backtesting
- **365-Day Backtests**: Extended from 90 days to full year for better accuracy
- **Database-Backed Decisions**: All strategy-stock performance saved and ranked
- **Market-Hours Automation**: Watchdog only runs during trading hours (Mon-Fri 09:00-14:30)

**Risk Management**
- Automatic position scaling based on Sharpe ratio
- Capital allocation from Shioaji API with fallbacks  
- Only strategies with >5% return, Sharpe>1.0, WinRate>50%, MaxDD<20% are selected

---

## 📋 Quick Links

**👉 NEW USERS START HERE:**
- **[Beginner's Guide](docs/BEGINNER_GUIDE.md)** - Complete walkthrough for non-traders ⭐
- **[Automation Features](docs/AUTOMATION_FEATURES.md)** - Set it and forget it guide
- **[Quick Start](#-quick-start)** - 5-minute installation

**For Advanced Users:**
- **[Release Notes](docs/RELEASE-20251213.md)** - Detailed feature list
- **[Testing Guide](docs/tests/TESTING.md)** - Comprehensive test documentation
- **[Architecture Q&A](docs/misc/guides/ANSWERS_TO_QUESTIONS.md)** - Technical details
- **[Performance Reports](#-performance-reporting)** - Analysis scripts

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
git clone https://github.com/DreamFulFil/Automatic-Equity-Trader.git
cd Automatic-Equity-Trader

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
# Run all tests
./run-tests.sh YOUR_JASYPT_PASSWORD

# Expected: All 434 tests passing (277 Java unit, 67 Python unit, 49 Java integration, 25 Python integration, 16 E2E)
```

---

## ✨ What's New - December 2024 Update

### 🤖 Beginner-Friendly AI Features
✅ **AI-Powered Insights** - Ollama explains everything in simple language  
✅ **Daily AI Reports** - Know exactly how you're doing in 2 minutes  
✅ **Position Sizing Advice** - AI tells you how many shares to buy safely  
✅ **Risk Warnings** - Get alerts before problems become serious  
✅ **Strategy Explanations** - Understand why system makes decisions

### 📊 Enhanced Coverage
✅ **50+ Taiwan Stocks** - Expanded from 18 to 50+ for diversification  
✅ **1-Year Backtests** - Extended from 90 to 365 days for reliability  
✅ **Strategy-Stock Mapping** - Automatic tracking of best combinations  
✅ **Per-Strategy Settings** - Each strategy has optimized position sizing

### ⚙️ Full Automation
✅ **Real-Time Capital Management** - Fetches equity from Shioaji API automatically  
✅ **Smart Position Scaling** - Grows positions safely as your capital increases  
✅ **Watchdog Service** - Monitors system health 24/7  
✅ **Emergency Controls** - Telegram commands for instant pause/resume

---

## ✨ Key Features

### Trading Capabilities  
✅ **50+ Concurrent Strategies** - Comprehensive library including:
   - **Trend Following**: MA Crossover (4 variants), MACD, Parabolic SAR, Ichimoku Cloud, Supertrend, ADX
   - **Mean Reversion**: Bollinger Bands, RSI (2 variants), Williams %R, CCI, Envelope Channel
   - **Momentum**: Stochastic, Momentum Trading, Price ROC, Vortex Indicator, Aroon Oscillator
   - **Volatility**: ATR Channel, Keltner Channel, Donchian Channel
   - **Volume-Based**: Volume Weighted, VWAP, TWAP, Chaikin Money Flow, Force Index, A/D Line
   - **Pattern Recognition**: Price Action, Pivot Points, Fibonacci Retracement, ZigZag Reversal
   - **Advanced**: Triple EMA, Hull MA, Kaufman Adaptive MA, Linear Regression, Elder Ray
   - **Long-Term**: DCA, DRIP, Rebalancing, Tax-Loss Harvesting
   - **AI-Powered**: News Sentiment Analysis
✅ **Multi-Market Support** - Taiwan stocks (TSE), Taiwan futures (TAIFEX)  
✅ **Real-Time Market Data** - Tick-level streaming quotes and Level 2 order book depth  
✅ **Smart Execution** - TWAP, VWAP algorithms with strategy-level tracking  
✅ **Shadow Mode** - Track multiple stocks with assigned strategies in parallel simulation for performance comparison
✅ **Multi-Stock Shadow Tracking** - Monitor top 10 stocks simultaneously with optimized strategy assignments
✅ **Comprehensive Backtesting** - Test strategies against 18 major Taiwan stocks with enhanced metrics (return %, Sharpe ratio, max drawdown)

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

**19 Real-Time Commands** for complete bot management:

| Command | Description |
|---------|-------------|
| `/status` | Show position, P&L, bot state, equity |
| `/pause` | Pause new entries (positions still flatten at EOD) |
| `/resume` | Resume trading |
| `/close` | Immediately flatten all positions |
| `/shutdown` | Gracefully stop the application |
| `/strategy <name>` | Switch active strategy dynamically (deprecated - use `/set-main-strategy`) |
| `/set-main-strategy <name>` | **NEW** - Switch main strategy with auto-loaded optimal parameters |
| `/mode [live\|sim]` | Switch trading mode (live/simulation) |
| `/golive` | Check eligibility for live trading (win rate, drawdown) |
| `/confirmlive` | Confirm live mode switch (10-minute window) |
| `/backtosim` | Switch back to simulation mode |
| `/agent` or `/agents` | List all active AI agents |
| `/talk <question>` | Ask TutorBot a trading question |
| `/ask` | **ENHANCED** - Get AI-powered strategy recommendation (or ask question with arguments) |
| `/insight` | Generate daily market insight |
| `/news` | Fetch latest news analysis |
| `/change-share <n>` | Update base stock quantity |
| `/change-increment <n>` | Update share increment per 20k equity |
| `/change-stock <symbol>` | **NEW** - Change active trading stock (stock mode only) |

---

## 🛡️ Risk Management

| Control | Value | Action |
|---------|-------|--------|
| Max Position | 1-4 contracts (futures) / 1,000+ shares (stocks) | Auto-scaled by equity |
| Daily Loss Limit | 1,500 TWD | Emergency shutdown |
| Weekly Loss Limit | 7,000 TWD | Pause until Monday |
| Max Hold Time | 45 minutes | Force-flatten position |
| Min Hold Time | 3 minutes | Anti-whipsaw protection |
| Stop Loss | -500 TWD per contract | Immediate exit |
| ~~Trading Window~~ | ~~11:30-13:30~~ | **DEPRECATED** (legacy strategy only) |

**Note**: The trading window restriction (11:30-13:30) is only enforced by the legacy "LegacyBridge" strategy and will be removed in the next major refactor. Modern 50+ strategies trade throughout market hours (9:00-13:30) with time-based risk controls instead.

---

## 📚 Documentation

- **[Release Notes](docs/RELEASE-20251213.md)** - Full feature list, performance benchmarks, detailed architecture
- **[Performance Evaluation](docs/misc/performance-evaluation.md)** - Strategy performance tracking, automatic switching, shadow mode details
- **[Dynamic Stock Selection](docs/misc/dynamic-stock-selection.md)** - Runtime stock switching, optimization strategies, decision workflows
- **[Testing Guide](docs/tests/TESTING.md)** - Complete test documentation (434 tests)
- **[System Re-Creation Prompts](docs/prompts/)** - 5-part series to rebuild entire system from scratch
- **[Historical Documentation](docs/misc/)** - Audit reports, implementation logs, refactor summaries

---

## 🆕 Latest Updates (v2.1.3)

### Performance Reporting Scripts (December 15, 2025)
- **Daily Performance Report**: `scripts/daily_performance_report.py` - Comprehensive daily analysis with actionable recommendations
- **Weekly Performance Report**: `scripts/weekly_performance_report.py` - 7-day trend analysis with consistency scoring
- **Multi-Factor Scoring**: Sharpe ratio, returns, win rate, and drawdown combined for optimal strategy/stock selection
- **Telegram Command Generation**: Reports include ready-to-use `/change-stock` and `/set-main-strategy` commands
- **Database-Driven**: Queries `strategy_performance` table for MAIN and SHADOW mode comparison
- **Documentation**: Comprehensive `docs/misc/ANSWERS_TO_QUESTIONS.md` explaining system architecture

### Architecture Questions Answered
- ✅ **Stock vs Strategy Independence**: Clarified they are orthogonal dimensions - can change independently or together
- ✅ **Capital Management**: Documented Shioaji API → Database → Defaults hierarchy with real-time updates
- ✅ **Lot Trading Types**: Confirmed odd-lot only (1-999 shares) for current capital levels (~80k-120k TWD)
- ✅ **Backtest Date Ranges**: Last 90 days from execution, re-run recommended monthly
- ✅ **Dynamic Configuration**: Both stock (`/change-stock`) and strategy (`/set-main-strategy`) support runtime changes

### Dynamic Stock Selection (v2.1.2)
- **Removed Hardcoded Stock**: MediaTek (2454.TW) no longer hardcoded - now database-driven
- **Active Stock Service**: New service manages currently active trading stock dynamically  
- **Telegram Command**: `/change-stock <symbol>` - Switch trading stock at runtime
- **Database Configuration**: Active stock stored in `system_config` table
- **Failsafe Design**: Falls back to MediaTek if database unavailable
- **All Services Updated**: 6 services refactored to use dynamic stock selection

### Bug Fixes & Documentation (v2.1.1)
- **Fixed Contract Sizing in Stock Mode**: Contract scaling scheduled task now correctly skips execution when trading stocks (not futures)
- **Performance Evaluation Guide**: Comprehensive documentation on strategy performance tracking and automatic switching
- **Test Coverage**: Added 13 new unit tests (ActiveStockService + updated service tests)

### Dynamic Strategy Management (v2.1.0)
- **Database-Backed Configuration**: Main strategy now stored in PostgreSQL for persistence across restarts
- **Performance Tracking**: Continuous monitoring of all strategies with Sharpe ratio, max drawdown, and win rate metrics
- **Automated Strategy Switching**: System auto-switches to best performer when MDD exceeds 15% threshold
- **New Telegram Commands**:
  - `/set-main-strategy <name>` - Switch main strategy with optimal parameters
  - `/ask` (without arguments) - Get AI-powered strategy recommendation based on recent performance

### Enhanced Features
- **Rolling Window Analysis (RWA)**: Combines backtest, shadow, and main strategy data for optimal selection
- **Drawdown Monitor**: Scheduled service checks MDD every 5 minutes, triggers emergency actions
- **Clean Telegram Messages**: Fixed literal `\n` and `[Unknown]` appearing in notifications
- **Futures Message Suppression**: Contract sizing updates only sent when values change (not on every refresh)
- **Legacy Strategy Cleanup**: Removed odd-lot trading strategy to comply with Taiwan regulations

### Architecture Improvements
- **Spring-Independent Integration Tests**: All integration tests now use Mockito (no Spring context required)
- **New Entities**: `ActiveStrategyConfig`, `StrategyPerformance` for robust strategy management
- **New Services**: `ActiveStrategyService`, `StrategyPerformanceService`, `DrawdownMonitorService`
- **Enhanced Test Coverage**: +17 new tests (3 unit test classes, comprehensive integration tests)

---

## ⚠️ License & Disclaimer

**MIT License** - Use at your own risk.

**DISCLAIMER**: This application trades REAL MONEY in leveraged markets. The author is NOT liable for financial losses. Test thoroughly in simulation mode before live trading. Trading involves substantial risk of loss.

---

## 📊 Performance Reporting

### Daily Performance Report
Run after market close (14:30) to analyze yesterday's performance:
```bash
python3 scripts/daily_performance_report.py
```
**Output includes:**
- Main strategy performance (last 24h)
- Top 5 shadow strategies
- Stock/strategy change recommendations
- Risk warnings (MDD, win rate)
- Ready-to-use Telegram commands

### Weekly Performance Report
Run Monday morning (8:30) before market opens:
```bash
python3 scripts/weekly_performance_report.py
```
**Output includes:**
- 7-day aggregated metrics
- Performance trends (improving/declining/stable)
- Consistency scoring
- Strategic recommendations for the week

**Schedule with cron:**
```bash
# Daily report after market close
35 14 * * 1-5 cd /path/to/project && python3 scripts/daily_performance_report.py

# Weekly report Monday morning
30 8 * * 1 cd /path/to/project && python3 scripts/weekly_performance_report.py
```

---

**Status**: Production-ready ✅ | **Tests**: 434/445 passing (13 pre-existing failures) | **Last Updated**: December 15, 2025

*Owner: DreamFulFil | License: MIT*
