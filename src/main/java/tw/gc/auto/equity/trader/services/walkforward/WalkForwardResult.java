package tw.gc.auto.equity.trader.services.walkforward;

import java.util.List;
import java.util.Map;

/**
 * Contains the results from a single walk-forward optimization window.
 * Captures both in-sample (training) and out-of-sample (test) performance metrics.
 * 
 * <p>Key metrics tracked:
 * <ul>
 *   <li>In-sample performance (optimized on training data)</li>
 *   <li>Out-of-sample performance (verified on unseen data)</li>
 *   <li>IS/OOS ratio to detect overfitting</li>
 *   <li>Optimal parameters discovered</li>
 * </ul>
 * 
 * @param window The walk-forward window this result belongs to
 * @param strategyName Name of the strategy being optimized
 * @param optimalParameters Map of parameter names to their optimal values
 * @param inSampleSharpe Sharpe ratio on training data
 * @param inSampleSortino Sortino ratio on training data
 * @param inSampleCalmar Calmar ratio on training data
 * @param inSampleReturn Total return percentage on training data
 * @param inSampleMaxDrawdown Maximum drawdown percentage on training data
 * @param inSampleWinRate Win rate percentage on training data
 * @param inSampleTrades Number of trades executed in training period
 * @param outOfSampleSharpe Sharpe ratio on test data
 * @param outOfSampleSortino Sortino ratio on test data
 * @param outOfSampleCalmar Calmar ratio on test data
 * @param outOfSampleReturn Total return percentage on test data
 * @param outOfSampleMaxDrawdown Maximum drawdown percentage on test data
 * @param outOfSampleWinRate Win rate percentage on test data
 * @param outOfSampleTrades Number of trades executed in test period
 * @param combinedFitness Multi-objective fitness score used for optimization
 * @param parameterCombinationsTested Number of parameter combinations tested
 * @param optimizationDurationMs Time spent on optimization in milliseconds
 * 
 * @since 2026-01-30 Phase 1: Walk-Forward Optimization Framework
 */
