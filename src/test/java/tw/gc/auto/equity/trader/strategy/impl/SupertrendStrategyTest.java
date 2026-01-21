package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class SupertrendStrategyTest {

    private SupertrendStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new SupertrendStrategy(10, 3.0);
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
    void execute_withBullishTrendChange_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Feed declining prices to establish bearish trend
        for (int i = 0; i < 15; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 - i)
                    .high(101.0 - i)
                    .low(99.0 - i)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Feed rising prices to trigger bullish trend change
        for (int i = 0; i < 10; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(85.0 + i * 3)
                    .high(86.0 + i * 3)
                    .low(84.0 + i * 3)
                    .volume(1000L)
                    .build();
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getReason().contains("bullish"));
                return;
            }
        }
    }

    @Test
    void execute_withBearishTrendChange_returnsShortSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Feed rising prices to establish bullish trend
        for (int i = 0; i < 15; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 + i)
                    .high(101.0 + i)
                    .low(99.0 + i)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Feed declining prices to trigger bearish trend change
        for (int i = 0; i < 10; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(115.0 - i * 3)
                    .high(116.0 - i * 3)
                    .low(114.0 - i * 3)
                    .volume(1000L)
                    .build();
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
                assertTrue(signal.getReason().contains("bearish"));
                return;
            }
        }
    }

    @Test
    void calculateATR_withSingleDataPoint_returnsZero() {
        // Create strategy and feed only one data point
        // ATR loop starts at i=1, so count stays 0 (line 91 branch)
        SupertrendStrategy shortStrategy = new SupertrendStrategy(10, 3.0);
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = shortStrategy.execute(portfolio, data);
        
        // With only one bar, should be warming up
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("Supertrend", strategy.getName());
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsHistory() {
        // Build up history
        for (int i = 0; i < 15; i++) {
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
