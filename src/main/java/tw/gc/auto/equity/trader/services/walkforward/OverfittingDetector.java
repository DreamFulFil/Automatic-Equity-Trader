package tw.gc.auto.equity.trader.services.walkforward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Detects overfitting in walk-forward optimization results.
 * 
 * <p>Overfitting Indicators:
 * <ul>
 *   <li>High IS/OOS Sharpe ratio (> 2.0 is concerning)</li>
 *   <li>Negative OOS returns when IS returns are positive</li>
 *   <li>High parameter drift across windows</li>
 *   <li>Inconsistent performance across windows</li>
 * </ul>
 * 
 * <p>This component helps reject strategies that are "curve-fitted" to historical
 * data and unlikely to perform well in live trading.
 * 
 * @since 2026-01-30 Phase 1: Walk-Forward Optimization Framework
 */
@Component
@Slf4j
public class OverfittingDetector {
    
    /** IS/OOS Sharpe ratio threshold above which overfitting is suspected */
    public static final double ISOOS_RATIO_WARNING_THRESHOLD = 2.0;
    
    /** IS/OOS Sharpe ratio threshold above which overfitting is highly likely */
    public static final double ISOOS_RATIO_CRITICAL_THRESHOLD = 3.0;
    
    /** Minimum robustness score required (0-100) */
    public static final double MIN_ROBUSTNESS_SCORE = 50.0;
    
    /** Maximum parameter coefficient of variation allowed (std/mean) */
    public static final double MAX_PARAMETER_CV = 0.5;
    
    /** Minimum number of windows required for reliable analysis */
    public static final int MIN_WINDOWS_FOR_ANALYSIS = 3;
    
    /**
     * Analyzes walk-forward results for signs of overfitting.
     * 
     * @param results List of walk-forward window results
     * @return Analysis with overfitting assessment and warnings
     */
    public OverfittingAnalysis analyzeResults(List<WalkForwardResult> results) {
        if (results == null || results.isEmpty()) {
            return new OverfittingAnalysis(
                true, 0.0, List.of("No results to analyze"), Map.of());
        }
        
        List<String> warnings = new ArrayList<>();
        Map<String, Object> diagnostics = new HashMap<>();
        
        // 1. Check IS/OOS ratio across windows
        double avgIsOosRatio = analyzeIsOosRatio(results, warnings, diagnostics);
        
        // 2. Check for negative OOS performance
        int negativeOosWindows = analyzeOosPerformance(results, warnings, diagnostics);
        
        // 3. Check parameter stability
        double parameterStability = analyzeParameterStability(results, warnings, diagnostics);
        
        // 4. Check performance consistency
        double performanceConsistency = analyzePerformanceConsistency(results, warnings, diagnostics);
        
        // 5. Calculate overall overfit confidence
        double overfitConfidence = calculateOverfitConfidence(
            avgIsOosRatio, negativeOosWindows, parameterStability, performanceConsistency, results.size());
        
        boolean isOverfit = overfitConfidence > 0.5;
        
        if (results.size() < MIN_WINDOWS_FOR_ANALYSIS) {
            warnings.add("Insufficient windows (%d) for reliable overfitting detection (min: %d)"
                .formatted(results.size(), MIN_WINDOWS_FOR_ANALYSIS));
        }
        
        log.info("Overfitting Analysis: confidence={:.2f}, overfit={}, warnings={}", 
            overfitConfidence, isOverfit, warnings.size());
        
        return new OverfittingAnalysis(isOverfit, overfitConfidence, warnings, diagnostics);
    }
    
    /**
     * Analyzes the in-sample to out-of-sample Sharpe ratio.
     */
    private double analyzeIsOosRatio(List<WalkForwardResult> results, 
            List<String> warnings, Map<String, Object> diagnostics) {
        
        List<Double> ratios = results.stream()
            .map(WalkForwardResult::sharpeIsOosRatio)
            .filter(Double::isFinite)
            .toList();
        
        if (ratios.isEmpty()) {
            warnings.add("Could not calculate IS/OOS ratios (invalid metrics)");
            return Double.NaN;
        }
        
        double avgRatio = ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxRatio = ratios.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        int criticalCount = (int) ratios.stream().filter(r -> r > ISOOS_RATIO_CRITICAL_THRESHOLD).count();
        int warningCount = (int) ratios.stream().filter(r -> r > ISOOS_RATIO_WARNING_THRESHOLD).count();
        
        diagnostics.put("avgIsOosRatio", avgRatio);
        diagnostics.put("maxIsOosRatio", maxRatio);
        diagnostics.put("criticalRatioWindows", criticalCount);
        diagnostics.put("warningRatioWindows", warningCount);
        
        if (avgRatio > ISOOS_RATIO_CRITICAL_THRESHOLD) {
            warnings.add("Critical: Average IS/OOS ratio (%.2f) exceeds %.1f - severe overfitting likely"
                .formatted(avgRatio, ISOOS_RATIO_CRITICAL_THRESHOLD));
        } else if (avgRatio > ISOOS_RATIO_WARNING_THRESHOLD) {
            warnings.add("Warning: Average IS/OOS ratio (%.2f) exceeds %.1f - moderate overfitting suspected"
                .formatted(avgRatio, ISOOS_RATIO_WARNING_THRESHOLD));
        }
        
        if (criticalCount > results.size() / 2) {
            warnings.add("Critical: %d/%d windows have IS/OOS ratio > %.1f"
                .formatted(criticalCount, results.size(), ISOOS_RATIO_CRITICAL_THRESHOLD));
        }
        
        return avgRatio;
    }
    
