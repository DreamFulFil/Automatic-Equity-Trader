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

class MomentumTradingStrategyTest {

    private MomentumTradingStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new MomentumTradingStrategy(0.02);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    @Test
    void nullMarketData_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No market data", signal.getReason());
    }

    @Test
    void nullSymbol_returnsNeutral() {
        MarketData md = MarketData.builder()
                .symbol(null)
                .close(100.0)
                .build();
        TradeSignal signal = strategy.execute(portfolio, md);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void warmup_returnsNeutral() {
        for (int i = 0; i < 4; i++) {
            MarketData md = createMarketData("TEST", 100.0 + i);
            TradeSignal signal = strategy.execute(portfolio, md);
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().contains("Warming up"));
        }
    }

    @Test
    void strongPositiveMomentum_producesLong() {
        // Build upward momentum
        for (int i = 0; i < 5; i++) {
            MarketData md = createMarketData("LONG", 100.0 + i * 2);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("LONG", 0);
        
        // Strong upward move
        MarketData md = createMarketData("LONG", 115.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertEquals(0.75, signal.getConfidence());
        assertTrue(signal.getReason().contains("Strong momentum"));
    }

    @Test
    void strongNegativeMomentum_producesShort() {
        // Test lines 45-46: Short signal on negative momentum
        // Build downward momentum
        for (int i = 0; i < 5; i++) {
            MarketData md = createMarketData("SHORT", 100.0 - i * 2);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("SHORT", 0); // no position
        
        // Strong downward move
        MarketData md = createMarketData("SHORT", 85.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertEquals(0.75, signal.getConfidence());
        assertTrue(signal.getReason().contains("Negative momentum"));
    }

    @Test
    void noMomentum_returnsNeutral() {
        // Build flat price history
        for (int i = 0; i < 6; i++) {
            MarketData md = createMarketData("NEUT", 100.0);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("NEUT", 0);
        
        MarketData md = createMarketData("NEUT", 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No momentum signal", signal.getReason());
    }

    @Test
    void existingPosition_noSignal() {
        // Build upward momentum
        for (int i = 0; i < 5; i++) {
            MarketData md = createMarketData("POS", 100.0 + i);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("POS", 100); // already have position
        
        MarketData md = createMarketData("POS", 115.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        // With position already, no new signal even with momentum
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void getName_shouldReturnMomentumTrading() {
        assertEquals("Momentum Trading", strategy.getName());
    }

    @Test
    void getType_shouldReturnShortTerm() {
        assertEquals(StrategyType.SHORT_TERM, strategy.getType());
    }

    @Test
    void reset_shouldClearHistory() {
        for (int i = 0; i < 10; i++) {
            strategy.execute(portfolio, createMarketData("RST", 100.0));
        }
        
        strategy.reset();
        
        MarketData md = createMarketData("RST", 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(1000L)
                .build();
    }
}
