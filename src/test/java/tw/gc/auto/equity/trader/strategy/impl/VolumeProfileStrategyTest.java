package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VolumeProfileStrategyTest {

    private VolumeProfileStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new VolumeProfileStrategy(5, 0.7);
        Map<String, Integer> positions = new HashMap<>();
        positions.put("TEST", 0);
        portfolio = Portfolio.builder()
                .positions(positions)
                .equity(100000.0)
                .tradingMode("stock")
                .tradingQuantity(1)
                .build();
    }

    @Test
    void testWarmup_ReturnsNeutral() {
        MarketData data = MarketData.builder().symbol("TEST").close(100.0).volume(100L).build();
        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testInsufficientPriceRange() {
        // Feed identical prices to force zero bin size
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).volume(100L).build());
        }
        TradeSignal signal = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).volume(100L).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("insufficient price range"));
    }

    @Test
    void testBuyAtLowerEdge() {
        // Build a profile where lower bins have volume and current price near low edge
        double[] prices = {95, 96, 97, 100, 105, 96};
        long[] vols =   {100,100,100,100,100, 50};
        for (int i = 0; i < prices.length - 1; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(prices[i]).volume(vols[i]).build());
        }
        // Current price close to lower edge (within 1% tolerance)
        MarketData current = MarketData.builder().symbol("TEST").close(95.5).volume(60L).build();
        TradeSignal signal = strategy.execute(portfolio, current);
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("volume profile buy"));
    }

    @Test
    void testSellAtUpperEdge() {
        // Create profile and set a long position
        portfolio.getPositions().put("TEST", 1);
        double[] prices = {95, 96, 97, 100, 105, 104};
        long[] vols =   {100,100,100,100,100, 80};
        for (int i = 0; i < prices.length - 1; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(prices[i]).volume(vols[i]).build());
        }
        MarketData current = MarketData.builder().symbol("TEST").close(105.0).volume(200L).build();
        TradeSignal signal = strategy.execute(portfolio, current);
        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("volume profile sell"));
    }

    @Test
    void testWeakBreakoutShort() {
        double[] prices = {95, 96, 97, 100, 105, 107};
        long[] vols =   {100,100,100,100,100, 10}; // last vol weak
        for (int i = 0; i < prices.length - 1; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(prices[i]).volume(vols[i]).build());
        }
        MarketData current = MarketData.builder().symbol("TEST").close(108.0).volume(5L).build();
        TradeSignal signal = strategy.execute(portfolio, current);
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("weak breakout"));
    }

    @Test
    void testGetName_Type_Reset() {
        assertTrue(strategy.getName().contains("Volume Profile"));
        assertEquals(tw.gc.auto.equity.trader.strategy.StrategyType.SWING, strategy.getType());

        // Warm up
        for (int i = 0; i < 5; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 + i).volume(100L).build());
        }
        // After warmup it should no longer return 'Warming up' for next call
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(106.0).volume(120L).build());
        assertFalse(s.getReason().toLowerCase().contains("warming up"));

        // Reset and expect warming up again
        strategy.reset();
        TradeSignal afterReset = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).volume(100L).build());
        assertTrue(afterReset.getReason().toLowerCase().contains("warming up"));
    }
}
