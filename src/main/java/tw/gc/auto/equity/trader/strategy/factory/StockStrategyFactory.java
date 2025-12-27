package tw.gc.auto.equity.trader.strategy.factory;

import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.StrategyFactory;
import tw.gc.auto.equity.trader.strategy.impl.*;
import tw.gc.auto.equity.trader.strategy.impl.library.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating strategies optimized for Stock trading (2454.TW).
 * Generally uses standard settings and lower leverage/risk.
 */
public class StockStrategyFactory implements StrategyFactory {

    @Override
    public List<IStrategy> createStrategies() {
        List<IStrategy> strategies = new ArrayList<>();

        // 1. Moving Average Crossovers (Trend) - Standard settings for stocks
        strategies.add(new MovingAverageCrossoverStrategy(5, 10, 0.001));
        strategies.add(new MovingAverageCrossoverStrategy(10, 20, 0.001));
        strategies.add(new MovingAverageCrossoverStrategy(20, 50, 0.002));
        strategies.add(new MovingAverageCrossoverStrategy(50, 200, 0.005)); // Golden Cross

        // 2. RSI Strategies (Mean Reversion)
        strategies.add(new RSIStrategy(14, 70, 30)); // Standard
        strategies.add(new RSIStrategy(21, 65, 35)); // Conservative

        // 3. MACD Strategies (Momentum)
        strategies.add(new MACDStrategy(12, 26, 9)); // Standard

        // 4. Bollinger Bands (Volatility)
        strategies.add(new BollingerBandStrategy()); // Default 20, 2.0

        // 5. Stochastic (Momentum)
        strategies.add(new StochasticStrategy(14, 3, 80, 20));

        // 6. ATR Channels (Breakout)
        strategies.add(new ATRChannelStrategy(20, 2.0));

        // 7. Pivot Points (Support/Resistance)
        strategies.add(new PivotPointStrategy());

        // 8. DCA (Investment) - Good for stocks
        strategies.add(new DCAStrategy());

        // 9. Rebalancing
        strategies.add(new AutomaticRebalancingStrategy(5));

        // 10. Momentum - Higher threshold for stocks to avoid noise
        strategies.add(new MomentumTradingStrategy(0.02));

        return strategies;
    }
}
