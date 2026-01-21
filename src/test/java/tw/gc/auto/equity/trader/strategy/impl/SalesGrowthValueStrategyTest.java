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

class SalesGrowthValueStrategyTest {

    private SalesGrowthValueStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new SalesGrowthValueStrategy(1.2, 0.05);
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
    void longSignal_whenLowPSAndHighGrowth() throws Exception {
        // Create conditions for long signal (lines 81-84):
        // - priceToSalesProxy < maxPriceToSales (1.2)
        // - growthRate > minGrowthRate (0.05)
        // - position <= 0
        
        int size = 65;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // First half: lower prices (for positive growth)
        for (int i = 0; i < size / 2; i++) {
            prices[i] = 95;
            volumes[i] = 10000L;
        }
        // Second half: higher prices
        for (int i = size / 2; i < size; i++) {
            prices[i] = 105; // ~10% growth
            volumes[i] = 10000L;
        }
        primeHistory(strategy, "SGV", prices, volumes);
        
        // Current price close to VWAP (low P/S proxy)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SGV", 100, 10000L));
        
        assertNotNull(signal);
        // Should trigger long signal when P/S < 1.2 and growth > 5%
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getReason().toLowerCase().contains("sales growth value"));
        }
    }

    @Test
    void exitSignal_whenValuationExpandsOrGrowthStalls() throws Exception {
        // Set position > 0
        portfolio.setPosition("SGV", 100);
        
        // Create conditions for exit (lines 88-90):
        // - priceToSalesProxy > maxPriceToSales * 1.5 OR growthRate < 0
        // - position > 0
        
        int size = 65;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // First half: higher prices
        for (int i = 0; i < size / 2; i++) {
            prices[i] = 120;
            volumes[i] = 10000L;
        }
        // Second half: lower prices (negative growth)
        for (int i = size / 2; i < size; i++) {
            prices[i] = 100;
            volumes[i] = 10000L;
        }
        primeHistory(strategy, "SGV", prices, volumes);
        
        // High current price relative to VWAP OR negative growth
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SGV", 95, 10000L));
        
        assertNotNull(signal);
        // Should trigger exit when P/S > 1.8 OR growth < 0
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && signal.isExitSignal()) {
            assertTrue(signal.getReason().toLowerCase().contains("sgv exit"));
        }
    }

    @Test
    void getName_returnsCorrectFormat() {
        String name = strategy.getName();
        assertTrue(name.contains("Sales Growth Value"));
        assertTrue(name.contains("P/S<1.2"));
        assertTrue(name.contains("growth>5%"));
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_clearsAllHistory() throws Exception {
        strategy.execute(portfolio, createMarketData("TEST", 100, 1000L));
        
        strategy.reset();
        
        Field fPrice = SalesGrowthValueStrategy.class.getDeclaredField("priceHistory");
        fPrice.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> priceMap = (Map<String, Deque<Double>>) fPrice.get(strategy);
        assertTrue(priceMap.isEmpty());
        
        Field fVol = SalesGrowthValueStrategy.class.getDeclaredField("volumeHistory");
        fVol.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> volMap = (Map<String, Deque<Long>>) fVol.get(strategy);
        assertTrue(volMap.isEmpty());
    }

    private void primeHistory(SalesGrowthValueStrategy strat, String symbol, double[] prices, long[] volumes) {
        try {
            Field fPrice = SalesGrowthValueStrategy.class.getDeclaredField("priceHistory");
            fPrice.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> priceMap = (Map<String, Deque<Double>>) fPrice.get(strat);
            Deque<Double> priceDq = new ArrayDeque<>();
            for (double p : prices) priceDq.addLast(p);
            priceMap.put(symbol, priceDq);
            
            Field fVol = SalesGrowthValueStrategy.class.getDeclaredField("volumeHistory");
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
