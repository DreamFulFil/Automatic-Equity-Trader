# DATA STORAGE DURING TRADING - COMPLETE GUIDE

This document explains **every piece of data** stored during trading operations, its purpose, storage location, and why it exists.

## GUIDING PRINCIPLE
**Nothing is stored without justification**. Every entity, column, and piece of data earns its right to exist by serving a clear purpose in:
- Taiwan stock/futures trading execution
- Risk management and compliance
- Statistical analysis and performance evaluation
- System auditability and debugging

---

## ENTITIES & THEIR PURPOSE

### 1. TRADE (Core)
**Purpose:** Record every trade executed (real or simulated)

**Storage:** `trade` table in PostgreSQL

**Fields:**
- `id` - Unique identifier
- `timestamp` - When trade occurred (Taiwan time)
- `symbol` - Stock/futures symbol (e.g., "2330", "MTXF")
- `action` - BUY / SELL
- `quantity` - Number of shares/contracts
- `entry_price` - Price at entry
- `exit_price` - Price at exit (if closed)
- `realized_pnl` - Profit/loss in TWD (if closed)
- `strategy_name` - Which strategy generated this trade
- `reason` - Why the trade was taken (strategy logic)
- `mode` - SIMULATION or LIVE
- `status` - OPEN or CLOSED
- `hold_duration_minutes` - How long position was held

**Why:** Core audit trail for all trading activity. Required for P&L calculation, strategy evaluation, and regulatory compliance.

**When Stored:** Every trade execution (real or shadow)

---

### 2. DAILY_STATISTICS (Analysis)
**Purpose:** Aggregate daily trading performance metrics

**Storage:** `daily_statistics` table

**Fields:**
- `id` - Unique identifier
- `trade_date` - Date of trading day
- `symbol` - Stock/futures symbol
- `total_trades` - Number of trades that day
- `winning_trades` - Number of profitable trades
- `losing_trades` - Number of losing trades
- `total_pnl` - Daily profit/loss in TWD
- `win_rate` - Percentage of winning trades
- `sharpe_ratio` - Risk-adjusted return metric
- `max_drawdown` - Largest peak-to-trough decline
- `avg_hold_duration_minutes` - Average time per trade
- `strategy_name` - Primary strategy used
- `mode` - SIMULATION or LIVE

**Why:** Track daily performance, identify patterns, support automated strategy selection.

**When Stored:** End of trading day (13:35 Taiwan time)

---

### 3. STRATEGY_PERFORMANCE (Backtesting)
**Purpose:** Store backtest results for all strategies

**Storage:** `strategy_performance` table

**Fields:**
- `id` - Unique identifier
- `strategy_name` - Name of strategy tested
- `symbol` - Stock tested
- `total_trades` - Trades in backtest period
- `win_rate` - Success rate
- `total_pnl` - Cumulative P&L in backtest
- `sharpe_ratio` - Risk-adjusted metric
- `max_drawdown_percent` - Worst loss period
- `avg_trade_duration_minutes` - Typical hold time
- `backtest_start_date` - Period start
- `backtest_end_date` - Period end
- `last_updated` - When backtest was run

**Why:** Foundation for automated strategy selection. Determines which strategies trade which stocks.

**When Stored:** After each backtest run (manual or scheduled)

---

### 4. STRATEGY_STOCK_MAPPING (Configuration)
**Purpose:** Link strategies to stocks with performance scores

**Storage:** `strategy_stock_mapping` table

**Fields:**
- `id` - Unique identifier
- `strategy_name` - Strategy name
- `symbol` - Stock symbol
- `priority_score` - Ranking metric (higher = better)
- `is_enabled` - Whether this combo is active
- `last_updated` - Last modification time

**Why:** Supports multi-stock shadow mode. Allows system to track performance of strategy-stock combinations.

**When Stored:** Updated during auto-selection or manual configuration

---

### 5. ACTIVE_STRATEGY_CONFIG (State)
**Purpose:** Track which strategy is currently active for trading

**Storage:** `active_strategy_config` table

**Fields:**
- `id` - Unique identifier
- `strategy_name` - Currently selected strategy
- `symbol` - Currently trading stock
- `activated_at` - When it became active
- `reason` - Why this was selected

**Why:** Single source of truth for "what am I trading right now"

**When Stored:** Updated on strategy changes (manual or auto-select)

---

### 6. SHADOW_MODE_STOCK (Simulation)
**Purpose:** Configure additional stocks to track (without real trades)

**Storage:** `shadow_mode_stock` table

**Fields:**
- `id` - Unique identifier
- `symbol` - Stock symbol
- `strategy_name` - Strategy to simulate
- `rank_position` - Priority ranking
- `enabled` - Active or not
- `added_at` - When added
- `last_signal_at` - Last activity time

**Why:** Test multiple strategies simultaneously without capital risk.

**When Stored:** Updated during auto-selection (top 5 candidates)

---

