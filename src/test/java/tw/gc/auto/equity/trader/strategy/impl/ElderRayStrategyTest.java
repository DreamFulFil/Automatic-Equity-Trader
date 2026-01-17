package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ElderRayStrategyTest {

    private ElderRayStrategy strategy;

    @Mock
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        strategy = new ElderRayStrategy(13);
    }

    private MarketData createMarketData(String symbol, double high, double low, double close) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setHigh(high);
        data.setLow(low);
        data.setClose(close);
        return data;
    }

    @Test
    void testWarmupPeriod() {
        // Test with less than period data
        for (int i = 0; i < 12; i++) {
            MarketData data = createMarketData("TEST", 100.0 + i, 95.0 + i, 97.5 + i);
            TradeSignal signal = strategy.execute(portfolio, data);
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertEquals("Warming up Elder Ray", signal.getReason());
        }
    }

    @Test
    void testNeutralSignal() {
        // Fill warmup period with constant prices
        for (int i = 0; i < 13; i++) {
            MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
            strategy.execute(portfolio, data);
        }

        // Test neutral signal - prices that don't trigger conditions
        MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
        when(portfolio.getPosition("TEST")).thenReturn(0);

        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("Elder Ray neutral", signal.getReason());
    }

    @Test
    void testLongSignal() {
        // Fill warmup period
        for (int i = 0; i < 13; i++) {
            MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
            strategy.execute(portfolio, data);
        }

        // Create conditions for long signal: bullPower > 0, bearPower < 0, bearPower > -1.0
        // EMA will be around 97.57, so high=99.0 gives bullPower>0, low=96.8 gives bearPower=-0.77 (> -1.0)
        MarketData data = createMarketData("TEST", 99.0, 96.8, 98.0);
        when(portfolio.getPosition("TEST")).thenReturn(0);

        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertEquals(0.75, signal.getConfidence());
        assertEquals("Elder Ray bullish", signal.getReason());
    }

    @Test
    void testShortSignal() {
        // Fill warmup period
        for (int i = 0; i < 13; i++) {
            MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
            strategy.execute(portfolio, data);
        }

        // Create conditions for short signal: bearPower < 0, bullPower > 0, bullPower < 1.0
        // EMA will be around 97.57, so high=98.4 gives bullPower=0.83 (<1.0), low=96.0 gives bearPower<0
        MarketData data = createMarketData("TEST", 98.4, 96.0, 97.0);
        when(portfolio.getPosition("TEST")).thenReturn(0);

        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertEquals(0.75, signal.getConfidence());
        assertEquals("Elder Ray bearish", signal.getReason());
    }

    @Test
    void testPositionConstraints() {
        // Fill warmup period
        for (int i = 0; i < 13; i++) {
            MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
            strategy.execute(portfolio, data);
        }

        // Test long signal blocked by existing long position
        MarketData data = createMarketData("TEST", 99.0, 96.8, 98.0);
        when(portfolio.getPosition("TEST")).thenReturn(1);

        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("Elder Ray neutral", signal.getReason());
    }

    @Test
    void testShortSignalBlockedByPosition() {
        // Fill warmup period
        for (int i = 0; i < 13; i++) {
            MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
            strategy.execute(portfolio, data);
        }

        // Test short signal blocked by existing short position
        MarketData data = createMarketData("TEST", 98.4, 96.0, 97.0);
        when(portfolio.getPosition("TEST")).thenReturn(-1);

        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("Elder Ray neutral", signal.getReason());
    }

    @Test
    void testHistorySizeLimit() {
        // Fill beyond period * 2 (26) to test history trimming
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("TEST", 100.0 + i, 95.0 + i, 97.5 + i);
            strategy.execute(portfolio, data);
        }

        // History should be trimmed to period * 2 = 26
        // Verify strategy still works and doesn't crash
        MarketData data = createMarketData("TEST", 130.0, 125.0, 127.5);
        when(portfolio.getPosition("TEST")).thenReturn(0);

        TradeSignal signal = strategy.execute(portfolio, data);
        // Just verify it returns a valid signal, not necessarily LONG
        assertNotNull(signal);
        assertNotNull(signal.getDirection());
    }

    @Test
    void testGetName() {
        assertEquals("Elder Ray Index", strategy.getName());
    }

    @Test
    void testGetType() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void testReset() {
        // Fill some history
        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
            strategy.execute(portfolio, data);
        }

        // Reset
        strategy.reset();

        // Verify warmup again
        MarketData data = createMarketData("TEST", 100.0, 95.0, 97.5);
        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("Warming up Elder Ray", signal.getReason());
    }
}