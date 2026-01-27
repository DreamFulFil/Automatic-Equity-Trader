package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.services.BetaCalculationService.BetaCategory;
import tw.gc.auto.equity.trader.services.BetaCalculationService.BetaResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Functional interface for providing beta calculations to strategies.
 * 
 * <p>This interface allows strategies to access stock beta values
 * without direct dependency on BetaCalculationService.
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // In BacktestService (provider implementation)
 * BetaProvider provider = symbol -> Optional.ofNullable(betaService.getBeta(symbol));
 * 
 * // In strategy constructor
 * BettingAgainstBetaStrategy strategy = new BettingAgainstBetaStrategy(60, 0.8, provider);
 * </pre>
 * 
 * <h3>Beta Interpretation:</h3>
 * <ul>
 *   <li>β = 1.0: Stock moves with the market</li>
 *   <li>β &gt; 1.0: More volatile than market</li>
 *   <li>β &lt; 1.0: Less volatile than market</li>
 *   <li>β &lt; 0: Moves opposite to market (rare)</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@FunctionalInterface
public interface BetaProvider extends Function<String, Optional<Double>> {

    /**
     * Get beta for a stock symbol.
     * 
     * @param symbol Stock symbol
     * @return Optional containing beta value if available
     */
    Optional<Double> getBeta(String symbol);

    /**
     * Default implementation delegates to getBeta.
     */
    @Override
    default Optional<Double> apply(String symbol) {
        return getBeta(symbol);
    }

    /**
     * Get beta with default value if unavailable.
     * 
     * @param symbol Stock symbol
     * @param defaultBeta Default beta to use if not available
     * @return Beta value or default
     */
    default double getBetaOrDefault(String symbol, double defaultBeta) {
        return getBeta(symbol).orElse(defaultBeta);
    }

    /**
     * Check if stock is low beta (defensive).
     * 
     * @param symbol Stock symbol
     * @param threshold Maximum beta to be considered low (e.g., 0.8)
     * @return true if beta is below threshold
     */
    default boolean isLowBeta(String symbol, double threshold) {
        return getBeta(symbol)
                .map(beta -> beta < threshold)
                .orElse(false);
    }

    /**
     * Check if stock is high beta (aggressive).
     * 
     * @param symbol Stock symbol
     * @param threshold Minimum beta to be considered high (e.g., 1.2)
     * @return true if beta is above threshold
     */
    default boolean isHighBeta(String symbol, double threshold) {
        return getBeta(symbol)
                .map(beta -> beta > threshold)
                .orElse(false);
    }

    /**
     * Categorize stock by beta.
     * 
     * @param symbol Stock symbol
     * @return Category: LOW_BETA (β<0.8), NEUTRAL, HIGH_BETA (β>1.2), UNKNOWN
     */
    default BetaCategory categorize(String symbol) {
        return getBeta(symbol)
                .map(beta -> {
                    if (beta < 0.8) return BetaCategory.LOW_BETA;
                    if (beta > 1.2) return BetaCategory.HIGH_BETA;
                    return BetaCategory.NEUTRAL;
                })
                .orElse(BetaCategory.UNKNOWN);
    }

    /**
     * Compare beta of two stocks.
     * 
     * @param symbol1 First stock
     * @param symbol2 Second stock
     * @return -1 if symbol1 has lower beta, 1 if higher, 0 if equal or unknown
     */
    default int compareBeta(String symbol1, String symbol2) {
        Optional<Double> beta1 = getBeta(symbol1);
        Optional<Double> beta2 = getBeta(symbol2);
        
        if (beta1.isEmpty() || beta2.isEmpty()) {
            return 0;
        }
        
        return Double.compare(beta1.get(), beta2.get());
    }

    /**
     * Calculate expected return based on CAPM.
     * Expected Return = Risk-Free Rate + β × (Market Return - Risk-Free Rate)
     * 
     * @param symbol Stock symbol
     * @param marketReturn Expected market return (e.g., 0.08 for 8%)
     * @param riskFreeRate Risk-free rate (e.g., 0.02 for 2%)
     * @return Expected return, or null if beta unavailable
     */
    default Double calculateExpectedReturn(String symbol, double marketReturn, double riskFreeRate) {
        return getBeta(symbol)
                .map(beta -> riskFreeRate + beta * (marketReturn - riskFreeRate))
                .orElse(null);
    }

    /**
     * Calculate position size adjustment based on beta.
     * Higher beta = smaller position for same risk.
     * 
     * @param symbol Stock symbol
     * @param targetBetaExposure Target portfolio beta exposure (typically 1.0)
     * @return Position size multiplier (e.g., 0.8 for high-beta stock)
     */
    default double getPositionSizeMultiplier(String symbol, double targetBetaExposure) {
        return getBeta(symbol)
                .map(beta -> {
                    if (beta <= 0) return 0.0; // Avoid negative beta positions
                    return Math.min(2.0, targetBetaExposure / beta); // Cap at 2x
                })
                .orElse(1.0);
    }

    // ========== Factory Methods ==========

    /**
     * Create a no-op provider that returns empty for all queries.
     */
    static BetaProvider noOp() {
        return symbol -> Optional.empty();
    }

    /**
     * Create a provider with a fixed beta value for all symbols.
     */
    static BetaProvider withFixedBeta(double beta) {
        return symbol -> Optional.of(beta);
    }

    /**
     * Create a provider from a map of symbol to beta.
     */
    static BetaProvider fromMap(Map<String, Double> betas) {
        return symbol -> Optional.ofNullable(betas.get(symbol));
    }

    /**
     * Create a provider that uses default beta (1.0) when unavailable.
     */
    static BetaProvider withDefault(BetaProvider delegate, double defaultBeta) {
        return symbol -> Optional.of(delegate.getBeta(symbol).orElse(defaultBeta));
    }
}
