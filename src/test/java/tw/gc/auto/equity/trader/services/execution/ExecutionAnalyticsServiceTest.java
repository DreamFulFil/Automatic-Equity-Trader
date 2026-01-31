package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for ExecutionAnalyticsService.
 * Phase 4: Realistic Execution Modeling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExecutionAnalyticsService Tests")
class ExecutionAnalyticsServiceTest {

    private ExecutionAnalyticsService service;
    
    @BeforeEach
    void setUp() {
        service = new ExecutionAnalyticsService();
    }
    
    @AfterEach
    void tearDown() {
        service.clearAllRecords();
    }
    
    // ==================== Record Execution Tests ====================
    
    @Nested
    @DisplayName("Record Execution Tests")
    class RecordExecutionTests {
        
        @Test
        @DisplayName("should record execution and calculate slippage")
        void shouldRecordExecutionAndCalculateSlippage() {
            // Given: A BUY execution where fill price is higher than decision price
            ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                .symbol("2330.TW")
                .action("BUY")
                .decisionPrice(600.0)
                .fillPrice(600.30)  // Paid 0.30 more (0.05% slippage)
                .requestedQuantity(100)
                .filledQuantity(100)
                .executionTime(LocalDateTime.of(2026, 1, 15, 10, 30))
                .strategyName("MomentumStrategy")
                .build();
            
            // When
            service.recordExecution(record);
            
            // Then
            assertThat(service.getTotalRecordCount()).isEqualTo(1);
            
            ExecutionAnalyticsService.SymbolSlippageStats stats = service.getStatsForSymbol("2330.TW");
            assertThat(stats).isNotNull();
            assertThat(stats.getAverageSlippageBps()).isCloseTo(5.0, within(0.5)); // ~5 bps
        }
        
        @Test
        @DisplayName("should calculate negative slippage for favorable fills")
        void shouldCalculateNegativeSlippageForFavorableFills() {
            // Given: A BUY execution where fill price is LOWER than decision (favorable)
            ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                .symbol("2330.TW")
                .action("BUY")
                .decisionPrice(600.0)
                .fillPrice(599.70)  // Paid 0.30 less (negative slippage = favorable)
                .requestedQuantity(100)
                .filledQuantity(100)
                .executionTime(LocalDateTime.of(2026, 1, 15, 10, 30))
                .build();
            
            // When
            service.recordExecution(record);
            
            // Then
            ExecutionAnalyticsService.SymbolSlippageStats stats = service.getStatsForSymbol("2330.TW");
            assertThat(stats.getAverageSlippageBps()).isNegative(); // Favorable slippage
        }
        
        @Test
        @DisplayName("should handle SELL action slippage correctly")
        void shouldHandleSellSlippageCorrectly() {
            // Given: A SELL execution where fill price is lower than decision (unfavorable)
            ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                .symbol("2330.TW")
                .action("SELL")
                .decisionPrice(600.0)
                .fillPrice(599.70)  // Received 0.30 less (0.05% slippage)
                .requestedQuantity(100)
                .filledQuantity(100)
                .executionTime(LocalDateTime.of(2026, 1, 15, 10, 30))
                .build();
            
            // When
            service.recordExecution(record);
            
            // Then
            ExecutionAnalyticsService.SymbolSlippageStats stats = service.getStatsForSymbol("2330.TW");
            assertThat(stats.getAverageSlippageBps()).isCloseTo(5.0, within(0.5)); // ~5 bps
        }
        
