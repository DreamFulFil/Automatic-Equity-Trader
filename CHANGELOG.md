# Changelog

All notable changes to the Automatic Equity Trader are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [2.0.0] - 2025-12-19

### Major Release: Complete System Rebuild

Complete rebuild of the trading system with modern architecture, comprehensive testing, and production-ready features.

### Added
- **REST APIs for Data Operations**
  - `/api/backtest/populate-data` - Historical data population
  - `/api/backtest/run-all` - Combinatorial backtesting
  - `/api/backtest/select-strategy` - Auto-strategy selection
  - `/api/backtest/full-pipeline` - Complete workflow
  - `/api/backtest/data-status` - System status
  
- **Telegram Commands**
  - `/populate-data` - Load historical data
  - `/run-backtests` - Test all strategies
  - `/select-best-strategy` - Auto-select optimal strategy
  - `/full-pipeline` - Run complete workflow
  - `/data-status` - Show statistics

- **100 Trading Strategies**
  - 53 complete strategies with full implementation
  - 47 template strategies for future development
  - All strategies tested and documented

- **Ollama AI Integration**
  - Trade veto system with LLM analysis
  - News sentiment analysis
  - Configurable AI parameters

- **Taiwan Market Compliance**
  - Stock exchange rules validation
  - Odd-lot trading restrictions
  - Market hours enforcement
  - Earnings blackout integration

- **Comprehensive Risk Management**
  - 15 risk parameters with Telegram configuration
  - Daily/weekly loss limits
  - Position sizing controls
  - Volatility filters

- **Testing Infrastructure**
  - 333 unit tests (100% pass rate)
  - Integration tests with Testcontainers
  - E2E test scenarios
  - CI/CD with GitHub Actions

### Changed
- Merged DataOperationsController into BacktestController for clarity
- Improved error messages and logging
- Enhanced Telegram message formatting (no more `\n` escape sequences)
- Reorganized documentation structure

### Fixed
- Telegram newline formatting issues
- Ollama timeout handling
- Database column name mapping
- Controller redundancy

### Documentation
- Complete API reference
- Beginner's guide updated
- Testing guide enhanced
- Architecture documentation
- FAQ expanded

---

## [1.0.0] - 2024-12-14

### Initial Production Release

First stable release of the Automatic Equity Trader.

### Added
- Basic trading engine
- RSI, MACD, Bollinger Bands strategies
- Taiwan stock market support
- Telegram bot integration
- Basic risk management
- PostgreSQL data storage

---

## Version Numbering

- **Major**: Breaking changes, significant feature additions
- **Minor**: New features, backwards compatible
- **Patch**: Bug fixes, minor improvements

---

For detailed changes, see individual [Release Notes](https://github.com/DreamFulFil/Automatic-Equity-Trader/releases).
