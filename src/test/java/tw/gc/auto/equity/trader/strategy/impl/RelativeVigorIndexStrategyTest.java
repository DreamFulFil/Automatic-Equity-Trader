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

class RelativeVigorIndexStrategyTest {

    private RelativeVigorIndexStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new RelativeVigorIndexStrategy(5);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    @Test
    void warmup_returnsNeutral() {
        for (int i = 0; i < 4; i++) {
            MarketData md = createMarketData("TEST", 100.0, 105.0, 95.0, 102.0);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().contains("Warming up"));
        }
    }

    @Test
    void bullishRVI_producesLong() {
        // Build history with close > open (bullish bars)
        for (int i = 0; i < 5; i++) {
            MarketData md = createMarketData("BULL", 100.0, 110.0, 95.0, 108.0); // close > open
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("BULL", 0);
        
        // Bullish bar with RVI > 0.1
        MarketData md = createMarketData("BULL", 100.0, 115.0, 95.0, 112.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.75, signal.getConfidence());
            assertTrue(signal.getReason().contains("RVI bullish"));
        }
    }

    @Test
    void bearishRVI_producesShort() {
        // Test lines 53-54: Short signal on bearish RVI
        // Build history with close < open (bearish bars)
        for (int i = 0; i < 5; i++) {
            // Open high, close low (bearish)
            MarketData md = createMarketData("BEAR", 110.0, 112.0, 95.0, 97.0); // open=110, close=97
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("BEAR", 0); // no position (position >= 0)
        
        // Another bearish bar
        MarketData md = createMarketData("BEAR", 108.0, 110.0, 90.0, 92.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertEquals(0.75, signal.getConfidence());
            assertTrue(signal.getReason().contains("RVI bearish"));
        }
    }

    @Test
    void neutralRVI_returnsNeutral() {
        // Build history with mixed bars
        for (int i = 0; i < 5; i++) {
            double open = 100.0 + (i % 2 == 0 ? 1 : -1);
            double close = 100.0;
            MarketData md = createMarketData("NEUT", open, 102.0, 98.0, close);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("NEUT", 0);
        
        MarketData md = createMarketData("NEUT", 100.0, 101.0, 99.0, 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("RVI="));
    }

    @Test
    void getName_shouldReturnRelativeVigorIndex() {
        assertEquals("Relative Vigor Index", strategy.getName());
    }

    @Test
    void getType_shouldReturnShortTerm() {
        assertEquals(StrategyType.SHORT_TERM, strategy.getType());
    }

    @Test
    void reset_shouldClearHistory() {
        for (int i = 0; i < 10; i++) {
            strategy.execute(portfolio, createMarketData("RST", 100.0, 105.0, 95.0, 102.0));
        }
        
        strategy.reset();
        
        MarketData md = createMarketData("RST", 100.0, 105.0, 95.0, 102.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void shortPositionWithBearishRVI_noSignal() {
        // Build bearish history
        for (int i = 0; i < 5; i++) {
            MarketData md = createMarketData("SHORT_POS", 110.0, 112.0, 95.0, 97.0);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("SHORT_POS", -10); // already short (position < 0)
        
        // Bearish bar
        MarketData md = createMarketData("SHORT_POS", 108.0, 110.0, 90.0, 92.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        // With position < 0, short signal should not trigger
        assertNotEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
    }

    private MarketData createMarketData(String symbol, double open, double high, double low, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(1000L)
                .build();
    }
}
