package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class DualMomentumStrategyTest {

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
        DualMomentumStrategy s = new DualMomentumStrategy(2, 3, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        TradeSignal t = s.execute(p, md("ABC", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, t.getDirection());
        assertTrue(t.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    public void primed_history_returnsNeutral_with_small_moves() {
        DualMomentumStrategy s = new DualMomentumStrategy(2, 3, 0.05);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // prime small, slowly increasing prices that do not exceed threshold
        double[] prices = new double[]{100.0, 101.0, 101.5, 102.0};
        primeHistory(s, "ABC", prices);

        TradeSignal t = s.execute(p, md("ABC", 102.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, t.getDirection());
        assertTrue(t.getReason().toLowerCase().contains("abs=") || t.getReason().toLowerCase().contains("rel="));
    }

    private void primeHistory(DualMomentumStrategy strat, String symbol, double[] prices) {
        try {
            java.lang.reflect.Field f = DualMomentumStrategy.class.getDeclaredField("priceHistory");
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
