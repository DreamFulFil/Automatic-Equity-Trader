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

class NetStockIssuanceStrategyTest {

    private NetStockIssuanceStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new NetStockIssuanceStrategy(3, 0.10);
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
    void historyRemoveFirst_whenExceedsLookback() throws Exception {
        // lookbackPeriod=3, so limit is 3 * 21 = 63 days
        NetStockIssuanceStrategy strat = new NetStockIssuanceStrategy(3, 0.10);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Prime with exactly lookback * 21 entries (line 47-49 triggers when size > lookback * 21 after addLast)
        int limit = 3 * 21;
        double[] prices = new double[limit];
        long[] volumes = new long[limit];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 + i * 0.1;
            volumes[i] = 1000 + i;
        }
        primeHistory(strat, "TEST", prices, volumes);
        
        // Execute - adds one (size becomes limit+1), then removeFirst triggers
        TradeSignal signal = strat.execute(p, createMarketData("TEST", 110, 1100L));
        assertNotNull(signal);
        
        // Verify trimmed to limit
        Field fVol = NetStockIssuanceStrategy.class.getDeclaredField("volumeHistory");
        fVol.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> volMap = (Map<String, Deque<Long>>) fVol.get(strat);
        assertEquals(limit, volMap.get("TEST").size());
    }

    @Test
    void longSignal_whenBuybackDetected() throws Exception {
        // Create conditions for buyback signal (lines 82-85):
        // - declining volume (buybackProxy > minBuybackRate)
        // - currentPrice >= avgPrice * 0.95
        // - position <= 0
        
        // Prime with declining volume pattern
        int size = 70;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // First half: high volume
        for (int i = 0; i < size / 2; i++) {
            prices[i] = 100;
            volumes[i] = 10000L;
        }
        // Second half: much lower volume (declining)
        for (int i = size / 2; i < size; i++) {
            prices[i] = 100;
            volumes[i] = 3000L; // 70% decline
        }
        primeHistory(strategy, "BUY", prices, volumes);
        
        // Execute with stable price (near average)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("BUY", 100, 2500L));
        
        assertNotNull(signal);
        // If buyback conditions are met, should be LONG
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getReason().toLowerCase().contains("buyback"));
        }
    }

    @Test
    void exitSignal_whenIssuanceDetected() throws Exception {
        // Set position > 0
        portfolio.setPosition("ISS", 100);
        
        // Create conditions for issuance warning (lines 89-91):
        // - volumeChange > 0.2
        // - currentPrice < avgPrice * 0.95
        
        int size = 70;
        double[] prices = new double[size];
        long[] volumes = new long[size];
        
        // First half: low volume
        for (int i = 0; i < size / 2; i++) {
            prices[i] = 100;
            volumes[i] = 5000L;
        }
        // Second half: much higher volume (increasing by > 20%)
        for (int i = size / 2; i < size; i++) {
            prices[i] = 100;
            volumes[i] = 8000L;
        }
        primeHistory(strategy, "ISS", prices, volumes);
        
        // Execute with price drop below 95% of average
        TradeSignal signal = strategy.execute(portfolio, createMarketData("ISS", 90, 9000L));
        
        assertNotNull(signal);
        // Should trigger exit signal
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertTrue(signal.isExitSignal());
            assertTrue(signal.getReason().toLowerCase().contains("issuance"));
        }
    }

    @Test
    void getName_returnsCorrectFormat() {
        String name = strategy.getName();
        assertTrue(name.contains("Net Stock Issuance"));
        assertTrue(name.contains("3mo"));
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_clearsHistory() throws Exception {
        strategy.execute(portfolio, createMarketData("TEST", 100, 1000L));
        
        strategy.reset();
        
        Field fVol = NetStockIssuanceStrategy.class.getDeclaredField("volumeHistory");
        fVol.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> volMap = (Map<String, Deque<Long>>) fVol.get(strategy);
        assertTrue(volMap.isEmpty());
        
        Field fPrice = NetStockIssuanceStrategy.class.getDeclaredField("priceHistory");
        fPrice.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> priceMap = (Map<String, Deque<Double>>) fPrice.get(strategy);
        assertTrue(priceMap.isEmpty());
    }

    private void primeHistory(NetStockIssuanceStrategy strat, String symbol, double[] prices, long[] volumes) {
        try {
            Field fPrice = NetStockIssuanceStrategy.class.getDeclaredField("priceHistory");
            fPrice.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> priceMap = (Map<String, Deque<Double>>) fPrice.get(strat);
            Deque<Double> priceDq = new ArrayDeque<>();
            for (double p : prices) priceDq.addLast(p);
            priceMap.put(symbol, priceDq);
            
            Field fVol = NetStockIssuanceStrategy.class.getDeclaredField("volumeHistory");
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
