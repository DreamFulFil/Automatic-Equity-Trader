package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class LowVolatilityAnomalyStrategyTest {

    private LowVolatilityAnomalyStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new LowVolatilityAnomalyStrategy(20, 30);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInvalidVolatilityWindow_returnsNeutral() {
        // Create strategy with volatilityWindow < 2 to cover line 51
        LowVolatilityAnomalyStrategy invalidStrategy = new LowVolatilityAnomalyStrategy(1, 30);
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        TradeSignal signal = invalidStrategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("Invalid volatility window", signal.getReason());
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
    void execute_withLowVolatility_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Feed stable prices to create low volatility
        for (int i = 0; i < 70; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 + (i % 2 == 0 ? 0.01 : -0.01))
                    .high(100.5)
                    .low(99.5)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(100.5)
                .low(99.5)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void execute_withHighVolatility_returnsShortSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Feed volatile prices
        for (int i = 0; i < 70; i++) {
            double price = 100.0 + (i % 2 == 0 ? 10 : -10);
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(price)
                    .high(price + 5)
                    .low(price - 5)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(110.0)
                .high(115.0)
                .low(105.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void execute_withLongPositionAndHighVolatility_returnsExitSignal() {
        portfolio.setPosition("TEST", 10);
        
        // Feed volatile prices to trigger exit
        for (int i = 0; i < 70; i++) {
            double price = 100.0 + (i % 2 == 0 ? 15 : -15);
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(price)
                    .high(price + 5)
                    .low(price - 5)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(115.0)
                .high(120.0)
                .low(110.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void getName_returnsCorrectName() {
        String name = strategy.getName();
        assertTrue(name.contains("Low Volatility Anomaly"));
        assertTrue(name.contains("20d"));
        assertTrue(name.contains("top30"));
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_clearsHistory() {
        // Build up history
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 + i)
                    .high(101.0 + i)
                    .low(99.0 + i)
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
}
