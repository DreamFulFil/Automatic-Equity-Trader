package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ADXTrendStrategyTest {

    private ADXTrendStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new ADXTrendStrategy(14, 25.0);
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
        MarketData data = MarketData.builder().symbol("TEST").high(101.0).low(99.0).close(100.0).build();
        TradeSignal s = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testStrongUptrend_GeneratesLongSignal() {
        // Build consistent rising highs/lows -> plusDM > 0, minusDM = 0
        for (int i = 0; i < 16; i++) {
            double high = 100.0 + i; // strictly increasing
            double low = 99.0 + i;
            double close = 99.5 + i;
            MarketData md = MarketData.builder().symbol("TEST").high(high).low(low).close(close).build();
            strategy.execute(portfolio, md);
        }
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(116.0).low(115.0).close(115.5).build());
        assertEquals(TradeSignal.SignalDirection.LONG, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("strong uptrend"));
        assertTrue(s.getConfidence() >= 0.7);
    }

    @Test
    void testStrongDowntrend_GeneratesShortSignal() {
        // Build consistent falling highs/lows -> minusDM > 0
        for (int i = 0; i < 16; i++) {
            double high = 200.0 - i; // strictly decreasing
            double low = 199.0 - i;
            double close = 199.5 - i;
            MarketData md = MarketData.builder().symbol("TEST").high(high).low(low).close(close).build();
            strategy.execute(portfolio, md);
        }
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(183.0).low(182.0).close(182.5).build());
        assertEquals(TradeSignal.SignalDirection.SHORT, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("strong downtrend"));
        assertTrue(s.getConfidence() >= 0.7);
    }

    @Test
    void testNeutral_WhenADXLow() {
        // Alternating small moves -> ADX should be low
        for (int i = 0; i < 16; i++) {
            double high = 100.0 + (i % 2 == 0 ? 1 : 0);
            double low = 99.0 - (i % 2 == 0 ? 0 : 1);
            double close = 99.5 + (i % 2 == 0 ? 1 : 0);
            MarketData md = MarketData.builder().symbol("TEST").high(high).low(low).close(close).build();
            strategy.execute(portfolio, md);
        }
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101.0).low(99.0).close(100.0).build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("adx"));
    }

    @Test
    void testGetName_Type_Reset() {
        assertEquals("ADX Trend Strength", strategy.getName());
        assertEquals(tw.gc.auto.equity.trader.strategy.StrategyType.SWING, strategy.getType());
        // Warm up
        for (int i = 0; i < 15; i++) {
            strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(100+i).low(99+i).close(99.5+i).build());
        }
        strategy.reset();
        TradeSignal s = strategy.execute(portfolio, MarketData.builder().symbol("TEST").high(101).low(100).close(100.5).build());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void testATR_WithSingleDataPoint_ReturnsZero() {
        // Create strategy with period larger than data
        ADXTrendStrategy shortStrategy = new ADXTrendStrategy(14, 25.0);
        
        // Feed only one data point - ATR loop starts at i=1, so count stays 0
        MarketData md = MarketData.builder().symbol("TEST").high(101.0).low(99.0).close(100.0).build();
        TradeSignal s = shortStrategy.execute(portfolio, md);
        
        // With only one bar, should be warming up (covers count=0 branch in ATR)
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.getDirection());
        assertTrue(s.getReason().toLowerCase().contains("warming up"));
    }
}
