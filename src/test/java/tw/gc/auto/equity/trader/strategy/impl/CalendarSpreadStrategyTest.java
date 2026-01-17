package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class CalendarSpreadStrategyTest {

    private MarketData md(String sym, double close) {
        return MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol(sym)
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(100L)
                .build();
    }

    @Test
    public void warmingUp_returnsNeutral() {
        CalendarSpreadStrategy s = new CalendarSpreadStrategy(1, 3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        TradeSignal t = s.execute(p, md("VOL", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, t.getDirection());
        assertTrue(t.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    public void detectsBackwardation_and_contango_and_exit() {
        // near=1month ~21, far=3months ~63 -> use small periods via priming
        CalendarSpreadStrategy s = new CalendarSpreadStrategy(1, 3, 0.005);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // prime history: far-term flat, near-term volatile -> nearVol > farVol
        int near = 21; int far = 63; int total = 70;
        double[] arr = new double[total];
        for (int i = 0; i < total - near; i++) arr[i] = 100.0; // far-term flat
        for (int i = total - near; i < total; i++) arr[i] = 100.0 + ((i % 2 == 0) ? 30.0 : -30.0); // volatile near-term
        primeHistory(s, "VOL", arr);
        TradeSignal longSig = s.execute(p, md("VOL", 121));
        assertEquals(TradeSignal.SignalDirection.LONG, longSig.getDirection());

        // simulate being long then normalization -> exit
        p.setPosition("VOL", 1);
        primeHistory(s, "VOL", new double[]{120.0, 110.0, 100.0, 95.0});
        TradeSignal exit = s.execute(p, md("VOL", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, exit.getDirection());

        // contango path (near < far)
        p.setPosition("VOL", 0);
        // prime history: far-term volatile, near-term calmer -> farVol > nearVol
        double[] arr2 = new double[70];
        for (int i = 0; i < 70 - near; i++) arr2[i] = 100.0 + ((i % 2 == 0) ? 25.0 : -25.0); // volatile far-term
        for (int i = 70 - near; i < 70; i++) arr2[i] = 100.0; // near-term flat
        primeHistory(s, "VOL", arr2);
        TradeSignal shortSig = s.execute(p, md("VOL", 69));
        assertEquals(TradeSignal.SignalDirection.SHORT, shortSig.getDirection());
    }

    private double[] generatePricesIncreasing(int start, int end, int count) {
        double[] arr = new double[count];
        for (int i = 0; i < count; i++) arr[i] = start + (end - start) * (i / (double)(count - 1));
        return arr;
    }

    private double[] generatePricesDecreasing(int start, int end, int count) {
        double[] arr = generatePricesIncreasing(end, start, count);
        return arr;
    }

    private double[] generatePricesFlat(int base, int count) {
        double[] arr = new double[count];
        for (int i = 0; i < count; i++) arr[i] = base;
        return arr;
    }

    private void primeHistory(CalendarSpreadStrategy strat, String symbol, double[] prices) {
        try {
            java.lang.reflect.Field f = CalendarSpreadStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, java.util.Deque<Double>> map = (java.util.Map<String, java.util.Deque<Double>>) f.get(strat);
            java.util.Deque<Double> dq = new java.util.ArrayDeque<>();
            for (double p : prices) dq.addLast(p);
            map.put(symbol, dq);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
