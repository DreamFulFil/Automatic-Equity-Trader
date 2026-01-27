package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.FundamentalData;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.FundamentalDataProvider;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EarningsYieldStrategy with FundamentalDataProvider integration.
 * Tests both legacy (proxy-based) and new (real fundamental data) modes.
 */
class EarningsYieldStrategyFundamentalTest {

    private Portfolio portfolio;
    private static final String SYMBOL = "2330.TW";

    @BeforeEach
    void setUp() {
        Map<String, Integer> positions = new HashMap<>();
        positions.put(SYMBOL, 0);
        portfolio = Portfolio.builder()
                .positions(positions)
                .equity(1_000_000)
                .build();
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

    private FundamentalData createFundamentalData(String symbol, Double eps, Double peRatio) {
        double earningsYield = (peRatio != null && peRatio > 0) ? 1.0 / peRatio : null;
        return FundamentalData.builder()
                .symbol(symbol)
                .name("Test Stock")
                .reportDate(LocalDate.now())
                .fetchedAt(OffsetDateTime.now())
                .eps(eps)
                .peRatio(peRatio)
                .build();
    }

    private void primeHistory(EarningsYieldStrategy strategy, String symbol, double[] prices) throws Exception {
        Field f = EarningsYieldStrategy.class.getDeclaredField("priceHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
        Deque<Double> deque = new ArrayDeque<>();
        for (double p : prices) {
            deque.addLast(p);
        }
        map.put(symbol, deque);
    }

    @Nested
    @DisplayName("Legacy Constructor (Price Proxy)")
    class LegacyConstructorTests {

        @Test
        void legacyConstructor_usesNoOpProvider() {
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60);
            assertNotNull(strategy);
            assertEquals("Earnings Yield (5.0%, 60d)", strategy.getName());
        }

        @Test
        void warmingUp_returnsNeutral() {
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60);
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().toLowerCase().contains("warming up"));
        }
    }

    @Nested
    @DisplayName("Fundamental Data Provider Integration")
    class FundamentalDataProviderTests {

        @Test
        void withRealEarningsYield_usesRealData() throws Exception {
            // Create a provider that returns real E/P data
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .peRatio(10.0) // P/E = 10, so E/P = 0.10 (10%)
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60, provider);
            
            // Prime with stable prices to pass volatility check
            double[] stablePrices = new double[70];
            for (int i = 0; i < stablePrices.length; i++) {
                stablePrices[i] = 100.0 + (Math.random() * 2 - 1); // ~100 ± 1
            }
            primeHistory(strategy, SYMBOL, stablePrices);
            
            // Execute
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            
            // Should generate a LONG signal with real E/P (10% > 5% threshold)
            assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
            assertTrue(signal.getReason().contains("real E/P"));
            assertEquals(0.80, signal.getConfidence()); // Higher confidence with real data
        }

        @Test
        void withNoFundamentalData_fallsBackToProxy() throws Exception {
            FundamentalDataProvider provider = symbol -> Optional.empty();
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60, provider);
            
            // Prime history
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100 + (Math.random() * 2 - 1);
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            
            // Should still work with proxy
            assertNotNull(signal);
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getReason().contains("proxy"));
                assertEquals(0.70, signal.getConfidence()); // Lower confidence with proxy
            }
        }

        @Test
        void withZeroPeRatio_fallsBackToProxy() throws Exception {
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .peRatio(0.0) // Invalid P/E
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60, provider);
            
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100 + (Math.random() * 2 - 1);
            }
            primeHistory(strategy, SYMBOL, prices);
            
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            
            assertNotNull(signal);
            // Should not say "real E/P" when P/E is invalid
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertFalse(signal.getReason().contains("real E/P"));
            }
        }

        @Test
        void noOpProvider_alwaysReturnsEmpty() {
            FundamentalDataProvider provider = FundamentalDataProvider.noOp();
            assertTrue(provider.getFundamentalData(SYMBOL).isEmpty());
            assertTrue(provider.getFundamentalData("ANY").isEmpty());
            assertTrue(provider.apply("TEST").isEmpty());
        }
    }

    @Nested
    @DisplayName("Strategy Lifecycle")
    class StrategyLifecycleTests {

        @Test
        void getName_includesParameters() {
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.08, 90);
            assertEquals("Earnings Yield (8.0%, 90d)", strategy.getName());
        }

        @Test
        void getType_returnsLongTerm() {
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60);
            assertEquals(StrategyType.LONG_TERM, strategy.getType());
        }

        @Test
        void reset_clearsPriceHistory() throws Exception {
            EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60);
            
            // Prime history
            double[] prices = new double[10];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Reset
            strategy.reset();
            
            // Verify cleared
            Field f = EarningsYieldStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
            assertTrue(map.isEmpty());
        }
    }
}
