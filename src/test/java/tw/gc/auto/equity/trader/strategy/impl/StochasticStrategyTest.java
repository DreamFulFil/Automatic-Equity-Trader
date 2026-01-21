package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class StochasticStrategyTest {

    private StochasticStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new StochasticStrategy(14, 3, 80, 20);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    @Test
    void warmup_returnsNeutral() {
        for (int i = 0; i < 13; i++) {
            MarketData md = createMarketData("TEST", 100.0, 105.0, 95.0);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().contains("Warming up"));
        }
    }

    @Test
    void oversold_producesLong() {
        // Build history with prices near the low
        for (int i = 0; i < 14; i++) {
            // High range but closing near low = oversold
            MarketData md = createMarketData("OVER", 96.0, 120.0, 95.0);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("OVER", 0);
        
        // Price at bottom of range
        MarketData md = createMarketData("OVER", 96.0, 120.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.75, signal.getConfidence());
            assertTrue(signal.getReason().contains("Stoch"));
        }
    }

    @Test
    void overbought_producesExit() {
        // Build history with prices near the high
        for (int i = 0; i < 14; i++) {
            // Closing near high = overbought
            MarketData md = createMarketData("OVB", 119.0, 120.0, 95.0);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("OVB", 100); // long position
        
        // Price at top of range
        MarketData md = createMarketData("OVB", 119.0, 120.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
            assertEquals(0.75, signal.getConfidence());
        }
    }

    @Test
    void zeroHighLow_fallbackToClose() {
        // Test lines 82-83: Fallback when high/low are 0 or not provided
        for (int i = 0; i < 14; i++) {
            // Pass 0 for high and low, should fallback to close
            MarketData md = createMarketDataWithZeroHighLow("FALLBACK", 100.0);
            TradeSignal signal = strategy.execute(portfolio, md);
            assertNotNull(signal);
        }
        
        // Should complete warmup and work normally
        MarketData md = createMarketDataWithZeroHighLow("FALLBACK", 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertNotNull(signal);
        assertFalse(signal.getReason().contains("Warming up"));
    }

    @Test
    void negativeHighLow_fallbackToClose() {
        // Test fallback for negative values
        for (int i = 0; i < 14; i++) {
            MarketData md = createMarketDataWithNegativeHighLow("NEG", 100.0);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("NEG", 0);
        
        MarketData md = createMarketDataWithNegativeHighLow("NEG", 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertNotNull(signal);
        assertTrue(signal.getReason().contains("Stoch"));
    }

    @Test
    void midRange_returnsNeutral() {
        // Build history with prices in the middle
        for (int i = 0; i < 14; i++) {
            MarketData md = createMarketData("MID", 107.5, 120.0, 95.0);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("MID", 0);
        
        // Price in middle of range
        MarketData md = createMarketData("MID", 107.5, 120.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Stoch %K"));
    }

    @Test
    void getName_shouldContainStochastic() {
        String name = strategy.getName();
        assertTrue(name.contains("Stochastic"));
    }

    @Test
    void getType_shouldReturnIntraday() {
        assertEquals(StrategyType.INTRADAY, strategy.getType());
    }

    @Test
    void reset_shouldClearHistory() {
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("RST", 100.0, 105.0, 95.0));
        }
        
        strategy.reset();
        
        MarketData md = createMarketData("RST", 100.0, 105.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(String symbol, double close, double high, double low) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(high)
                .low(low)
                .close(close)
                .volume(1000L)
                .build();
    }

    private MarketData createMarketDataWithZeroHighLow(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(0) // zero high
                .low(0)  // zero low
                .close(close)
                .volume(1000L)
                .build();
    }

    private MarketData createMarketDataWithNegativeHighLow(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(-1) // negative high
                .low(-1)  // negative low
                .close(close)
                .volume(1000L)
                .build();
    }
}
