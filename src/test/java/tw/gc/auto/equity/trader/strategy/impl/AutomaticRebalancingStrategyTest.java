package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutomaticRebalancingStrategyTest {

    private AutomaticRebalancingStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new AutomaticRebalancingStrategy(100, 7, 0.10);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 1)
                .low(close - 1)
                .open(close)
                .volume(10000L)
                .build();
    }

    @Test
    void execute_withNullData_returnsNeutral() {
        // Test line 56: null data check
        TradeSignal signal = strategy.execute(portfolio, null);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("no market data"));
    }

    @Test
    void execute_withNullSymbol_returnsNeutral() {
        // Test line 56: null symbol check
        MarketData data = MarketData.builder()
                .symbol(null)
                .timestamp(LocalDateTime.now())
                .close(100)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void rebalanceSell_whenPositionAboveTarget() throws Exception {
        // Set position above target (lines 89-93)
        portfolio.setPosition("REBAL", 150); // target is 100, so need to sell
        
        // Clear last rebalance to allow rebalancing
        clearLastRebalance(strategy, "REBAL");
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("REBAL", 100));
        
        // drift = |150-100|/100 = 0.50 > 0.10 threshold
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("rebalance"));
        assertTrue(signal.getReason().contains("decrease"));
    }

    @Test
    void rebalanceBuy_whenPositionBelowTarget() throws Exception {
        // Set position below target
        portfolio.setPosition("REBAL", 50); // target is 100, so need to buy
        
        // Clear last rebalance to allow rebalancing
        clearLastRebalance(strategy, "REBAL");
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("REBAL", 100));
        
        // drift = |50-100|/100 = 0.50 > 0.10 threshold
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("rebalance"));
        assertTrue(signal.getReason().contains("increase"));
    }

    @Test
    void neutral_whenWithinDriftThreshold() throws Exception {
        // Set position within threshold (lines 97)
        portfolio.setPosition("REBAL", 95); // drift = 5% < 10%
        
        // Clear last rebalance
        clearLastRebalance(strategy, "REBAL");
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("REBAL", 100));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("within drift threshold"));
    }

    @Test
    void neutral_whenNotTimeToRebalance() throws Exception {
        portfolio.setPosition("REBAL", 50);
        
        // Set last rebalance to recent time (within 7 days)
        setLastRebalance(strategy, "REBAL", LocalDateTime.now().minusDays(3));
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("REBAL", 100));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("next rebalance"));
    }

    @Test
    void getName_returnsCorrectFormat() {
        assertEquals("Automatic Rebalancing", strategy.getName());
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_clearsLastRebalance() throws Exception {
        // Execute to populate lastRebalance
        strategy.execute(portfolio, createMarketData("TEST", 100));
        
        strategy.reset();
        
        Field f = AutomaticRebalancingStrategy.class.getDeclaredField("lastRebalance");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> map = (Map<String, LocalDateTime>) f.get(strategy);
        assertTrue(map.isEmpty());
    }

    @Test
    void singleArgConstructor_usesDefaults() {
        AutomaticRebalancingStrategy defaultStrategy = new AutomaticRebalancingStrategy(200);
        assertEquals("Automatic Rebalancing", defaultStrategy.getName());
        assertEquals(StrategyType.LONG_TERM, defaultStrategy.getType());
    }

    private void clearLastRebalance(AutomaticRebalancingStrategy strat, String symbol) throws Exception {
        Field f = AutomaticRebalancingStrategy.class.getDeclaredField("lastRebalance");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> map = (Map<String, LocalDateTime>) f.get(strat);
        map.remove(symbol);
    }

    private void setLastRebalance(AutomaticRebalancingStrategy strat, String symbol, LocalDateTime time) throws Exception {
        Field f = AutomaticRebalancingStrategy.class.getDeclaredField("lastRebalance");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> map = (Map<String, LocalDateTime>) f.get(strat);
        map.put(symbol, time);
    }
}
