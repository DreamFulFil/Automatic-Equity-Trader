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

class OnBalanceVolumeStrategyTest {

    private OnBalanceVolumeStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new OnBalanceVolumeStrategy(5, 0.0);

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
        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0, 1000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void testIncreasingOBVGeneratesLong() {
        // Simulate rising close and volume
        double price = 100.0;
        for (int i = 0; i < 6; i++) {
            price += 1.0; // price up
            TradeSignal sig = strategy.execute(portfolio, createMarketData(price, 1000L + i * 100));
            if (i < 5) {
                assertEquals(TradeSignal.SignalDirection.NEUTRAL, sig.getDirection());
            }
        }
        TradeSignal finalSig = strategy.execute(portfolio, createMarketData(107.0, 2000L));
        assertEquals(TradeSignal.SignalDirection.LONG, finalSig.getDirection());
    }

    @Test
    void testDecreasingOBVGeneratesShort() {
        double price = 200.0;
        for (int i = 0; i < 6; i++) {
            price -= 1.0; // price down
            TradeSignal sig = strategy.execute(portfolio, createMarketData(price, 1000L + i * 100));
            if (i < 5) {
                assertEquals(TradeSignal.SignalDirection.NEUTRAL, sig.getDirection());
            }
        }
        TradeSignal finalSig = strategy.execute(portfolio, createMarketData(192.0, 2000L));
        assertEquals(TradeSignal.SignalDirection.SHORT, finalSig.getDirection());
    }

    @Test
    void testNullDataReturnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No market data", signal.getReason());
    }

    @Test
    void testEqualCloseDoesNotChangeOBV() {
        // First call to set prevClose
        strategy.execute(portfolio, createMarketData(100.0, 1000L));
        // Second call with same close
        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0, 1000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void testSlopeZeroReturnsNeutral() {
        // Create flat OBV by keeping price constant
        double price = 100.0;
        long volume = 1000L;
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, createMarketData(price, volume));
        }
        // OBV should be 0 for all
        TradeSignal signal = strategy.execute(portfolio, createMarketData(price, volume));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("OBV slope 0.0000"));
    }

    @Test
    void testLongNotGeneratedWhenPositionPositive() {
        // Set position to positive
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", 1);
        // Simulate increasing OBV
        double price = 100.0;
        for (int i = 0; i < 6; i++) {
            price += 1.0;
            strategy.execute(portfolio, createMarketData(price, 1000L + i * 100));
        }
        TradeSignal signal = strategy.execute(portfolio, createMarketData(107.0, 2000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void testShortNotGeneratedWhenPositionNegative() {
        // Set position to negative
        portfolio.getPositions().put("AUTO_EQUITY_TRADER", -1);
        // Simulate decreasing OBV
        double price = 200.0;
        for (int i = 0; i < 6; i++) {
            price -= 1.0;
            strategy.execute(portfolio, createMarketData(price, 1000L + i * 100));
        }
        TradeSignal signal = strategy.execute(portfolio, createMarketData(193.0, 2000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void testGetName() {
        assertEquals("OnBalanceVolume (5)", strategy.getName());
    }

    @Test
    void testGetType() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void testReset() {
        strategy.execute(portfolio, createMarketData(100.0, 1000L));
        strategy.reset();
        // After reset, should behave like new
        TradeSignal signal = strategy.execute(portfolio, createMarketData(100.0, 1000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(double price, long volume) {
        return MarketData.builder()
                .symbol("AUTO_EQUITY_TRADER")
                .close(price)
                .volume(volume)
                .timestamp(LocalDateTime.now())
                .build();
    }
}