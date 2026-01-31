package tw.gc.auto.equity.trader.entities;

/**
 * Strategy Lifecycle Status Enum
 * 
 * Phase 6: Strategy Performance Culling
 * Lifecycle: CANDIDATE → PAPER_TRADING → SHADOW_MODE → LIVE → RETIRED
 */
public enum StrategyLifecycleStatus {
    /**
     * New strategy, not yet evaluated
     */
    CANDIDATE,
    
    /**
     * Paper trading only (no real money)
     * Requires 30+ trades and 60 days minimum
     */
    PAPER_TRADING,
    
    /**
     * Shadow mode (parallel simulation with live market data)
     * Final validation before live trading
     */
    SHADOW_MODE,
    
    /**
     * Active live trading with real capital
     * Maximum 5 strategies simultaneously
     */
    LIVE,
    
    /**
     * Retired due to poor performance
     * Negative Sharpe or consistently below threshold
     */
    RETIRED
}
