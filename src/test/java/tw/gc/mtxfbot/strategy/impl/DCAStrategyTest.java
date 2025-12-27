package tw.gc.mtxfbot.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.Portfolio;
import tw.gc.mtxfbot.strategy.StrategyType;
import tw.gc.mtxfbot.strategy.TradeSignal;
import tw.gc.mtxfbot.strategy.TradeSignal.SignalDirection;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DCA Strategy
 * Tests buy signal generation, interval timing, and target position limits
 */
class DCAStrategyTest {
    
    private DCAStrategy strategy;
    private Portfolio portfolio;
    private MarketData marketData;
    
    @BeforeEach
    void setUp() {
        // Default: 30-minute interval, unlimited target
        strategy = new DCAStrategy(30, 100);
        
        // Create test portfolio (flat position)
        Map<String, Integer> positions = new HashMap<>();
        positions.put("MTXF", 0);
        
        portfolio = Portfolio.builder()
                .positions(positions)
                .equity(100000.0)
                .availableMargin(80000.0)
                .dailyPnL(0.0)
                .weeklyPnL(0.0)
                .tradingMode("futures")
                .tradingQuantity(1)
                .build();
        
        // Create test market data
        marketData = MarketData.builder()
                .symbol("MTXF")
                .close(22000.0)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    @Test
    void testGetName() {
        assertEquals("Dollar-Cost Averaging (DCA)", strategy.getName());
    }
    
    @Test
    void testGetType() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }
    
    @Test
    void testFirstPurchase_GeneratesBuySignal() {
        TradeSignal signal = strategy.execute(portfolio, marketData);
        
        assertNotNull(signal);
        assertEquals(SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getConfidence() > 0.8);
        assertFalse(signal.isExitSignal());
        assertTrue(signal.getReason().contains("DCA"));
    }
    
    @Test
    void testRepeatedCallsWithinInterval_ReturnsNeutral() {
        // First call generates BUY
        TradeSignal signal1 = strategy.execute(portfolio, marketData);
        assertEquals(SignalDirection.LONG, signal1.getDirection());
        
        // Immediate second call within interval returns NEUTRAL
        TradeSignal signal2 = strategy.execute(portfolio, marketData);
        assertEquals(SignalDirection.NEUTRAL, signal2.getDirection());
        assertTrue(signal2.getReason().contains("minutes"));
    }
    
    @Test
    void testTargetPositionReached_ReturnsNeutral() {
        // Set position to target
        portfolio.getPositions().put("MTXF", 100);
        
        TradeSignal signal = strategy.execute(portfolio, marketData);
        
        assertEquals(SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Target position reached"));
    }
    
    @Test
    void testTargetPositionExceeded_ReturnsNeutral() {
        // Set position above target
        portfolio.getPositions().put("MTXF", 150);
        
        TradeSignal signal = strategy.execute(portfolio, marketData);
        
        assertEquals(SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Target position reached"));
    }
    
    @Test
    void testNullMarketData_ReturnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        
        assertEquals(SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No market data", signal.getReason());
    }
    
    @Test
    void testNullSymbol_ReturnsNeutral() {
        marketData.setSymbol(null);
        
        TradeSignal signal = strategy.execute(portfolio, marketData);
        
        assertEquals(SignalDirection.NEUTRAL, signal.getDirection());
    }
    
    @Test
    void testReset_ClearsState() {
        // Generate first signal
        strategy.execute(portfolio, marketData);
        assertNotNull(strategy.getLastPurchaseTime("MTXF"));
        
        // Reset
        strategy.reset();
        
        // Verify state cleared
        assertNull(strategy.getLastPurchaseTime("MTXF"));
        
        // Should generate BUY signal immediately after reset
        TradeSignal signal = strategy.execute(portfolio, marketData);
        assertEquals(SignalDirection.LONG, signal.getDirection());
    }
    
    @Test
    void testCustomInterval_5Minutes() {
        DCAStrategy shortInterval = new DCAStrategy(5, 100);
        
        // First purchase
        TradeSignal signal1 = shortInterval.execute(portfolio, marketData);
        assertEquals(SignalDirection.LONG, signal1.getDirection());
        
        // Within 5 minutes - should be neutral
        TradeSignal signal2 = shortInterval.execute(portfolio, marketData);
        assertEquals(SignalDirection.NEUTRAL, signal2.getDirection());
    }
    
    @Test
    void testDefaultConstructor() {
        DCAStrategy defaultStrategy = new DCAStrategy();
        assertEquals("Dollar-Cost Averaging (DCA)", defaultStrategy.getName());
        
        // Should work with default settings
        TradeSignal signal = defaultStrategy.execute(portfolio, marketData);
        assertNotNull(signal);
    }
    
    @Test
    void testPartialPosition_StillGeneratesBuySignal() {
        // Position at 50%, below target of 100
        portfolio.getPositions().put("MTXF", 50);
        
        TradeSignal signal = strategy.execute(portfolio, marketData);
        
        assertEquals(SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("50/100"));
    }
    
    @Test
    void testMultipleSymbols_IndependentTracking() {
        // Create market data for different symbol
        MarketData stockData = MarketData.builder()
                .symbol("2454.TW")
                .close(1100.0)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
        
        portfolio.getPositions().put("2454.TW", 0);
        
        // First purchase for MTXF
        TradeSignal signal1 = strategy.execute(portfolio, marketData);
        assertEquals(SignalDirection.LONG, signal1.getDirection());
        
        // First purchase for 2454.TW should also be LONG (independent tracking)
        TradeSignal signal2 = strategy.execute(portfolio, stockData);
        assertEquals(SignalDirection.LONG, signal2.getDirection());
        
        // Second call for MTXF should be NEUTRAL (within interval)
        TradeSignal signal3 = strategy.execute(portfolio, marketData);
        assertEquals(SignalDirection.NEUTRAL, signal3.getDirection());
    }
}
