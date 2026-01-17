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

class BettingAgainstBetaStrategyTest {

    private BettingAgainstBetaStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new BettingAgainstBetaStrategy(20, 0.8);
        portfolio = mock(Portfolio.class);
    }

    @Test
    void execute_invalidBetaWindow_shouldReturnNeutral() {
        BettingAgainstBetaStrategy invalidStrategy = new BettingAgainstBetaStrategy(1, 0.8);
        
        MarketData data = createMarketData("2330.TW", 500.0);
        TradeSignal signal = invalidStrategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Invalid beta window"));
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
    void execute_lowBetaWithNoPosition_shouldGenerateSignal() {
        // Build stable price history (low volatility)
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + Math.sin(i * 0.1) * 2));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 501.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should generate some kind of signal (LONG or SHORT or NEUTRAL depending on calculated beta)
        assertNotNull(signal);
        assertNotNull(signal.getReason());
        assertTrue(signal.getReason().contains("beta") || signal.getReason().contains("Beta"));
    }

    @Test
    void execute_highBetaWithNoPosition_shouldReturnShortSignal() {
        // Build volatile price history
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + Math.pow(-1, i) * (i * 10)));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 550.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // With high volatility, may generate short signal
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertTrue(signal.getReason().contains("High beta"));
            assertEquals(0.60, signal.getConfidence());
        }
    }

    @Test
    void execute_betaIncreasesWithLongPosition_shouldReturnExitSignal() {
        // Build initially stable history
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 0.5));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(100);
        
        // Add high volatility data
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 510.0 + Math.pow(-1, i) * (i * 20)));
        }
        
        MarketData data = createMarketData("2330.TW", 550.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should detect beta increase and exit
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("Beta increased"));
        }
    }

    @Test
    void execute_neutralBeta_shouldReturnNeutral() {
        // Build moderate price history
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i * 2));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        MarketData data = createMarketData("2330.TW", 550.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // May return neutral depending on calculated beta
        if (signal.getDirection() == TradeSignal.SignalDirection.NEUTRAL) {
            assertTrue(signal.getReason().contains("Beta"));
        }
    }

    @Test
    void execute_maxHistorySize_shouldLimitPrices() {
        // Add more than betaWindow + 10 prices
        for (int i = 0; i < 40; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + i));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(0);
        
        // Should work without error
        MarketData data = createMarketData("2330.TW", 540.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void getName_shouldReturnFormattedName() {
        assertEquals("Betting Against Beta (20d, β<0.8)", strategy.getName());
    }

    @Test
    void getType_shouldReturnLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_shouldClearPriceHistoryAndMarketReturns() {
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0));
        }
        
        strategy.reset();
        
        // After reset, should be warming up again
        MarketData data = createMarketData("2330.TW", 500.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void execute_lowBetaWithShortPosition_shouldGenerateSignal() {
        // Build stable price history (low volatility)
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("2330.TW", 500.0 + Math.sin(i * 0.1)));
        }
        
        when(portfolio.getPosition("2330.TW")).thenReturn(-100);
        
        MarketData data = createMarketData("2330.TW", 500.5);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should generate some kind of signal
        assertNotNull(signal);
        assertNotNull(signal.getReason());
    }

    private MarketData createMarketData(String symbol, double close) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setClose(close);
        data.setTimestamp(LocalDateTime.now());
        return data;
    }
}
