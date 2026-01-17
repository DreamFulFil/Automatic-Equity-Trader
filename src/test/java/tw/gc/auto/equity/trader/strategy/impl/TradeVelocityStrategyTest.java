package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TradeVelocityStrategyTest {

    private MarketData md(String sym, double close, long volume) {
        return MarketData.builder()
                .timestamp(LocalDateTime.now())
                .symbol(sym)
                .timeframe(MarketData.Timeframe.MIN_1)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(volume)
                .build();
    }

    @Test
    public void warmingUp_returnsNeutral() {
        TradeVelocityStrategy s = new TradeVelocityStrategy(4, 2.0);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        TradeSignal t = s.execute(p, md("TV", 100.0, 100L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, t.getDirection());
        assertTrue(t.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    public void low_toxicity_uptrend_generatesLong() {
        TradeVelocityStrategy s = new TradeVelocityStrategy(4, 2.0);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // prime with low volumes and rising prices
        double[] prices = new double[]{100, 101, 102, 103};
        long[] vols = new long[]{100, 110, 120, 130};
        for (int i = 0; i < prices.length; i++) s.execute(p, md("TV", prices[i], vols[i]));

        TradeSignal t = s.execute(p, md("TV", 104.0, 140));
        assertEquals(TradeSignal.SignalDirection.LONG, t.getDirection());
        assertTrue(t.getReason().toLowerCase().contains("low toxicity") || t.getReason().toLowerCase().contains("toxicity"));
    }
}
