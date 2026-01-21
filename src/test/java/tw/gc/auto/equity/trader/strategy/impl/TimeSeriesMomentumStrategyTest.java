package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TimeSeriesMomentumStrategyTest {

    private MarketData makeMarketData(String symbol, double price) {
        return MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol(symbol)
                .timeframe(MarketData.Timeframe.MIN_1)
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .volume(100L)
                .build();
    }

    @Test
    public void warmingUp_returnsNeutral() {
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(2, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // fewer than lookback -> warming up (lookback=2 requires <2 prices)
        TradeSignal s1 = strat.execute(p, makeMarketData("ABC", 100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s1.getDirection());
        assertTrue(s1.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    public void producesLongAndShort_and_exitSignals() {
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(2, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        // prime internal price history so execute sees lookback+1 entries
        primeHistory(strat, "XYZ", new double[]{100.0, 101.0});
        // big jump -> positive momentum
        TradeSignal longSignal = strat.execute(p, makeMarketData("XYZ", 110.0));
        assertEquals(TradeSignal.SignalDirection.LONG, longSignal.getDirection());
        assertTrue(Double.isFinite(longSignal.getConfidence()));

        // now simulate a position long and momentum turns negative
        p.setPosition("XYZ", 1);
        // prime to create a falling history then tick down
        primeHistory(strat, "XYZ", new double[]{109.0, 95.0});
        TradeSignal exit = strat.execute(p, makeMarketData("XYZ", 90.0));
        assertEquals(TradeSignal.SignalDirection.SHORT, exit.getDirection());

        // new short signal when flat and strong negative momentum
        p.setPosition("XYZ", 0);
        strat.reset();
        primeHistory(strat, "XYZ", new double[]{200.0, 180.0});
        TradeSignal shortSignal = strat.execute(p, makeMarketData("XYZ", 150.0));
        assertEquals(TradeSignal.SignalDirection.SHORT, shortSignal.getDirection());
    }

    @Test
    public void reset_clearsHistory() {
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(2, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        // prime and then reset — verify internal map cleared
        primeHistory(strat, "AAA", new double[]{10.0, 11.0});
        strat.reset();
        // use reflection to inspect private priceHistory map
        try {
            java.lang.reflect.Field f = TimeSeriesMomentumStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            java.util.Map<?,?> map = (java.util.Map<?,?>) f.get(strat);
            assertTrue(map.isEmpty());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }

    @Test
    public void historyRemoveFirst_whenExceedsLookbackPlus10() {
        // Test line 45: prices.removeFirst() when size > lookback + 10
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(5, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Fill history with more than lookback + 10 = 15 prices
        double[] prices = new double[20];
        for (int i = 0; i < 20; i++) prices[i] = 100 + i;
        primeHistory(strat, "HIST", prices);
        
        // Execute should trigger removeFirst
        TradeSignal signal = strat.execute(p, makeMarketData("HIST", 120));
        assertNotNull(signal);
    }

    @Test
    public void exitLong_whenMomentumTurnsNegative() {
        // Test lines 92-94: position > 0 && momentum < 0 (but not < -threshold)
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(3, 0.10); // 10% threshold
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        p.setPosition("EXIT", 10);
        
        // Prime with slight decline - momentum between 0 and -threshold
        // Current price will be 98, pastPrice (lookback=3) will be 100
        // momentum = (98-100)/100 = -0.02 = -2%, which is < 0 but > -10%
        primeHistory(strat, "EXIT", new double[]{100, 100.5, 100.2, 99.5});
        
        TradeSignal signal = strat.execute(p, makeMarketData("EXIT", 98));
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.isExitSignal());
        assertTrue(signal.getReason().contains("TSMOM Exit Long"));
    }

    @Test
    public void exitShort_whenMomentumTurnsPositive() {
        // Test lines 98-100: position < 0 && momentum > 0 (but not > threshold)
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(3, 0.10); // 10% threshold
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        p.setPosition("EXIT", -10);
        
        // Prime with slight increase - momentum between 0 and +threshold
        // Current price will be 102, pastPrice (lookback=3) will be 100
        // momentum = (102-100)/100 = 0.02 = 2%, which is > 0 but < 10%
        primeHistory(strat, "EXIT", new double[]{100, 99.5, 99.8, 100.5});
        
        TradeSignal signal = strat.execute(p, makeMarketData("EXIT", 102));
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.isExitSignal());
        assertTrue(signal.getReason().contains("TSMOM Exit Short"));
    }

    @Test
    public void neutral_whenMomentumWithinThreshold() {
        // Test line 103: neutral signal
        TimeSeriesMomentumStrategy strat = new TimeSeriesMomentumStrategy(3, 0.10);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Prime with very small price changes (momentum within threshold)
        primeHistory(strat, "NEUT", new double[]{100, 100.5, 100.2, 100.3});
        
        TradeSignal signal = strat.execute(p, makeMarketData("NEUT", 100.1));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("TSMOM:"));
    }

    private void primeHistory(TimeSeriesMomentumStrategy strat, String symbol, double[] prices) {
        try {
            java.lang.reflect.Field f = TimeSeriesMomentumStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, java.util.Deque<Double>> map = (java.util.Map<String, java.util.Deque<Double>>) f.get(strat);
            java.util.Deque<Double> dq = new java.util.ArrayDeque<>();
            for (double p : prices) dq.addLast(p);
            map.put(symbol, dq);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
