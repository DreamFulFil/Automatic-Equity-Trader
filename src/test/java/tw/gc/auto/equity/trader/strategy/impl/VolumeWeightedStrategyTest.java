package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class VolumeWeightedStrategyTest {

    private VolumeWeightedStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new VolumeWeightedStrategy(20, 1.5);
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
    void execute_priceAboveVWAPWithHighVolume_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build up history with low volume and low prices
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
        
        // Price above VWAP with high volume (> avg * 1.5)
        MarketData bullish = MarketData.builder()
                .symbol("TEST")
                .close(110.0)  // Above VWAP
                .high(111.0)
                .low(109.0)
                .volume(2000L)  // High volume
                .build();
        TradeSignal signal = strategy.execute(portfolio, bullish);
        
        // Line 73-74: Price > VWAP && volume > threshold && position <= 0 -> LONG
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("above VWAP"));
    }

    @Test
    void execute_priceBelowVWAPWithHighVolume_returnsShortSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build up history with low volume and higher prices
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
        
        // Price below VWAP with high volume
        MarketData bearish = MarketData.builder()
                .symbol("TEST")
                .close(90.0)  // Below VWAP
                .high(91.0)
                .low(89.0)
                .volume(2000L)  // High volume
                .build();
        TradeSignal signal = strategy.execute(portfolio, bearish);
        
        // Line 75-76: Price < VWAP && volume > threshold && position >= 0 -> SHORT
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("below VWAP"));
    }

    @Test
    void execute_withLowVolume_returnsNeutral() {
        portfolio.setPosition("TEST", 0);
        
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
        
        // Price above VWAP but low volume
        MarketData lowVol = MarketData.builder()
                .symbol("TEST")
                .close(110.0)
                .high(111.0)
                .low(109.0)
                .volume(500L)  // Low volume
                .build();
        TradeSignal signal = strategy.execute(portfolio, lowVol);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Volume not significant"));
    }

    @Test
    void execute_withLongPosition_skipsBullishSignal() {
        portfolio.setPosition("TEST", 10);
        
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
        
        // Bullish signal but already long
        MarketData bullish = MarketData.builder()
                .symbol("TEST")
                .close(110.0)
                .high(111.0)
                .low(109.0)
                .volume(2000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, bullish);
        
        // position > 0, so long check (position <= 0) fails
        assertNotEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
    }

    @Test
    void execute_withShortPosition_skipsBearishSignal() {
        portfolio.setPosition("TEST", -10);
        
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
        
        // Bearish signal but already short
        MarketData bearish = MarketData.builder()
                .symbol("TEST")
                .close(90.0)
                .high(91.0)
                .low(89.0)
                .volume(2000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, bearish);
        
        // position < 0, so short check (position >= 0) fails
        assertNotEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("Volume Weighted", strategy.getName());
    }

    @Test
    void getType_returnsIntraday() {
        assertEquals(StrategyType.INTRADAY, strategy.getType());
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
    void defaultConstructor_usesDefaultValues() {
        VolumeWeightedStrategy defaultStrategy = new VolumeWeightedStrategy();
        assertEquals("Volume Weighted", defaultStrategy.getName());
    }
}
