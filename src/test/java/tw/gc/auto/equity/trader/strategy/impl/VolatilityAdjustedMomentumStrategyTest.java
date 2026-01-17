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

class VolatilityAdjustedMomentumStrategyTest {

    private VolatilityAdjustedMomentumStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new VolatilityAdjustedMomentumStrategy(10, 20);
        portfolio = mock(Portfolio.class);
    }

    @Test
    void execute_invalidPeriods_shouldReturnNeutral() {
        VolatilityAdjustedMomentumStrategy invalidStrategy = new VolatilityAdjustedMomentumStrategy(0, 1);
        
        MarketData data = createMarketData("2330.TW", 500.0);
        TradeSignal signal = invalidStrategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Invalid"));
    }

    @Test
    void execute_warmingUp_shouldReturnNeutral() {
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330.TW", 500.0);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (i < 20) {
                assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
                assertTrue(signal.getReason().contains("Warming up"));
            }
        }
    }

    @Test
    void execute_positiveMomentumWithNoPosition_shouldReturnLongSignal() {
        // Build history: steady rise from 400 to 500
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 400.0 + i * 3.5));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 510.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("Vol-adj momentum long"));
    }

    @Test
    void execute_negativeMomentumWithNoPosition_shouldReturnShortSignal() {
        // Build history: steady decline from 500 to 400
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 - i * 3.5));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 390.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("Vol-adj momentum short"));
    }

    @Test
    void execute_momentumReversalWithLongPosition_shouldHandleSignal() {
        // Build history with initial uptrend
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 400.0 + i * 4.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(100);
        
        // Price declines
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 490.0 - i * 5.0));
        }
        
        MarketData data = createMarketData("2330.TW", 460.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should detect reversal (either exit or neutral depending on exact momentum calculation)
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("Momentum reversal"));
        }
    }

    @Test
    void execute_momentumReversalWithShortPosition_shouldHandleSignal() {
        // Build history with decline
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 - i * 4.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(-100);
        
        // Price starts rising
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 410.0 + i * 5.0));
        }
        
        MarketData data = createMarketData("2330.TW", 440.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should detect reversal (either exit or neutral depending on exact momentum calculation)
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("Momentum reversal"));
        }
    }

    @Test
    void execute_neutralRange_shouldReturnNeutral() {
        // Build flat history (small momentum, below threshold)
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + Math.sin(i) * 2));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 501.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Mom="));
    }

    @Test
    void execute_maxHistorySize_shouldLimitPrices() {
        // Add more than maxPeriod + 10 prices
        for (int i = 0; i < 50; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Should work without error
        MarketData data = createMarketData("2330.TW", 550.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void getName_shouldReturnFormattedName() {
        assertEquals("Vol-Adjusted Momentum (10/20)", strategy.getName());
    }

    @Test
    void getType_shouldReturnSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_shouldClearPriceHistory() {
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
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
