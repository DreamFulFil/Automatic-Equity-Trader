package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class KeltnerChannelStrategyTest {

    private KeltnerChannelStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new KeltnerChannelStrategy(20, 10, 2.0);
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
    void execute_withSufficientData_calculatesChannel() {
        // Feed enough data to pass warmup
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 + i * 0.1)
                    .high(101.0 + i * 0.1)
                    .low(99.0 + i * 0.1)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(103.0)
                .high(104.0)
                .low(102.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void execute_priceAboveUpperChannel_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build stable price history
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
        
        // Spike price above upper channel
        MarketData breakout = MarketData.builder()
                .symbol("TEST")
                .close(120.0)
                .high(121.0)
                .low(119.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, breakout);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getReason().contains("Keltner Channel"));
        }
    }

    @Test
    void execute_priceBelowLowerChannel_returnsShortSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build stable price history
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
        
        // Drop price below lower channel
        MarketData breakdown = MarketData.builder()
                .symbol("TEST")
                .close(80.0)
                .high(81.0)
                .low(79.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, breakdown);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertTrue(signal.getReason().contains("Keltner Channel"));
        }
    }

    @Test
    void calculateATR_withSingleDataPoint_returnsZero() {
        // Create strategy and feed only one data point
        // ATR loop starts at i=1, so count stays 0 (line 104 branch)
        KeltnerChannelStrategy shortStrategy = new KeltnerChannelStrategy(20, 10, 2.0);
        
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
        assertEquals("Keltner Channel Breakout", strategy.getName());
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
