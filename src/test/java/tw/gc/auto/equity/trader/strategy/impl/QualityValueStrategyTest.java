package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QualityValueStrategyTest {

    private QualityValueStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new QualityValueStrategy(0.05, 7); // 5% value, F-score 7
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
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testQualityValue_BuySignal() {
        // Build 60-day history: first 59 high ~150, last day low 100 to create strong value proxy
        // Build 60-day history: increasing prices to ensure positive returns and then a low current price
        for (int i = 0; i < 59; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 + i).build());
        }
        // last price low to increase value proxy
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(80.0).build());
        assertEquals(TradeSignal.SignalDirection.LONG, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("quality value"));
    }

    @Test
    void testQualityValue_ExitSignalOnDeterioration() {
        // Build good history to enter long
        for (int i = 0; i < 59; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(150.0 + (i % 2)).build());
        strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).build());
        portfolio.getPositions().put("TEST", 1);

        // Now feed deteriorating prices to lower F-score/value
        for (int i = 0; i < 60; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(200.0 - i * 2).build());

        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(80.0).build());
        assertTrue(s.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, s.getDirection());
    }

    @Test
    void testNoBuy_AlreadyLong() {
        // Build good history
        for (int i = 0; i < 59; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 + i).build());
        strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(80.0).build());
        portfolio.getPositions().put("TEST", 1); // already long

        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(80.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
    }

    @Test
    void testNoBuy_LowValueProxy() {
        // Build history with low value proxy (current price high)
        for (int i = 0; i < 59; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 + i).build());
        // current price high, so value proxy low
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(200.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
    }

    @Test
    void testNoBuy_LowFScore() {
        // Build history with declining prices to lower F-score
        for (int i = 0; i < 59; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 - i).build());
        // current price low, but F-score low due to declining
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(10.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
    }

    @Test
    void testNoExit_GoodConditions() {
        // Build good history and set long position
        for (int i = 0; i < 59; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 + i).build());
        strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(80.0).build());
        portfolio.getPositions().put("TEST", 1);

        // Continue with good prices
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(85.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
    }

    @Test
    void testWarmup_With59Prices() {
        // Add 58 prices, then 59th, still warming up
        for (int i = 0; i < 58; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).build());
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testExit_OnLowValueProxy() {
        // Build good history to enter long
        for (int i = 0; i < 59; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 + i).build());
        strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(80.0).build());
        portfolio.getPositions().put("TEST", 1);

        // Now set current price very high to make value proxy very low (year high ~158, current 4000, proxy=158/4000≈0.039 <0.05)
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(4000.0).build());
        assertTrue(s.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, s.getDirection());
    }

    @Test
    void testExit_OnLowFScore() {
        // Build history with decreasing prices to lower F-Score
        for (int i = 0; i < 60; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0 - i * 0.5).build());
        portfolio.getPositions().put("TEST", 1);

        // Current price still high enough for value proxy, but F-Score low
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(90.0).build());
        assertTrue(s.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, s.getDirection());
    }

    @Test
    void testHistorySizeLimit() {
        // Execute 253 times to trigger size > 252 removal
        for (int i = 0; i < 253; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).build());
        }
        // Should not crash, and history size should be 252
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, strategy.execute(portfolio, MarketData.builder().symbol("TEST").close(100.0).build()).getDirection());
    }
}