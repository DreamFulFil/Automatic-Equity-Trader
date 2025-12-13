package tw.gc.auto.equity.trader.strategy;

import java.util.List;

/**
 * Abstract Factory for creating trading strategies.
 * Allows creating different families of strategies for different markets (Stock vs Futures).
 */
public interface StrategyFactory {
    
    /**
     * Create a list of strategies configured for the specific market.
     * @return List of configured strategies
     */
    List<IStrategy> createStrategies();
}
