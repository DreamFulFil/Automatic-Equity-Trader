package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class ChaikinMoneyFlowStrategyTest {

    private ChaikinMoneyFlowStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new ChaikinMoneyFlowStrategy(20);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_returnsWarmingUp() {
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void execute_withBullishCMF_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Feed data with strong buying pressure (close near high)
        // CMF > 0.05 when buying pressure dominates
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(101.0)  // Close near high
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(101.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Line 58-59: CMF > 0.05 && position <= 0 -> LONG
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("CMF bullish"));
    }

    @Test
    void execute_withBearishCMF_returnsShortSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Feed data with strong selling pressure (close near low)
        // CMF < -0.05 when selling pressure dominates
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(99.0)  // Close near low
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(99.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Line 60-61: CMF < -0.05 && position >= 0 -> SHORT
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("CMF bearish"));
    }

    @Test
    void execute_withNeutralCMF_returnsNeutral() {
        portfolio.setPosition("TEST", 0);
        
        // Feed data with balanced pressure (close at midpoint)
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)  // Close at midpoint
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("CMF="));
    }

    @Test
    void execute_withLongPosition_skipsBullishSignal() {
        portfolio.setPosition("TEST", 10);
        
        // Feed bullish data
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(101.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(101.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // position > 0, so CMF bullish check (position <= 0) fails
        assertNotEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
    }

    @Test
    void execute_withShortPosition_skipsBearishSignal() {
        portfolio.setPosition("TEST", -10);
        
        // Feed bearish data
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(99.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(99.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // position < 0, so CMF bearish check (position >= 0) fails
        assertNotEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("Chaikin Money Flow", strategy.getName());
    }

    @Test
    void getType_returnsShortTerm() {
        assertEquals(StrategyType.SHORT_TERM, strategy.getType());
    }

    @Test
    void reset_clearsHistory() {
        // Build up history
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        strategy.reset();
        
        // After reset, should be back to warming up
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void defaultConstructor_usesPeriod20() {
        ChaikinMoneyFlowStrategy defaultStrategy = new ChaikinMoneyFlowStrategy();
        assertEquals("Chaikin Money Flow", defaultStrategy.getName());
    }
}