    /**
     * Analyzes out-of-sample performance for negative returns.
     */
    private int analyzeOosPerformance(List<WalkForwardResult> results, 
            List<String> warnings, Map<String, Object> diagnostics) {
        
        int negativeOosCount = 0;
        int positiveIsNegativeOosCount = 0;
        
        for (WalkForwardResult result : results) {
            if (result.outOfSampleSharpe() < 0) {
                negativeOosCount++;
                if (result.inSampleSharpe() > 0) {
                    positiveIsNegativeOosCount++;
                }
            }
        }
        
        diagnostics.put("negativeOosWindows", negativeOosCount);
        diagnostics.put("positiveIsNegativeOosWindows", positiveIsNegativeOosCount);
        
        double negativeRatio = (double) negativeOosCount / results.size();
        
        if (negativeRatio > 0.5) {
            warnings.add("Critical: %.0f%% of windows have negative OOS Sharpe"
                .formatted(negativeRatio * 100));
        } else if (negativeRatio > 0.3) {
            warnings.add("Warning: %.0f%% of windows have negative OOS Sharpe"
                .formatted(negativeRatio * 100));
        }
        
        if (positiveIsNegativeOosCount > results.size() / 3) {
            warnings.add("Critical: %d/%d windows show positive IS but negative OOS - classic overfit pattern"
                .formatted(positiveIsNegativeOosCount, results.size()));
        }
        
        return negativeOosCount;
    }
    
    /**
     * Analyzes parameter stability across windows.
     * High drift suggests overfitting to specific market conditions.
     */
    private double analyzeParameterStability(List<WalkForwardResult> results, 
            List<String> warnings, Map<String, Object> diagnostics) {
        
        if (results.size() < 2) {
            return 1.0; // Can't assess stability with one window
        }
        
        // Collect all parameter names
        Map<String, List<Double>> parameterValues = new HashMap<>();
        for (WalkForwardResult result : results) {
            for (var entry : result.optimalParameters().entrySet()) {
                parameterValues.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(entry.getValue());
            }
        }
        
        // Calculate coefficient of variation for each parameter
        Map<String, Double> parameterCVs = new HashMap<>();
        List<Double> allCVs = new ArrayList<>();
        
        for (var entry : parameterValues.entrySet()) {
            String paramName = entry.getKey();
            List<Double> values = entry.getValue();
            
            if (values.size() < 2) continue;
            
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            
            double cv = mean != 0 ? stdDev / Math.abs(mean) : 0;
            parameterCVs.put(paramName, cv);
            allCVs.add(cv);
            
            if (cv > MAX_PARAMETER_CV) {
                warnings.add("Warning: Parameter '%s' has high drift (CV=%.2f > %.2f)"
                    .formatted(paramName, cv, MAX_PARAMETER_CV));
            }
        }
        
        diagnostics.put("parameterCoefficientsOfVariation", parameterCVs);
        
        // Calculate overall stability score (1 = stable, 0 = unstable)
        if (allCVs.isEmpty()) {
            return 1.0;
        }
        
        double avgCV = allCVs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double stabilityScore = Math.max(0, 1.0 - (avgCV / MAX_PARAMETER_CV));
        
        diagnostics.put("parameterStabilityScore", stabilityScore);
        
        return stabilityScore;
    }
    
