package tw.gc.auto.equity.trader.services.positionsizing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Correlation Tracker for monitoring portfolio correlation and concentration risk.
 * 
 * <p>Tracks rolling correlation between positions to:
 * <ul>
 *   <li>Detect concentrated risk when positions are highly correlated</li>
 *   <li>Reduce new position sizes when portfolio correlation exceeds threshold</li>
 *   <li>Alert on sector exposure concentration</li>
 * </ul>
 * 
 * <p>Key thresholds:
 * <ul>
 *   <li>High correlation warning: &gt; 0.7</li>
 *   <li>Critical correlation: &gt; 0.85</li>
 *   <li>Max single position: 25% of portfolio</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CorrelationTracker {

    /**
     * High correlation threshold (warning).
     */
    private static final double HIGH_CORRELATION_THRESHOLD = 0.7;

    /**
     * Critical correlation threshold (reduce exposure).
     */
    private static final double CRITICAL_CORRELATION_THRESHOLD = 0.85;

    /**
     * Maximum single position concentration.
     */
    private static final double MAX_SINGLE_POSITION_PCT = 0.25;

    /**
     * Maximum sector concentration.
     */
    private static final double MAX_SECTOR_CONCENTRATION_PCT = 0.40;

    /**
     * Default correlation period in days.
     */
    private static final int DEFAULT_CORRELATION_PERIOD = 60;

    /**
     * Correlation matrix cache.
     */
    private final Map<String, Map<String, CorrelationEstimate>> correlationMatrix = new ConcurrentHashMap<>();

    /**
     * Returns history cache per symbol.
     */
    private final Map<String, List<Double>> returnsCache = new ConcurrentHashMap<>();

    /**
     * Correlation estimate between two symbols.
     */
    public record CorrelationEstimate(
            String symbol1,
            String symbol2,
            double correlation,
            int periodDays,
            long timestamp,
            CorrelationLevel level
    ) {
        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000;
        }
    }

    /**
     * Correlation level classification.
     */
    public enum CorrelationLevel {
        NEGATIVE,    // < 0
        LOW,         // 0 - 0.3
        MODERATE,    // 0.3 - 0.7
        HIGH,        // 0.7 - 0.85
        CRITICAL     // > 0.85
    }

    /**
     * Portfolio concentration analysis result.
     */
    public record ConcentrationAnalysis(
            double avgPairwiseCorrelation,
            double maxPairwiseCorrelation,
            List<String> highlyCorrelatedPairs,
            Map<String, Double> sectorConcentrations,
            List<String> concentrationWarnings,
            boolean shouldReduceExposure
    ) {}

    /**
     * Position info for analysis.
     */
    public record PositionInfo(
            String symbol,
            double weight,
            String sector
    ) {}

    /**
     * Calculate correlation between two symbols from return series.
     * 
     * @param returns1 daily returns for symbol 1
     * @param returns2 daily returns for symbol 2
     * @return Pearson correlation coefficient
     */
    public double calculateCorrelation(double[] returns1, double[] returns2) {
        if (returns1 == null || returns2 == null) {
            return 0.0;
        }

        int n = Math.min(returns1.length, returns2.length);
        if (n < 10) {
            return 0.0; // Insufficient data
        }

        // Calculate means
        double mean1 = 0.0, mean2 = 0.0;
        for (int i = 0; i < n; i++) {
            mean1 += returns1[i];
            mean2 += returns2[i];
        }
        mean1 /= n;
        mean2 /= n;

        // Calculate covariance and standard deviations
        double covariance = 0.0;
        double var1 = 0.0, var2 = 0.0;

        for (int i = 0; i < n; i++) {
            double d1 = returns1[i] - mean1;
            double d2 = returns2[i] - mean2;
            covariance += d1 * d2;
            var1 += d1 * d1;
            var2 += d2 * d2;
        }

        double std1 = Math.sqrt(var1 / (n - 1));
        double std2 = Math.sqrt(var2 / (n - 1));

        if (std1 < 1e-10 || std2 < 1e-10) {
            return 0.0; // Avoid division by zero
        }

        return (covariance / (n - 1)) / (std1 * std2);
    }

    /**
     * Calculate returns from market data history.
     * 
     * @param history market data list (oldest first)
     * @return array of daily returns
     */
    public double[] calculateReturns(List<MarketData> history) {
        if (history == null || history.size() < 2) {
            return new double[0];
        }

        double[] returns = new double[history.size() - 1];
        for (int i = 1; i < history.size(); i++) {
            double prevClose = history.get(i - 1).getClose();
            double currClose = history.get(i).getClose();
            
            if (prevClose > 0) {
                returns[i - 1] = (currClose - prevClose) / prevClose;
            }
        }

        return returns;
    }

    /**
     * Calculate and cache correlation between two symbols.
     * 
     * @param history1 market data for symbol 1
     * @param history2 market data for symbol 2
     * @param symbol1 symbol 1 name
     * @param symbol2 symbol 2 name
     * @return correlation estimate
     */
    public CorrelationEstimate calculateCorrelation(
            List<MarketData> history1, 
            List<MarketData> history2,
            String symbol1, 
            String symbol2) {

        double[] returns1 = calculateReturns(history1);
        double[] returns2 = calculateReturns(history2);

        double correlation = calculateCorrelation(returns1, returns2);
        int period = Math.min(returns1.length, returns2.length);
        CorrelationLevel level = classifyCorrelation(correlation);

        CorrelationEstimate estimate = new CorrelationEstimate(
                symbol1, symbol2, correlation, period, System.currentTimeMillis(), level
        );

        // Cache the estimate (both directions)
        correlationMatrix
                .computeIfAbsent(symbol1, k -> new ConcurrentHashMap<>())
                .put(symbol2, estimate);
        correlationMatrix
                .computeIfAbsent(symbol2, k -> new ConcurrentHashMap<>())
                .put(symbol1, estimate);

        log.info("📊 Correlation {}-{}: {:.3f} ({})", symbol1, symbol2, correlation, level);

        return estimate;
    }

    /**
     * Get cached correlation if valid.
     */
    public Optional<CorrelationEstimate> getCachedCorrelation(String symbol1, String symbol2) {
        Map<String, CorrelationEstimate> row = correlationMatrix.get(symbol1);
        if (row != null) {
            CorrelationEstimate estimate = row.get(symbol2);
            if (estimate != null && estimate.isValid()) {
                return Optional.of(estimate);
            }
        }
        return Optional.empty();
    }

    /**
     * Analyze portfolio concentration and correlation risk.
     * 
     * @param positions list of current positions
     * @param correlationData map of symbol pairs to correlation values
     * @return concentration analysis result
     */
    public ConcentrationAnalysis analyzePortfolio(
            List<PositionInfo> positions,
            Map<String, Double> correlationData) {

        List<String> warnings = new ArrayList<>();
        List<String> highlyCorrelatedPairs = new ArrayList<>();
        boolean shouldReduceExposure = false;

        // Check single position concentration
        for (PositionInfo pos : positions) {
            if (pos.weight() > MAX_SINGLE_POSITION_PCT) {
                warnings.add(String.format(
                        "Position %s at %.1f%% exceeds max %.1f%%",
                        pos.symbol(), pos.weight() * 100, MAX_SINGLE_POSITION_PCT * 100
                ));
            }
        }

        // Calculate sector concentrations
        Map<String, Double> sectorWeights = positions.stream()
                .filter(p -> p.sector() != null)
                .collect(Collectors.groupingBy(
                        PositionInfo::sector,
                        Collectors.summingDouble(PositionInfo::weight)
                ));

        for (Map.Entry<String, Double> entry : sectorWeights.entrySet()) {
            if (entry.getValue() > MAX_SECTOR_CONCENTRATION_PCT) {
                warnings.add(String.format(
                        "Sector %s at %.1f%% exceeds max %.1f%%",
                        entry.getKey(), entry.getValue() * 100, MAX_SECTOR_CONCENTRATION_PCT * 100
                ));
            }
        }

        // Analyze pairwise correlations
        double sumCorrelation = 0.0;
        double maxCorrelation = 0.0;
        int pairCount = 0;

        for (Map.Entry<String, Double> entry : correlationData.entrySet()) {
            double corr = Math.abs(entry.getValue());
            sumCorrelation += corr;
            maxCorrelation = Math.max(maxCorrelation, corr);
            pairCount++;

            if (corr > HIGH_CORRELATION_THRESHOLD) {
                highlyCorrelatedPairs.add(entry.getKey());
                
                if (corr > CRITICAL_CORRELATION_THRESHOLD) {
                    warnings.add(String.format(
                            "Critical correlation: %s at %.2f",
                            entry.getKey(), entry.getValue()
                    ));
                    shouldReduceExposure = true;
                }
            }
        }

        double avgCorrelation = pairCount > 0 ? sumCorrelation / pairCount : 0.0;

        // Check overall portfolio correlation
        if (avgCorrelation > HIGH_CORRELATION_THRESHOLD) {
            warnings.add(String.format(
                    "Portfolio average correlation %.2f exceeds threshold %.2f",
                    avgCorrelation, HIGH_CORRELATION_THRESHOLD
            ));
            shouldReduceExposure = true;
        }

        log.info("📊 Portfolio analysis: avgCorr={:.3f}, maxCorr={:.3f}, warnings={}",
                avgCorrelation, maxCorrelation, warnings.size());

        return new ConcentrationAnalysis(
                avgCorrelation,
                maxCorrelation,
                highlyCorrelatedPairs,
                sectorWeights,
                warnings,
                shouldReduceExposure
        );
    }

    /**
     * Calculate position scaling factor based on correlation.
     * 
     * <p>Reduces position size when adding to highly correlated portfolio.
     * 
     * @param newSymbolCorrelation correlation of new position with existing portfolio
     * @return scaling factor (0.5 - 1.0)
     */
    public double calculateCorrelationScaling(double newSymbolCorrelation) {
        double absCorr = Math.abs(newSymbolCorrelation);

        if (absCorr > CRITICAL_CORRELATION_THRESHOLD) {
            return 0.5; // Reduce by 50%
        } else if (absCorr > HIGH_CORRELATION_THRESHOLD) {
            // Linear scaling from 1.0 to 0.5 as correlation goes from 0.7 to 0.85
            double range = CRITICAL_CORRELATION_THRESHOLD - HIGH_CORRELATION_THRESHOLD;
            double excess = absCorr - HIGH_CORRELATION_THRESHOLD;
            return 1.0 - (excess / range) * 0.5;
        }

        return 1.0; // No reduction
    }

    /**
     * Calculate average correlation of a symbol with existing portfolio positions.
     * 
     * @param symbol the symbol to check
     * @param existingSymbols symbols in the existing portfolio
     * @return average correlation with existing positions
     */
    public double calculateAverageCorrelationWithPortfolio(
            String symbol, 
            Set<String> existingSymbols) {
        
        if (existingSymbols == null || existingSymbols.isEmpty()) {
            return 0.0;
        }

        double sumCorr = 0.0;
        int count = 0;

        Map<String, CorrelationEstimate> symbolCorrelations = correlationMatrix.get(symbol);
        if (symbolCorrelations == null) {
            return 0.0;
        }

        for (String existing : existingSymbols) {
            CorrelationEstimate estimate = symbolCorrelations.get(existing);
            if (estimate != null && estimate.isValid()) {
                sumCorr += estimate.correlation();
                count++;
            }
        }

        return count > 0 ? sumCorr / count : 0.0;
    }

    /**
     * Check if adding a position would exceed concentration limits.
     * 
     * @param symbol symbol to add
     * @param proposedWeight proposed weight of new position
     * @param existingPositions current portfolio positions
     * @return list of warnings (empty if OK to proceed)
     */
    public List<String> checkConcentrationLimits(
            String symbol,
            double proposedWeight,
            List<PositionInfo> existingPositions) {

        List<String> warnings = new ArrayList<>();

        // Check single position limit
        if (proposedWeight > MAX_SINGLE_POSITION_PCT) {
            warnings.add(String.format(
                    "Proposed position %.1f%% exceeds max %.1f%%",
                    proposedWeight * 100, MAX_SINGLE_POSITION_PCT * 100
            ));
        }

        // Check correlation with existing
        Set<String> existingSymbols = existingPositions.stream()
                .map(PositionInfo::symbol)
                .collect(Collectors.toSet());

        double avgCorr = calculateAverageCorrelationWithPortfolio(symbol, existingSymbols);
        
        if (avgCorr > CRITICAL_CORRELATION_THRESHOLD) {
            warnings.add(String.format(
                    "New position highly correlated with portfolio (avg %.2f)",
                    avgCorr
            ));
        } else if (avgCorr > HIGH_CORRELATION_THRESHOLD) {
            warnings.add(String.format(
                    "New position moderately correlated with portfolio (avg %.2f)",
                    avgCorr
            ));
        }

        return warnings;
    }

    /**
     * Classify correlation level.
     */
    private CorrelationLevel classifyCorrelation(double correlation) {
        double absCorr = Math.abs(correlation);

        if (correlation < 0) {
            return CorrelationLevel.NEGATIVE;
        } else if (absCorr < 0.3) {
            return CorrelationLevel.LOW;
        } else if (absCorr < HIGH_CORRELATION_THRESHOLD) {
            return CorrelationLevel.MODERATE;
        } else if (absCorr < CRITICAL_CORRELATION_THRESHOLD) {
            return CorrelationLevel.HIGH;
        } else {
            return CorrelationLevel.CRITICAL;
        }
    }

    /**
     * Clear correlation cache.
     */
    public void clearCache() {
        correlationMatrix.clear();
        returnsCache.clear();
        log.info("📊 Correlation cache cleared");
    }

    /**
     * Get high correlation threshold.
     */
    public double getHighCorrelationThreshold() {
        return HIGH_CORRELATION_THRESHOLD;
    }

    /**
     * Get critical correlation threshold.
     */
    public double getCriticalCorrelationThreshold() {
        return CRITICAL_CORRELATION_THRESHOLD;
    }

    /**
     * Get max single position percentage.
     */
    public double getMaxSinglePositionPct() {
        return MAX_SINGLE_POSITION_PCT;
    }
}
