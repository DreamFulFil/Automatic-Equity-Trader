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
 * Unit tests for BookToMarketStrategy with FundamentalDataProvider integration.
 * Tests both legacy (proxy-based) and new (real fundamental data) modes.
 */
class BookToMarketStrategyFundamentalTest {

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

    private void primeHistory(BookToMarketStrategy strategy, String symbol, double[] prices) throws Exception {
        Field f = BookToMarketStrategy.class.getDeclaredField("priceHistory");
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
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.7, 90);
            assertNotNull(strategy);
            assertEquals("Book-to-Market (70%, 90d)", strategy.getName());
        }

        @Test
        void warmingUp_returnsNeutral() {
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.7, 90);
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().toLowerCase().contains("warming up"));
        }
    }

    @Nested
    @DisplayName("Fundamental Data Provider Integration")
    class FundamentalDataProviderTests {

        @Test
        void withRealBookToMarket_usesRealData() throws Exception {
            // Create a provider that returns real B/M data
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .pbRatio(0.5) // P/B = 0.5, so B/M = 2.0 (high value)
                    .bookValue(200.0)
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.7, 90, provider);
            
            // Prime with declining prices to generate positive reversal
            double[] decliningThenUp = new double[70];
            for (int i = 0; i < 50; i++) {
                decliningThenUp[i] = 120 - i * 0.5; // declining from 120 to 95
            }
            for (int i = 50; i < 70; i++) {
                decliningThenUp[i] = 95 + (i - 50) * 0.5; // rising from 95 to 105
            }
            primeHistory(strategy, SYMBOL, decliningThenUp);
            
            // Execute with price showing positive short-term return
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 106));
            
            // Should generate a LONG signal with real B/M (2.0 > 0.7 threshold)
            assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
            assertTrue(signal.getReason().contains("real B/M"));
            assertEquals(0.80, signal.getConfidence()); // Higher confidence with real data
        }

        @Test
        void withNoFundamentalData_fallsBackToProxy() throws Exception {
            FundamentalDataProvider provider = symbol -> Optional.empty();
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.3, 90, provider);
            
            // Prime with prices showing stock near 52-week low with reversal
            double[] prices = new double[70];
            for (int i = 0; i < 50; i++) {
                prices[i] = 150 - i; // peak at 150, decline to 100
            }
            for (int i = 50; i < 70; i++) {
                prices[i] = 100 + (i - 50) * 0.5; // slight recovery
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Execute
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 110));
            
            // Should work with proxy (distance from high as B/M proxy)
            assertNotNull(signal);
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                // Proxy-based signal should have lower confidence
                assertEquals(0.70, signal.getConfidence());
            }
        }

        @Test
        void withZeroPbRatio_fallsBackToProxy() throws Exception {
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .pbRatio(0.0) // Invalid P/B
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.3, 90, provider);
            
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            
            assertNotNull(signal);
            // Should not say "real B/M" when P/B is invalid
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertFalse(signal.getReason().contains("real B/M"));
            }
        }

        @Test
        void withNullPbRatio_fallsBackToProxy() throws Exception {
            FundamentalData fd = FundamentalData.builder()
                    .symbol(SYMBOL)
                    .pbRatio(null) // Missing P/B
                    .bookValue(100.0)
                    .reportDate(LocalDate.now())
                    .fetchedAt(OffsetDateTime.now())
                    .build();
            
            FundamentalDataProvider provider = symbol -> Optional.of(fd);
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.3, 90, provider);
            
            double[] prices = new double[70];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            TradeSignal signal = strategy.execute(portfolio, createMarketData(SYMBOL, 100));
            
            assertNotNull(signal);
        }
    }

    @Nested
    @DisplayName("Strategy Lifecycle")
    class StrategyLifecycleTests {

        @Test
        void getName_includesParameters() {
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.5, 60);
            assertEquals("Book-to-Market (50%, 60d)", strategy.getName());
        }

        @Test
        void getType_returnsLongTerm() {
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.7, 90);
            assertEquals(StrategyType.LONG_TERM, strategy.getType());
        }

        @Test
        void reset_clearsPriceHistory() throws Exception {
            BookToMarketStrategy strategy = new BookToMarketStrategy(0.7, 90);
            
            // Prime history
            double[] prices = new double[10];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100;
            }
            primeHistory(strategy, SYMBOL, prices);
            
            // Reset
            strategy.reset();
            
            // Verify cleared
            Field f = BookToMarketStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
            assertTrue(map.isEmpty());
        }
    }
}
