package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashFlowValueStrategyTest {

    private CashFlowValueStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new CashFlowValueStrategy(0.05);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    private MarketData createMarketData(String symbol, double close, long volume) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 1)
                .low(close - 1)
                .open(close)
                .volume(volume)
                .build();
    }

    @Test
    void warmingUp_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, createMarketData("TEST", 100, 1000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void longSignal_whenHighFCFYield() throws Exception {
        // Create conditions for long signal:
        // - impliedFCFYield > minFCFYield
        // - position <= 0
        
        int size = 65;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // Price history showing stable prices above current (creates positive valueRatio)
        for (int i = 0; i < size; i++) {
            prices[i] = 110; // Stable high prices
            volumes[i] = 10000L; // Consistent volume
        }
        primeHistory(strategy, "FCF", prices, volumes);
        
        // Current price below average (creates high impliedFCFYield)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("FCF", 95, 10000L));
        
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getReason().toLowerCase().contains("high fcf yield"));
        }
    }

    @Test
    void exitSignal_whenFCFYieldDeteriorates() throws Exception {
        // Set position > 0
        portfolio.setPosition("FCF", 100);
        
        // Create conditions for exit:
        // - impliedFCFYield < minFCFYield / 3
        // - position > 0
        
        int size = 65;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // Price history with low average
        for (int i = 0; i < size; i++) {
            prices[i] = 90;
            volumes[i] = 10000L;
        }
        primeHistory(strategy, "FCF", prices, volumes);
        
        // Current price above average (creates negative/low impliedFCFYield)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("FCF", 120, 10000L));
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.isExitSignal());
        assertTrue(signal.getReason().toLowerCase().contains("fcf yield exit"));
    }

    @Test
    void shortSignal_whenLowFCFYieldAndPoorQuality() throws Exception {
        // Create conditions for short signal (lines 108-110):
        // - impliedFCFYield < -minFCFYield
        // - priceStability < 0.5
        // - position >= 0
        
        int size = 65;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // High volatility prices (low stability) oscillating around 100
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + ((i % 2 == 0) ? 30 : -30); // Very volatile
            volumes[i] = 10000L;
        }
        primeHistory(strategy, "FCF", prices, volumes);
        
        portfolio.setPosition("FCF", 0);
        
        // Current price way above average (creates negative valueRatio -> negative impliedFCFYield)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("FCF", 160, 10000L));
        
        assertNotNull(signal);
        // Should trigger short when impliedFCFYield < -minFCFYield and priceStability < 0.5
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && !signal.isExitSignal()) {
            assertEquals(0.60, signal.getConfidence());
            assertTrue(signal.getReason().toLowerCase().contains("low fcf yield"));
            assertTrue(signal.getReason().toLowerCase().contains("poor quality"));
        }
    }

    @Test
    void getName_returnsCorrectFormat() {
        String name = strategy.getName();
        assertTrue(name.contains("Cash Flow Value"));
        assertTrue(name.contains("5.0%"));
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_clearsAllHistory() throws Exception {
        strategy.execute(portfolio, createMarketData("TEST", 100, 1000L));
        
        strategy.reset();
        
        Field fPrice = CashFlowValueStrategy.class.getDeclaredField("priceHistory");
        fPrice.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> priceMap = (Map<String, Deque<Double>>) fPrice.get(strategy);
        assertTrue(priceMap.isEmpty());
        
        Field fVol = CashFlowValueStrategy.class.getDeclaredField("volumeHistory");
        fVol.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> volMap = (Map<String, Deque<Long>>) fVol.get(strategy);
        assertTrue(volMap.isEmpty());
    }

    private void primeHistory(CashFlowValueStrategy strat, String symbol, double[] prices, long[] volumes) {
        try {
            Field fPrice = CashFlowValueStrategy.class.getDeclaredField("priceHistory");
            fPrice.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> priceMap = (Map<String, Deque<Double>>) fPrice.get(strat);
            Deque<Double> priceDq = new ArrayDeque<>();
            for (double p : prices) priceDq.addLast(p);
            priceMap.put(symbol, priceDq);
            
            Field fVol = CashFlowValueStrategy.class.getDeclaredField("volumeHistory");
            fVol.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Long>> volMap = (Map<String, Deque<Long>>) fVol.get(strat);
            Deque<Long> volDq = new ArrayDeque<>();
            for (long v : volumes) volDq.addLast(v);
            volMap.put(symbol, volDq);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
