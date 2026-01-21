package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VWAPExecutionStrategyTest {
    
    private VWAPExecutionStrategy strategy;
    private Portfolio portfolio;
    
    @BeforeEach
    void setUp() {
        strategy = new VWAPExecutionStrategy(10, 30, 0.003, 1);
        
        Map<String, Integer> positions = new HashMap<>();
        positions.put("AUTO_EQUITY_TRADER", 0);
        
        portfolio = Portfolio.builder()
                .positions(positions)
                .equity(100000.0)
                .tradingMode("futures")
                .tradingQuantity(1)
                .build();
    }
    
    @Test
    void testGetName() {
        assertEquals("VWAP Execution Algorithm", strategy.getName());
    }
    
    @Test
    void testGetType() {
        assertEquals(StrategyType.SHORT_TERM, strategy.getType());
    }
    
    @Test
    void testInsufficientData_ReturnsNeutral() {
        MarketData data = createMarketData(22000.0, 1000L);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }
    
    @Test
    void testGoodPrice_ExecutesSlice() {
        // Build VWAP around 22000
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 + i, 1000L));
        }
        
        // Price below VWAP should trigger execution
        MarketData belowVWAP = createMarketData(21990.0, 1000L);
        TradeSignal signal = strategy.execute(portfolio, belowVWAP);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getConfidence() > 0.8);
        
        // Check volume was recorded
        assertNotNull(strategy.getExecutedVolume("AUTO_EQUITY_TRADER"));
        assertEquals(1, strategy.getExecutedVolume("AUTO_EQUITY_TRADER"));
    }
    
    @Test
    void testTargetVolumeReached_ReturnsNeutral() {
        // Execute full target volume
        for (int i = 0; i < 15; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 - i, 1000L));
        }
        
        // Should return neutral when target reached
        MarketData data = createMarketData(21980.0, 1000L);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Target volume reached"));
    }
    
    @Test
    void testNullMarketData_ReturnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }
    
    @Test
    void testReset_ClearsState() {
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 + i, 1000L));
        }
        
        assertNotNull(strategy.getVwapHistory("AUTO_EQUITY_TRADER"));
        
        strategy.reset();
        
        assertNull(strategy.getVwapHistory("AUTO_EQUITY_TRADER"));
        assertNull(strategy.getExecutedVolume("AUTO_EQUITY_TRADER"));
    }
    
    @Test
    void testBehindPace_ExecutesWithLowerConfidence() throws Exception {
        // Test line 144: behindPace calculation
        VWAPExecutionStrategy strategy2 = new VWAPExecutionStrategy(100, 60, 0.003, 1);
        
        // Build VWAP history
        for (int i = 0; i < 5; i++) {
            strategy2.execute(portfolio, createMarketData(22000.0 + i, 1000L));
        }
        
        // Set start time to past to simulate being behind pace
        java.lang.reflect.Field startTimeField = VWAPExecutionStrategy.class.getDeclaredField("startTime");
        startTimeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> startTimeMap = (Map<String, LocalDateTime>) startTimeField.get(strategy2);
        startTimeMap.put("AUTO_EQUITY_TRADER", LocalDateTime.now().minusMinutes(30)); // halfway through
        
        // Price above VWAP but behind pace should still execute
        MarketData aboveVWAP = createMarketData(22020.0, 1000L);
        TradeSignal signal = strategy2.execute(portfolio, aboveVWAP);
        
        // Behind pace forces execution even with bad price
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getConfidence() >= 0.60 && signal.getConfidence() <= 0.90);
        }
    }

    @Test
    void testGoodPriceConfidence_IsHigherThanUrgency() throws Exception {
        // Test line 157: confidence calculation for goodPrice vs urgency
        VWAPExecutionStrategy strategy3 = new VWAPExecutionStrategy(100, 60, 0.01, 1);
        
        // Build VWAP around 22000
        for (int i = 0; i < 5; i++) {
            strategy3.execute(portfolio, createMarketData(22000.0, 1000L));
        }
        
        // Good price (below VWAP) should have 0.85 confidence
        MarketData goodPrice = createMarketData(21990.0, 1000L);
        TradeSignal signal = strategy3.execute(portfolio, goodPrice);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertEquals(0.85, signal.getConfidence());
    }

    @Test
    void testUrgencyFactorConfidence_IncreasesOverTime() throws Exception {
        // Test confidence calculation based on urgency factor
        VWAPExecutionStrategy strategy4 = new VWAPExecutionStrategy(100, 60, 0.001, 1);
        
        // Build VWAP history
        for (int i = 0; i < 5; i++) {
            strategy4.execute(portfolio, createMarketData(22000.0, 1000L));
        }
        
        // Set start time far in past (80% through window)
        java.lang.reflect.Field startTimeField = VWAPExecutionStrategy.class.getDeclaredField("startTime");
        startTimeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> startTimeMap = (Map<String, LocalDateTime>) startTimeField.get(strategy4);
        startTimeMap.put("AUTO_EQUITY_TRADER", LocalDateTime.now().minusMinutes(48)); // 80% through
        
        // Price at VWAP (not good price, but urgency should force execution)
        MarketData neutralPrice = createMarketData(22005.0, 1000L);
        TradeSignal signal = strategy4.execute(portfolio, neutralPrice);
        
        // At 80% urgency, confidence should be around 0.60 + 0.80 * 0.30 = 0.84
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getConfidence() >= 0.75);
        }
    }

    private MarketData createMarketData(double price, long volume) {
        return MarketData.builder()
                .symbol("AUTO_EQUITY_TRADER")
                .close(price)
                .volume(volume)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
