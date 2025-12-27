package tw.gc.auto.equity.trader.strategy.factory;

import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.StrategyFactory;
import tw.gc.auto.equity.trader.strategy.impl.*;
import tw.gc.auto.equity.trader.strategy.impl.library.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating strategies optimized for Futures trading (MTXF).
 * Generally uses faster settings, tighter thresholds, and momentum focus.
 */
public class FuturesStrategyFactory implements StrategyFactory {

    @Override
    public List<IStrategy> createStrategies() {
        List<IStrategy> strategies = new ArrayList<>();

        // 1. Moving Average Crossovers (Trend) - Faster for futures
        strategies.add(new MovingAverageCrossoverStrategy(3, 8, 0.0005)); // Faster
        strategies.add(new MovingAverageCrossoverStrategy(5, 15, 0.001));

        // 2. RSI Strategies (Mean Reversion) - More aggressive
        strategies.add(new RSIStrategy(7, 80, 20));  // Aggressive
        strategies.add(new RSIStrategy(14, 75, 25)); // Slightly more aggressive than stock

        // 3. MACD Strategies (Momentum)
        strategies.add(new MACDStrategy(5, 35, 5));  // Custom fast MACD

        // 4. Bollinger Bands (Volatility)
        strategies.add(new BollingerBandStrategy()); // Default 20, 2.0

        // 5. Stochastic (Momentum)
        strategies.add(new StochasticStrategy(5, 3, 90, 10)); // Fast

        // 6. ATR Channels (Breakout) - Tighter channel
        strategies.add(new ATRChannelStrategy(14, 1.5));

        // 7. Pivot Points (Support/Resistance)
        strategies.add(new PivotPointStrategy());

        // 8. DCA (Investment) - NOT typically for futures, but maybe for long-term holding?
        // Skipping DCA for futures as it's risky

        // 9. Rebalancing
        strategies.add(new AutomaticRebalancingStrategy(5));

        // 10. Momentum - Lower threshold for futures to catch small moves
        strategies.add(new MomentumTradingStrategy(0.005)); // 0.5% vs 2% for stocks

        return strategies;
    }
}
