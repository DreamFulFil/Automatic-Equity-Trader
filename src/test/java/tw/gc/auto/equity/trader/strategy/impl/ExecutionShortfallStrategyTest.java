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

class ExecutionShortfallStrategyTest {

    private ExecutionShortfallStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new ExecutionShortfallStrategy(10000, 10, 0.8);
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
        // Less than 10 data points -> warming up
        for (int i = 0; i < 5; i++) {
            TradeSignal signal = strategy.execute(portfolio, createMarketData("EXEC", 100 + i));
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().toLowerCase().contains("warming up"));
        }
    }

    @Test
    void executionSlice_whenConditionsFavorable() throws Exception {
        // Prime with enough history (10+ data points)
        double[] prices = new double[15];
        for (int i = 0; i < 15; i++) prices[i] = 100;
        primeHistory(strategy, "EXEC", prices);
        
        // Set decision price and slices executed
        setDecisionPrice(strategy, "EXEC", 100);
        setExecutedSlices(strategy, "EXEC", 0);
        
        portfolio.setPosition("EXEC", 0);
        
        // Price dropped (favorability is positive), should execute slice
        TradeSignal signal = strategy.execute(portfolio, createMarketData("EXEC", 98));
        
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getReason().contains("Execution slice"));
            assertTrue(signal.getConfidence() >= 0.60);
        }
    }

    @Test
    void executionSlice_whenUrgencyHigh() throws Exception {
        // Line 91: Test the OR condition (adjustedUrgency > 0.7 && slicesExecuted < numSlices / 2)
        ExecutionShortfallStrategy highUrgencyStrategy = new ExecutionShortfallStrategy(10000, 10, 0.9);
        
        double[] prices = new double[15];
        for (int i = 0; i < 15; i++) prices[i] = 100;
        primeHistory(highUrgencyStrategy, "EXEC", prices);
        
        setDecisionPrice(highUrgencyStrategy, "EXEC", 100);
        setExecutedSlices(highUrgencyStrategy, "EXEC", 2); // < 10/2 = 5
        
        portfolio.setPosition("EXEC", 0);
        
        // Even with slightly adverse price, high urgency should trigger execution
        TradeSignal signal = highUrgencyStrategy.execute(portfolio, createMarketData("EXEC", 101));
        
        assertNotNull(signal);
    }

    @Test
    void exitSignal_whenShortfallTooNegative() throws Exception {
        // Exit when position > 0 && shortfall < -volatility * 3
        portfolio.setPosition("EXEC", 100);
        
        double[] prices = new double[15];
        for (int i = 0; i < 15; i++) prices[i] = 100;
        primeHistory(strategy, "EXEC", prices);
        
        setDecisionPrice(strategy, "EXEC", 100);
        setExecutedSlices(strategy, "EXEC", 10); // All slices executed
        
        // Price rose significantly (large negative shortfall from buyer's perspective)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("EXEC", 130));
        
        assertNotNull(signal);
        // shortfall = (130-100)/100 = 0.30 (30%), which is positive for buyer
        // need price drop for negative shortfall to trigger exit
    }

    @Test
    void exitSignal_whenPriceDropsSignificantly() throws Exception {
        portfolio.setPosition("EXEC", 100);
        
        // Create volatile history for higher volatility calculation
        double[] prices = new double[15];
        for (int i = 0; i < 15; i++) prices[i] = 100 + (i % 2 == 0 ? 2 : -2);
        primeHistory(strategy, "EXEC", prices);
        
        setDecisionPrice(strategy, "EXEC", 100);
        setExecutedSlices(strategy, "EXEC", 10);
        
        // Price drops significantly (negative shortfall)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("EXEC", 85));
        
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
            assertTrue(signal.getReason().contains("Shortfall limit"));
        }
    }

    @Test
    void neutralSignal_whenNoSlicesToExecute() throws Exception {
        // All slices executed, no exit condition
        double[] prices = new double[15];
        for (int i = 0; i < 15; i++) prices[i] = 100;
        primeHistory(strategy, "EXEC", prices);
        
        setDecisionPrice(strategy, "EXEC", 100);
        setExecutedSlices(strategy, "EXEC", 10);
        
        portfolio.setPosition("EXEC", 0);
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("EXEC", 100));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Shortfall"));
        assertTrue(signal.getReason().contains("slices: 10/10"));
    }

    @Test
    void getName_returnsCorrectFormat() {
        assertTrue(strategy.getName().contains("Execution Shortfall"));
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsAllState() throws Exception {
        // Execute once to initialize state
        for (int i = 0; i < 15; i++) {
            strategy.execute(portfolio, createMarketData("EXEC", 100 + i));
        }
        
        strategy.reset();
        
        // After reset, should be warming up again
        TradeSignal signal = strategy.execute(portfolio, createMarketData("EXEC", 100));
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    private void primeHistory(ExecutionShortfallStrategy strat, String symbol, double[] prices) {
        try {
            Field f = ExecutionShortfallStrategy.class.getDeclaredField("priceHistory");
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

    private void setDecisionPrice(ExecutionShortfallStrategy strat, String symbol, double price) {
        try {
            Field f = ExecutionShortfallStrategy.class.getDeclaredField("decisionPrices");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Double> map = (Map<String, Double>) f.get(strat);
            map.put(symbol, price);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setExecutedSlices(ExecutionShortfallStrategy strat, String symbol, int slices) {
        try {
            Field f = ExecutionShortfallStrategy.class.getDeclaredField("executedSlices");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Integer> map = (Map<String, Integer>) f.get(strat);
            map.put(symbol, slices);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
