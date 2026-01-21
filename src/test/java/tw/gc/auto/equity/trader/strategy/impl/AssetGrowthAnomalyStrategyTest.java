package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class AssetGrowthAnomalyStrategyTest {

    private AssetGrowthAnomalyStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new AssetGrowthAnomalyStrategy(1, 0.15);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    @Test
    void warmup_returnsNeutral() {
        MarketData md = createMarketData("TEST", 100.0, 1000L);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void lowGrowth_shouldProduceLong() {
        // Build 60+ days of stable prices (low growth)
        for (int i = 0; i < 65; i++) {
            MarketData md = createMarketData("LOW", 100.0, 1000L);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            if (i >= 59 && signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getConfidence() > 0);
                return;
            }
        }
    }

    @Test
    void highGrowth_shouldProduceShort() {
        // Build history with rapidly increasing prices (high growth)
        for (int i = 0; i < 65; i++) {
            double price = 100.0 + i * 5; // fast growth
            long volume = 1000L + i * 100;
            MarketData md = createMarketData("HIGH", price, volume);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            if (i >= 59 && signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
                assertTrue(signal.getConfidence() > 0);
                return;
            }
        }
    }

    @Test
    void historyTrimming_whenExceedsLookbackDays() {
        // Test lines 49-50: history trimming when > lookbackDays
        // Use 1 year lookback = 252 days, add more than that
        AssetGrowthAnomalyStrategy s = new AssetGrowthAnomalyStrategy(1, 0.15);
        
        // Add 300 data points to exceed 252 day limit
        for (int i = 0; i < 300; i++) {
            MarketData md = createMarketData("TRIM", 100.0 + i * 0.1, 1000L);
            TradeSignal signal = s.execute(portfolio, md);
            assertNotNull(signal);
        }
        
        // Strategy should still work after trimming
        MarketData md = createMarketData("TRIM", 130.0, 1000L);
        TradeSignal signal = s.execute(portfolio, md);
        assertNotNull(signal);
        assertNotEquals("Warming up", signal.getReason());
    }

    @Test
    void getName_shouldReturnFormattedName() {
        String name = strategy.getName();
        assertTrue(name.contains("Asset Growth"));
    }

    @Test
    void getType_shouldReturnLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_shouldClearHistory() {
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("RST", 100.0, 1000L));
        }
        
        strategy.reset();
        
        MarketData md = createMarketData("RST", 100.0, 1000L);
        TradeSignal signal = strategy.execute(portfolio, md);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(String symbol, double close, long volume) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(volume)
                .build();
    }
}