    /**
     * Analyzes performance consistency across windows.
     * Inconsistent results suggest the strategy may not generalize.
     */
    private double analyzePerformanceConsistency(List<WalkForwardResult> results, 
            List<String> warnings, Map<String, Object> diagnostics) {
        
        if (results.size() < 2) {
            return 1.0;
        }
        
        // Calculate robustness score statistics
        List<Double> robustnessScores = results.stream()
            .map(WalkForwardResult::robustnessScore)
            .toList();
        
        double avgRobustness = robustnessScores.stream()
            .mapToDouble(Double::doubleValue)
            .average().orElse(0);
        double minRobustness = robustnessScores.stream()
            .mapToDouble(Double::doubleValue)
            .min().orElse(0);
        double variance = robustnessScores.stream()
            .mapToDouble(r -> Math.pow(r - avgRobustness, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        diagnostics.put("avgRobustnessScore", avgRobustness);
        diagnostics.put("minRobustnessScore", minRobustness);
        diagnostics.put("robustnessStdDev", stdDev);
        
        if (avgRobustness < MIN_ROBUSTNESS_SCORE) {
            warnings.add("Warning: Average robustness (%.1f%%) below threshold (%.1f%%)"
                .formatted(avgRobustness, MIN_ROBUSTNESS_SCORE));
        }
        
        if (minRobustness < 20) {
            warnings.add("Warning: At least one window has very low robustness (%.1f%%)"
                .formatted(minRobustness));
        }
        
        if (stdDev > 25) {
            warnings.add("Warning: High variance in robustness scores (std=%.1f) indicates inconsistency"
                .formatted(stdDev));
        }
        
        // Consistency score based on low variance and high average
        double consistencyScore = (avgRobustness / 100.0) * Math.max(0, 1.0 - (stdDev / 50.0));
        diagnostics.put("consistencyScore", consistencyScore);
        
        return consistencyScore;
    }
    
    /**
     * Calculates overall confidence that the strategy is overfit.
     * 
     * @return Confidence score [0, 1] where 1 = definitely overfit
     */
    private double calculateOverfitConfidence(
            double avgIsOosRatio,
            int negativeOosWindows,
            double parameterStability,
            double performanceConsistency,
            int totalWindows) {
        
        double confidence = 0.0;
        
        // IS/OOS ratio contribution (40% weight)
        if (Double.isFinite(avgIsOosRatio)) {
            if (avgIsOosRatio > ISOOS_RATIO_CRITICAL_THRESHOLD) {
                confidence += 0.4;
            } else if (avgIsOosRatio > ISOOS_RATIO_WARNING_THRESHOLD) {
                confidence += 0.4 * (avgIsOosRatio - ISOOS_RATIO_WARNING_THRESHOLD) / 
                    (ISOOS_RATIO_CRITICAL_THRESHOLD - ISOOS_RATIO_WARNING_THRESHOLD);
            }
        }
        
        // Negative OOS contribution (25% weight)
        if (totalWindows > 0) {
            double negativeRatio = (double) negativeOosWindows / totalWindows;
            confidence += 0.25 * negativeRatio;
        }
        
        // Parameter instability contribution (20% weight)
        confidence += 0.20 * (1.0 - parameterStability);
        
        // Performance inconsistency contribution (15% weight)
        confidence += 0.15 * (1.0 - performanceConsistency);
        
        return Math.min(1.0, confidence);
    }
    
    /**
     * Checks if a single walk-forward result shows signs of overfitting.
     * 
     * @param result A single window result
     * @return true if the result appears overfit
     */
    public boolean isWindowOverfit(WalkForwardResult result) {
        return result.isOverfit() || result.robustnessScore() < MIN_ROBUSTNESS_SCORE;
    }
    
    /**
     * Result of overfitting analysis.
     */
    public record OverfittingAnalysis(
        /** Whether overfitting is detected */
        boolean isOverfit,
        
        /** Confidence level of overfitting detection [0, 1] */
        double confidenceLevel,
        
        /** List of warning messages */
        List<String> warnings,
        
        /** Detailed diagnostic data */
        Map<String, Object> diagnostics
    ) {
        /**
         * Compact constructor with defensive copying.
         */
        public OverfittingAnalysis {
            warnings = List.copyOf(warnings);
            diagnostics = Map.copyOf(diagnostics);
        }
        
        /**
         * Returns a human-readable summary.
         */
        public String summarize() {
            StringBuilder sb = new StringBuilder();
            sb.append("Overfitting Analysis:\n");
            sb.append("  Overfit: %s (confidence: %.1f%%)\n".formatted(
                isOverfit ? "⚠️ YES" : "✅ NO", confidenceLevel * 100));
            
            if (!warnings.isEmpty()) {
                sb.append("  Warnings:\n");
                for (String warning : warnings) {
                    sb.append("    • ").append(warning).append("\n");
                }
            }
            
            return sb.toString();
        }
    }
}
