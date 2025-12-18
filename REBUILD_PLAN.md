# FULL SYSTEM REBUILD PLAN
## Started: 2025-12-18

This document tracks the complete system rebuild mandate.

## CRITICAL FIXES (Immediate)
- [x] #10: Remove all 08:30 schedules → Change to startup-only or explicit
- [x] #12: Fix Telegram shadow mode double-send bug (likely fixed by disabling @Scheduled)
- [x] #13: Earnings scraping → Run on startup, NOT scheduled

## DATABASE & ENTITIES (Foundation)
- [ ] #1: Database reset with intentional schema design
- [x] #7: Create DATA_STORE_WHILE_TRADE_TUTORIAL.md
- [x] #8: Entity/field justification - remove unused (removed EconomicNews, MarketConfig, Quote)
- [x] #14: Implement Testcontainers for PostgreSQL

## TAIWAN COMPLIANCE (Critical)
- [x] #2: Taiwan compliance service created
- [x] #2: Block day-trading with odd lots until capital ≥ 2M TWD
- [x] #2: Expose bank balance via Python FastAPI (Shioaji) - endpoint exists
- [x] #2: Java retrieves balance via REST - TaiwanStockComplianceService.fetchCurrentCapital()
- [x] #2: Integrate compliance checks into order execution flow

## STRATEGY IMPLEMENTATION (Massive)
- [ ] #3: Implement exactly 100 strategies
- [ ] #3: Focus on academically/professionally validated strategies
- [ ] #3: Document each strategy clearly

## DATA & BACKTESTING (Large)
- [ ] #4: Re-download all historical data for Taiwan stocks
- [ ] #4: Justify stock selection
- [ ] #4: Delegate to Python FastAPI if unreliable
- [ ] #5: Run combinatorial backtests (all strategies × all history)
- [ ] #5: Ensure deterministic, reproducible results
- [ ] #9: Run statistical analysis on backtest results

## RISK CONFIGURATION (Critical)
- [ ] #17: Centralize all risk parameters in one place
- [ ] #17: Make Telegram-configurable
- [ ] #17: Parameters:
  - max_shares_per_trade = 50
  - daily_loss_limit_twd = 1000
  - weekly_loss_limit_twd = 4000
  - max_daily_trades = 2
  - min_hold_minutes = 15
  - max_hold_minutes = 45
  - stop_loss_twd_per_trade = 800
  - min_sharpe_ratio = 1.5
  - min_win_rate = 0.55
  - max_drawdown_percent = 15
  - strategy_backtest_days = 730
  - min_total_trades_in_backtest = 150
  - enable_ai_veto = true
  - enable_volatility_filter = true
  - volatility_threshold_multiplier = 1.8

## OLLAMA INTEGRATION (DONE)
- [x] #15: System prompt implemented (Python + Java)
- [x] #16: User prompt format structure ready (LlmService.executeTradeVeto)
- [ ] #16: Integrate into actual trade execution flow

## RISK CONFIGURATION (DONE)
- [x] #17: All 17 risk parameters centralized in RiskSettings entity
- [x] #17: Telegram commands for runtime configuration (/risk command implemented)

## DOCUMENTATION (Large)
- [ ] #18: Update all markdown files
- [ ] #18: Remove obsolete documentation
- [ ] #18: Add missing documentation
- [ ] #18: File names in UPPER CASE
- [ ] #18: Clear categorization

## FUTURES (Deferred)
- [ ] #11: Keep interfaces only for Taiwan futures
- [ ] #11: No functional implementation yet

## VALIDATION
- [ ] #10: Front-testing works reliably (bug was fixed)
- [ ] All tests pass
- [ ] System compiles
- [ ] Documentation complete

---

## EXECUTION ORDER
1. Fix critical bugs (#10, #12, #13)
2. Create data documentation (#7)
3. Entity audit (#8)
4. Centralize risk config (#17)
5. Taiwan compliance (#2)
6. Testcontainers (#14)
7. Database reset (#1)
8. 100 strategies (#3)
9. Historical data + backtesting (#4, #5, #9)
10. Documentation overhaul (#18)
11. Ollama user prompt (#16)
12. Final validation

## GUIDING PRINCIPLES
- Conservative, boring, explainable
- Everything earns its right to exist
- No silent failures
- No unused code/data
- Auditable at all times
