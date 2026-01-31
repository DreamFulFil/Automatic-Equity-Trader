package tw.gc.auto.equity.trader.services.walkforward;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ParameterOptimizer}.
 */
class ParameterOptimizerTest {
    
    private ParameterOptimizer optimizer;
    
    @BeforeEach
    void setUp() {
        optimizer = new ParameterOptimizer();
    }
    
    @Nested
    @DisplayName("Construction")
    class ConstructionTests {
        
        @Test
        @DisplayName("should create optimizer with default weights")
        void shouldCreateWithDefaultWeights() {
            var opt = new ParameterOptimizer();
            // Just verify it was created successfully
            assertThat(opt).isNotNull();
        }
        
        @Test
        @DisplayName("should normalize weights that don't sum to 1.0")
        void shouldNormalizeWeights() {
            // Weights sum to 2.0, should be normalized
            var opt = new ParameterOptimizer(0.8, 0.7, 0.5, false);
            // Just verify it was created successfully (weights normalized internally)
            assertThat(opt).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Grid Search Optimization")
    class GridSearchTests {
        
        @Test
        @DisplayName("should optimize single parameter")
        void shouldOptimizeSingleParameter() {
            var params = List.of(
                StrategyParameterDefinition.ofInt("period", 10, 20, 5, 15)
            );
            
            // Backtest function that returns best metrics for period=15
            var result = optimizer.optimize(params, paramMap -> {
                double period = paramMap.get("period");
                double fitness = period == 15 ? 1.5 : 0.5; // Best at period=15
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(fitness)
                    .sortinoRatio(fitness * 1.2)
                    .calmarRatio(fitness * 0.8)
                    .totalReturnPct(fitness * 10)
                    .maxDrawdownPct(5.0)
                    .winRatePct(55.0)
                    .totalTrades(20)
                    .build();
            });
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getBestParameters()).containsEntry("period", 15.0);
        }
        
        @Test
        @DisplayName("should optimize multiple parameters")
        void shouldOptimizeMultipleParameters() {
            var params = List.of(
                StrategyParameterDefinition.ofInt("period", 10, 20, 5, 15),
                StrategyParameterDefinition.ofDouble("threshold", 20.0, 40.0, 10.0, 30.0)
            );
            
            // Best at period=15, threshold=30
            var result = optimizer.optimize(params, paramMap -> {
                double period = paramMap.get("period");
                double threshold = paramMap.get("threshold");
                double fitness = (period == 15 && threshold == 30.0) ? 2.0 : 0.5;
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(fitness)
                    .sortinoRatio(fitness)
                    .calmarRatio(fitness)
                    .totalReturnPct(10.0)
                    .maxDrawdownPct(5.0)
                    .winRatePct(50.0)
                    .totalTrades(25)
                    .build();
            });
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getBestParameters())
                .containsEntry("period", 15.0)
                .containsEntry("threshold", 30.0);
        }
        
        @Test
        @DisplayName("should evaluate all combinations")
        void shouldEvaluateAllCombinations() {
            var params = List.of(
                StrategyParameterDefinition.ofInt("param1", 1, 3, 1, 2), // 3 values
                StrategyParameterDefinition.ofInt("param2", 10, 20, 5, 15) // 3 values
            );
            
            AtomicInteger evaluationCount = new AtomicInteger(0);
            
            optimizer.optimize(params, paramMap -> {
                evaluationCount.incrementAndGet();
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(1.0)
                    .sortinoRatio(1.0)
                    .calmarRatio(1.0)
                    .totalReturnPct(10.0)
                    .maxDrawdownPct(5.0)
                    .winRatePct(50.0)
                    .totalTrades(20)
                    .build();
            });
            
            // Should evaluate 3 * 3 = 9 combinations
            assertThat(evaluationCount.get()).isEqualTo(9);
        }
        
        @Test
        @DisplayName("should reject empty parameters list")
        void shouldRejectEmptyParameters() {
            assertThatThrownBy(() -> optimizer.optimize(List.of(), p -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parameter");
        }
        
        @Test
        @DisplayName("should handle null metrics gracefully")
        void shouldHandleNullMetrics() {
            var params = List.of(
                StrategyParameterDefinition.ofInt("period", 10, 20, 5, 15)
            );
            
            var result = optimizer.optimize(params, paramMap -> null);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getValidCombinations()).isZero();
        }
        
        @Test
        @DisplayName("should filter out results with too few trades")
        void shouldFilterResultsWithFewTrades() {
            var params = List.of(
                StrategyParameterDefinition.ofInt("period", 10, 20, 5, 15)
            );
            
            var result = optimizer.optimize(params, paramMap -> {
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(2.0)
                    .sortinoRatio(2.0)
                    .calmarRatio(2.0)
                    .totalReturnPct(20.0)
                    .maxDrawdownPct(5.0)
                    .winRatePct(60.0)
                    .totalTrades(5) // Too few trades (< 10)
                    .build();
            });
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getValidCombinations()).isZero();
        }
        
