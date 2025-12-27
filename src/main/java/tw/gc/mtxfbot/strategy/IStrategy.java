package tw.gc.mtxfbot.strategy;

import tw.gc.mtxfbot.entities.MarketData;

/**
 * Strategy Pattern Interface for Trading Strategies
 * 
 * All trading strategies must implement this interface to be runtime-swappable
 * within the TradingEngine context. Strategies are completely independent and
 * have no control over engine flow - they only provide trading decisions.
 * 
 * Design Principles:
 * - Pure strategy logic (no engine control)
 * - Stateless or internally managed state
 * - All external dependencies must be mockable
 * - Structured logging: TRACE for decisions, INFO for lifecycle, DEBUG for diagnostics
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
public interface IStrategy {
    
    /**
     * Execute trading strategy logic based on current portfolio state and market data.
     * 
     * Strategy implementations should:
     * 1. Analyze market data and portfolio state
     * 2. Make trading decisions (BUY/SELL/HOLD)
     * 3. Return trading signal/direction
     * 4. NOT directly execute orders (engine handles execution)
     * 5. NOT control engine lifecycle
     * 
     * @param portfolio Current portfolio state (positions, equity, P&L)
     * @param data Real-time market data (price, volume, indicators)
     * @return TradeSignal indicating recommended action (LONG/SHORT/NEUTRAL)
     */
    TradeSignal execute(Portfolio portfolio, MarketData data);
    
    /**
     * Get strategy name for identification and logging
     * 
     * @return Human-readable strategy name (e.g., "Dollar-Cost Averaging", "Moving Average Crossover")
     */
    String getName();
    
    /**
     * Get strategy type classification
     * 
     * @return Strategy type (LONG_TERM or SHORT_TERM)
     */
    StrategyType getType();
    
    /**
     * Reset strategy state (if stateful)
     * Called when strategy is swapped or trading day ends
     */
    default void reset() {
        // Default: no-op for stateless strategies
    }
}
