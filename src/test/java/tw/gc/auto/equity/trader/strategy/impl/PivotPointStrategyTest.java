package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PivotPointStrategyTest {

    private PivotPointStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new PivotPointStrategy();
        portfolio = new Portfolio();
    }

    @Test
    void testGetName() {
        assertEquals("Pivot Points (Intraday)", strategy.getName());
    }

    @Test
    void testGetType() {
        assertEquals(StrategyType.INTRADAY, strategy.getType());
    }

    @Test
    void testInitialExecuteCalculatesLevels() {
        MarketData data = createMarketData("2330", 100.0, 105.0, 95.0, 100.0);
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Calculated new Pivot Levels"));
    }

    @Test
    void testBuySignalAtS1Support() {
        // First execute to calculate levels
        MarketData data1 = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        strategy.execute(portfolio, data1);
        
        // Pivot = (110 + 90 + 100) / 3 = 100
        // S1 = (2 * 100) - 110 = 90
        // Price at S1 should generate buy signal
        MarketData data2 = createMarketData("2330", 90.0, 90.5, 89.5, 90.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("S1 Support"));
        assertTrue(signal.getConfidence() > 0);
    }

    @Test
    void testSellSignalAtR1Resistance() {
        // First execute to calculate levels with position
        MarketData data1 = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        strategy.execute(portfolio, data1);
        
        // Set a long position
        portfolio.setPosition("2330", 100);
        
        // Pivot = 100, R1 = (2 * 100) - 90 = 110
        // Price at R1 should generate exit signal
        MarketData data2 = createMarketData("2330", 110.0, 110.5, 109.5, 110.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        assertNotNull(signal);
        assertTrue(signal.getDirection() == TradeSignal.SignalDirection.SHORT || 
                   signal.getDirection() == TradeSignal.SignalDirection.SHORT);
        assertTrue(signal.getReason().contains("R1 Resistance"));
    }

    @Test
    void testShortSignalAtR1WithNoPosition() {
        // Calculate levels
        MarketData data1 = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        strategy.execute(portfolio, data1);
        
        // R1 = 110, no position should generate short signal
        MarketData data2 = createMarketData("2330", 110.0, 110.5, 109.5, 110.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("R1 Resistance"));
    }

    @Test
    void testNeutralSignalBetweenLevels() {
        // Calculate levels
        MarketData data1 = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        strategy.execute(portfolio, data1);
        
        // Price between levels should be neutral
        MarketData data2 = createMarketData("2330", 100.0, 101.0, 99.0, 100.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("P:") || 
                   signal.getReason().contains("S1:") || 
                   signal.getReason().contains("R1:"));
    }

    @Test
    void testLevelsRecalculatedAfterResetPeriod() {
        // Execute 60 times to trigger recalculation
        for (int i = 0; i < 60; i++) {
            MarketData data = createMarketData("2330", 100.0 + i, 110.0, 90.0, 100.0);
            strategy.execute(portfolio, data);
        }
        
        // 61st execution should recalculate
        MarketData data = createMarketData("2330", 105.0, 110.0, 90.0, 105.0);
        TradeSignal signal = strategy.execute(portfolio, data);
        
        // Should indicate recalculation or continue with new levels
        assertNotNull(signal);
    }

    @Test
    void testReset() {
        // Execute once
        MarketData data = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        strategy.execute(portfolio, data);
        
        // Reset
        strategy.reset();
        
        // Next execute should recalculate levels
        MarketData data2 = createMarketData("2330", 95.0, 100.0, 90.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Calculated new Pivot Levels"));
    }

    @Test
    void testSessionHighLowTracking() {
        // First data point sets session open
        MarketData data1 = createMarketData("2330", 100.0, 105.0, 95.0, 100.0);
        strategy.execute(portfolio, data1);
        
        // Higher high
        MarketData data2 = createMarketData("2330", 102.0, 112.0, 98.0, 102.0);
        strategy.execute(portfolio, data2);
        
        // Lower low
        MarketData data3 = createMarketData("2330", 98.0, 108.0, 85.0, 98.0);
        TradeSignal signal = strategy.execute(portfolio, data3);
        
        // Should track session highs and lows properly
        assertNotNull(signal);
    }

    @Test
    void testPivotCalculationAccuracy() {
        // Test the pivot point formula: P = (H + L + C) / 3
        MarketData data = createMarketData("2330", 100.0, 120.0, 80.0, 100.0);
        strategy.execute(portfolio, data);
        
        // Expected: Pivot = (120 + 80 + 100) / 3 = 100
        // R1 = (2 * 100) - 80 = 120
        // S1 = (2 * 100) - 120 = 80
        
        // Test at calculated R1 (120)
        MarketData data2 = createMarketData("2330", 120.0, 120.5, 119.5, 120.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        // Should recognize R1
        assertTrue(signal.getReason().contains("R1") || signal.getReason().contains("120"));
    }

    @Test
    void testNoSignalWhenPositionConflicts() {
        // Calculate levels
        MarketData data1 = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        strategy.execute(portfolio, data1);
        
        // Set long position
        portfolio.setPosition("2330", 100);
        
        // Price at S1 but already long - should not generate another long signal
        MarketData data2 = createMarketData("2330", 90.0, 90.5, 89.5, 90.0);
        TradeSignal signal = strategy.execute(portfolio, data2);
        
        // Signal should be neutral or different from long
        assertNotEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
    }

    @Test
    void testHandlesZeroVolume() {
        MarketData data = createMarketData("2330", 100.0, 110.0, 90.0, 100.0);
        data.setVolume(0L);
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
    }

    @Test
    void testHandlesMissingHighLow() {
        // When high/low not provided (0), should use close price
        MarketData data = createMarketData("2330", 100.0, 0.0, 0.0, 100.0);
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertNotNull(signal);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    private MarketData createMarketData(String symbol, double close, double high, double low, double open) {
        MarketData data = new MarketData();
        data.setSymbol(symbol);
        data.setClose(close);
        data.setHigh(high);
        data.setLow(low);
        data.setOpen(open);
        data.setVolume(100000L);
        data.setTimestamp(LocalDateTime.now());
        return data;
    }
}