public record WalkForwardResult(
    WalkForwardWindow window,
    String strategyName,
    Map<String, Double> optimalParameters,
    // In-sample (training) metrics
    double inSampleSharpe,
    double inSampleSortino,
    double inSampleCalmar,
    double inSampleReturn,
    double inSampleMaxDrawdown,
    double inSampleWinRate,
    int inSampleTrades,
    // Out-of-sample (test) metrics
    double outOfSampleSharpe,
    double outOfSampleSortino,
    double outOfSampleCalmar,
    double outOfSampleReturn,
    double outOfSampleMaxDrawdown,
    double outOfSampleWinRate,
    int outOfSampleTrades,
    // Optimization metadata
    double combinedFitness,
    int parameterCombinationsTested,
    long optimizationDurationMs
) {
    /**
     * Compact constructor with null checks.
     */
    public WalkForwardResult {
        if (window == null) {
            throw new IllegalArgumentException("window cannot be null");
        }
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("strategyName cannot be null or blank");
        }
        if (optimalParameters == null) {
            throw new IllegalArgumentException("optimalParameters cannot be null");
        }
        // Defensively copy to ensure immutability
        optimalParameters = Map.copyOf(optimalParameters);
    }
    
    /**
     * Calculates the in-sample to out-of-sample Sharpe ratio.
     * A ratio > 2.0 indicates potential overfitting.
     * 
     * @return IS/OOS Sharpe ratio, or Double.POSITIVE_INFINITY if OOS is 0 or negative
     */
    public double sharpeIsOosRatio() {
        if (outOfSampleSharpe <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return inSampleSharpe / outOfSampleSharpe;
    }
    
    /**
     * Calculates the in-sample to out-of-sample return ratio.
     * A ratio > 2.0 indicates potential overfitting.
     * 
     * @return IS/OOS return ratio, or Double.POSITIVE_INFINITY if OOS is 0 or negative
     */
    public double returnIsOosRatio() {
        if (outOfSampleReturn <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return inSampleReturn / outOfSampleReturn;
    }
    
    /**
     * Determines if this result shows signs of overfitting.
     * Uses multiple heuristics:
     * <ul>
     *   <li>IS/OOS Sharpe ratio > 2.0</li>
     *   <li>OOS Sharpe < 0</li>
     *   <li>OOS return significantly negative</li>
     * </ul>
     * 
     * @return true if overfitting is detected
     */
    public boolean isOverfit() {
        // Severe overfitting: OOS is negative while IS is positive
        if (inSampleSharpe > 0 && outOfSampleSharpe < 0) {
            return true;
        }
        // High degradation in out-of-sample
        if (sharpeIsOosRatio() > 2.0) {
            return true;
        }
        // OOS return significantly negative while IS positive
        if (inSampleReturn > 0 && outOfSampleReturn < -5.0) {
            return true;
        }
        return false;
    }
    
    /**
     * Calculates a robustness score (0-100) based on how well the
     * out-of-sample results hold up compared to in-sample.
     * 
     * <p>Scoring:
     * <ul>
     *   <li>100 = OOS equals or exceeds IS</li>
     *   <li>80+ = Excellent (OOS degradation < 20%)</li>
     *   <li>60-80 = Good (OOS degradation 20-40%)</li>
     *   <li>40-60 = Fair (OOS degradation 40-60%)</li>
     *   <li>< 40 = Poor (severe overfitting)</li>
     * </ul>
     * 
     * @return Robustness score 0-100
     */
    public double robustnessScore() {
        if (inSampleSharpe <= 0) {
            // Can't compare if IS is non-positive
            return outOfSampleSharpe > 0 ? 50.0 : 0.0;
        }
        
        double ratio = outOfSampleSharpe / inSampleSharpe;
        // Cap at 100, floor at 0
        return Math.max(0, Math.min(100, ratio * 100));
    }
    
    /**
     * Returns a summary string for logging/reporting.
     * 
     * @return Human-readable summary of the result
     */
    public String summarize() {
        return """
            WalkForward Result [%s] - Window %d
            ═══════════════════════════════════════════════════════
            Parameters: %s
            
            In-Sample (Training):
              • Sharpe: %.3f | Sortino: %.3f | Calmar: %.3f
              • Return: %.2f%% | MaxDD: %.2f%% | WinRate: %.1f%% | Trades: %d
            
            Out-of-Sample (Test):
              • Sharpe: %.3f | Sortino: %.3f | Calmar: %.3f
              • Return: %.2f%% | MaxDD: %.2f%% | WinRate: %.1f%% | Trades: %d
            
            Analysis:
              • IS/OOS Sharpe Ratio: %.2f (>2.0 suggests overfit)
              • Robustness Score: %.1f/100
              • Overfit Detected: %s
              • Combinations Tested: %d in %dms
            ═══════════════════════════════════════════════════════
            """.formatted(
                strategyName, window.windowIndex(),
                optimalParameters,
                inSampleSharpe, inSampleSortino, inSampleCalmar,
                inSampleReturn, inSampleMaxDrawdown, inSampleWinRate, inSampleTrades,
                outOfSampleSharpe, outOfSampleSortino, outOfSampleCalmar,
                outOfSampleReturn, outOfSampleMaxDrawdown, outOfSampleWinRate, outOfSampleTrades,
                sharpeIsOosRatio(),
                robustnessScore(),
                isOverfit() ? "⚠️ YES" : "✅ NO",
                parameterCombinationsTested, optimizationDurationMs
            );
    }
    
    /**
     * Builder for creating WalkForwardResult instances.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Mutable builder for WalkForwardResult.
     */
    public static class Builder {
        private WalkForwardWindow window;
        private String strategyName;
        private Map<String, Double> optimalParameters = Map.of();
        private double inSampleSharpe;
        private double inSampleSortino;
        private double inSampleCalmar;
        private double inSampleReturn;
        private double inSampleMaxDrawdown;
        private double inSampleWinRate;
        private int inSampleTrades;
        private double outOfSampleSharpe;
        private double outOfSampleSortino;
        private double outOfSampleCalmar;
        private double outOfSampleReturn;
        private double outOfSampleMaxDrawdown;
        private double outOfSampleWinRate;
        private int outOfSampleTrades;
        private double combinedFitness;
        private int parameterCombinationsTested;
        private long optimizationDurationMs;
        
        public Builder window(WalkForwardWindow window) {
            this.window = window;
            return this;
        }
        
        public Builder strategyName(String strategyName) {
            this.strategyName = strategyName;
            return this;
        }
        
        public Builder optimalParameters(Map<String, Double> optimalParameters) {
            this.optimalParameters = optimalParameters;
            return this;
        }
        
        public Builder inSampleSharpe(double inSampleSharpe) {
            this.inSampleSharpe = inSampleSharpe;
            return this;
        }
        
        public Builder inSampleSortino(double inSampleSortino) {
            this.inSampleSortino = inSampleSortino;
            return this;
        }
        
        public Builder inSampleCalmar(double inSampleCalmar) {
            this.inSampleCalmar = inSampleCalmar;
            return this;
        }
        
        public Builder inSampleReturn(double inSampleReturn) {
            this.inSampleReturn = inSampleReturn;
            return this;
        }
        
        public Builder inSampleMaxDrawdown(double inSampleMaxDrawdown) {
            this.inSampleMaxDrawdown = inSampleMaxDrawdown;
            return this;
        }
        
        public Builder inSampleWinRate(double inSampleWinRate) {
            this.inSampleWinRate = inSampleWinRate;
            return this;
        }
        
        public Builder inSampleTrades(int inSampleTrades) {
            this.inSampleTrades = inSampleTrades;
            return this;
        }
        
        public Builder outOfSampleSharpe(double outOfSampleSharpe) {
            this.outOfSampleSharpe = outOfSampleSharpe;
            return this;
        }
        
        public Builder outOfSampleSortino(double outOfSampleSortino) {
            this.outOfSampleSortino = outOfSampleSortino;
            return this;
        }
        
        public Builder outOfSampleCalmar(double outOfSampleCalmar) {
            this.outOfSampleCalmar = outOfSampleCalmar;
            return this;
        }
        
        public Builder outOfSampleReturn(double outOfSampleReturn) {
            this.outOfSampleReturn = outOfSampleReturn;
            return this;
        }
        
        public Builder outOfSampleMaxDrawdown(double outOfSampleMaxDrawdown) {
            this.outOfSampleMaxDrawdown = outOfSampleMaxDrawdown;
            return this;
        }
        
        public Builder outOfSampleWinRate(double outOfSampleWinRate) {
            this.outOfSampleWinRate = outOfSampleWinRate;
            return this;
        }
        
        public Builder outOfSampleTrades(int outOfSampleTrades) {
            this.outOfSampleTrades = outOfSampleTrades;
            return this;
        }
        
        public Builder combinedFitness(double combinedFitness) {
            this.combinedFitness = combinedFitness;
            return this;
        }
        
        public Builder parameterCombinationsTested(int parameterCombinationsTested) {
            this.parameterCombinationsTested = parameterCombinationsTested;
            return this;
        }
        
        public Builder optimizationDurationMs(long optimizationDurationMs) {
            this.optimizationDurationMs = optimizationDurationMs;
            return this;
        }
        
        public WalkForwardResult build() {
            return new WalkForwardResult(
                window, strategyName, optimalParameters,
                inSampleSharpe, inSampleSortino, inSampleCalmar,
                inSampleReturn, inSampleMaxDrawdown, inSampleWinRate, inSampleTrades,
                outOfSampleSharpe, outOfSampleSortino, outOfSampleCalmar,
                outOfSampleReturn, outOfSampleMaxDrawdown, outOfSampleWinRate, outOfSampleTrades,
                combinedFitness, parameterCombinationsTested, optimizationDurationMs
            );
        }
    }
}
