package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class BreakoutMomentumStrategyTest {

    private BreakoutMomentumStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new BreakoutMomentumStrategy(20, 0.01);
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
    void execute_withBreakout_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build up history with stable prices
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
        
        // Breakout above period high + margin
        MarketData breakout = MarketData.builder()
                .symbol("TEST")
                .close(110.0)
                .high(111.0)
                .low(109.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, breakout);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("Breakout above"));
    }

    @Test
    void execute_withBreakdownStop_returnsExitSignal() {
        // Build up history with position <= 0 first
        portfolio.setPosition("TEST", 0);
        
        // Build up history - periodLow uses last 10 lows from the deque
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(105.0)
                    .low(99.0)  // This sets periodLow baseline
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Trigger breakout to set entry price (position must be <= 0 for breakout)
        MarketData breakout = MarketData.builder()
                .symbol("TEST")
                .close(120.0)  // Above breakoutLevel (105 * 1.01 = 106.05)
                .high(121.0)
                .low(100.0)
                .volume(1000L)
                .build();
        TradeSignal breakoutSignal = strategy.execute(portfolio, breakout);
        assertEquals(TradeSignal.SignalDirection.LONG, breakoutSignal.getDirection());
        
        // Now set position as long and drop below stop level
        // The last 10 lows include 99s and 100, min is 99, stopLevel = 99 * 0.98 = 97.02
        // The breakdown data's low will also be in the deque, so use a low that's high
        portfolio.setPosition("TEST", 10);
        MarketData breakdown = MarketData.builder()
                .symbol("TEST")
                .close(50.0)  // Way below stopLevel of 97.02
                .high(51.0)
                .low(99.0)    // Keep high low so periodLow stays around 99
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, breakdown);
        
        // Lines 90-93: Exit signal with PnL calculation
        assertTrue(signal.isExitSignal(), "Expected exit signal but got: " + signal);
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("Breakdown stop"));
        assertTrue(signal.getReason().contains("PnL"));
    }

    @Test
    void execute_withBreakdownStopNoEntryPrice_returnsExitSignal() {
        // Build up history first
        portfolio.setPosition("TEST", 0);
        
        for (int i = 0; i < 25; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(105.0)
                    .low(99.0)  // periodLow will be 99
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Set position as long without triggering breakout (so no entry price)
        portfolio.setPosition("TEST", 10);
        
        // Drop below stop level - periodLow = 99, stopLevel = 99 * 0.98 = 97.02
        // Keep the low high so it doesn't affect periodLow
        MarketData breakdown = MarketData.builder()
                .symbol("TEST")
                .close(50.0)  // Way below 97.02
                .high(51.0)
                .low(99.0)    // Same as history lows
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, breakdown);
        
        // Lines 90-93: Exit signal with entry==null, so pnlPct=0
        assertTrue(signal.isExitSignal(), "Expected exit signal but got: " + signal);
        assertTrue(signal.getReason().contains("PnL: 0.00%"));
    }

    @Test
    void getName_returnsCorrectName() {
        String name = strategy.getName();
        assertTrue(name.contains("Breakout Momentum"));
        assertTrue(name.contains("20"));
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
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
