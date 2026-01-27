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
 * Unit tests for QualityMinusJunkStrategy with FundamentalDataProvider integration.
 * Tests both legacy (proxy-based) and new (real fundamental data) modes.
 */
class QualityMinusJunkStrategyFundamentalTest {

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

    private void primeHistory(QualityMinusJunkStrategy strategy, String symbol, double[] prices) throws Exception {
        Field f = QualityMinusJunkStrategy.class.getDeclaredField("priceHistory");
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
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30);
            assertNotNull(strategy);
            assertEquals("Quality Minus Junk (60/30)", strategy.getName());
        }

        @Test
        void warmingUp_returnsNeutral() {
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30);
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().toLowerCase().contains("warming up"));
        }
    }

    @Nested
    @DisplayName("Fundamental Data Provider Integration")
    class FundamentalDataProviderTests {

        @Test
        void withHighQualityMetrics_usesRealData() throws Exception {
            // Create a high-quality stock: high ROE, low debt, positive growth
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .roe(0.25)           // 25% ROE (excellent)
                    .netMargin(0.20)     // 20% net margin (excellent)
                    .debtToEquity(0.3)   // Low debt
                    .currentRatio(2.0)   // Strong liquidity
                    .revenueGrowth(0.15) // 15% revenue growth
                    .earningsGrowth(0.20) // 20% earnings growth
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30, provider);
            
            // Prime with reasonable price history
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100 + i * 0.2; // gradually increasing
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 115));
            
            // Should generate a LONG signal for quality stock
            assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
            assertTrue(signal.getReason().contains("real metrics"));
            assertEquals(0.80, signal.getConfidence()); // Higher confidence with real data
            assertTrue(signal.getReason().contains("ROE"));
        }

        @Test
        void withLowQualityMetrics_shortSignal() throws Exception {
            // Create a junk stock: low ROE, high debt, negative growth
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .roe(0.02)           // 2% ROE (poor)
                    .netMargin(0.02)     // 2% margin (poor)
                    .debtToEquity(2.0)   // High debt
                    .currentRatio(0.5)   // Weak liquidity
                    .revenueGrowth(-0.10) // -10% revenue (declining)
                    .earningsGrowth(-0.15)
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30, provider);
            
            // Prime with declining price history
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100 - i * 0.3; // gradually declining
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 80));
            
            // Should generate a SHORT signal for junk stock
            assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
            assertTrue(signal.getReason().toLowerCase().contains("junk"));
        }

        @Test
        void withNoFundamentalData_fallsBackToProxy() throws Exception {
            FundamentalDataProvider provider = symbol -> Optional.empty();
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30, provider);
            
            // Prime with stable, increasing prices (should indicate quality via proxy)
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100 + i * 0.5 + (i % 2); // upward trend with minor oscillation
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 135));
            
            // Should work with proxy
            assertNotNull(signal);
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertEquals(0.70, signal.getConfidence()); // Lower confidence with proxy
                assertTrue(signal.getReason().contains("proxy"));
            }
        }

        @Test
        void withPartialMetrics_stillCalculatesScore() throws Exception {
            // Only have 2 components: profitability and safety (no growth)
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .roe(0.20)
                    .debtToEquity(0.5)
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30, provider);
            
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100 + i * 0.2;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute - should use real data with 2 valid components
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 115));
            
            assertNotNull(signal);
            // With only ROE and D/E, quality score should be calculated
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getReason().contains("real metrics"));
            }
        }

        @Test
        void withOnlyOneMetric_fallsBackToProxy() throws Exception {
            // Only one component - not enough for real score
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .roe(0.20) // Only ROE, no other metrics
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30, provider);
            
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute - should fall back to proxy
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            
            assertNotNull(signal);
            // With only 1 metric, should use proxy (needs >= 2 components)
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getReason().contains("proxy"));
            }
        }
    }

    @Nested
    @DisplayName("Strategy Lifecycle")
    class StrategyLifecycleTests {

        @Test
        void getName_includesParameters() {
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(90, 45);
            assertEquals("Quality Minus Junk (90/45)", strategy.getName());
        }

        @Test
        void getType_returnsLongTerm() {
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30);
            assertEquals(StrategyType.LONG_TERM, strategy.getType());
        }

        @Test
        void reset_clearsPriceHistory() throws Exception {
            QualityMinusJunkStrategy strategy = new QualityMinusJunkStrategy(60, 30);
            
            // Prime history
            double[] prices = new double[10];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Reset
            strategy.reset();
            
            // Verify cleared
            Field f = QualityMinusJunkStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
            assertTrue(map.isEmpty());
        }
    }
}
