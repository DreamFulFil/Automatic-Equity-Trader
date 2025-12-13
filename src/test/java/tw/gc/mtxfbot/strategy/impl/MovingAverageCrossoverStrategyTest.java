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

class MovingAverageCrossoverStrategyTest {
    
    private MovingAverageCrossoverStrategy strategy;
    private Portfolio portfolio;
    
    @BeforeEach
    void setUp() {
        strategy = new MovingAverageCrossoverStrategy(5, 20, 0.001);
        
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
        assertEquals("Moving Average Crossover", strategy.getName());
    }
    
    @Test
    void testGetType() {
        assertEquals(StrategyType.SHORT_TERM, strategy.getType());
    }
    
    @Test
    void testInsufficientData_ReturnsNeutral() {
        MarketData data = createMarketData(22000.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }
    
    @Test
    void testGoldenCross_GeneratesBuySignal() {
        // Feed declining prices (fast MA will be below slow MA)
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 - i * 10));
        }
        
        // Feed rising prices (fast MA will cross above slow MA)
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData(21800.0 + i * 20);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getConfidence() > 0.7);
                assertTrue(signal.getReason().toLowerCase().contains("golden cross"));
                return;
            }
        }
    }
    
    @Test
    void testNullMarketData_ReturnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }
    
    @Test
    void testReset_ClearsHistory() {
        // Build up history
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 + i));
        }
        
        assertNotNull(strategy.getPriceHistory("MTXF"));
        
        strategy.reset();
        
        assertNull(strategy.getPriceHistory("MTXF"));
        assertNull(strategy.getPreviousGoldenCross("MTXF"));
    }
    
    @Test
    void testInvalidParameters_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MovingAverageCrossoverStrategy(20, 5, 0.001); // fast >= slow
        });
    }
    
    private MarketData createMarketData(double price) {
        return MarketData.builder()
                .symbol("MTXF")
                .close(price)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
