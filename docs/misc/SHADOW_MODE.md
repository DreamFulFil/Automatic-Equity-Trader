# Shadow Mode Documentation

## Overview

Shadow Mode allows the trading system to run multiple strategies concurrently in virtual/simulation mode while only one strategy executes real trades. This enables:

1. **Strategy Comparison**: Compare performance of multiple strategies side-by-side
2. **Risk-Free Testing**: Test new strategies without risking capital
3. **Performance Analytics**: Gather data on strategy effectiveness over time

## How Shadow Mode Works

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Trading Engine                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │            Strategy Manager                            │  │
│  │                                                         │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │ Strategy 1  │  │ Strategy 2  │  │ Strategy 3  │  │  │
│  │  │  (ACTIVE)   │  │  (SHADOW)   │  │  (SHADOW)   │  │  │
│  │  │   REAL $$$  │  │  VIRTUAL    │  │  VIRTUAL    │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  │         │                │                │            │  │
│  │         ▼                ▼                ▼            │  │
│  │    Order API      Shadow Trade      Shadow Trade      │  │
│  │    (Shioaji)         Logger            Logger         │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Data Isolation

#### Shadow Mode
- **Database**: Stores trades with `mode = SIMULATION` and `strategyName` field
- **Portfolio**: Each strategy maintains independent virtual portfolio (80k TWD)
- **Positions**: Tracked separately per strategy in memory
- **P&L**: Calculated based on virtual entry/exit prices
- **Table**: Uses `trades` table with strategy differentiation

#### Backtesting
- **Database**: Uses separate backtesting-specific queries/filters
- **Data Source**: Historical market data (not live data)
- **Execution**: Runs on demand, not during live trading
- **Table**: Uses same `trades` table but with different context
- **Time Range**: Operates on past data ranges

### No Conflicts

Shadow mode and backtesting do NOT conflict because:

1. **Different Time Windows**: Shadow runs during live trading hours, backtesting runs on historical data
2. **Clear Tagging**: All trades tagged with `strategyName` and `mode` (SIMULATION/LIVE)
3. **Separate Portfolios**: Shadow strategies use in-memory portfolios, backtesting creates temporary portfolios
4. **Query Isolation**: Queries filter by mode and time range to avoid mixing data

## Implementation

### Strategy Execution Flow

```java
public void executeStrategies(MarketData marketData, double currentPrice) {
    for (IStrategy strategy : activeStrategies) {
        Portfolio p = strategyPortfolios.get(strategy.getName());
        TradeSignal signal = strategy.execute(p, marketData);
        
        if (signal.getDirection() != TradeSignal.SignalDirection.NEUTRAL) {
            // Execute Shadow Trade (Virtual)
            executeShadowTrade(strategy.getName(), p, signal, currentPrice);
            
            // Execute REAL Trade if this is the selected active strategy
            if (strategy.getName().equalsIgnoreCase(activeStrategyName)) {
                executeRealStrategyTrade(signal, currentPrice);
            }
        }
    }
}
```

### Shadow Trade Logging

```java
private void logShadowTrade(String strategyName, String action, int qty, 
                            double price, Double pnl, String reason) {
    Trade trade = Trade.builder()
        .timestamp(LocalDateTime.now(AppConstants.TAIPEI_ZONE))
        .action(action.contains("BUY") ? Trade.TradeAction.BUY : Trade.TradeAction.SELL)
        .quantity(qty)
        .entryPrice(price)
        .symbol(getActiveSymbol())
        .strategyName(strategyName) // KEY: Strategy identification
        .reason(reason)
        .mode(Trade.TradingMode.SIMULATION) // KEY: Always SIMULATION for shadow
        .status(Trade.TradeStatus.CLOSED)
        .realizedPnL(pnl)
        .build();
        
    dataLoggingService.logTrade(trade);
    log.info("👻 Shadow Trade [{}]: {} {} @ {} (PnL: {})", 
        strategyName, action, qty, price, pnl);
}
```

## Querying Shadow Mode Data

### Get Shadow Mode Trades for Specific Strategy

```sql
SELECT * FROM trades 
WHERE strategy_name = 'MovingAverageCrossover' 
  AND mode = 'SIMULATION'
  AND timestamp >= '2025-12-13'
ORDER BY timestamp DESC;
```

### Get Shadow Mode Performance Summary

```sql
SELECT 
    strategy_name,
    COUNT(*) as trade_count,
    SUM(realized_pnl) as total_pnl,
    AVG(realized_pnl) as avg_pnl,
    SUM(CASE WHEN realized_pnl > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as win_rate
FROM trades
WHERE mode = 'SIMULATION'
  AND strategy_name IS NOT NULL
  AND timestamp >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY strategy_name
ORDER BY total_pnl DESC;
```

### Exclude Shadow Mode from Live Analysis

```sql
SELECT * FROM trades
WHERE mode = 'LIVE'  -- Excludes all shadow mode trades
  AND timestamp >= '2025-12-01'
ORDER BY timestamp DESC;
```

## Telegram Notifications

Shadow mode trades are logged internally but do NOT trigger Telegram notifications to avoid spam. Only the active strategy's trades send notifications.

```java
// Active strategy - sends Telegram
executeRealStrategyTrade(signal, currentPrice);  // → Telegram notification

// Shadow strategy - no Telegram
executeShadowTrade(strategyName, p, signal, currentPrice);  // → Log only
```

## Performance Considerations

- **Memory**: Each shadow strategy maintains a Portfolio object (~1KB)
- **CPU**: Minimal overhead, strategies execute in <10ms
- **Database**: One insert per shadow trade (async, non-blocking)
- **Network**: No API calls for shadow trades

## Future Enhancements

1. **Daily Shadow Reports**: Automated daily performance comparison reports
2. **Strategy Switching**: Auto-switch to best-performing shadow strategy
3. **Risk Metrics**: Real-time Sharpe ratio, max drawdown for shadow strategies
4. **Visual Dashboard**: Web UI showing shadow strategy performance

## Related Documentation

- [Backtesting Guide](./tests/BACKTESTING.md)
- [Strategy Implementation](./STRATEGY_GUIDE.md)
- [Database Schema](./DATABASE_SCHEMA.md)
