package tw.gc.auto.equity.trader.services.walkforward;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OverfittingDetector}.
 */
class OverfittingDetectorTest {
    
    private OverfittingDetector detector;
    
    @BeforeEach
    void setUp() {
        detector = new OverfittingDetector();
    }
    
    private WalkForwardWindow createWindow(int index) {
        LocalDate trainStart = LocalDate.of(2025, 1, 1).plusDays(index * 20L);
        LocalDate trainEnd = trainStart.plusDays(59);
        LocalDate testStart = trainEnd.plusDays(1);
        LocalDate testEnd = testStart.plusDays(19);
        return new WalkForwardWindow(index, trainStart, trainEnd, testStart, testEnd);
    }
    
    private WalkForwardResult createResult(
            int windowIndex, 
            double isSharpe, 
            double oosSharpe,
            double isReturn,
            double oosReturn,
            Map<String, Double> params) {
        return WalkForwardResult.builder()
            .window(createWindow(windowIndex))
            .strategyName("TestStrategy")
            .optimalParameters(params)
            .inSampleSharpe(isSharpe)
            .inSampleSortino(isSharpe * 1.2)
            .inSampleCalmar(isSharpe * 0.8)
            .inSampleReturn(isReturn)
            .inSampleMaxDrawdown(8.0)
            .inSampleWinRate(55.0)
            .inSampleTrades(50)
            .outOfSampleSharpe(oosSharpe)
            .outOfSampleSortino(oosSharpe * 1.2)
            .outOfSampleCalmar(oosSharpe * 0.8)
            .outOfSampleReturn(oosReturn)
            .outOfSampleMaxDrawdown(6.0)
            .outOfSampleWinRate(52.0)
            .outOfSampleTrades(20)
            .combinedFitness(0.85)
            .parameterCombinationsTested(100)
            .optimizationDurationMs(1000)
            .build();
    }
    
    @Nested
    @DisplayName("Empty/Null Input Handling")
    class EmptyInputTests {
        
        @Test
        @DisplayName("should return overfit=true for null results")
        void shouldReturnOverfitForNullResults() {
            var analysis = detector.analyzeResults(null);
            
            assertThat(analysis.isOverfit()).isTrue();
            assertThat(analysis.warnings()).contains("No results to analyze");
        }
        
        @Test
        @DisplayName("should return overfit=true for empty results")
        void shouldReturnOverfitForEmptyResults() {
            var analysis = detector.analyzeResults(List.of());
            
            assertThat(analysis.isOverfit()).isTrue();
            assertThat(analysis.warnings()).contains("No results to analyze");
        }
    }
    
    @Nested
    @DisplayName("IS/OOS Ratio Analysis")
    class IsOosRatioTests {
        
