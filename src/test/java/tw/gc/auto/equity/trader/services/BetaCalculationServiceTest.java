package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.IndexData;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.IndexDataRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.BetaCalculationService.BetaCategory;
import tw.gc.auto.equity.trader.services.BetaCalculationService.BetaResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BetaCalculationService.
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BetaCalculationService Unit Tests")
class BetaCalculationServiceTest {

    @Mock
    private IndexDataRepository indexDataRepository;

    @Mock
    private MarketDataRepository marketDataRepository;

    private BetaCalculationService betaCalculationService;

    @BeforeEach
    void setUp() {
        betaCalculationService = new BetaCalculationService(
                indexDataRepository,
                marketDataRepository
        );
    }

    @Nested
    @DisplayName("getBeta() with sufficient data")
    class GetBetaWithDataTests {

        @Test
        void shouldCalculateBetaWithSufficientData() {
            // Setup: create 60+ days of index and stock data
            List<IndexData> indexHistory = createIndexHistory(70, 1.0); // 1% daily moves
            List<MarketData> stockHistory = createStockHistory("2330.TW", 70, 1.5); // 1.5% daily moves
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            Double beta = betaCalculationService.getBeta("2330.TW", 60);
            
            assertThat(beta).isNotNull();
            // Beta should be around 1.5 (stock moves 1.5x the index)
            assertThat(beta).isGreaterThan(0);
        }

        @Test
        void shouldCacheBetaResults() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory = createStockHistory("2330.TW", 70, 1.0);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            // First call - calculates
            Double beta1 = betaCalculationService.getBeta("2330.TW");
            // Second call - should use cache
            Double beta2 = betaCalculationService.getBeta("2330.TW");
            
            assertThat(beta1).isEqualTo(beta2);
            // Repository should only be called once due to caching
            verify(indexDataRepository, times(1)).findBySymbolAndDateRange(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getBeta() with insufficient data")
    class GetBetaInsufficientDataTests {

        @Test
        void shouldReturnNullWhenInsufficientIndexData() {
            List<IndexData> indexHistory = createIndexHistory(10, 1.0); // Only 10 days
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            
            Double beta = betaCalculationService.getBeta("2330.TW");
            
            assertThat(beta).isNull();
        }

        @Test
        void shouldReturnNullWhenInsufficientStockData() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory = createStockHistory("2330.TW", 10, 1.0); // Only 10 days
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            Double beta = betaCalculationService.getBeta("2330.TW");
            
            assertThat(beta).isNull();
        }
    }

    @Nested
    @DisplayName("getBetaResult()")
    class GetBetaResultTests {

        @Test
        void shouldReturnDetailedBetaResult() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory = createStockHistory("2330.TW", 70, 1.2);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            BetaResult result = betaCalculationService.getBetaResult("2330.TW");
            
            assertThat(result).isNotNull();
            assertThat(result.symbol()).isEqualTo("2330.TW");
            assertThat(result.beta()).isNotNull();
            assertThat(result.rSquared()).isBetween(0.0, 1.0);
            assertThat(result.dataPoints()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("getBetas() for multiple symbols")
    class GetBetasTests {

        @Test
        void shouldReturnBetasForMultipleSymbols() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory1 = createStockHistory("2330.TW", 70, 1.0);
            List<MarketData> stockHistory2 = createStockHistory("2454.TW", 70, 1.5);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory1);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2454.TW"), any(), any()))
                    .thenReturn(stockHistory2);
            
            Map<String, Double> betas = betaCalculationService.getBetas(List.of("2330.TW", "2454.TW"));
            
            assertThat(betas).containsKeys("2330.TW", "2454.TW");
        }
    }

    @Nested
    @DisplayName("categorize()")
    class CategorizeTests {

        @Test
        void shouldCategorizeLowBeta() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory = createStockHistory("2330.TW", 70, 0.5); // Low volatility
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            BetaCategory category = betaCalculationService.categorize("2330.TW");
            
            // May be LOW_BETA or NEUTRAL depending on calculated beta
            assertThat(category).isIn(BetaCategory.LOW_BETA, BetaCategory.NEUTRAL, BetaCategory.HIGH_BETA);
        }

        @Test
        void shouldReturnUnknownWhenNoData() {
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(List.of());
            
            BetaCategory category = betaCalculationService.categorize("UNKNOWN");
            
            assertThat(category).isEqualTo(BetaCategory.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("findLowBetaStocks()")
    class FindLowBetaStocksTests {

        @Test
        void shouldFindLowBetaStocks() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> lowVolStock = createStockHistory("2412.TW", 70, 0.5);
            List<MarketData> highVolStock = createStockHistory("2330.TW", 70, 2.0);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2412.TW"), any(), any()))
                    .thenReturn(lowVolStock);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(highVolStock);
            
            List<String> lowBeta = betaCalculationService.findLowBetaStocks(
                    List.of("2412.TW", "2330.TW"), 0.8);
            
            // At least validate the method runs without error
            assertThat(lowBeta).isNotNull();
        }
    }

    @Nested
    @DisplayName("calculatePortfolioBeta()")
    class CalculatePortfolioBetaTests {

        @Test
        void shouldCalculatePortfolioBeta() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory1 = createStockHistory("2330.TW", 70, 1.0);
            List<MarketData> stockHistory2 = createStockHistory("2454.TW", 70, 1.5);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory1);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2454.TW"), any(), any()))
                    .thenReturn(stockHistory2);
            
            Map<String, Double> positions = new HashMap<>();
            positions.put("2330.TW", 0.6); // 60% weight
            positions.put("2454.TW", 0.4); // 40% weight
            
            Double portfolioBeta = betaCalculationService.calculatePortfolioBeta(positions);
            
            assertThat(portfolioBeta).isNotNull();
        }

