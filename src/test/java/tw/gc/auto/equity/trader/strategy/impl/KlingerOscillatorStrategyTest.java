package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class KlingerOscillatorStrategyTest {

    @Test
    void trendChangeBranch_isExecuted() {
        KlingerOscillatorStrategy strategy = new KlingerOscillatorStrategy();
        Portfolio portfolio = Portfolio.builder().positions(new HashMap<>()).build();

        // Initialize
        strategy.execute(portfolio,
            md("TEST", 100, 101, 99, LocalDateTime.of(2024, 1, 1, 9, 0)));

        // Force hlc to go down -> trend -1 (covers currentTrend false-branch)
        strategy.execute(portfolio,
            md("TEST", 50, 51, 49, LocalDateTime.of(2024, 1, 1, 9, 1)));

        // Force hlc to go up -> trend change (covers currentTrend != prevTrend)
        TradeSignal s = strategy.execute(portfolio,
            md("TEST", 200, 201, 199, LocalDateTime.of(2024, 1, 1, 9, 2)));

        assertNotNull(s);
    }

    @Test
    void bullishKo_producesLongSignal() {
        KlingerOscillatorStrategy strategy = new KlingerOscillatorStrategy();
        Portfolio portfolio = Portfolio.builder().positions(new HashMap<>()).build();
        portfolio.setPosition("TEST", 0);

        TradeSignal last = null;
        for (int i = 0; i < 60; i++) {
            double close = 100 + i * 0.1;
            double dm = 1.0 + (i * 0.05); // increasing dm => increasing vf
            last = strategy.execute(portfolio,
                md("TEST", close, close + dm / 2, close - dm / 2, LocalDateTime.of(2024, 1, 1, 9, 0).plusMinutes(i)));
        }

        assertNotNull(last);
        assertEquals(TradeSignal.SignalDirection.LONG, last.getDirection());
        assertEquals("Klinger Oscillator", strategy.getName());
        assertNotNull(strategy.getType());
        strategy.reset();
        assertNotNull(strategy.execute(portfolio,
            md("TEST", 100, 101, 99, LocalDateTime.of(2024, 1, 1, 10, 0))));
    }

    @Test
    void bearishKo_producesShortSignal() {
        KlingerOscillatorStrategy strategy = new KlingerOscillatorStrategy();
        Portfolio portfolio = Portfolio.builder().positions(new HashMap<>()).build();
        portfolio.setPosition("TEST", 0);

        TradeSignal last = null;
        for (int i = 0; i < 60; i++) {
            double close = 100 + i * 0.1;
            double dm = 5.0 - (i * 0.05); // decreasing dm => decreasing vf
            last = strategy.execute(portfolio,
                md("TEST", close, close + dm / 2, close - dm / 2, LocalDateTime.of(2024, 1, 1, 9, 0).plusMinutes(i)));
        }

        assertNotNull(last);
        assertEquals(TradeSignal.SignalDirection.SHORT, last.getDirection());

        // Ensure we also cover the neutral path (position blocks long/short signals).
        // With a long position, ko>0 yields no signal; use a large range to ensure cm positive and ko likely > 0.
        portfolio.setPosition("TEST", 1);
        TradeSignal neutral = strategy.execute(portfolio, md("TEST", 200, 210, 190, LocalDateTime.of(2024, 1, 1, 10, 1)));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, neutral.getDirection());
    }

    @Test
    void calculateEma_insufficientPeriod_returnsZero() throws Exception {
        KlingerOscillatorStrategy strategy = new KlingerOscillatorStrategy();
        Method m = KlingerOscillatorStrategy.class.getDeclaredMethod("calculateEMA", Deque.class, int.class);
        m.setAccessible(true);

        Deque<Double> values = new ArrayDeque<>();
        values.add(1.0);

        assertEquals(0.0, (double) m.invoke(strategy, values, 2));
    }

    private static MarketData md(String symbol, double close, double high, double low, LocalDateTime ts) {
        return MarketData.builder()
            .symbol(symbol)
            .timestamp(ts)
            .timeframe(MarketData.Timeframe.MIN_1)
            .open(close)
            .high(high)
            .low(low)
            .close(close)
            .volume(1000L)
            .build();
    }
}
