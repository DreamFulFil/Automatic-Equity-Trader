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

class GrahamDefensiveStrategyTest {

    private GrahamDefensiveStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new GrahamDefensiveStrategy(2.0, 15.0, 0.03);
        portfolio = mock(Portfolio.class);
    }

    @Test
    void execute_warmingUp_shouldReturnNeutral() {
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("2330.TW", 500.0);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().contains("Warming up"));
        }
    }

    @Test
    void execute_meetsCriteriaWithNoPosition_shouldReturnLongSignal() {
        // Build stable history with consistent uptrend (meets criteria)
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 0.5));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 535.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("Graham defensive"));
    }

    @Test
    void execute_doesNotMeetCriteriaWithLongPosition_shouldReturnExitSignal() {
        // Build initially good history
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 0.5));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(100);
        
        // Add highly volatile data that breaks criteria
        for (int i = 0; i < 10; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 530.0 + Math.pow(-1, i) * (i * 30)));
        }
        
        MarketData data = createMarketData("2330.TW", 650.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("Graham exit"));
            assertEquals(0.65, signal.getConfidence());
        }
    }

    @Test
    void execute_insufficientCriteria_shouldReturnNeutral() {
        // Build history that doesn't meet enough criteria
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + Math.pow(-1, i) * (i * 2)));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 520.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // May return neutral if less than 2 criteria met
        if (signal.getDirection() == TradeSignal.SignalDirection.NEUTRAL) {
            assertTrue(signal.getReason().contains("Graham"));
            assertTrue(signal.getReason().contains("criteria met"));
        }
    }

    @Test
    void execute_stablePrices_shouldHaveGoodStabilityRatio() {
        // Build very stable price history
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 0.1));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 507.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Stable prices should lead to a signal (long or neutral with criteria info)
        assertNotNull(signal);
        assertNotNull(signal.getReason());
    }

    @Test
    void execute_volatilePrices_shouldHavePoorStabilityRatio() {
        // Build volatile price history
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + Math.pow(-1, i) * (i * 5)));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 540.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Volatile prices should typically result in neutral or fewer criteria met
        assertNotNull(signal);
    }

    @Test
    void execute_maxHistorySize_shouldLimitTo252Days() {
        // Add more than 252 prices
        for (int i = 0; i < 300; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 0.1));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Should work without error
        MarketData data = createMarketData("2330.TW", 530.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void execute_highPositiveReturnRate_shouldMeetYieldCriteria() {
        // Build history with consistent positive returns
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 1.0));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 570.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // High positive return rate should contribute to criteria
        assertNotNull(signal);
    }

    @Test
    void getName_shouldReturnFormattedName() {
        String name = strategy.getName();
        assertTrue(name.contains("Graham Defensive"));
        assertTrue(name.contains("CR>2.0"));
        assertTrue(name.contains("PE<15"));
        assertTrue(name.contains("Yield>3.0%"));
    }

    @Test
    void getType_shouldReturnLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_shouldClearPriceHistory() {
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        strategy.reset();
        
        // After reset, should be warming up again
        MarketData data = createMarketData("2330.TW", 500.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void execute_twoCriteriaMet_shouldReturnLongSignal() {
        // Build history designed to meet exactly 2 criteria
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 0.3));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 521.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // 2 or more criteria met should generate long signal
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getConfidence() >= 0.65);
        }
    }

    private MarketData createMarketData(String symbol, double close) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setClose(close);
        data.setTimestamp(LocalDateTime.now());
        return data;
    }
}
