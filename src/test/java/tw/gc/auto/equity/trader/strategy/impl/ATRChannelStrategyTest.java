package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ATRChannelStrategyTest {

    private ATRChannelStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new ATRChannelStrategy(5, 1.0);
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
        MarketData md = MarketData.builder().symbol("TEST").high(101).low(99).close(100).build();
        TradeSignal s = strategy.execute(portfolio, md);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testUpperBreakout_LongSignal() {
        // feed constant values to produce zero ATR and MA ~100
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        }
        // price above MA + multiplier*ATR (ATR==0) so >100 triggers long
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(106).low(105).close(105).build());
        assertEquals(TradeSignal.SignalDirection.LONG, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("atr breakout") || s.getConfidence() >= 0.8);
    }

    @Test
    void testLowerBreakout_ShortAndExit() {
        // warmup
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        }
        // short when price below lower band and no position
        TradeSignal sShort = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(94).low(93).close(93).build());
        assertEquals(TradeSignal.SignalDirection.SHORT, sShort.getDirection());

        // simulate having a long position and price below lower band -> exit signal
        portfolio.getPositions().put("TEST", 1);
        TradeSignal exit = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(90).low(89).close(89).build());
        assertTrue(exit.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, exit.getDirection());
    }

    @Test
    void testGetName_Type_Reset() {
        assertTrue(strategy.getName().contains("ATR Channel"));
        assertEquals(tw.gc.auto.equity.trader.strategy.StrategyType.SWING, strategy.getType());
        // warmup and then reset
        for (int i = 0; i < 6; i++) strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        strategy.reset();
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testAlreadyLong_NoSignalOnUpperBreakout() {
        // warmup
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        }
        // set position to long
        portfolio.getPositions().put("TEST", 1);
        // price above upper band
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(106).low(105).close(105).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("in channel"));
    }

    @Test
    void testShortPosition_NoSignalOnLowerBreakout() {
        // warmup
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        }
        // set position to short
        portfolio.getPositions().put("TEST", -1);
        // price below lower band
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(94).low(93).close(93).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("in channel"));
    }

    @Test
    void testShortPosition_LongSignalOnUpperBreakout() {
        // warmup
        for (int i = 0; i < 6; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(99).close(100).build());
        }
        // set position to short
        portfolio.getPositions().put("TEST", -1);
        // price above upper band
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(106).low(105).close(105).build());
        assertEquals(TradeSignal.SignalDirection.LONG, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("atr breakout"));
    }

    @Test
    void testUpdateHistory_WithZeroHighLow() {
        // Test edge case where high or low is zero or negative
        MarketData md = MarketData.builder().symbol("TEST").high(0).low(-1).close(100).build();
        strategy.execute(portfolio, md);
        // Should not crash, and use close for high/low
        assertTrue(strategy.getName().contains("ATR Channel")); // just to ensure strategy is intact
    }
}