### 7. BOT_SETTINGS (Configuration)
**Purpose:** Store key-value configuration parameters

**Storage:** `bot_settings` table

**Fields:**
- `key` - Setting name (e.g., "daily_loss_limit")
- `value` - Setting value (string)
- `description` - What it controls

**Why:** Dynamic configuration without code changes. Supports Telegram-based config updates.

**When Stored:** On startup (defaults) or via Telegram commands

---

### 8. RISK_SETTINGS (Risk Management)
**Purpose:** Centralize all risk parameters

**Storage:** `risk_settings` table

**Fields:**
- `max_shares_per_trade` - Position size limit
- `daily_loss_limit_twd` - Max daily loss
- `weekly_loss_limit_twd` - Max weekly loss
- `max_daily_trades` - Trade frequency cap
- `min_hold_minutes` - Minimum trade duration
- `max_hold_minutes` - Maximum trade duration
- `stop_loss_twd_per_trade` - Per-trade loss limit
- `enable_ai_veto` - Use Ollama for trade approval
- `enable_volatility_filter` - Block trades in high volatility
- `volatility_threshold_multiplier` - Volatility sensitivity

**Why:** Single source of truth for all risk controls. Supports Telegram configuration.

**When Stored:** On startup (defaults) or via Telegram commands

---

### 9. MARKET_DATA (Real-Time)
**Purpose:** Store market tick data for analysis

**Storage:** `market_data` table (or in-memory only - TBD)

**Fields:**
- `timestamp` - Tick time
- `symbol` - Stock/futures
- `price` - Last price
- `volume` - Tick volume
- `bid` - Best bid price
- `ask` - Best ask price
- `bid_volume` - Bid size
- `ask_volume` - Ask size

**Why:** Support strategy calculations, post-trade analysis. **NOTE:** High-frequency data may not be persisted permanently.

**When Stored:** Real-time during market hours (if persistence enabled)

---

### 10. VETO_EVENT (AI Risk Management)
**Purpose:** Log Ollama veto decisions

**Storage:** `veto_event` table

**Fields:**
- `id` - Unique identifier
- `timestamp` - When veto occurred
- `trade_proposal` - JSON of proposed trade
- `veto_reason` - Why trade was blocked
- `system_state` - JSON snapshot (P&L, drawdown, etc.)
- `news_headlines` - Headlines at time of decision
- `ai_response` - Raw Ollama output

**Why:** Audit trail for AI risk manager. Debug why trades were blocked.

**When Stored:** Every time Ollama rejects a trade

---

### 11. LLM_INSIGHT (AI Analytics)
**Purpose:** Store all LLM-generated analysis

**Storage:** `llm_insight` table

**Fields:**
- `id` - Unique identifier
- `timestamp` - When generated
- `insight_type` - NEWS_IMPACT_SCORING, VETO_RATIONALE, etc.
- `source` - Which service requested it
- `symbol` - Related stock (if any)
- `prompt` - Input sent to LLM
- `response_json` - Structured LLM output
- `confidence_score` - LLM confidence (0-1)
- `processing_time_ms` - How long it took
- `success` - Whether call succeeded
- `error_message` - If failed

**Why:** Track LLM usage, debug failures, analyze LLM reliability.

**When Stored:** Every LLM API call

---

### 12. EARNINGS_BLACKOUT_DATE (Compliance)
**Purpose:** Store upcoming earnings announcement dates

**Storage:** `earnings_blackout_date` table

**Fields:**
- `id` - Unique identifier
- `symbol` - Stock symbol
- `earnings_date` - Announcement date
- `fetched_at` - When data was scraped

**Why:** Prevent trading near earnings (high risk). Required for risk compliance.

**When Stored:** On application startup (scraped from Yahoo Finance)

---

### 13. EARNINGS_BLACKOUT_META (Audit)
**Purpose:** Track when earnings data was refreshed

**Storage:** `earnings_blackout_meta` table

**Fields:**
- `id` - Unique identifier
- `last_refresh` - Last successful refresh time
- `source` - Data source (e.g., "yahoo-finance")
- `status` - SUCCESS or FAILED
- `error_message` - If failed

**Why:** Monitor data freshness, alert if stale.

**When Stored:** After each earnings refresh attempt

---

### 14. ECONOMIC_NEWS (REMOVED - Not Implemented)
**Removed:** 2025-12-18
**Reason:** Not implemented, no concrete plan. Will be recreated if/when news sentiment analysis is implemented.

---

### 15. AGENT_INTERACTION (Multi-Agent)
**Purpose:** Log interactions between AI agents

**Storage:** `agent_interaction` table

**Fields:**
- `id` - Unique identifier
- `timestamp` - When interaction occurred
- `from_agent` - Sender agent name
- `to_agent` - Receiver agent name
- `message_type` - REQUEST, RESPONSE, etc.
- `payload` - JSON message content

**Why:** Future multi-agent collaboration audit trail. Currently unused but reserved.

**When Stored:** When agents communicate (not yet implemented)

