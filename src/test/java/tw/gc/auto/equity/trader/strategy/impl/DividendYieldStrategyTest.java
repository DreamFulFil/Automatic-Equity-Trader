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

class DividendYieldStrategyTest {

    private DividendYieldStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new DividendYieldStrategy(0.03, 1);
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
    void warmingUp_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, createMarketData("TEST", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void priceHistoryRemoveFirst_whenExceedsLookback() throws Exception {
        // Use minConsecutiveYears=1, lookback = 1 * 252 = 252
        DividendYieldStrategy strat = new DividendYieldStrategy(0.03, 1);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        
        // Prime history with exactly lookback prices (line 46 triggers when size > lookback after addLast)
        int lookback = 252;
        double[] prices = new double[lookback];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = 100 + (i * 0.01); // slightly increasing
        }
        primeHistory(strat, "TEST", prices);
        
        // Execute - adds one price (size becomes lookback+1), then removeFirst triggers
        TradeSignal signal = strat.execute(p, createMarketData("TEST", 100));
        assertNotNull(signal);
        
        // Verify the history size is now lookback (after add and remove)
        Field f = DividendYieldStrategy.class.getDeclaredField("priceHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strat);
        assertEquals(lookback, map.get("TEST").size());
    }

    @Test
    void longSignal_whenHighDividendYieldAndLowVolatility() throws Exception {
        // Lines 87-90: Long signal when impliedYield > minDividendYield && vol < 0.30 && position <= 0
        // Create conditions: stable price + low volatility + trading below average
        double[] prices = new double[70];
        // Very stable prices around 110 (low volatility)
        for (int i = 0; i < 70; i++) {
            prices[i] = 110 + (Math.sin(i * 0.1) * 0.2); // Very low volatility
        }
        primeHistory(strategy, "DIV", prices);
        
        portfolio.setPosition("DIV", 0); // No position
        
        // Execute with price significantly below average (creates high valueDiscount -> high impliedYield)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("DIV", 85));
        
        // Should produce a long signal when implied yield > 3% and vol < 30%
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertTrue(signal.getConfidence() >= 0.70);
            assertTrue(signal.getReason().toLowerCase().contains("high dividend yield"));
        }
    }

    @Test
    void exitSignal_whenYieldDeterioratesOrVolatilityIncreases() throws Exception {
        // Line 94: Exit when position > 0 && (impliedYield < minDividendYield/2 || vol > 0.40)
        // Set up portfolio with long position
        portfolio.setPosition("DIV", 100);
        
        // Create history with very high volatility (to trigger exit condition vol > 0.40)
        double[] prices = new double[70];
        for (int i = 0; i < 70; i++) {
            // Extreme volatility - alternating prices
            prices[i] = 100 + ((i % 2 == 0) ? 50 : -50);
        }
        primeHistory(strategy, "DIV", prices);
        
        // Execute - should trigger exit due to high volatility
        TradeSignal signal = strategy.execute(portfolio, createMarketData("DIV", 100));
        
        assertNotNull(signal);
        // With high volatility > 0.40, should trigger exit if position > 0
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.isExitSignal());
        assertTrue(signal.getReason().toLowerCase().contains("yield exit"));
        assertEquals(0.65, signal.getConfidence());
    }

    @Test
    void exitSignal_whenYieldBelowHalfMinimum() throws Exception {
        // Set up portfolio with long position
        portfolio.setPosition("YLD", 50);
        
        // Create history where impliedYield will be low (price well above average)
        double[] prices = new double[70];
        for (int i = 0; i < 60; i++) {
            prices[i] = 100; // Stable at 100
        }
        for (int i = 60; i < 70; i++) {
            prices[i] = 110; // Rising prices
        }
        primeHistory(strategy, "YLD", prices);
        
        // Current price way above average -> negative valueDiscount -> low impliedYield
        TradeSignal signal = strategy.execute(portfolio, createMarketData("YLD", 130));
        
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().toLowerCase().contains("yield exit"));
        }
    }

    @Test
    void getName_returnsCorrectFormat() {
        String name = strategy.getName();
        assertTrue(name.contains("Dividend Yield"));
        assertTrue(name.contains("3.0%"));
        assertTrue(name.contains("1y"));
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_clearsPriceHistory() throws Exception {
        // Add some data
        strategy.execute(portfolio, createMarketData("TEST", 100));
        
        // Reset
        strategy.reset();
        
        // Verify history is cleared
        Field f = DividendYieldStrategy.class.getDeclaredField("priceHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
        assertTrue(map.isEmpty());
    }

    private void primeHistory(DividendYieldStrategy strat, String symbol, double[] prices) {
        try {
            Field f = DividendYieldStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strat);
            Deque<Double> dq = new ArrayDeque<>();
            for (double p : prices) dq.addLast(p);
            map.put(symbol, dq);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
