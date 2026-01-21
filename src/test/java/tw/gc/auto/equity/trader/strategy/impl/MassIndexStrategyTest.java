package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MassIndexStrategyTest {

    private MassIndexStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new MassIndexStrategy();
        portfolio = new Portfolio();
    }

    @Test
    void warmingUpProducesNeutral() {
        for (int i = 0; i < 10; i++) {
            TradeSignal s = strategy.execute(portfolio, md("M", 100.0, 100.5, 100.0, i));
            assertNotNull(s);
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        }
    }

    @Test
    void triggersBulgeAndCompletionPaths() {
        boolean sawLong = false;
        boolean sawExit = false;

        // Keep MI < 27 for a while.
        for (int i = 0; i < 80; i++) {
            strategy.execute(portfolio, md("A", 100.0, 100.5, 100.0, i));
        }

        // Increase range to push MI > 27 (crossing from below).
        for (int i = 80; i < 140; i++) {
            TradeSignal s = strategy.execute(portfolio, md("A", 100.0, 120.0, 80.0, i));
            if (s.getDirection() == TradeSignal.SignalDirection.LONG) {
                sawLong = true;
                break;
            }
        }
        assertTrue(sawLong);

        portfolio.setPosition("A", 1);

        // Reduce range to push MI back below 26.5 (crossing from above).
        // The crossover happens fairly quickly after switching back to small ranges.
        for (int i = 94; i < 140; i++) {
            TradeSignal s = strategy.execute(portfolio, md("A", 100.0, 100.5, 100.0, i));
            if (s.isExitSignal()) {
                sawExit = true;
                break;
            }
        }

        assertTrue(sawExit);
    }

    @Test
    void coversZeroEma9of9Branch_andGettersReset() {
        // range=0 forces ema9=0 and ema9of9=0 during warmup, covering the ratio ternary true-branch.
        for (int i = 0; i < 40; i++) {
            strategy.execute(portfolio, md("Z", 100.0, 100.0, 100.0, i));
        }

        assertEquals("Mass Index", strategy.getName());
        assertNotNull(strategy.getType());

        strategy.reset();
        TradeSignal s = strategy.execute(portfolio, md("Z", 100.0, 100.0, 100.0, 100));
        assertNotNull(s);
    }

    private static MarketData md(String symbol, double close, double high, double low, int minutes) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setClose(close);
        data.setHigh(high);
        data.setLow(low);
        data.setOpen(close);
        data.setVolume(1000L);
        data.setTimestamp(LocalDateTime.of(2024, 1, 1, 9, 0).plusMinutes(minutes));
        return data;
    }
}

