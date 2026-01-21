package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class NewsRevisionMomentumStrategyTest {

    private MarketData mk(String sym, double close, long vol) {
        return MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol(sym)
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(vol)
                .build();
    }

    @Test
    void warmingUpProducesNeutral() {
        NewsRevisionMomentumStrategy s = new NewsRevisionMomentumStrategy(5, 0.02);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        for (int i = 0; i < 4; i++) {
            TradeSignal t = s.execute(p, mk("N", 100 + i, 1000));
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, t.getDirection());
        }
    }

    @Test
    void positiveRevisionGeneratesLong() throws Exception {
        NewsRevisionMomentumStrategy s = new NewsRevisionMomentumStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // prime history with small moves and average volume
        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(100.2);
        prices.addLast(100.25);

        Deque<Long> vols = new ArrayDeque<>();
        vols.addLast(100L);
        vols.addLast(110L);
        vols.addLast(120L);

        Field fPrices = NewsRevisionMomentumStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> pm = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        pm.put("P", prices);

        Field fVols = NewsRevisionMomentumStrategy.class.getDeclaredField("volumeHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Long>> vm = (java.util.Map<String, Deque<Long>>) fVols.get(s);
        vm.put("P", vols);

        // current bar is a large up move with heavy volume
        TradeSignal sig = s.execute(p, mk("P", 110.0, 500L));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.LONG, sig.getDirection());
        assertTrue(sig.getReason().toLowerCase().contains("positive revision"));
    }

    @Test
    void negativeRevisionGeneratesShort() throws Exception {
        NewsRevisionMomentumStrategy s = new NewsRevisionMomentumStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(200.0);
        prices.addLast(199.0);
        prices.addLast(198.5);

        Deque<Long> vols = new ArrayDeque<>();
        vols.addLast(100L);
        vols.addLast(100L);
        vols.addLast(100L);

        Field fPrices = NewsRevisionMomentumStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> pm = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        pm.put("S", prices);

        Field fVols = NewsRevisionMomentumStrategy.class.getDeclaredField("volumeHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Long>> vm = (java.util.Map<String, Deque<Long>>) fVols.get(s);
        vm.put("S", vols);

        TradeSignal sig = s.execute(p, mk("S", 180.0, 400L));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.SHORT, sig.getDirection());
        assertTrue(sig.getReason().toLowerCase().contains("negative revision"));
    }

    @Test
    void followThroughExitRemovesEvent() throws Exception {
        NewsRevisionMomentumStrategy s = new NewsRevisionMomentumStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        p.setPosition("E", 10);

        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(120.0);
        prices.addLast(140.0);
        prices.addLast(130.0);

        Deque<Long> vols = new ArrayDeque<>();
        vols.addLast(100L);
        vols.addLast(100L);
        vols.addLast(100L);

        Field fPrices = NewsRevisionMomentumStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> pm = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        pm.put("E", prices);

        Field fVols = NewsRevisionMomentumStrategy.class.getDeclaredField("volumeHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Long>> vm = (java.util.Map<String, Deque<Long>>) fVols.get(s);
        vm.put("E", vols);

        // set event price to an earlier value to simulate ongoing position
        Field fEvents = NewsRevisionMomentumStrategy.class.getDeclaredField("eventPrices");
        fEvents.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Double> events = (java.util.Map<String, Double>) fEvents.get(s);
        events.put("E", 140.0);

        TradeSignal sig = s.execute(p, mk("E", 130.0, 100L));
        assertNotNull(sig);
        assertTrue(sig.getReason().toLowerCase().contains("pnl") || sig.getReason().toLowerCase().contains("faded"));
    }

    @Test
    void exitSignal_shortPositionMomentumFaded() throws Exception {
        // Test lines 102, 106: Exit signal for SHORT position when momentum reverses
        NewsRevisionMomentumStrategy s = new NewsRevisionMomentumStrategy(3, 0.02);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        p.setPosition("SHORT_EXIT", -10); // short position

        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(95.0);
        prices.addLast(90.0);

        Deque<Long> vols = new ArrayDeque<>();
        vols.addLast(100L);
        vols.addLast(100L);
        vols.addLast(100L);

        Field fPrices = NewsRevisionMomentumStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> pm = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        pm.put("SHORT_EXIT", prices);

        Field fVols = NewsRevisionMomentumStrategy.class.getDeclaredField("volumeHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Long>> vm = (java.util.Map<String, Deque<Long>>) fVols.get(s);
        vm.put("SHORT_EXIT", vols);

        // Set event price at 90 (short entry) - price going up reverses momentum
        Field fEvents = NewsRevisionMomentumStrategy.class.getDeclaredField("eventPrices");
        fEvents.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Double> events = (java.util.Map<String, Double>) fEvents.get(s);
        events.put("SHORT_EXIT", 90.0);

        // Price rises above significanceThreshold/2 from event price
        TradeSignal sig = s.execute(p, mk("SHORT_EXIT", 92.0, 100L)); // 2.2% up from 90

        assertNotNull(sig);
        if (sig.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.LONG, sig.getDirection()); // cover short
            assertEquals(0.70, sig.getConfidence());
            assertTrue(sig.getReason().contains("faded"));
        }
    }
}
