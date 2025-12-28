package tw.gc.auto.equity.trader.strategy.factory;

import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.StrategyFactory;
import tw.gc.auto.equity.trader.strategy.impl.*;
import tw.gc.auto.equity.trader.strategy.impl.*;

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

        // Original strategies
        strategies.add(new MovingAverageCrossoverStrategy(5, 10, 0.001));
        strategies.add(new MovingAverageCrossoverStrategy(10, 20, 0.001));
        strategies.add(new MovingAverageCrossoverStrategy(20, 50, 0.002));
        strategies.add(new MovingAverageCrossoverStrategy(50, 200, 0.005)); // Golden Cross
        strategies.add(new RSIStrategy(14, 70, 30));
        strategies.add(new RSIStrategy(21, 65, 35));
        strategies.add(new MACDStrategy(12, 26, 9));
        strategies.add(new BollingerBandStrategy());
        strategies.add(new StochasticStrategy(14, 3, 80, 20));
        strategies.add(new ATRChannelStrategy(20, 2.0));
        strategies.add(new PivotPointStrategy());
        strategies.add(new DCAStrategy());
        strategies.add(new AutomaticRebalancingStrategy(5));
        strategies.add(new MomentumTradingStrategy(0.02));
        strategies.add(new AggressiveRSIStrategy());
        strategies.add(new ArbitragePairsTradingStrategy());
        strategies.add(new DividendReinvestmentStrategy());
        strategies.add(new NewsSentimentStrategy());
        strategies.add(new TWAPExecutionStrategy(100, 30));
        strategies.add(new TaxLossHarvestingStrategy());
        strategies.add(new VWAPExecutionStrategy(100, 30, 0.003, 1));
        
        // New strategies (33 more) - ALL included for shadow mode
        strategies.add(new KeltnerChannelStrategy());
        strategies.add(new IchimokuCloudStrategy());
        strategies.add(new ParabolicSARStrategy());
        strategies.add(new ADXTrendStrategy());
        strategies.add(new WilliamsRStrategy());
        strategies.add(new CCIStrategy());
        strategies.add(new VolumeWeightedStrategy());
        strategies.add(new TripleEMAStrategy());
        strategies.add(new FibonacciRetracementStrategy());
        strategies.add(new DonchianChannelStrategy());
        strategies.add(new SupertrendStrategy());
        strategies.add(new HullMovingAverageStrategy());
        strategies.add(new ChaikinMoneyFlowStrategy());
        strategies.add(new VortexIndicatorStrategy());
        strategies.add(new AroonOscillatorStrategy());
        strategies.add(new ElderRayStrategy());
        strategies.add(new KaufmanAdaptiveMAStrategy());
        strategies.add(new ZigZagStrategy());
        strategies.add(new PriceActionStrategy());
        strategies.add(new BalanceOfPowerStrategy());
        strategies.add(new KlingerOscillatorStrategy());
        strategies.add(new UltimateOscillatorStrategy());
        strategies.add(new MassIndexStrategy());
        strategies.add(new TrixStrategy());
        strategies.add(new RelativeVigorIndexStrategy());
        strategies.add(new DPOStrategy());
        strategies.add(new ForceIndexStrategy());
        strategies.add(new EnvelopeStrategy());
        strategies.add(new PriceVolumeRankStrategy());
        strategies.add(new AccumulationDistributionStrategy());
        strategies.add(new PriceRateOfChangeStrategy());
        strategies.add(new StandardDeviationStrategy());
        strategies.add(new LinearRegressionStrategy());
        // New strategy to bring total to 100
        strategies.add(new OnBalanceVolumeStrategy(20, 0.0));

        return strategies;
    }
}
