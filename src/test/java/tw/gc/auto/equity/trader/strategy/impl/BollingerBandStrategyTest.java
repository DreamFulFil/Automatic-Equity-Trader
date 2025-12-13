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

class BollingerBandStrategyTest {
    
    private BollingerBandStrategy strategy;
    private Portfolio portfolio;
    
    @BeforeEach
    void setUp() {
        strategy = new BollingerBandStrategy(20, 2.0, 0.002);
        
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
        assertEquals("Bollinger Band Mean Reversion", strategy.getName());
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
    void testOversoldCondition_GeneratesBuySignal() {
        // Feed stable prices around 22000
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 + (i % 2) * 5));
        }
        
        // Price drops significantly below lower band
        MarketData lowPrice = createMarketData(21700.0);
        TradeSignal signal = strategy.execute(portfolio, lowPrice);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getConfidence() > 0.65);
        assertTrue(signal.getReason().toLowerCase().contains("oversold"));
    }
    
    @Test
    void testOverboughtCondition_GeneratesShortSignal() {
        // Feed stable prices around 22000
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 + (i % 2) * 5));
        }
        
        // Price rises significantly above upper band
        MarketData highPrice = createMarketData(22300.0);
        TradeSignal signal = strategy.execute(portfolio, highPrice);
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getConfidence() > 0.65);
        assertTrue(signal.getReason().toLowerCase().contains("overbought"));
    }
    
    @Test
    void testNullMarketData_ReturnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }
    
    @Test
    void testReset_ClearsHistory() {
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData(22000.0 + i));
        }
        
        assertNotNull(strategy.getPriceHistory("AUTO_EQUITY_TRADER"));
        
        strategy.reset();
        
        assertNull(strategy.getPriceHistory("AUTO_EQUITY_TRADER"));
    }
    
    private MarketData createMarketData(double price) {
        return MarketData.builder()
                .symbol("AUTO_EQUITY_TRADER")
                .close(price)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
