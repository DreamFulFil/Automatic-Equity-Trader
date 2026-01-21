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

public class VolatilityArbitrageStrategyTest {

    private MarketData mk(String sym, double close) {
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
    void warmingUpReturnsNeutral() {
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(5, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // feed fewer than window -> warming up
        for (int i = 0; i < 4; i++) {
            TradeSignal t = s.execute(p, mk("V", 100 + i));
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, t.getDirection());
            assertTrue(t.getReason().toLowerCase().contains("warming up"));
        }
    }

    @Test
    void longSignalWhenImpliedGreaterThanRealized() throws Exception {
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // prime priceHistory with low realized volatility (small moves)
        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(100.05);
        prices.addLast(100.02);
        prices.addLast(100.03); // length = window+1

        // prime volHistory with high implied vols
        Deque<Double> vols = new ArrayDeque<>();
        vols.addLast(0.5);
        vols.addLast(0.5);
        vols.addLast(0.5);

        // inject into private fields
        Field fPrices = VolatilityArbitrageStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> priceMap = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        priceMap.put("VOL", prices);

        Field fVols = VolatilityArbitrageStrategy.class.getDeclaredField("volHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> volMap = (java.util.Map<String, Deque<Double>>) fVols.get(s);
        volMap.put("VOL", vols);

        TradeSignal sig = s.execute(p, mk("VOL", 100.04));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.LONG, sig.getDirection());
        assertTrue(sig.getReason().toLowerCase().contains("vol arb long"));
    }

    @Test
    void shortSignalWhenRealizedMuchHigher() throws Exception {
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // prime priceHistory with large returns to make realized vol high
        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(120.0);
        prices.addLast(80.0);
        prices.addLast(140.0);

        Deque<Double> vols = new ArrayDeque<>();
        vols.addLast(0.05); // implied small
        vols.addLast(0.05);

        Field fPrices = VolatilityArbitrageStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> priceMap = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        priceMap.put("S", prices);

        Field fVols = VolatilityArbitrageStrategy.class.getDeclaredField("volHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> volMap = (java.util.Map<String, Deque<Double>>) fVols.get(s);
        volMap.put("S", vols);

        TradeSignal sig = s.execute(p, mk("S", 150.0));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.SHORT, sig.getDirection());
        assertTrue(sig.getReason().toLowerCase().contains("vol arb short"));
    }

    @Test
    void exitWhenPositionAndSpreadCollapses() throws Exception {
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        p.setPosition("X", 10);

        // prime with modest realized > implied but not extreme (trigger exit condition)
        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(130.0);
        prices.addLast(90.0);
        prices.addLast(95.0);

        Deque<Double> vols = new ArrayDeque<>();
        vols.addLast(0.1);
        vols.addLast(0.1);

        Field fPrices = VolatilityArbitrageStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> priceMap = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        priceMap.put("X", prices);

        Field fVols = VolatilityArbitrageStrategy.class.getDeclaredField("volHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> volMap = (java.util.Map<String, Deque<Double>>) fVols.get(s);
        volMap.put("X", vols);

        TradeSignal sig = s.execute(p, mk("X", 96.0));
        assertNotNull(sig);
        // exitSignal used SHORT as exit direction in implementation
        assertEquals(TradeSignal.SignalDirection.SHORT, sig.getDirection());
        assertTrue(sig.getReason().toLowerCase().contains("vol spread"));
    }

    @Test
    void historyRemoveFirst_whenExceedsLimit() throws Exception {
        // Test line 45: prices.removeFirst() when size > realizedWindow * 2
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(5, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Prime with exactly 5 * 2 = 10 prices, then execute adds one -> triggers removeFirst
        Deque<Double> prices = new ArrayDeque<>();
        for (int i = 0; i < 10; i++) prices.addLast(100.0 + i);
        
        Field fPrices = VolatilityArbitrageStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> priceMap = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        priceMap.put("TRIM", prices);
        
        TradeSignal sig = s.execute(p, mk("TRIM", 115));
        assertNotNull(sig);
        
        // Verify size is now 10 (one added, one removed)
        assertEquals(10, priceMap.get("TRIM").size());
    }

    @Test
    void volHistoryRemoveFirst_whenExceeds30() throws Exception {
        // Test line 67: vols.removeFirst() when size > 30
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Prime vol history with exactly 30 entries
        Deque<Double> vols = new ArrayDeque<>();
        for (int i = 0; i < 30; i++) vols.addLast(0.2);
        
        // Prime price history
        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(101.0);
        prices.addLast(102.0);
        prices.addLast(103.0);
        
        Field fPrices = VolatilityArbitrageStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> priceMap = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        priceMap.put("VOLTRIM", prices);
        
        Field fVols = VolatilityArbitrageStrategy.class.getDeclaredField("volHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> volMap = (java.util.Map<String, Deque<Double>>) fVols.get(s);
        volMap.put("VOLTRIM", vols);
        
        TradeSignal sig = s.execute(p, mk("VOLTRIM", 104));
        assertNotNull(sig);
        
        // Verify vol history size is 30 (one added, one removed due to size > 30 check)
        assertEquals(30, volMap.get("VOLTRIM").size());
    }

    @Test
    void neutralSignal_whenSpreadWithinThreshold() throws Exception {
        // Test line 98: neutral signal
        VolatilityArbitrageStrategy s = new VolatilityArbitrageStrategy(3, 0.50);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Prime with similar realized and implied vol (spread within threshold)
        Deque<Double> prices = new ArrayDeque<>();
        prices.addLast(100.0);
        prices.addLast(100.5);
        prices.addLast(100.2);
        prices.addLast(100.3);
        
        Deque<Double> vols = new ArrayDeque<>();
        vols.addLast(0.10);
        vols.addLast(0.10);
        
        Field fPrices = VolatilityArbitrageStrategy.class.getDeclaredField("priceHistory");
        fPrices.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> priceMap = (java.util.Map<String, Deque<Double>>) fPrices.get(s);
        priceMap.put("NEUT", prices);
        
        Field fVols = VolatilityArbitrageStrategy.class.getDeclaredField("volHistory");
        fVols.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Deque<Double>> volMap = (java.util.Map<String, Deque<Double>>) fVols.get(s);
        volMap.put("NEUT", vols);
        
        TradeSignal sig = s.execute(p, mk("NEUT", 100.4));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, sig.getDirection());
        assertTrue(sig.getReason().contains("Vol spread"));
    }
}
