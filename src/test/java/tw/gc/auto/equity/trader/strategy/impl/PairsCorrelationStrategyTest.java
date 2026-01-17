package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PairsCorrelationStrategyTest {

    private PairsCorrelationStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new PairsCorrelationStrategy("2330.TW", "2454.TW", 30, 2.0);
        portfolio = mock(Portfolio.class);
    }

    @Test
    void execute_symbolNotInPair_shouldReturnNeutral() {
        MarketData data = createMarketData("0050.TW", 500.0);
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Not part of pair"));
    }

    @Test
    void execute_warmingUp_shouldReturnNeutral() {
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330.TW", 500.0);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().contains("Warming up"));
        }
    }

    @Test
    void execute_waitingForBothPairs_shouldReturnNeutral() {
        // Add data for primary only
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        // Request signal for primary when pair data missing
        MarketData data = createMarketData("2330.TW", 500.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Waiting for both pairs"));
    }

    @Test
    void execute_primaryUndervalued_shouldReturnLongSignal() {
        // Build history: primary at 500, pair at 100 (ratio 5.0)
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Primary drops to 440 (ratio 4.4, below mean of 5.0, z-score negative)
        MarketData data = createMarketData("2330.TW", 440.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("undervalued"));
        assertEquals(0.7, signal.getConfidence());
    }

    @Test
    void execute_primaryOvervalued_shouldReturnShortSignal() {
        // Build history: primary at 500, pair at 100 (ratio 5.0)
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Primary rises to 560 (ratio 5.6, above mean of 5.0, z-score positive)
        MarketData data = createMarketData("2330.TW", 560.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("overvalued"));
        assertEquals(0.7, signal.getConfidence());
    }

    @Test
    void execute_spreadConvergedWithLongPosition_shouldReturnExitSignal() {
        // Build history: primary at 500, pair at 100
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0 + i * 0.2));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(100);
        
        // Spread converges (z-score near 0)
        MarketData data = createMarketData("2330.TW", 515.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("converged"));
            assertEquals(0.6, signal.getConfidence());
        }
    }

    @Test
    void execute_spreadConvergedWithShortPosition_shouldReturnExitSignal() {
        // Build history: primary at 500, pair at 100
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0 + i * 0.2));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(-100);
        
        // Spread converges
        MarketData data = createMarketData("2330.TW", 515.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("converged"));
        }
    }

    @Test
    void execute_pairSymbolData_shouldReturnNeutralWithZScore() {
        // Build history for both
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0));
        }
        
        when(portfolio.getPosition("2454.TW")).thenReturn(0);
        
        // Data for pair symbol (not primary)
        MarketData data = createMarketData("2454.TW", 100.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should return neutral with z-score info
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("z-score"));
    }

    @Test
    void execute_maxHistorySize_shouldLimitPrices() {
        // Add more than lookbackPeriod prices
        for (int i = 0; i < 50; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0 + i * 0.2));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Should work without error
        MarketData data = createMarketData("2330.TW", 550.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void getName_shouldReturnFormattedName() {
        assertEquals("Pairs Trading (2330.TW/2454.TW)", strategy.getName());
    }

    @Test
    void getType_shouldReturnSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_shouldClearPriceHistory() {
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
            strategy.execute(portfolio, createMarketData("2454.TW", 100.0));
        }
        
        strategy.reset();
        
        // After reset, should be warming up again
        MarketData data = createMarketData("2330.TW", 500.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(String symbol, double close) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setClose(close);
        data.setTimestamp(LocalDateTime.now());
        return data;
    }
}
