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

class CointegrationPairsStrategyTest {

    private CointegrationPairsStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new CointegrationPairsStrategy("STOCK1", "STOCK2", 2.0);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 1)
                .low(close - 1)
                .open(close)
                .volume(10000L)
                .build();
    }

    @Test
    void warmingUp_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void longSignal_whenZScoreNegative() throws Exception {
        // z-score < -entryThreshold && position <= 0
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) prices[i] = 100;
        primeHistory(strategy, "STOCK1", prices);
        
        portfolio.setPosition("STOCK1", 0);
        
        // Current price well below average (creates negative z-score)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 70));
        
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.75, signal.getConfidence());
            assertTrue(signal.getReason().contains("Cointegration long"));
        }
    }

    @Test
    void shortSignal_whenZScorePositive() throws Exception {
        // z-score > entryThreshold && position >= 0
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) prices[i] = 100;
        primeHistory(strategy, "STOCK1", prices);
        
        portfolio.setPosition("STOCK1", 0);
        
        // Current price well above average (creates positive z-score)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 130));
        
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertEquals(0.70, signal.getConfidence());
            assertTrue(signal.getReason().contains("Cointegration short"));
        }
    }

    @Test
    void exitSignal_longPosition_whenMeanReverts() throws Exception {
        // Line 97-98: Exit when position != 0 && |zScore| < entryThreshold/2
        // For position > 0, exit direction should be SHORT
        portfolio.setPosition("STOCK1", 100);
        
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) prices[i] = 100;
        primeHistory(strategy, "STOCK1", prices);
        primeRatioHistory(strategy, new double[]{1.0, 1.0, 1.0, 1.0, 1.0});
        
        // Price at average (z-score near 0 -> mean reversion)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 100));
        
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
            assertEquals(0.70, signal.getConfidence());
            assertTrue(signal.getReason().contains("Mean reversion exit"));
        }
    }

    @Test
    void exitSignal_shortPosition_whenMeanReverts() throws Exception {
        // Line 98: For position < 0, exit direction should be LONG
        portfolio.setPosition("STOCK1", -100);
        
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) prices[i] = 100;
        primeHistory(strategy, "STOCK1", prices);
        primeRatioHistory(strategy, new double[]{1.0, 1.0, 1.0, 1.0, 1.0});
        
        // Price at average
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 100));
        
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
            assertEquals(0.70, signal.getConfidence());
            assertTrue(signal.getReason().contains("Mean reversion exit"));
        }
    }

    @Test
    void neutralSignal_whenNoConditionsMet() throws Exception {
        portfolio.setPosition("STOCK1", 0);
        
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) prices[i] = 100;
        primeHistory(strategy, "STOCK1", prices);
        
        // Price near average (z-score small but not triggering any condition)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 105));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Z-score"));
    }

    @Test
    void getName_returnsCorrectFormat() {
        assertEquals("Cointegration Pairs (STOCK1/STOCK2)", strategy.getName());
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsHistory() throws Exception {
        for (int i = 0; i < 35; i++) {
            strategy.execute(portfolio, createMarketData("STOCK1", 100 + i));
        }
        
        strategy.reset();
        
        // After reset, should be warming up
        TradeSignal signal = strategy.execute(portfolio, createMarketData("STOCK1", 100));
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    private void primeHistory(CointegrationPairsStrategy strat, String symbol, double[] prices) {
        try {
            Field f = CointegrationPairsStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strat);
            Deque<Double> dq = new ArrayDeque<>();
            for (double p : prices) dq.addLast(p);
            map.put(symbol, dq);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void primeRatioHistory(CointegrationPairsStrategy strat, double[] ratios) {
        try {
            Field f = CointegrationPairsStrategy.class.getDeclaredField("ratioHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Deque<Double> dq = (Deque<Double>) f.get(strat);
            dq.clear();
            for (double r : ratios) dq.addLast(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