---

### 16. EVENT (Generic Log)
**Purpose:** General system event log

**Storage:** `event` table

**Fields:**
- `id` - Unique identifier
- `timestamp` - When event occurred
- `event_type` - STRATEGY_SWITCH, RISK_LIMIT_HIT, etc.
- `message` - Human-readable description
- `metadata` - JSON details

**Why:** Catch-all for important events not covered by specific entities.

**When Stored:** On significant system events

---

### 17. SIGNAL (Strategy Output)
**Purpose:** Store raw strategy signals before execution

**Storage:** `signal` table

**Fields:**
- `id` - Unique identifier
- `timestamp` - When signal generated
- `strategy_name` - Source strategy
- `symbol` - Target stock
- `direction` - LONG, SHORT, or NEUTRAL
- `confidence` - Signal strength (0-1)
- `reason` - Why signal was generated
- `executed` - Whether trade was placed
- `veto_reason` - If blocked, why

**Why:** Separate signal generation from execution. Allows post-analysis of "what did strategies want to do vs. what was allowed".

**When Stored:** Every strategy signal generation

---

### 18. QUOTE (REMOVED - Redundant)
**Removed:** 2025-12-18
**Reason:** Redundant with Bar entity which already stores OHLCV data. Quote provided no additional value.

---

### 19. BAR (OHLCV Data)
**Purpose:** Store aggregated bar/candlestick data

**Storage:** `bar` table

**Fields:**
- `id` - Unique identifier
- `timestamp` - Bar start time
- `symbol` - Stock/futures
- `interval` - 1m, 5m, 15m, 1h, 1d
- `open` - Open price
- `high` - High price
- `low` - Low price
- `close` - Close price
- `volume` - Volume

**Why:** Strategy calculations need OHLCV bars. Used in backtesting.

**When Stored:** Aggregated from tick data or fetched from data provider

---

### 20. STOCK_SETTINGS (Configuration)
**Purpose:** Per-stock trading parameters

**Storage:** `stock_settings` table

**Fields:**
- `symbol` - Stock symbol
- `enabled` - Whether trading is allowed
- `max_position_size` - Position limit for this stock
- `special_rules` - JSON for stock-specific logic

**Why:** Some stocks require special handling (liquidity, regulations).

**When Stored:** Manual configuration or auto-detected

---

### 21. MARKET_CONFIG (REMOVED - Premature)
**Removed:** 2025-12-18
**Reason:** Multi-market support not needed. System is Taiwan-only. Will be recreated if multi-market support is added.

---

## DATA LIFECYCLE

### REAL-TIME (During Trading)
1. **Market Data** → Received from Shioaji
2. **Signal** → Strategy generates signal
3. **VetoEvent** → Ollama evaluates (if enabled)
4. **Trade** → If approved, execute and log
5. **Event** → Log major system events

### END-OF-DAY
1. **DailyStatistics** → Aggregate today's trades
2. **StrategyPerformance** → Update backtest results (if scheduled)

### ON-DEMAND
1. **EarningsBlackoutDate** → Refresh earnings calendar
2. **EconomicNews** → Scrape news (if implemented)

---

## REMOVED ENTITIES
**Removed 2025-12-18 during entity audit (#8):**
- `EconomicNews` - News sentiment analysis not implemented
- `MarketConfig` - Multi-market support not needed (Taiwan-only)
- `Quote` - Redundant with Bar entity

## UNUSED / RESERVED ENTITIES
**As of 2025-12-18:**
- `AgentInteraction` - Reserved for future multi-agent system (not yet used)
- `VetoEvent` - Reserved for Ollama veto logging (not yet integrated into trade flow)

---

## DATABASE MAINTENANCE

### Retention Policies
- **Trades:** Keep forever (required for tax reporting)
- **DailyStatistics:** Keep forever (long-term analysis)
- **MarketData/Quote/Bar:** Keep 2 years (storage limits)
- **LlmInsight:** Keep 90 days (debug purposes)
- **VetoEvent:** Keep 90 days (audit trail)
- **Event:** Keep 90 days (system logs)
- **Signal:** Keep 30 days (short-term analysis)

### Backup Strategy
- Daily PostgreSQL dump
- Weekly full backup
- Critical tables (Trade, DailyStatistics) → replicate to separate system

---

## ADDING NEW DATA

**Before adding any new entity or field:**
1. **Justify its existence** - What problem does it solve?
2. **Document storage location** - Which table/column?
3. **Define lifecycle** - When is it created? Updated? Deleted?
4. **Plan retention** - How long to keep it?
5. **Update this document**

**No data is stored "just in case" or "might be useful later".**

---

## NOTES

- All timestamps use `Asia/Taipei` timezone
- All P&L values are in Taiwan Dollars (TWD)
- Foreign key relationships enforce referential integrity
- Indexes exist on frequently-queried fields (symbol, timestamp, strategy_name)

---

Last Updated: 2025-12-18
