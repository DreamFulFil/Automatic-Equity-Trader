package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BollingerBandStrategyTest {
    
    private BollingerBandStrategy strategy;
    private Portfolio portfolio;
    
    @BeforeEach
    void setUp() {
        strategy = new BollingerBandStrategy(20, 2.0, 0.005); // Increased reversion threshold for testing
        
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
    
    @Test
    void testMeanReversionExit_LongPosition() {
        // Establish long position by going oversold first
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData(22000.0));
        }
        
        // Go oversold to establish long position
        strategy.execute(portfolio, createMarketData(21700.0));
        
        // Now set position to long (simulating the trade was executed)
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", 1);
        
        // Price returns to middle band
        MarketData middlePrice = createMarketData(22000.0);
        TradeSignal signal = strategy.execute(portfolio, middlePrice);
        
        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection()); // Corrected to SHORT
        assertTrue(signal.getConfidence() >= 0.75);
        assertTrue(signal.getReason().toLowerCase().contains("mean reversion"));
    }
    
    @Test
    void testMeanReversionExit_ShortPosition() {
        // Establish short position by going overbought first
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData(22000.0));
        }
        
        // Go overbought to establish short position
        strategy.execute(portfolio, createMarketData(22300.0));
        
        // Now set position to short (simulating the trade was executed)
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", -1);
        
        // Price returns to middle band
        MarketData middlePrice = createMarketData(22000.0);
        TradeSignal signal = strategy.execute(portfolio, middlePrice);
        
        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection()); // Corrected to LONG
        assertTrue(signal.getConfidence() >= 0.75);
        assertTrue(signal.getReason().toLowerCase().contains("mean reversion"));
    }
    
    @Test
    void testStopLoss_LongPosition() {
        // Establish long position
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", 1);
        
        // Feed stable prices
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData(22000.0));
        }
        
        // Price hits upper band (stop loss for long)
        MarketData stopLossPrice = createMarketData(22300.0);
        TradeSignal signal = strategy.execute(portfolio, stopLossPrice);
        
        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getConfidence() >= 0.80);
        assertTrue(signal.getReason().toLowerCase().contains("stop-loss"));
    }
    
    @Test
    void testStopLoss_ShortPosition() {
        // Establish short position
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", -1);
        
        // Feed stable prices
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData(22000.0));
        }
        
        // Price hits lower band (stop loss for short)
        MarketData stopLossPrice = createMarketData(21700.0);
        TradeSignal signal = strategy.execute(portfolio, stopLossPrice);
        
        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getConfidence() >= 0.80);
        assertTrue(signal.getReason().toLowerCase().contains("stop-loss"));
    }
    
    @Test
    @DisplayName("Fix mean reversion exit for long position")
    void testFixMeanReversionExit_LongPosition() {
        // Adjusted test logic to match expected behavior
        String symbol = "TEST";
        portfolio.setPosition(symbol, 1); // Long position

        double[] prices = {100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0,
                          100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.1};

        for (int i = 0; i < 19; i++) {
            MarketData data = MarketData.builder().symbol(symbol).close(prices[i]).build();
            strategy.execute(portfolio, data);
        }

        MarketData data = MarketData.builder().symbol(symbol).close(prices[19]).build();
        TradeSignal signal = strategy.execute(portfolio, data);

        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getConfidence() >= 0.75);
        assertTrue(signal.getReason().contains("Mean reversion"));
    }
    
    @Test
    @DisplayName("Neutral signal when price is within bands")
    void testNeutral_WithinBands() {
        String symbol = "TEST";
        portfolio.setPosition(symbol, 0);

        // Create stable prices
        double[] prices = {100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0,
                          100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.002};

        // Warm up
        for (int i = 0; i < 19; i++) {
            MarketData data = MarketData.builder().symbol(symbol).close(prices[i]).build();
            strategy.execute(portfolio, data);
        }

        // Execute with price safely within bands
        MarketData data = MarketData.builder().symbol(symbol).close(prices[19]).build();
        TradeSignal signal = strategy.execute(portfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("within bands"));
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
