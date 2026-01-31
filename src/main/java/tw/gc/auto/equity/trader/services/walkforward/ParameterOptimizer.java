package tw.gc.auto.equity.trader.services.walkforward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Multi-objective parameter optimizer using grid search.
 * 
 * <p>Optimizes strategy parameters using a weighted combination of:
 * <ul>
 *   <li>Sharpe Ratio (risk-adjusted returns)</li>
 *   <li>Sortino Ratio (downside risk-adjusted returns)</li>
 *   <li>Calmar Ratio (return vs max drawdown)</li>
 * </ul>
 * 
 * <p>Best Practices:
 * <ul>
 *   <li>Limit parameters to 3-5 to avoid combinatorial explosion</li>
 *   <li>Use reasonable step sizes (finer = more accurate but slower)</li>
 *   <li>Enable parallel execution for faster optimization</li>
 * </ul>
 * 
 * <p>Future Enhancement: Bayesian optimization for more efficient parameter search.
 * 
 * @since 2026-01-30 Phase 1: Walk-Forward Optimization Framework
 */
@Slf4j
public class ParameterOptimizer {
    
    /** Default weight for Sharpe ratio in the fitness function (40%) */
    private static final double DEFAULT_SHARPE_WEIGHT = 0.40;
    
    /** Default weight for Sortino ratio in the fitness function (35%) */
    private static final double DEFAULT_SORTINO_WEIGHT = 0.35;
    
    /** Default weight for Calmar ratio in the fitness function (25%) */
    private static final double DEFAULT_CALMAR_WEIGHT = 0.25;
    
    /** Maximum number of parallel optimization tasks */
    private static final int MAX_PARALLEL_TASKS = Runtime.getRuntime().availableProcessors();
    
    /** Minimum number of trades required for valid optimization */
    private static final int MIN_TRADES_FOR_VALID_RESULT = 10;
    
    private final double sharpeWeight;
    private final double sortinoWeight;
    private final double calmarWeight;
    private final boolean parallelExecution;
    
    /**
     * Creates a ParameterOptimizer with default weights.
     */
    public ParameterOptimizer() {
        this(DEFAULT_SHARPE_WEIGHT, DEFAULT_SORTINO_WEIGHT, DEFAULT_CALMAR_WEIGHT, true);
    }
    
    /**
     * Creates a ParameterOptimizer with custom weights.
     * 
     * @param sharpeWeight Weight for Sharpe ratio (should sum to 1.0 with other weights)
     * @param sortinoWeight Weight for Sortino ratio
     * @param calmarWeight Weight for Calmar ratio
     * @param parallelExecution Whether to run optimizations in parallel
     */
    public ParameterOptimizer(double sharpeWeight, double sortinoWeight, double calmarWeight, boolean parallelExecution) {
        double total = sharpeWeight + sortinoWeight + calmarWeight;
        if (Math.abs(total - 1.0) > 0.001) {
            log.warn("Weights sum to {} instead of 1.0, normalizing...", total);
            this.sharpeWeight = sharpeWeight / total;
            this.sortinoWeight = sortinoWeight / total;
            this.calmarWeight = calmarWeight / total;
        } else {
            this.sharpeWeight = sharpeWeight;
            this.sortinoWeight = sortinoWeight;
            this.calmarWeight = calmarWeight;
        }
        this.parallelExecution = parallelExecution;
    }
    
    /**
     * Performs grid search optimization over the parameter space.
     * 
     * @param parameters List of parameter definitions to optimize
     * @param backtestFunction Function that runs a backtest with given parameters and returns metrics
     * @return Optimization result with best parameters and fitness
     */
    public OptimizationResult optimize(
            List<StrategyParameterDefinition> parameters,
            Function<Map<String, Double>, BacktestMetrics> backtestFunction) {
        
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("At least one parameter is required for optimization");
        }
        
        // Calculate total combinations
        int totalCombinations = 1;
        for (var param : parameters) {
            totalCombinations *= param.gridSize();
        }
        
        log.info("Starting grid search optimization over {} parameters ({} combinations)", 
            parameters.size(), totalCombinations);
        
        long startTime = System.currentTimeMillis();
        
        List<ParameterCombination> allCombinations = generateAllCombinations(parameters);
        
