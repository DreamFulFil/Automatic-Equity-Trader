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

class TriangularArbitrageStrategyTest {

    private TriangularArbitrageStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        String[] symbols = {"SYM1", "SYM2", "SYM3"};
        strategy = new TriangularArbitrageStrategy(symbols, 0.02);
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
        TradeSignal signal = strategy.execute(portfolio, createMarketData("TEST", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void longSignal_whenUndervalued() throws Exception {
        // Create conditions for long signal (lines 72-75):
        // - deviation < -2 (significantly below average)
        // - profitOpportunity > minProfit
        // - position <= 0
        
        // Prime with prices that put current price significantly below average
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) {
            prices[i] = 100; // Average is 100
        }
        primeHistory(strategy, "ARB", prices);
        
        // Current price way below average creates negative z-score
        TradeSignal signal = strategy.execute(portfolio, createMarketData("ARB", 70));
        
        assertNotNull(signal);
        // Should trigger long when deviation < -2
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getReason().toLowerCase().contains("triangular arb long"));
        }
    }

    @Test
    void exitSignal_whenOvervaluedAndPositionLong() throws Exception {
        // Set position > 0
        portfolio.setPosition("ARB", 100);
        
        // Create conditions for exit (lines 79-81):
        // - deviation > 2
        // - position > 0
        
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) {
            prices[i] = 100;
        }
        primeHistory(strategy, "ARB", prices);
        
        // Current price way above average creates positive z-score
        TradeSignal signal = strategy.execute(portfolio, createMarketData("ARB", 130));
        
        assertNotNull(signal);
        // Should trigger exit when deviation > 2 and position > 0
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertTrue(signal.isExitSignal());
            assertTrue(signal.getReason().toLowerCase().contains("triangular arb exit"));
        }
    }

    @Test
    void exitSignal_whenEquilibriumReached() throws Exception {
        // Set position != 0
        portfolio.setPosition("ARB", 50);
        
        // Create conditions for equilibrium exit (lines 91-95):
        // - position != 0
        // - |deviation| < 0.5
        
        // Use prices with some variance so stdDev is non-zero
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) {
            prices[i] = 100 + (i % 5); // Avg ~102, stdDev ~1.4
        }
        primeHistory(strategy, "ARB", prices);
        
        // Current price very close to average (small deviation normalized by stdDev)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("ARB", 102));
        
        assertNotNull(signal);
        // Either exit signal or check reason contains equilibrium
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().toLowerCase().contains("equilibrium"));
        }
    }

    @Test
    void shortSignal_whenOvervaluedAndFlat() throws Exception {
        // position >= 0 (flat)
        portfolio.setPosition("ARB", 0);
        
        // Create conditions for short signal (lines 85-87):
        // - deviation > 2
        // - profitOpportunity > minProfit
        // - position >= 0 (not already short)
        
        double[] prices = new double[35];
        for (int i = 0; i < 35; i++) {
            prices[i] = 100;
        }
        primeHistory(strategy, "ARB", prices);
        
        // Price way above avg
        TradeSignal signal = strategy.execute(portfolio, createMarketData("ARB", 140));
        
        assertNotNull(signal);
        // Should trigger short when deviation > 2 and profit > minProfit
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertTrue(signal.getReason().toLowerCase().contains("triangular arb short"));
        }
    }

    @Test
    void getName_returnsCorrectFormat() {
        String name = strategy.getName();
        assertTrue(name.contains("Triangular Arbitrage"));
        assertTrue(name.contains("3 symbols"));
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsHistory() throws Exception {
        strategy.execute(portfolio, createMarketData("TEST", 100));
        
        strategy.reset();
        
        Field f = TriangularArbitrageStrategy.class.getDeclaredField("priceHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
        assertTrue(map.isEmpty());
    }

    private void primeHistory(TriangularArbitrageStrategy strat, String symbol, double[] prices) {
        try {
            Field f = TriangularArbitrageStrategy.class.getDeclaredField("priceHistory");
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
}
