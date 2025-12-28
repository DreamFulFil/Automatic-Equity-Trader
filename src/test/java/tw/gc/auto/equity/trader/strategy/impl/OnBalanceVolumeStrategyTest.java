package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
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

    private MarketData createMarketData(double price, long volume) {
        return MarketData.builder()
                .symbol("AUTO_EQUITY_TRADER")
                .close(price)
                .volume(volume)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