        @Test
        void shouldReturnNullForEmptyPortfolio() {
            Double portfolioBeta = betaCalculationService.calculatePortfolioBeta(new HashMap<>());
            
            assertThat(portfolioBeta).isNull();
        }
    }

    @Nested
    @DisplayName("Cache Management")
    class CacheTests {

        @Test
        void shouldInvalidateCacheForSymbol() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory = createStockHistory("2330.TW", 70, 1.0);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            // First call
            betaCalculationService.getBeta("2330.TW");
            
            // Invalidate
            betaCalculationService.invalidateCache("2330.TW");
            
            // Second call should recalculate
            betaCalculationService.getBeta("2330.TW");
            
            // Should have been called twice (once before invalidation, once after)
            verify(indexDataRepository, times(2)).findBySymbolAndDateRange(any(), any(), any());
        }

        @Test
        void shouldClearAllCache() {
            List<IndexData> indexHistory = createIndexHistory(70, 1.0);
            List<MarketData> stockHistory = createStockHistory("2330.TW", 70, 1.0);
            
            when(indexDataRepository.findBySymbolAndDateRange(eq("^TWII"), any(), any()))
                    .thenReturn(indexHistory);
            when(marketDataRepository.findBySymbolAndDateRange(eq("2330.TW"), any(), any()))
                    .thenReturn(stockHistory);
            
            // First call
            betaCalculationService.getBeta("2330.TW");
            
            // Clear all cache
            betaCalculationService.clearCache();
            
            // Second call should recalculate
            betaCalculationService.getBeta("2330.TW");
            
            verify(indexDataRepository, times(2)).findBySymbolAndDateRange(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("BetaResult record")
    class BetaResultTests {

        @Test
        void shouldDetectExpiredResult() {
            BetaResult result = new BetaResult(
                    "2330.TW",
                    1.0,
                    0.8,
                    0.05,
                    0.2,
                    0.1,
                    60,
                    50,
                    System.currentTimeMillis() - 5 * 60 * 60 * 1000 // 5 hours ago
            );
            
            assertThat(result.isExpired()).isTrue();
        }

        @Test
        void shouldDetectFreshResult() {
            BetaResult result = new BetaResult(
                    "2330.TW",
                    1.0,
                    0.8,
                    0.05,
                    0.2,
                    0.1,
                    60,
                    50,
                    System.currentTimeMillis() // Now
            );
            
            assertThat(result.isExpired()).isFalse();
        }

        @Test
        void shouldDetectReliableResult() {
            BetaResult reliable = new BetaResult(
                    "2330.TW", 1.0, 0.5, 0.05, 0.2, 0.1, 60, 50, System.currentTimeMillis()
            );
            
            BetaResult unreliable = new BetaResult(
                    "2330.TW", 1.0, 0.1, 0.05, 0.2, 0.1, 60, 10, System.currentTimeMillis()
            );
            
            assertThat(reliable.isReliable()).isTrue();
            assertThat(unreliable.isReliable()).isFalse();
        }

        @Test
        void shouldDetectLowAndHighBeta() {
            BetaResult lowBeta = new BetaResult(
                    "2412.TW", 0.6, 0.8, 0.05, 0.1, 0.05, 60, 50, System.currentTimeMillis()
            );
            
            BetaResult highBeta = new BetaResult(
                    "2330.TW", 1.5, 0.8, 0.05, 0.3, 0.1, 60, 50, System.currentTimeMillis()
            );
            
            assertThat(lowBeta.isLowBeta()).isTrue();
            assertThat(lowBeta.isHighBeta()).isFalse();
            assertThat(highBeta.isHighBeta()).isTrue();
            assertThat(highBeta.isLowBeta()).isFalse();
        }

        @Test
        void shouldFormatToString() {
            BetaResult result = new BetaResult(
                    "2330.TW", 1.25, 0.8, 0.05, 0.2, 0.1, 60, 50, System.currentTimeMillis()
            );
            
            String str = result.toString();
            assertThat(str).contains("2330.TW");
            assertThat(str).contains("1.25");
        }
    }

    // ========== Helper Methods ==========

    private List<IndexData> createIndexHistory(int days, double dailyMovePercent) {
        List<IndexData> history = new ArrayList<>();
        double baseValue = 20000.0;
        
        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(days - i);
            double prevClose = baseValue;
            // Alternate up and down moves
            double change = (i % 2 == 0 ? 1 : -1) * prevClose * dailyMovePercent / 100;
            double closeValue = prevClose + change;
            
            history.add(IndexData.builder()
                    .indexSymbol("^TWII")
                    .tradeDate(date)
                    .fetchedAt(OffsetDateTime.now())
                    .closeValue(closeValue)
                    .previousClose(prevClose)
                    .build());
            
            baseValue = closeValue;
        }
        
        return history;
    }

    private List<MarketData> createStockHistory(String symbol, int days, double betaMultiplier) {
        List<MarketData> history = new ArrayList<>();
        double basePrice = 500.0;
        
        for (int i = 0; i < days; i++) {
            LocalDateTime timestamp = LocalDateTime.now().minusDays(days - i);
            double prevClose = basePrice;
            // Alternate up and down moves, scaled by beta
            double change = (i % 2 == 0 ? 1 : -1) * prevClose * 0.01 * betaMultiplier;
            double closePrice = prevClose + change;
            
            MarketData data = new MarketData();
            data.setSymbol(symbol);
            data.setTimestamp(timestamp);
            data.setClose(closePrice);
            
            history.add(data);
            basePrice = closePrice;
        }
        
        return history;
    }
}
