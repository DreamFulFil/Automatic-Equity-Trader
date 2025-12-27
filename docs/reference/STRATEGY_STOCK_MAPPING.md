# Strategy Stock Mapping (Auto-population)

This document explains the automatic population of the `strategy_stock_mapping` table from backtest runs.

## Background

The system persists detailed `BacktestResult` records after each backtest run. To keep a canonical, queryable view of the best strategy/stock combinations for UI, reporting, and auto-selection routines, the service now automatically upserts mappings into the `strategy_stock_mapping` table.

## Behavior

- After each backtest result is persisted, the system calls `StrategyStockMappingService.updateMapping(...)` to create or update a `StrategyStockMapping` for the `(symbol, strategy)` pair.
- The mapping stores metrics such as Sharpe ratio, total return, win rate, max drawdown, total trades, average profit per trade and the backtest period.
- This ensures `strategy_stock_mapping` is kept in sync with the latest backtest results and can be relied upon by automation (e.g., `AutoStrategySelector`).

## Notes

- The auto-population is implemented in `BacktestService.persistBacktestResult(...)`.
- If the mapping update fails, the error is logged at DEBUG level and backtest persistence is not rolled back (non-fatal).

## Why this change

- Downstream features (shadow-mode selection and active/shadow table population) expect a reliable, up-to-date `strategy_stock_mapping` table.
- Centralizing the update immediately after persistence reduces the risk of stale or missing entries.

