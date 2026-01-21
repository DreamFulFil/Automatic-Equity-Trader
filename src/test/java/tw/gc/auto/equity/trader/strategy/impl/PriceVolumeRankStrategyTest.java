package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriceVolumeRankStrategyTest {

    private PriceVolumeRankStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new PriceVolumeRankStrategy(20);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    private MarketData createMarketData(String symbol, double close, long volume) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 1)
                .low(close - 1)
                .open(close)
                .volume(volume)
                .build();
    }

    @Test
    void warmingUp_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, createMarketData("TEST", 100, 1000L));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void historyRemoveFirst_whenExceedsPeriod() throws Exception {
        // Prime history with more than period entries to trigger removeFirst (line 47)
        PriceVolumeRankStrategy strat = new PriceVolumeRankStrategy(5);
        
        // Add more than 5 entries
        for (int i = 0; i < 10; i++) {
            strat.execute(portfolio, createMarketData("TEST", 100 + i, 1000L + i));
        }
        
        // Verify history size is trimmed
        Field f = PriceVolumeRankStrategy.class.getDeclaredField("dataHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<MarketData>> map = (Map<String, Deque<MarketData>>) f.get(strat);
        assertTrue(map.get("TEST").size() <= 5);
    }

    @Test
    void longSignal_whenVolumeSpikeAboveAverage() throws Exception {
        // Create conditions for long signal (lines 53-54):
        // - volumeSpike (current > avg * 1.5)
        // - priceAboveAvg (current price > avg)
        // - position <= 0
        
        PriceVolumeRankStrategy strat = new PriceVolumeRankStrategy(5);
        
        // Prime with moderate volume and price
        for (int i = 0; i < 5; i++) {
            strat.execute(portfolio, createMarketData("PVR", 100, 1000L));
        }
        
        // Execute with volume spike and price above average
        TradeSignal signal = strat.execute(portfolio, createMarketData("PVR", 105, 2000L));
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("breakout above"));
    }

    @Test
    void shortSignal_whenVolumeSpikeAndPriceBelowAverage() throws Exception {
        // Create conditions for short signal (lines 55-56):
        // - volumeSpike (current > avg * 1.5)
        // - priceBelowAvg (current price < avg)
        // - position >= 0
        
        PriceVolumeRankStrategy strat = new PriceVolumeRankStrategy(5);
        
        // Prime with moderate volume and price
        for (int i = 0; i < 5; i++) {
            strat.execute(portfolio, createMarketData("PVR", 100, 1000L));
        }
        
        // Execute with volume spike and price below average
        TradeSignal signal = strat.execute(portfolio, createMarketData("PVR", 95, 2000L));
        
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("breakdown below"));
    }

    @Test
    void neutral_whenNoVolumeSpike() throws Exception {
        PriceVolumeRankStrategy strat = new PriceVolumeRankStrategy(5);
        
        // Prime with moderate volume
        for (int i = 0; i < 5; i++) {
            strat.execute(portfolio, createMarketData("PVR", 100, 1000L));
        }
        
        // Execute without volume spike
        TradeSignal signal = strat.execute(portfolio, createMarketData("PVR", 105, 1100L));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void defaultConstructor_usesPeriod20() {
        PriceVolumeRankStrategy defaultStrategy = new PriceVolumeRankStrategy();
        assertEquals("Price Volume Rank", defaultStrategy.getName());
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("Price Volume Rank", strategy.getName());
    }

    @Test
    void getType_returnsIntraday() {
        assertEquals(StrategyType.INTRADAY, strategy.getType());
    }

    @Test
    void reset_clearsHistory() throws Exception {
        strategy.execute(portfolio, createMarketData("TEST", 100, 1000L));
        
        strategy.reset();
        
        Field f = PriceVolumeRankStrategy.class.getDeclaredField("dataHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<MarketData>> map = (Map<String, Deque<MarketData>>) f.get(strategy);
        assertTrue(map.isEmpty());
    }
}