        OptimizationResult result;
        if (parallelExecution && totalCombinations > 10) {
            result = optimizeParallel(allCombinations, backtestFunction);
        } else {
            result = optimizeSequential(allCombinations, backtestFunction);
        }
        
        long durationMs = System.currentTimeMillis() - startTime;
        log.info("Optimization completed in {}ms. Best fitness: {:.4f}", durationMs, result.getBestFitness());
        
        return result;
    }
    
    /**
     * Generates all parameter combinations for grid search.
     */
    private List<ParameterCombination> generateAllCombinations(List<StrategyParameterDefinition> parameters) {
        List<ParameterCombination> combinations = new ArrayList<>();
        generateCombinationsRecursive(parameters, 0, new HashMap<>(), combinations);
        return combinations;
    }
    
    private void generateCombinationsRecursive(
            List<StrategyParameterDefinition> parameters,
            int paramIndex,
            Map<String, Double> current,
            List<ParameterCombination> results) {
        
        if (paramIndex >= parameters.size()) {
            results.add(new ParameterCombination(new HashMap<>(current)));
            return;
        }
        
        var param = parameters.get(paramIndex);
        for (double value : param.allValues()) {
            current.put(param.name(), value);
            generateCombinationsRecursive(parameters, paramIndex + 1, current, results);
        }
        current.remove(param.name());
    }
    
    /**
     * Sequential optimization (single-threaded).
     */
    private OptimizationResult optimizeSequential(
            List<ParameterCombination> combinations,
            Function<Map<String, Double>, BacktestMetrics> backtestFunction) {
        
        Map<String, Double> bestParameters = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        BacktestMetrics bestMetrics = null;
        int validCombinations = 0;
        
        for (int i = 0; i < combinations.size(); i++) {
            var combo = combinations.get(i);
            try {
                BacktestMetrics metrics = backtestFunction.apply(combo.getParameters());
                
                if (metrics != null && metrics.getTotalTrades() >= MIN_TRADES_FOR_VALID_RESULT) {
                    validCombinations++;
                    double fitness = calculateFitness(metrics);
                    
                    if (fitness > bestFitness) {
                        bestFitness = fitness;
                        bestParameters = new HashMap<>(combo.getParameters());
                        bestMetrics = metrics;
                    }
                }
                
                // Progress logging every 10%
                if ((i + 1) % Math.max(1, combinations.size() / 10) == 0) {
                    log.debug("Optimization progress: {}/{} combinations evaluated", i + 1, combinations.size());
                }
            } catch (Exception e) {
                log.trace("Failed to evaluate combination {}: {}", combo.getParameters(), e.getMessage());
            }
        }
        
        return OptimizationResult.builder()
            .bestParameters(bestParameters != null ? bestParameters : Map.of())
            .bestFitness(bestFitness)
            .bestMetrics(bestMetrics)
            .totalCombinations(combinations.size())
            .validCombinations(validCombinations)
            .build();
    }
    
    /**
     * Parallel optimization using virtual threads.
     */
    private OptimizationResult optimizeParallel(
            List<ParameterCombination> combinations,
            Function<Map<String, Double>, BacktestMetrics> backtestFunction) {
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<EvaluationResult>> futures = new ArrayList<>();
            
            for (var combo : combinations) {
                futures.add(executor.submit(() -> {
                    try {
                        BacktestMetrics metrics = backtestFunction.apply(combo.getParameters());
                        if (metrics != null && metrics.getTotalTrades() >= MIN_TRADES_FOR_VALID_RESULT) {
                            double fitness = calculateFitness(metrics);
                            return new EvaluationResult(combo.getParameters(), fitness, metrics);
                        }
                    } catch (Exception e) {
                        log.trace("Failed to evaluate combination: {}", e.getMessage());
                    }
                    return null;
                }));
            }
            
            Map<String, Double> bestParameters = null;
            double bestFitness = Double.NEGATIVE_INFINITY;
            BacktestMetrics bestMetrics = null;
            int validCombinations = 0;
            
            for (Future<EvaluationResult> future : futures) {
                try {
                    EvaluationResult result = future.get(60, TimeUnit.SECONDS);
                    if (result != null) {
                        validCombinations++;
                        if (result.fitness > bestFitness) {
                            bestFitness = result.fitness;
                            bestParameters = result.parameters;
                            bestMetrics = result.metrics;
                        }
                    }
                } catch (Exception e) {
                    log.trace("Future evaluation failed: {}", e.getMessage());
                }
            }
            
            return OptimizationResult.builder()
                .bestParameters(bestParameters != null ? bestParameters : Map.of())
                .bestFitness(bestFitness)
                .bestMetrics(bestMetrics)
                .totalCombinations(combinations.size())
                .validCombinations(validCombinations)
                .build();
        }
    }
    
    /**
     * Calculates the multi-objective fitness score.
     * 
     * <p>Formula: fitness = w1*Sharpe + w2*Sortino + w3*Calmar
     * 
     * <p>Each metric is normalized to prevent any single metric from dominating:
     * <ul>
     *   <li>Sharpe: typical range [-2, 3], capped at [-3, 5]</li>
     *   <li>Sortino: typical range [-2, 5], capped at [-3, 7]</li>
     *   <li>Calmar: typical range [0, 3], capped at [-1, 5]</li>
     * </ul>
     * 
     * @param metrics The backtest metrics
     * @return Combined fitness score
     */
    public double calculateFitness(BacktestMetrics metrics) {
        // Handle invalid or extreme values
        double sharpe = normalizeMetric(metrics.getSharpeRatio(), -3.0, 5.0);
        double sortino = normalizeMetric(metrics.getSortinoRatio(), -3.0, 7.0);
        double calmar = normalizeMetric(metrics.getCalmarRatio(), -1.0, 5.0);
        
        // Penalize strategies with very few trades (might be overfitted to specific conditions)
        double tradePenalty = 1.0;
        if (metrics.getTotalTrades() < 20) {
            tradePenalty = 0.8 + (metrics.getTotalTrades() / 100.0);
        }
        
        // Penalize high drawdowns
        double drawdownPenalty = 1.0;
        if (metrics.getMaxDrawdownPct() > 20.0) {
            drawdownPenalty = Math.max(0.5, 1.0 - (metrics.getMaxDrawdownPct() - 20.0) / 100.0);
        }
        
        double rawFitness = (sharpeWeight * sharpe) + (sortinoWeight * sortino) + (calmarWeight * calmar);
        return rawFitness * tradePenalty * drawdownPenalty;
    }
    
    /**
     * Normalizes a metric value to [0, 1] range using min-max normalization.
     */
    private double normalizeMetric(double value, double minExpected, double maxExpected) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        // Clamp to expected range
        double clamped = Math.max(minExpected, Math.min(maxExpected, value));
        // Normalize to [0, 1]
        return (clamped - minExpected) / (maxExpected - minExpected);
    }
    
    /**
     * Internal class to hold a parameter combination.
     */
    @Data
    private static class ParameterCombination {
        private final Map<String, Double> parameters;
    }
    
    /**
     * Internal class for parallel evaluation result.
     */
    private record EvaluationResult(
        Map<String, Double> parameters,
        double fitness,
        BacktestMetrics metrics
    ) {}
    
    /**
     * Result of an optimization run.
     */
    @Data
    @Builder
    public static class OptimizationResult {
        /** Optimal parameter values discovered */
        private final Map<String, Double> bestParameters;
        
        /** Best fitness score achieved */
        private final double bestFitness;
        
        /** Full metrics for the best parameter combination */
        private final BacktestMetrics bestMetrics;
        
        /** Total number of combinations evaluated */
        private final int totalCombinations;
        
        /** Number of valid (enough trades) combinations */
        private final int validCombinations;
        
        /**
         * Checks if optimization found valid parameters.
         */
        public boolean isValid() {
            return bestParameters != null && !bestParameters.isEmpty() && bestMetrics != null;
        }
    }
    
    /**
     * Backtest metrics required for optimization fitness calculation.
     * This interface allows decoupling from the specific BacktestService implementation.
     */
    public interface BacktestMetrics {
        double getSharpeRatio();
        double getSortinoRatio();
        double getCalmarRatio();
        double getTotalReturnPct();
        double getMaxDrawdownPct();
        double getWinRatePct();
        int getTotalTrades();
    }
    
    /**
     * Simple implementation of BacktestMetrics for use with the optimizer.
     */
    @Data
    @Builder
    public static class SimpleBacktestMetrics implements BacktestMetrics {
        private double sharpeRatio;
        private double sortinoRatio;
        private double calmarRatio;
        private double totalReturnPct;
        private double maxDrawdownPct;
        private double winRatePct;
        private int totalTrades;
    }
}