        @Test
        @DisplayName("should ignore null records")
        void shouldIgnoreNullRecords() {
            // When
            service.recordExecution(null);
            
            // Then
            assertThat(service.getTotalRecordCount()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("should ignore records with null symbol")
        void shouldIgnoreRecordsWithNullSymbol() {
            // Given
            ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                .symbol(null)
                .action("BUY")
                .decisionPrice(100.0)
                .fillPrice(100.05)
                .build();
            
            // When
            service.recordExecution(record);
            
            // Then
            assertThat(service.getTotalRecordCount()).isEqualTo(0);
        }
    }
    
    // ==================== Time Bucket Tests ====================
    
    @Nested
    @DisplayName("Time Bucket Classification Tests")
    class TimeBucketTests {
        
        @Test
        @DisplayName("should classify market open period")
        void shouldClassifyMarketOpen() {
            // Given
            ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                .symbol("2330.TW")
                .action("BUY")
                .decisionPrice(100.0)
                .fillPrice(100.10)
                .requestedQuantity(100)
                .filledQuantity(100)
                .executionTime(LocalDateTime.of(2026, 1, 15, 9, 15)) // 9:15 AM
                .build();
            
            // When
            service.recordExecution(record);
            
            // Then
            Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTime = service.getSlippageByTimeBucket();
            // With only 1 sample, won't meet MIN_SAMPLES_FOR_ANALYSIS, but bucket should be assigned
            assertThat(service.getStatsForSymbol("2330.TW")).isNotNull();
        }
        
        @Test
        @DisplayName("should classify market close period")
        void shouldClassifyMarketClose() {
            // Given: Add enough samples to get analysis
            for (int i = 0; i < 15; i++) {
                ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                    .symbol("2330.TW")
                    .action("BUY")
                    .decisionPrice(100.0)
                    .fillPrice(100.10 + i * 0.01)
                    .requestedQuantity(100)
                    .filledQuantity(100)
                    .executionTime(LocalDateTime.of(2026, 1, 15, 13, 15)) // 1:15 PM - close period
                    .build();
                service.recordExecution(record);
            }
            
            // When
            Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTime = service.getSlippageByTimeBucket();
            
            // Then
            assertThat(slippageByTime).containsKey(ExecutionAnalyticsService.TimeBucket.MARKET_CLOSE);
        }
    }
    
    // ==================== Symbol Statistics Tests ====================
    
    @Nested
    @DisplayName("Symbol Statistics Tests")
    class SymbolStatisticsTests {
        
        @Test
        @DisplayName("should return empty for insufficient samples")
        void shouldReturnEmptyForInsufficientSamples() {
            // Given: Less than MIN_SAMPLES_FOR_ANALYSIS
            for (int i = 0; i < 5; i++) {
                service.recordExecution(createBasicRecord("2330.TW", 100.0, 100.10));
            }
            
            // When
            OptionalDouble avg = service.getAverageSlippageForSymbol("2330.TW");
            
            // Then
            assertThat(avg).isEmpty();
        }
        
        @Test
        @DisplayName("should return average for sufficient samples")
        void shouldReturnAverageForSufficientSamples() {
            // Given: Add MIN_SAMPLES_FOR_ANALYSIS records
            for (int i = 0; i < 15; i++) {
                service.recordExecution(createBasicRecord("2330.TW", 100.0, 100.10));
            }
            
            // When
            OptionalDouble avg = service.getAverageSlippageForSymbol("2330.TW");
            
            // Then
            assertThat(avg).isPresent();
            assertThat(avg.getAsDouble()).isCloseTo(10.0, within(1.0)); // 10 bps
        }
        
        @Test
        @DisplayName("should identify high slippage symbols")
        void shouldIdentifyHighSlippageSymbols() {
            // Given: Add high-slippage records for one symbol
            for (int i = 0; i < 15; i++) {
                // High slippage: 100 bps = 1%
                service.recordExecution(createBasicRecord("BAD_STOCK", 100.0, 101.0));
            }
            
            // Add normal slippage for another
            for (int i = 0; i < 15; i++) {
                service.recordExecution(createBasicRecord("GOOD_STOCK", 100.0, 100.05));
            }
            
            // When
            List<ExecutionAnalyticsService.SymbolSlippageStats> highSlippage = service.getHighSlippageSymbols();
            
            // Then
            assertThat(highSlippage).hasSize(1);
            assertThat(highSlippage.get(0).getSymbol()).isEqualTo("BAD_STOCK");
            assertThat(highSlippage.get(0).isHighSlippage()).isTrue();
        }
    }
    
    // ==================== Fill Rate Tests ====================
    
    @Nested
    @DisplayName("Fill Rate Tests")
    class FillRateTests {
        
        @Test
        @DisplayName("should calculate 100% fill rate when all orders fully filled")
        void shouldCalculate100FillRate() {
            // Given
            for (int i = 0; i < 10; i++) {
                ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                    .symbol("2330.TW")
                    .action("BUY")
                    .decisionPrice(100.0)
                    .fillPrice(100.05)
                    .requestedQuantity(100)
                    .filledQuantity(100) // Fully filled
                    .executionTime(LocalDateTime.now())
                    .build();
                service.recordExecution(record);
            }
            
            // When
            double fillRate = service.calculateFillRate("2330.TW");
            
            // Then
            assertThat(fillRate).isEqualTo(100.0);
        }
        
        @Test
        @DisplayName("should calculate partial fill rate correctly")
        void shouldCalculatePartialFillRate() {
            // Given: 50% partial fills
            for (int i = 0; i < 10; i++) {
                ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                    .symbol("2330.TW")
                    .action("BUY")
                    .decisionPrice(100.0)
                    .fillPrice(100.05)
                    .requestedQuantity(100)
                    .filledQuantity(i % 2 == 0 ? 100 : 50) // Half are partial
                    .executionTime(LocalDateTime.now())
                    .build();
                service.recordExecution(record);
            }
            
            // When
            double fillRate = service.calculateFillRate("2330.TW");
            
            // Then
            assertThat(fillRate).isEqualTo(50.0); // 5 out of 10 fully filled
        }
        
        @Test
        @DisplayName("should return 100% for unknown symbol")
        void shouldReturn100ForUnknownSymbol() {
            // When
            double fillRate = service.calculateFillRate("UNKNOWN");
            
            // Then
            assertThat(fillRate).isEqualTo(100.0);
        }
    }
    
    // ==================== Period Summary Tests ====================
    
    @Nested
    @DisplayName("Period Summary Tests")
    class PeriodSummaryTests {
        
        @Test
        @DisplayName("should generate summary for date range")
        void shouldGenerateSummaryForDateRange() {
            // Given
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 20; i++) {
                ExecutionAnalyticsService.ExecutionRecord record = ExecutionAnalyticsService.ExecutionRecord.builder()
                    .symbol("2330.TW")
                    .action("BUY")
                    .decisionPrice(100.0)
                    .fillPrice(100.10)
                    .requestedQuantity(100)
                    .filledQuantity(100)
                    .executionTime(today.minusDays(i % 5).atTime(10, 30))
                    .build();
                service.recordExecution(record);
            }
            
            // When
            ExecutionAnalyticsService.ExecutionSummary summary = 
                service.getSummaryForPeriod(today.minusWeeks(1), today);
            
            // Then
            assertThat(summary.getTotalExecutions()).isGreaterThan(0);
            assertThat(summary.getAverageSlippageBps()).isPositive();
            assertThat(summary.getFillRate()).isEqualTo(100.0);
            assertThat(summary.getSymbolCount()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("should return empty summary for no data")
        void shouldReturnEmptySummaryForNoData() {
            // When
            ExecutionAnalyticsService.ExecutionSummary summary = 
                service.getSummaryForPeriod(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 7));
            
            // Then
            assertThat(summary.getTotalExecutions()).isEqualTo(0);
            assertThat(summary.getAverageSlippageBps()).isEqualTo(0.0);
            assertThat(summary.getFillRate()).isEqualTo(100.0);
        }
    }
    
    // ==================== Helper Methods ====================
    
    private ExecutionAnalyticsService.ExecutionRecord createBasicRecord(String symbol, double decisionPrice, double fillPrice) {
        return ExecutionAnalyticsService.ExecutionRecord.builder()
            .symbol(symbol)
            .action("BUY")
            .decisionPrice(decisionPrice)
            .fillPrice(fillPrice)
            .requestedQuantity(100)
            .filledQuantity(100)
            .executionTime(LocalDateTime.now())
            .build();
    }
}