        @Test
        @DisplayName("should handle exceptions in backtest function")
        void shouldHandleExceptionsInBacktestFunction() {
            var params = List.of(
                StrategyParameterDefinition.ofInt("period", 10, 20, 5, 15)
            );
            
            AtomicInteger callCount = new AtomicInteger(0);
            
            var result = optimizer.optimize(params, paramMap -> {
                int count = callCount.incrementAndGet();
                if (count % 2 == 0) {
                    throw new RuntimeException("Simulated error");
                }
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(1.0)
                    .sortinoRatio(1.0)
                    .calmarRatio(1.0)
                    .totalReturnPct(10.0)
                    .maxDrawdownPct(5.0)
                    .winRatePct(50.0)
                    .totalTrades(20)
                    .build();
            });
            
            // Some calls failed, but should still complete
            assertThat(result.getTotalCombinations()).isEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("Fitness Calculation")
    class FitnessCalculationTests {
        
        @Test
        @DisplayName("should calculate positive fitness for good metrics")
        void shouldCalculatePositiveFitnessForGoodMetrics() {
            var metrics = ParameterOptimizer.SimpleBacktestMetrics.builder()
                .sharpeRatio(1.5)
                .sortinoRatio(2.0)
                .calmarRatio(1.0)
                .totalReturnPct(15.0)
                .maxDrawdownPct(8.0)
                .winRatePct(55.0)
                .totalTrades(50)
                .build();
            
            double fitness = optimizer.calculateFitness(metrics);
            
            assertThat(fitness).isPositive();
        }
        
        @Test
        @DisplayName("should penalize high drawdowns")
        void shouldPenalizeHighDrawdowns() {
            var lowDrawdown = ParameterOptimizer.SimpleBacktestMetrics.builder()
                .sharpeRatio(1.5)
                .sortinoRatio(2.0)
                .calmarRatio(1.0)
                .totalReturnPct(15.0)
                .maxDrawdownPct(10.0) // Low drawdown
                .winRatePct(55.0)
                .totalTrades(50)
                .build();
            
            var highDrawdown = ParameterOptimizer.SimpleBacktestMetrics.builder()
                .sharpeRatio(1.5)
                .sortinoRatio(2.0)
                .calmarRatio(1.0)
                .totalReturnPct(15.0)
                .maxDrawdownPct(35.0) // High drawdown
                .winRatePct(55.0)
                .totalTrades(50)
                .build();
            
            double fitnessLow = optimizer.calculateFitness(lowDrawdown);
            double fitnessHigh = optimizer.calculateFitness(highDrawdown);
            
            assertThat(fitnessLow).isGreaterThan(fitnessHigh);
        }
        
        @Test
        @DisplayName("should penalize few trades")
        void shouldPenalizeFewTrades() {
            var manyTrades = ParameterOptimizer.SimpleBacktestMetrics.builder()
                .sharpeRatio(1.5)
                .sortinoRatio(2.0)
                .calmarRatio(1.0)
                .totalReturnPct(15.0)
                .maxDrawdownPct(10.0)
                .winRatePct(55.0)
                .totalTrades(100) // Many trades
                .build();
            
            var fewTrades = ParameterOptimizer.SimpleBacktestMetrics.builder()
                .sharpeRatio(1.5)
                .sortinoRatio(2.0)
                .calmarRatio(1.0)
                .totalReturnPct(15.0)
                .maxDrawdownPct(10.0)
                .winRatePct(55.0)
                .totalTrades(15) // Few trades
                .build();
            
            double fitnessMany = optimizer.calculateFitness(manyTrades);
            double fitnessFew = optimizer.calculateFitness(fewTrades);
            
            assertThat(fitnessMany).isGreaterThan(fitnessFew);
        }
        
        @Test
        @DisplayName("should handle infinite values gracefully")
        void shouldHandleInfiniteValues() {
            var metrics = ParameterOptimizer.SimpleBacktestMetrics.builder()
                .sharpeRatio(Double.POSITIVE_INFINITY)
                .sortinoRatio(Double.NaN)
                .calmarRatio(Double.NEGATIVE_INFINITY)
                .totalReturnPct(15.0)
                .maxDrawdownPct(10.0)
                .winRatePct(55.0)
                .totalTrades(50)
                .build();
            
            double fitness = optimizer.calculateFitness(metrics);
            
            assertThat(Double.isFinite(fitness)).isTrue();
        }
    }
    
    @Nested
    @DisplayName("Parallel Execution")
    class ParallelExecutionTests {
        
        @Test
        @DisplayName("should produce same results in parallel mode")
        void shouldProduceSameResultsInParallel() {
            var sequentialOptimizer = new ParameterOptimizer(0.4, 0.35, 0.25, false);
            var parallelOptimizer = new ParameterOptimizer(0.4, 0.35, 0.25, true);
            
            var params = List.of(
                StrategyParameterDefinition.ofInt("period", 10, 30, 5, 20)
            );
            
            // Deterministic backtest function
            var seqResult = sequentialOptimizer.optimize(params, paramMap -> {
                double period = paramMap.get("period");
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(period / 10.0)
                    .sortinoRatio(period / 8.0)
                    .calmarRatio(period / 15.0)
                    .totalReturnPct(period)
                    .maxDrawdownPct(10.0)
                    .winRatePct(50.0)
                    .totalTrades(25)
                    .build();
            });
            
            var parResult = parallelOptimizer.optimize(params, paramMap -> {
                double period = paramMap.get("period");
                return ParameterOptimizer.SimpleBacktestMetrics.builder()
                    .sharpeRatio(period / 10.0)
                    .sortinoRatio(period / 8.0)
                    .calmarRatio(period / 15.0)
                    .totalReturnPct(period)
                    .maxDrawdownPct(10.0)
                    .winRatePct(50.0)
                    .totalTrades(25)
                    .build();
            });
            
            assertThat(seqResult.getBestParameters()).isEqualTo(parResult.getBestParameters());
        }
    }
}
