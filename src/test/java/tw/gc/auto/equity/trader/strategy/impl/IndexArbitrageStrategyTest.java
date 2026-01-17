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

class IndexArbitrageStrategyTest {

    private IndexArbitrageStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new IndexArbitrageStrategy("TAIEX", 0.05);
        portfolio = mock(Portfolio.class);
    }

    @Test
    void execute_warmingUp_shouldReturnNeutral() {
        MarketData data = createMarketData("2330.TW", 500.0);
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void execute_undervaluedWithNoPosition_shouldReturnLongSignal() {
        // Build history: average around 500
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Price drops to 460 (8% below fair value of 500)
        MarketData data = createMarketData("2330.TW", 460.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("below fair value"));
        assertEquals(0.75, signal.getConfidence());
    }

    @Test
    void execute_overvaluedWithLongPosition_shouldReturnExitSignal() {
        // Build history: average around 500
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(100);
        
        // Price rises to 530 (6% above fair value)
        MarketData data = createMarketData("2330.TW", 530.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertTrue(signal.isExitSignal());
        assertTrue(signal.getReason().contains("above fair value"));
        assertEquals(0.70, signal.getConfidence());
    }

    @Test
    void execute_highlyOvervaluedWithNoPosition_shouldReturnShortSignal() {
        // Build history: average around 500
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Price rises to 560 (12% above fair value, exceeds 2*threshold=10%)
        MarketData data = createMarketData("2330.TW", 560.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("overvalued"));
        assertEquals(0.65, signal.getConfidence());
    }

    @Test
    void execute_normalDeviation_shouldReturnNeutral() {
        // Build history: average around 500
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Price at 510 (2% above, within threshold)
        MarketData data = createMarketData("2330.TW", 510.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Index deviation"));
    }

    @Test
    void execute_maxHistorySize_shouldLimitTo60() {
        for (int i = 0; i < 100; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Should work without error
        MarketData data = createMarketData("2330.TW", 600.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void execute_undervaluedWithShortPosition_shouldReturnLongSignal() {
        // Build history: average around 500
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(-100);
        
        // Price drops to 460 (8% below fair value)
        MarketData data = createMarketData("2330.TW", 460.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
    }

    @Test
    void getName_shouldReturnFormattedName() {
        assertEquals("Index Arbitrage (TAIEX, 5.0%)", strategy.getName());
    }

    @Test
    void getType_shouldReturnSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_shouldClearPriceHistory() {
        for (int i = 0; i < 25; i++) {
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
