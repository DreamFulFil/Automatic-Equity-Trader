package tw.gc.mtxfbot.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.Portfolio;
import tw.gc.mtxfbot.strategy.StrategyType;
import tw.gc.mtxfbot.strategy.TradeSignal;

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
        positions.put("MTXF", 0);
        
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
        assertNotNull(strategy.getExecutedVolume("MTXF"));
        assertEquals(1, strategy.getExecutedVolume("MTXF"));
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
        
        assertNotNull(strategy.getVwapHistory("MTXF"));
        
        strategy.reset();
        
        assertNull(strategy.getVwapHistory("MTXF"));
        assertNull(strategy.getExecutedVolume("MTXF"));
    }
    
    private MarketData createMarketData(double price, long volume) {
        return MarketData.builder()
                .symbol("MTXF")
                .close(price)
                .volume(volume)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
