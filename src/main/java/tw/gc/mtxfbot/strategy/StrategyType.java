package tw.gc.mtxfbot.strategy;

/**
 * Strategy type classification
 */
public enum StrategyType {
    /**
     * Long-term strategies (rebalancing, DCA, DRIP, tax-loss harvesting)
     * Typically hold positions for weeks/months
     */
    LONG_TERM,
    
    /**
     * Short-term strategies (MA crossover, Bollinger bands, momentum, arbitrage, etc.)
     * Typically hold positions for minutes/hours/days
     */
    SHORT_TERM
}
