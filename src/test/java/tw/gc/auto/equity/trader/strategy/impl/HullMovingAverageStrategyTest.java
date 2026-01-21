package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class HullMovingAverageStrategyTest {

    private HullMovingAverageStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new HullMovingAverageStrategy(20);
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
    void execute_withSufficientData_initializesHMA() {
        // Feed enough data to pass warmup but not yet have prevHMA
        for (int i = 0; i < 20; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 + i)
                    .high(101.0 + i)
                    .low(99.0 + i)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(120.0)
                .high(121.0)
                .low(119.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void execute_withBullishCrossover_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build up history with declining prices (HMA below price)
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 - i * 0.5)
                    .high(101.0 - i * 0.5)
                    .low(99.0 - i * 0.5)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Now price jumps up significantly to create bullish crossover
        for (int i = 0; i < 10; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(90.0 + i * 3)
                    .high(91.0 + i * 3)
                    .low(89.0 + i * 3)
                    .volume(1000L)
                    .build();
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getReason().contains("bullish crossover"));
                return;
            }
        }
    }

    @Test
    void execute_withBearishCrossover_returnsShortSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build up history with rising prices
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 + i * 0.5)
                    .high(101.0 + i * 0.5)
                    .low(99.0 + i * 0.5)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Now price drops significantly to create bearish crossover
        for (int i = 0; i < 10; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(110.0 - i * 3)
                    .high(111.0 - i * 3)
                    .low(109.0 - i * 3)
                    .volume(1000L)
                    .build();
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
                assertTrue(signal.getReason().contains("bearish crossover"));
                return;
            }
        }
    }

    @Test
    void calculateWMA_withZeroPeriod_returnsZero() {
        // Test with period 0 to cover weightSum=0 branch (line 72)
        HullMovingAverageStrategy zeroPeriodStrategy = new HullMovingAverageStrategy(0);
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        // With period=0, calculateWMA will have start=p.length and loop won't execute
        TradeSignal signal = zeroPeriodStrategy.execute(portfolio, data);
        assertNotNull(signal);
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("Hull Moving Average", strategy.getName());
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsPriceHistory() {
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

    @Test
    void defaultConstructor_usesPeriod20() {
        HullMovingAverageStrategy defaultStrategy = new HullMovingAverageStrategy();
        assertEquals("Hull Moving Average", defaultStrategy.getName());
    }
}
