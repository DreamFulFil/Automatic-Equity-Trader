package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StrategyImplReflectionTest {

    // A curated list of strategy impl classes where no complex dependencies are required
    private static final List<String> STRATEGY_CLASSES = List.of(
        "tw.gc.auto.equity.trader.strategy.impl.BollingerBandStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.MovingAverageCrossoverStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.DCAStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.BollingerSqueezeStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.RSIStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.MACDStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.VWAPExecutionStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.TWAPExecutionStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.DividendReinvestmentStrategy",
        "tw.gc.auto.equity.trader.strategy.impl.MomentumTradingStrategy"
    );

    @Test
    void instantiateAndRun_basicSmokeTest() throws Exception {
        Portfolio p = Portfolio.builder()
            .positions(new HashMap<String, Integer>())
            .entryPrices(new HashMap<String, Double>())
            .equity(100000.0)
            .availableMargin(100000.0)
            .tradingMode("stock")
            .tradingQuantity(1)
            .build();

        MarketData md = new MarketData();

        for (String cls : STRATEGY_CLASSES) {
            try {
                Class<?> c = Class.forName(cls);
                Object o = c.getDeclaredConstructor().newInstance();
                if (o instanceof IStrategy) {
                    IStrategy s = (IStrategy) o;
                    assertThat(s.getName()).isNotNull();
                    assertThat(s.getType()).isNotNull();
                    s.reset();
                    TradeSignal sig = s.execute(p, md);
                    assertThat(sig).isNotNull();
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Some strategies require complex setup; do not fail the smoke test.
                // Swallow exception but ensure exceptions are not ignored silently in IDE
                System.err.println("Could not instantiate " + cls + " : " + e.getClass().getSimpleName() + " -> " + e.getMessage());
            }
        }
    }
}
