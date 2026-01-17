package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PutCallParityArbitrageStrategyTest {

    private PutCallParityArbitrageStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new PutCallParityArbitrageStrategy(0.05); // 5% min deviation

        Map<String, Integer> positions = new HashMap<>();
        positions.put("AUTO_EQUITY_TRADER", 0);

        portfolio = Portfolio.builder()
                .positions(positions)
                .equity(100000.0)
                .tradingMode("stock")
                .tradingQuantity(1)
                .build();
    }

    @Test
    void testWarmupReturnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void testNeutralWhenDeviationWithinThreshold() {
        // Add 30 data points with constant price (no deviation)
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Parity dev: 0.00%"));
    }

    @Test
    void testLongSignalWhenUndervalued() {
        // Add 30 data points with constant price
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Price drops significantly (undervalued) - need large drop to overcome min vol
        TradeSignal signal = strategy.execute(portfolio, createMarketData(85.0)); // -15% deviation
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("Parity arb long"));
        assertTrue(signal.getReason().contains("dev=-14.59%"));
    }

    @Test
    void testNoLongSignalWhenAlreadyLong() {
        // Set position to positive (already long)
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", 1);

        // Add 30 data points with constant price
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Price drops significantly
        TradeSignal signal = strategy.execute(portfolio, createMarketData(85.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void testExitSignalWhenOvervaluedAndLong() {
        // Set position to positive (long)
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", 1);

        // Add 30 data points with constant price
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Price rises significantly (overvalued) - need large rise to overcome min vol
        TradeSignal signal = strategy.execute(portfolio, createMarketData(120.0)); // +20% deviation
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("Parity arb exit"));
        assertTrue(signal.getReason().contains("dev=19.23%"));
    }

    @Test
    void testShortSignalWhenHighlyOvervalued() {
        // Add 30 data points with constant price
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Price rises very significantly (highly overvalued) - need very large rise
        TradeSignal signal = strategy.execute(portfolio, createMarketData(140.0)); // +40% deviation
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("Parity arb short"));
    }

    @Test
    void testNoShortSignalWhenAlreadyShort() {
        // Set position to negative (already short)
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", -1);

        // Add 30 data points with constant price
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Price rises very significantly
        TradeSignal signal = strategy.execute(portfolio, createMarketData(140.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void testHistorySizeLimit() {
        // Add exactly 60 data points with constant price
        for (int i = 0; i < 60; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Add one more - should maintain size at 60
        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Parity dev: 0.00%"));
    }

    @Test
    void testZeroVolatilityHandling() {
        // Add 30 identical prices (zero variance)
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Any price change should trigger signal due to min vol of 0.01
        TradeSignal signal = strategy.execute(portfolio, createMarketData(105.0)); // +5% deviation
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
    }

    @Test
    void testGetName() {
        String name = strategy.getName();
        assertEquals("Put-Call Parity Arb (5.0%)", name);
    }

    @Test
    void testGetType() {
        StrategyType type = strategy.getType();
        assertEquals(StrategyType.SWING, type);
    }

    @Test
    void testReset() {
        // Add some data
        for (int i = 0; i < 35; i++) {
            strategy.execute(portfolio, createMarketData(100.0));
        }

        // Reset
        strategy.reset();

        // Should be back to warmup
        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(double close) {
        return MarketData.builder()
                .symbol("AUTO_EQUITY_TRADER")
                .close(close)
                .high(close + 1.0)
                .low(close - 1.0)
                .open(close)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
    }
}