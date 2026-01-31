package tw.gc.auto.equity.trader.services.walkforward;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WalkForwardResult}.
 */
class WalkForwardResultTest {
    
    private WalkForwardWindow createTestWindow() {
        return new WalkForwardWindow(0,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 2, 28),
            LocalDate.of(2025, 3, 1),
            LocalDate.of(2025, 3, 31));
    }
    
    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {
        
        @Test
        @DisplayName("should create valid result with builder")
        void shouldCreateValidResult() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("TestStrategy")
                .optimalParameters(Map.of("period", 14.0, "threshold", 30.0))
                .inSampleSharpe(1.5)
                .inSampleSortino(2.0)
                .inSampleCalmar(1.2)
                .inSampleReturn(15.0)
                .inSampleMaxDrawdown(8.0)
                .inSampleWinRate(55.0)
                .inSampleTrades(50)
                .outOfSampleSharpe(1.2)
                .outOfSampleSortino(1.5)
                .outOfSampleCalmar(1.0)
                .outOfSampleReturn(10.0)
                .outOfSampleMaxDrawdown(6.0)
                .outOfSampleWinRate(52.0)
                .outOfSampleTrades(20)
                .combinedFitness(0.85)
                .parameterCombinationsTested(100)
                .optimizationDurationMs(5000)
                .build();
            
            assertThat(result.strategyName()).isEqualTo("TestStrategy");
            assertThat(result.inSampleSharpe()).isEqualTo(1.5);
            assertThat(result.outOfSampleSharpe()).isEqualTo(1.2);
            assertThat(result.optimalParameters()).containsEntry("period", 14.0);
        }
        
        @Test
        @DisplayName("should reject null window")
        void shouldRejectNullWindow() {
            assertThatThrownBy(() -> WalkForwardResult.builder()
                    .window(null)
                    .strategyName("Test")
                    .optimalParameters(Map.of())
                    .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
        }
        
        @Test
        @DisplayName("should reject null or blank strategy name")
        void shouldRejectNullOrBlankStrategyName() {
            assertThatThrownBy(() -> WalkForwardResult.builder()
                    .window(createTestWindow())
                    .strategyName(null)
                    .optimalParameters(Map.of())
                    .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategyName");
                
            assertThatThrownBy(() -> WalkForwardResult.builder()
                    .window(createTestWindow())
                    .strategyName("  ")
                    .optimalParameters(Map.of())
                    .build())
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("should reject null parameters map")
        void shouldRejectNullParameters() {
            assertThatThrownBy(() -> WalkForwardResult.builder()
                    .window(createTestWindow())
                    .strategyName("Test")
                    .optimalParameters(null)
                    .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("optimalParameters");
        }
        
        @Test
        @DisplayName("should make defensive copy of parameters")
        void shouldMakeDefensiveCopy() {
            var mutableParams = new java.util.HashMap<String, Double>();
            mutableParams.put("period", 14.0);
            
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(mutableParams)
                .build();
            
            // Modify original map
            mutableParams.put("newParam", 99.0);
            
            // Result should be unaffected
            assertThat(result.optimalParameters()).doesNotContainKey("newParam");
        }
    }
    
    @Nested
    @DisplayName("IS/OOS Ratio Calculations")
    class IsOosRatioTests {
        
        @Test
        @DisplayName("should calculate Sharpe IS/OOS ratio correctly")
        void shouldCalculateSharpeIsOosRatio() {
            var result = createResult(1.5, 1.0); // IS=1.5, OOS=1.0
            
            assertThat(result.sharpeIsOosRatio()).isCloseTo(1.5, within(0.001));
        }
        
        @Test
        @DisplayName("should return infinity for zero OOS Sharpe")
        void shouldReturnInfinityForZeroOosSharpe() {
            var result = createResult(1.5, 0.0);
            
            assertThat(result.sharpeIsOosRatio()).isInfinite();
        }
        
        @Test
        @DisplayName("should return infinity for negative OOS Sharpe")
        void shouldReturnInfinityForNegativeOosSharpe() {
            var result = createResult(1.5, -0.5);
            
            assertThat(result.sharpeIsOosRatio()).isInfinite();
        }
        
        @Test
        @DisplayName("should calculate return IS/OOS ratio correctly")
        void shouldCalculateReturnIsOosRatio() {
            var result = createResultWithReturns(20.0, 10.0); // IS=20%, OOS=10%
            
            assertThat(result.returnIsOosRatio()).isCloseTo(2.0, within(0.001));
        }
        
        private WalkForwardResult createResult(double isSharpe, double oosSharpe) {
            return WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(isSharpe)
                .outOfSampleSharpe(oosSharpe)
                .build();
        }
        
        private WalkForwardResult createResultWithReturns(double isReturn, double oosReturn) {
            return WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleReturn(isReturn)
                .outOfSampleReturn(oosReturn)
                .build();
        }
    }
    
    @Nested
    @DisplayName("Overfitting Detection")
    class OverfittingDetectionTests {
        
        @Test
        @DisplayName("should detect overfitting when OOS Sharpe is negative and IS positive")
        void shouldDetectOverfitWhenOosNegativeIsPositive() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.5)      // Positive IS
                .outOfSampleSharpe(-0.5)  // Negative OOS
                .build();
            
            assertThat(result.isOverfit()).isTrue();
        }
        
        @Test
        @DisplayName("should detect overfitting when IS/OOS ratio exceeds 2.0")
        void shouldDetectOverfitWhenRatioExceeds2() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(3.0)      // High IS
                .outOfSampleSharpe(1.0)   // Low OOS (ratio = 3.0)
                .build();
            
            assertThat(result.isOverfit()).isTrue();
        }
        
        @Test
        @DisplayName("should detect overfitting when IS return positive but OOS severely negative")
        void shouldDetectOverfitWhenOosReturnSeverelyNegative() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.5)
                .outOfSampleSharpe(1.0)
                .inSampleReturn(15.0)     // Positive IS return
                .outOfSampleReturn(-10.0) // Severely negative OOS return
                .build();
            
            assertThat(result.isOverfit()).isTrue();
        }
        
        @Test
        @DisplayName("should not detect overfitting for robust results")
        void shouldNotDetectOverfitForRobustResults() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.5)
                .outOfSampleSharpe(1.2)   // Good OOS (ratio = 1.25)
                .inSampleReturn(15.0)
                .outOfSampleReturn(10.0)  // Positive OOS return
                .build();
            
            assertThat(result.isOverfit()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("Robustness Score")
    class RobustnessScoreTests {
        
        @Test
        @DisplayName("should return 100 when OOS equals IS")
        void shouldReturn100WhenOosEqualsIs() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.5)
                .outOfSampleSharpe(1.5) // Equal
                .build();
            
            assertThat(result.robustnessScore()).isCloseTo(100.0, within(0.1));
        }
        
        @Test
        @DisplayName("should cap at 100 when OOS exceeds IS")
        void shouldCapAt100WhenOosExceedsIs() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.0)
                .outOfSampleSharpe(1.5) // OOS > IS
                .build();
            
            assertThat(result.robustnessScore()).isEqualTo(100.0);
        }
        
        @Test
        @DisplayName("should return 80 when OOS is 80% of IS")
        void shouldReturn80When80PercentDegradation() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.0)
                .outOfSampleSharpe(0.8) // 80% of IS
                .build();
            
            assertThat(result.robustnessScore()).isCloseTo(80.0, within(0.1));
        }
        
        @Test
        @DisplayName("should return 0 for negative OOS when IS positive")
        void shouldReturn0ForNegativeOos() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("Test")
                .optimalParameters(Map.of())
                .inSampleSharpe(1.0)
                .outOfSampleSharpe(-0.5) // Negative OOS
                .build();
            
            assertThat(result.robustnessScore()).isLessThanOrEqualTo(0.0);
        }
    }
    
    @Nested
    @DisplayName("Summary Output")
    class SummaryTests {
        
        @Test
        @DisplayName("should generate comprehensive summary")
        void shouldGenerateComprehensiveSummary() {
            var result = WalkForwardResult.builder()
                .window(createTestWindow())
                .strategyName("RSI Strategy")
                .optimalParameters(Map.of("period", 14.0))
                .inSampleSharpe(1.5)
                .inSampleSortino(2.0)
                .inSampleCalmar(1.2)
                .inSampleReturn(15.0)
                .inSampleMaxDrawdown(8.0)
                .inSampleWinRate(55.0)
                .inSampleTrades(50)
                .outOfSampleSharpe(1.2)
                .outOfSampleSortino(1.5)
                .outOfSampleCalmar(1.0)
                .outOfSampleReturn(10.0)
                .outOfSampleMaxDrawdown(6.0)
                .outOfSampleWinRate(52.0)
                .outOfSampleTrades(20)
                .combinedFitness(0.85)
                .parameterCombinationsTested(100)
                .optimizationDurationMs(5000)
                .build();
            
            String summary = result.summarize();
            
            assertThat(summary).contains("RSI Strategy");
            assertThat(summary).contains("Window 0");
            assertThat(summary).contains("In-Sample");
            assertThat(summary).contains("Out-of-Sample");
            assertThat(summary).contains("Sharpe");
            assertThat(summary).contains("Sortino");
            assertThat(summary).contains("Calmar");
            assertThat(summary).contains("IS/OOS Sharpe Ratio");
            assertThat(summary).contains("Robustness Score");
            assertThat(summary).contains("period");
        }
    }
}
