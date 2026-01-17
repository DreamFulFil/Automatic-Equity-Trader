package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class StrategySmokeTest {

    private final Portfolio portfolio;

    StrategySmokeTest() {
        Map<String, Integer> positions = new HashMap<>();
        positions.put("TEST", 0);
        portfolio = Portfolio.builder().positions(positions).equity(100000.0).tradingMode("stock").tradingQuantity(1).build();
    }

    @Test
    void smokeTestAllStrategiesInstantiateAndExecute() throws Exception {
        List<Class<? extends IStrategy>> classes = List.of(
                ADXTrendStrategy.class,
                ATRChannelStrategy.class,
                AutomaticRebalancingStrategy.class,
                BollingerBandStrategy.class,
                CrossSectionalMomentumStrategy.class,
                DCAStrategy.class,
                DividendReinvestmentStrategy.class,
                DonchianChannelStrategy.class,
                DPOStrategy.class,
                EnvelopeStrategy.class,
                MeanReversionStrategy.class,
                MomentumTradingStrategy.class,
                MovingAverageCrossoverStrategy.class,
                OnBalanceVolumeStrategy.class,
                PriceActionStrategy.class,
                StochasticStrategy.class,
                StandardDeviationStrategy.class,
                TaxLossHarvestingStrategy.class,
                TimeSeriesMomentumStrategy.class,
                VWAPExecutionStrategy.class,
                VolumeProfileStrategy.class,
                VolumeWeightedStrategy.class
        );

        MarketData md = MarketData.builder().symbol("TEST").high(101).low(99).close(100).volume(100L).build();

        for (Class<? extends IStrategy> cls : classes) {
            IStrategy strat = instantiateStrategy(cls);
            assertNotNull(strat.getName());
            assertNotNull(strat.getType());
            // execute should not throw
            assertDoesNotThrow(() -> {
                TradeSignal s = strat.execute(portfolio, md);
                assertNotNull(s);
            }, "Strategy failed: " + cls.getSimpleName());
            assertDoesNotThrow(strat::reset, "Reset failed: " + cls.getSimpleName());
        }
    }

    private IStrategy instantiateStrategy(Class<? extends IStrategy> cls) throws Exception {
        // Try no-arg constructor
        try {
            Constructor<? extends IStrategy> c = cls.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException ignored) {}

        // Try common constructors
        try {
            Constructor<? extends IStrategy> c = cls.getDeclaredConstructor(int.class, double.class);
            c.setAccessible(true);
            return c.newInstance(20, 2.0);
        } catch (NoSuchMethodException ignored) {}

        try {
            Constructor<? extends IStrategy> c = cls.getDeclaredConstructor(int.class);
            c.setAccessible(true);
            return c.newInstance(20);
        } catch (NoSuchMethodException ignored) {}

        try {
            Constructor<? extends IStrategy> c = cls.getDeclaredConstructor(double.class, int.class);
            c.setAccessible(true);
            return c.newInstance(0.05, 5);
        } catch (NoSuchMethodException ignored) {}

        // Specific known constructors
        if (cls.equals(VolumeProfileStrategy.class)) {
            return new VolumeProfileStrategy(5, 0.7);
        }
        if (cls.equals(ATRChannelStrategy.class)) {
            return new ATRChannelStrategy(5, 1.0);
        }
        if (cls.equals(ADXTrendStrategy.class)) {
            return new ADXTrendStrategy(14, 25.0);
        }
        if (cls.equals(CrossSectionalMomentumStrategy.class)) {
            return new CrossSectionalMomentumStrategy(5, 20);
        }

        // Last resort: try default constructor via reflection
        Constructor<?> c = cls.getDeclaredConstructors()[0];
        c.setAccessible(true);
        // create with default params if primitive args
        Object[] args = new Object[c.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> p = c.getParameterTypes()[i];
            if (p == int.class) args[i] = 5;
            else if (p == double.class) args[i] = 1.0;
            else if (p == long.class) args[i] = 1L;
            else args[i] = null;
        }
        return (IStrategy) c.newInstance(args);
    }
}
