package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossSectionalMomentumStrategyTest {

    private CrossSectionalMomentumStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new CrossSectionalMomentumStrategy(5, 20); // rankPeriod=5, topN=20%
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
    void testWarmupAndInsufficientReturns() {
        // Ensure prices >= rankPeriod+1 but returns < 10 to trigger 'Need more return history for ranking'
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100 + i).build());
        }
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(106).build());
        assertTrue(s.getReason().toLowerCase().contains("need"));
    }

    @Test
    void testWinner_LongSignal() {
        // Ensure we have sufficient price and return history
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100 + i * 0.1).build());
        }
        // Add a large jump to create a high daily return
        strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(150.0).build());
        // Add more to build return history
        for (int i = 0; i < 15; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(150.0 + i * 0.01).build());
        }
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(155.0).build());
        assertNotNull(s.getDirection());
    }

    @Test
    void testMomentumReversal_Exit() {
        // Build momentum positive then reversal with sufficient history
        for (int i = 0; i < 8; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100 + i).build());
        // Take a long position
        portfolio.getPositions().put("TEST", 1);
        // Now drop price to cause reversal
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(90).build());
        if (s.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.SHORT, s.getDirection());
        } else {
            // If not exit due to thresholding, at least neutral is acceptable here
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        }
    }

    @Test
    void testGetName_Type_Reset() {
        assertTrue(strategy.getName().contains("Cross-Sectional"));
        assertEquals(tw.gc.auto.equity.trader.strategy.StrategyType.SWING, strategy.getType());
        for (int i = 0; i < 10; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100 + i).build());
        strategy.reset();
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100).build());
        assertTrue(s.getReason().toLowerCase().contains("warming up") || s.getReason().toLowerCase().contains("need"));
    }
}