        @Test
        @DisplayName("should detect overfitting with high IS/OOS ratio")
        void shouldDetectOverfittingWithHighRatio() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 3.0, 0.8, 20.0, 5.0, Map.of("period", 14.0)), // Ratio = 3.75
                createResult(1, 2.5, 0.7, 18.0, 4.0, Map.of("period", 14.0)), // Ratio = 3.57
                createResult(2, 2.8, 0.9, 19.0, 6.0, Map.of("period", 14.0))  // Ratio = 3.11
            );
            
            var analysis = detector.analyzeResults(results);
            
            assertThat(analysis.isOverfit()).isTrue();
            assertThat(analysis.confidenceLevel()).isGreaterThan(0.3);
            assertThat(analysis.warnings()).anyMatch(w -> w.contains("IS/OOS ratio"));
        }
        
        @Test
        @DisplayName("should not detect overfitting with low IS/OOS ratio")
        void shouldNotDetectOverfittingWithLowRatio() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.2, 1.1, 10.0, 9.0, Map.of("period", 14.0)),  // Ratio = 1.09
                createResult(1, 1.3, 1.2, 11.0, 10.0, Map.of("period", 14.0)), // Ratio = 1.08
                createResult(2, 1.1, 1.0, 9.0, 8.5, Map.of("period", 14.0))    // Ratio = 1.10
            );
            
            var analysis = detector.analyzeResults(results);
            
            assertThat(analysis.isOverfit()).isFalse();
            assertThat(analysis.confidenceLevel()).isLessThan(0.5);
        }
    }
    
    @Nested
    @DisplayName("Negative OOS Performance Analysis")
    class NegativeOosTests {
        
        @Test
        @DisplayName("should detect overfitting when most windows have negative OOS")
        void shouldDetectOverfittingWithNegativeOos() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, -0.3, 15.0, -5.0, Map.of("period", 14.0)),
                createResult(1, 1.3, -0.2, 12.0, -3.0, Map.of("period", 14.0)),
                createResult(2, 1.4, -0.4, 14.0, -6.0, Map.of("period", 14.0))
            );
            
            var analysis = detector.analyzeResults(results);
            
            // Confidence should be at or above threshold for detection (100% negative OOS = 0.25 base + more from ratio)
            assertThat(analysis.confidenceLevel()).isGreaterThanOrEqualTo(0.25);
            assertThat(analysis.warnings()).anyMatch(w -> w.contains("negative OOS"));
        }
        
        @Test
        @DisplayName("should flag positive IS but negative OOS pattern")
        void shouldFlagPositiveIsNegativeOosPattern() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, -0.3, 15.0, -5.0, Map.of("period", 14.0)),
                createResult(1, 1.3, 0.5, 12.0, 3.0, Map.of("period", 14.0)),
                createResult(2, 1.4, -0.4, 14.0, -6.0, Map.of("period", 14.0))
            );
            
            var analysis = detector.analyzeResults(results);
            
            assertThat(analysis.warnings()).anyMatch(w -> 
                w.contains("positive IS but negative OOS") || w.contains("negative OOS"));
        }
    }
    
    @Nested
    @DisplayName("Parameter Stability Analysis")
    class ParameterStabilityTests {
        
        @Test
        @DisplayName("should detect high parameter drift")
        void shouldDetectHighParameterDrift() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, 1.2, 15.0, 12.0, Map.of("period", 10.0)),
                createResult(1, 1.4, 1.1, 14.0, 11.0, Map.of("period", 20.0)), // Big jump
                createResult(2, 1.3, 1.0, 13.0, 10.0, Map.of("period", 15.0)),
                createResult(3, 1.5, 1.2, 15.0, 12.0, Map.of("period", 25.0))  // Big jump
            );
            
            var analysis = detector.analyzeResults(results);
            
            // Parameter drift should be detected in warnings
            assertThat(analysis.diagnostics()).containsKey("parameterCoefficientsOfVariation");
        }
        
        @Test
        @DisplayName("should accept stable parameters")
        void shouldAcceptStableParameters() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, 1.2, 15.0, 12.0, Map.of("period", 14.0)),
                createResult(1, 1.4, 1.1, 14.0, 11.0, Map.of("period", 14.0)), // Same
                createResult(2, 1.3, 1.0, 13.0, 10.0, Map.of("period", 14.0)), // Same
                createResult(3, 1.5, 1.2, 15.0, 12.0, Map.of("period", 14.0))  // Same
            );
            
            var analysis = detector.analyzeResults(results);
            
            // Should not have parameter drift warnings
            assertThat(analysis.warnings()).noneMatch(w -> w.contains("parameter") && w.contains("drift"));
        }
    }
    
    @Nested
    @DisplayName("Performance Consistency Analysis")
    class PerformanceConsistencyTests {
        
        @Test
        @DisplayName("should detect inconsistent robustness scores")
        void shouldDetectInconsistentRobustness() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, 1.4, 15.0, 14.0, Map.of("period", 14.0)),  // High robustness
                createResult(1, 1.5, 0.3, 15.0, 2.0, Map.of("period", 14.0)),   // Low robustness
                createResult(2, 1.5, 1.3, 15.0, 13.0, Map.of("period", 14.0)),  // High robustness
                createResult(3, 1.5, 0.2, 15.0, 1.0, Map.of("period", 14.0))    // Low robustness
            );
            
            var analysis = detector.analyzeResults(results);
            
            // Should have consistency-related diagnostics
            assertThat(analysis.diagnostics()).containsKey("robustnessStdDev");
        }
        
        @Test
        @DisplayName("should flag low average robustness")
        void shouldFlagLowAverageRobustness() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, 0.5, 15.0, 5.0, Map.of("period", 14.0)),
                createResult(1, 1.5, 0.4, 15.0, 4.0, Map.of("period", 14.0)),
                createResult(2, 1.5, 0.6, 15.0, 6.0, Map.of("period", 14.0))
            );
            
            var analysis = detector.analyzeResults(results);
            
            assertThat(analysis.warnings()).anyMatch(w -> 
                w.toLowerCase().contains("robustness") && w.toLowerCase().contains("below"));
        }
    }
    
    @Nested
    @DisplayName("Window-Level Overfitting Check")
    class WindowLevelTests {
        
        @Test
        @DisplayName("should detect overfit window")
        void shouldDetectOverfitWindow() {
            var overfitResult = createResult(0, 2.0, 0.5, 20.0, 5.0, Map.of("period", 14.0));
            
            assertThat(detector.isWindowOverfit(overfitResult)).isTrue();
        }
        
        @Test
        @DisplayName("should accept robust window")
        void shouldAcceptRobustWindow() {
            var robustResult = createResult(0, 1.5, 1.3, 15.0, 13.0, Map.of("period", 14.0));
            
            assertThat(detector.isWindowOverfit(robustResult)).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Analysis Summary")
    class AnalysisSummaryTests {
        
        @Test
        @DisplayName("should generate readable summary")
        void shouldGenerateReadableSummary() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, 1.2, 15.0, 12.0, Map.of("period", 14.0)),
                createResult(1, 1.4, 1.1, 14.0, 11.0, Map.of("period", 14.0))
            );
            
            var analysis = detector.analyzeResults(results);
            String summary = analysis.summarize();
            
            assertThat(summary).contains("Overfitting Analysis");
            assertThat(summary).contains("confidence");
        }
        
        @Test
        @DisplayName("should warn about insufficient windows")
        void shouldWarnAboutInsufficientWindows() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.5, 1.2, 15.0, 12.0, Map.of("period", 14.0))
            );
            
            var analysis = detector.analyzeResults(results);
            
            assertThat(analysis.warnings()).anyMatch(w -> w.toLowerCase().contains("insufficient windows"));
        }
    }
    
    @Nested
    @DisplayName("Confidence Level Calculation")
    class ConfidenceLevelTests {
        
        @Test
        @DisplayName("should return high confidence for clear overfitting")
        void shouldReturnHighConfidenceForClearOverfitting() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 3.0, -0.5, 30.0, -10.0, Map.of("period", 10.0)),
                createResult(1, 3.5, -0.3, 35.0, -8.0, Map.of("period", 20.0)),
                createResult(2, 2.8, -0.4, 28.0, -12.0, Map.of("period", 15.0))
            );
            
            var analysis = detector.analyzeResults(results);
            
            // When IS is positive but OOS is negative, confidence should be elevated
            assertThat(analysis.confidenceLevel()).isGreaterThan(0.4);
            assertThat(analysis.isOverfit()).isTrue();
        }
        
        @Test
        @DisplayName("should return low confidence for robust strategy")
        void shouldReturnLowConfidenceForRobustStrategy() {
            List<WalkForwardResult> results = List.of(
                createResult(0, 1.2, 1.1, 12.0, 11.0, Map.of("period", 14.0)),
                createResult(1, 1.3, 1.2, 13.0, 12.0, Map.of("period", 14.0)),
                createResult(2, 1.1, 1.0, 11.0, 10.0, Map.of("period", 14.0)),
                createResult(3, 1.2, 1.15, 12.0, 11.5, Map.of("period", 14.0))
            );
            
            var analysis = detector.analyzeResults(results);
            
            assertThat(analysis.confidenceLevel()).isLessThan(0.3);
        }
    }
}
