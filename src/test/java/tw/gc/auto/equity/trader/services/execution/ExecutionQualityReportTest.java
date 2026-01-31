package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ExecutionQualityReport.
 * Phase 4: Realistic Execution Modeling
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExecutionQualityReport Tests")
class ExecutionQualityReportTest {

    @Mock
    private ExecutionAnalyticsService executionAnalyticsService;
    
    private ExecutionQualityReport report;
    
    @BeforeEach
    void setUp() {
        report = new ExecutionQualityReport(executionAnalyticsService);
    }
    
    // ==================== Report Generation Tests ====================
    
    @Nested
    @DisplayName("Report Generation Tests")
    class ReportGenerationTests {
        
        @Test
        @DisplayName("should generate report with all components")
        void shouldGenerateReportWithAllComponents() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary summary = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(100)
                .averageSlippageBps(12.5)
                .maxSlippageBps(45.0)
                .minSlippageBps(2.0)
                .fillRate(97.5)
                .highSlippageCount(5)
                .symbolCount(15)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(summary);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of());
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getStartDate()).isEqualTo(startDate);
            assertThat(result.getEndDate()).isEqualTo(endDate);
            assertThat(result.getSummary()).isEqualTo(summary);
            assertThat(result.getOverallGrade()).isNotNull();
            assertThat(result.getRecommendations()).isNotEmpty();
        }
        
        @Test
        @DisplayName("should handle empty data gracefully")
        void shouldHandleEmptyData() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary emptySummary = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(0)
                .averageSlippageBps(0.0)
                .maxSlippageBps(0.0)
                .fillRate(100.0)
                .highSlippageCount(0)
                .symbolCount(0)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(emptySummary);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of());
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getSummary().getTotalExecutions()).isEqualTo(0);
            assertThat(result.getOverallGrade()).contains("N/A");
        }
    }
    
    // ==================== Grade Calculation Tests ====================
    
    @Nested
    @DisplayName("Grade Calculation Tests")
    class GradeCalculationTests {
        
        @Test
        @DisplayName("should assign A+ grade for excellent execution")
        void shouldAssignAPlusForExcellentExecution() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary excellent = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(50)
                .averageSlippageBps(5.0) // Very low
                .maxSlippageBps(10.0)
                .fillRate(99.0) // Very high
                .highSlippageCount(0)
                .symbolCount(10)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(excellent);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of());
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getOverallGrade()).contains("A");
        }
        
        @Test
        @DisplayName("should assign lower grade for poor execution")
        void shouldAssignLowerGradeForPoorExecution() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary poor = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(50)
                .averageSlippageBps(50.0) // Very high
                .maxSlippageBps(100.0)
                .fillRate(80.0) // Low
                .highSlippageCount(20)
                .symbolCount(10)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(poor);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of());
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getOverallGrade()).containsAnyOf("C", "D");
        }
    }
    
    // ==================== Recommendation Tests ====================
    
    @Nested
    @DisplayName("Recommendation Tests")
    class RecommendationTests {
        
        @Test
        @DisplayName("should recommend action for high slippage")
        void shouldRecommendActionForHighSlippage() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary highSlippage = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(50)
                .averageSlippageBps(30.0) // Above threshold
                .maxSlippageBps(50.0)
                .fillRate(95.0)
                .highSlippageCount(10)
                .symbolCount(10)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(highSlippage);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of());
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getRecommendations()).anyMatch(r -> r.contains("slippage") || r.contains("limit orders"));
        }
        
        @Test
        @DisplayName("should recommend action for low fill rate")
        void shouldRecommendActionForLowFillRate() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary lowFill = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(50)
                .averageSlippageBps(10.0)
                .maxSlippageBps(20.0)
                .fillRate(85.0) // Below 95% threshold
                .highSlippageCount(2)
                .symbolCount(10)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(lowFill);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of());
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getRecommendations()).anyMatch(r -> r.toLowerCase().contains("fill rate"));
        }
        
        @Test
        @DisplayName("should identify problematic stocks")
        void shouldIdentifyProblematicStocks() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary summary = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(50)
                .averageSlippageBps(10.0)
                .maxSlippageBps(20.0)
                .fillRate(95.0)
                .highSlippageCount(2)
                .symbolCount(10)
                .build();
            
            ExecutionAnalyticsService.SymbolSlippageStats badStock = ExecutionAnalyticsService.SymbolSlippageStats.builder()
                .symbol("BAD_STOCK")
                .sampleCount(20)
                .averageSlippageBps(50.0)
                .maxSlippageBps(100.0)
                .minSlippageBps(20.0)
                .stdDevSlippageBps(15.0)
                .fillRate(85.0)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(summary);
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of(badStock));
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class));
            
            // When
            ExecutionQualityReport.WeeklyReport result = report.generateReport(startDate, endDate);
            
            // Then
            assertThat(result.getHighSlippageSymbols()).hasSize(1);
            assertThat(result.getRecommendations()).anyMatch(r -> r.contains("BAD_STOCK"));
        }
    }
    
    // ==================== Telegram Summary Tests ====================
    
    @Nested
    @DisplayName("Telegram Summary Tests")
    class TelegramSummaryTests {
        
        @Test
        @DisplayName("should generate telegram summary")
        void shouldGenerateTelegramSummary() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary summary = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(100)
                .averageSlippageBps(12.5)
                .maxSlippageBps(45.0)
                .fillRate(97.5)
                .highSlippageCount(5)
                .symbolCount(15)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(summary);
            
            // When
            String telegramSummary = report.generateTelegramSummary(startDate, endDate);
            
            // Then
            assertThat(telegramSummary).contains("Execution Quality Report");
            assertThat(telegramSummary).contains("100"); // Total executions
            assertThat(telegramSummary).contains("12.5"); // Slippage
            assertThat(telegramSummary).contains("97.5"); // Fill rate
        }
        
        @Test
        @DisplayName("should handle no executions")
        void shouldHandleNoExecutions() {
            // Given
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate endDate = LocalDate.of(2026, 1, 7);
            
            ExecutionAnalyticsService.ExecutionSummary empty = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(0)
                .averageSlippageBps(0)
                .maxSlippageBps(0)
                .fillRate(100.0)
                .highSlippageCount(0)
                .symbolCount(0)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(startDate, endDate)).thenReturn(empty);
            
            // When
            String telegramSummary = report.generateTelegramSummary(startDate, endDate);
            
            // Then
            assertThat(telegramSummary).contains("No executions");
        }
    }
    
    // ==================== Stocks To Avoid Tests ====================
    
    @Nested
    @DisplayName("Stocks To Avoid Tests")
    class StocksToAvoidTests {
        
        @Test
        @DisplayName("should identify stocks to avoid")
        void shouldIdentifyStocksToAvoid() {
            // Given
            ExecutionAnalyticsService.SymbolSlippageStats veryBadStock = ExecutionAnalyticsService.SymbolSlippageStats.builder()
                .symbol("AVOID_ME")
                .sampleCount(20)
                .averageSlippageBps(100.0) // Very high (> 2x threshold)
                .maxSlippageBps(200.0)
                .minSlippageBps(50.0)
                .stdDevSlippageBps(30.0)
                .fillRate(70.0)
                .build();
            
            when(executionAnalyticsService.getHighSlippageSymbols()).thenReturn(List.of(veryBadStock));
            
            // When
            List<ExecutionQualityReport.StockAvoidanceRecommendation> toAvoid = report.getStocksToAvoid();
            
            // Then
            assertThat(toAvoid).hasSize(1);
            assertThat(toAvoid.get(0).getSymbol()).isEqualTo("AVOID_ME");
            assertThat(toAvoid.get(0).getReason()).containsIgnoringCase("slippage");
        }
    }
    
    // ==================== Optimal Execution Times Tests ====================
    
    @Nested
    @DisplayName("Optimal Execution Times Tests")
    class OptimalExecutionTimesTests {
        
        @Test
        @DisplayName("should rank time buckets by slippage")
        void shouldRankTimeBucketsBySlippage() {
            // Given
            Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTime = new EnumMap<>(ExecutionAnalyticsService.TimeBucket.class);
            slippageByTime.put(ExecutionAnalyticsService.TimeBucket.MARKET_OPEN, 25.0);
            slippageByTime.put(ExecutionAnalyticsService.TimeBucket.MORNING_EARLY, 10.0);
            slippageByTime.put(ExecutionAnalyticsService.TimeBucket.MIDDAY, 8.0);
            slippageByTime.put(ExecutionAnalyticsService.TimeBucket.MARKET_CLOSE, 30.0);
            
            when(executionAnalyticsService.getSlippageByTimeBucket()).thenReturn(slippageByTime);
            
            // When
            List<ExecutionQualityReport.TimeBucketRanking> rankings = report.getOptimalExecutionTimes();
            
            // Then
            assertThat(rankings).hasSize(4);
            assertThat(rankings.get(0).getTimeBucket()).isEqualTo(ExecutionAnalyticsService.TimeBucket.MIDDAY);
            assertThat(rankings.get(0).isRecommended()).isTrue();
            assertThat(rankings.get(3).getTimeBucket()).isEqualTo(ExecutionAnalyticsService.TimeBucket.MARKET_CLOSE);
        }
        
        @Test
        @DisplayName("should provide human-readable time ranges")
        void shouldProvideHumanReadableTimeRanges() {
            // Given
            ExecutionQualityReport.TimeBucketRanking ranking = ExecutionQualityReport.TimeBucketRanking.builder()
                .timeBucket(ExecutionAnalyticsService.TimeBucket.MARKET_OPEN)
                .averageSlippageBps(20.0)
                .isRecommended(false)
                .build();
            
            // Then
            assertThat(ranking.getTimeRange()).isEqualTo("09:00-09:30");
        }
    }
    
    // ==================== Execution Quality Check Tests ====================
    
    @Nested
    @DisplayName("Execution Quality Check Tests")
    class ExecutionQualityCheckTests {
        
        @Test
        @DisplayName("should return acceptable for good quality")
        void shouldReturnAcceptableForGoodQuality() {
            // Given
            ExecutionAnalyticsService.ExecutionSummary good = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(LocalDate.now().minusWeeks(4))
                .endDate(LocalDate.now())
                .totalExecutions(50)
                .averageSlippageBps(10.0)
                .maxSlippageBps(20.0)
                .fillRate(96.0)
                .highSlippageCount(2)
                .symbolCount(10)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(any(), any())).thenReturn(good);
            
            // When
            boolean acceptable = report.isExecutionQualityAcceptable();
            
            // Then
            assertThat(acceptable).isTrue();
        }
        
        @Test
        @DisplayName("should return true for insufficient data")
        void shouldReturnTrueForInsufficientData() {
            // Given
            ExecutionAnalyticsService.ExecutionSummary fewExecutions = ExecutionAnalyticsService.ExecutionSummary.builder()
                .startDate(LocalDate.now().minusWeeks(4))
                .endDate(LocalDate.now())
                .totalExecutions(5) // Less than 20
                .averageSlippageBps(50.0)
                .maxSlippageBps(100.0)
                .fillRate(50.0)
                .highSlippageCount(3)
                .symbolCount(2)
                .build();
            
            when(executionAnalyticsService.getSummaryForPeriod(any(), any())).thenReturn(fewExecutions);
            
            // When
            boolean acceptable = report.isExecutionQualityAcceptable();
            
            // Then
            assertThat(acceptable).isTrue(); // Assume acceptable with insufficient data
        }
    }
}
