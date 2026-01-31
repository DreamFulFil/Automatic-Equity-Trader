package tw.gc.auto.equity.trader.services.positionsizing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatility Targeting Service for portfolio-level risk management.
 * 
 * <p>Implements volatility targeting strategy where position sizes are
 * scaled inversely to recent volatility, maintaining a consistent
 * portfolio volatility target (default 15% annualized).
 * 
 * <p>Key features:
 * <ul>
 *   <li>20-day rolling volatility calculation</li>
 *   <li>Position scaling based on volatility ratio</li>
 *   <li>Caching of volatility estimates per symbol</li>
 *   <li>Regime-aware scaling factors</li>
 * </ul>
 * 
 * @see PositionSizingService for position-level sizing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VolatilityTargetingService {

    /**
     * Default target portfolio volatility (annualized).
     */
    private static final double DEFAULT_TARGET_VOL = 0.15;

    /**
     * Default volatility lookback period in days.
     */
    private static final int DEFAULT_VOL_PERIOD = 20;

    /**
     * Trading days per year for annualization.
     */
    private static final double TRADING_DAYS_PER_YEAR = 252.0;

    /**
     * Minimum scaling factor to avoid zero positions.
     */
    private static final double MIN_SCALE = 0.1;

    /**
     * Maximum scaling factor to avoid over-leveraging.
     */
    private static final double MAX_SCALE = 2.0;

    /**
     * High volatility threshold (above target * 1.5).
     */
    private static final double HIGH_VOL_THRESHOLD = 1.5;

    /**
     * Crisis volatility threshold (above target * 2.5).
     */
    private static final double CRISIS_VOL_THRESHOLD = 2.5;

    /**
     * Cache for volatility estimates per symbol.
     */
    private final Map<String, VolatilityEstimate> volatilityCache = new ConcurrentHashMap<>();

    /**
     * Volatility estimate with metadata.
     */
    public record VolatilityEstimate(
            String symbol,
            double dailyVol,
            double annualizedVol,
            int periodDays,
            long timestamp,
            VolatilityRegime regime
    ) {
        /**
         * Check if estimate is still valid (not older than 1 day).
         */
        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000;
        }

        /**
         * Get volatility ratio compared to target.
         */
        public double ratioToTarget(double targetVol) {
            return annualizedVol / targetVol;
        }
    }

    /**
     * Volatility regime classification.
     */
    public enum VolatilityRegime {
        LOW,
        NORMAL,
        HIGH,
        CRISIS
    }

    /**
     * Scaling result with reasoning.
     */
    public record ScalingResult(
            double scaleFactor,
            VolatilityRegime regime,
            String reasoning
    ) {}

    /**
     * Calculate rolling volatility for a symbol from market data history.
     * 
     * @param history list of market data (oldest first)
     * @param symbol stock symbol
     * @param period number of days for volatility calculation
     * @return volatility estimate
     */
    public VolatilityEstimate calculateVolatility(List<MarketData> history, String symbol, int period) {
        if (history == null || history.size() < period + 1) {
            log.warn("Insufficient data for volatility calculation: {} days, need {}", 
                    history != null ? history.size() : 0, period + 1);
            return createDefaultEstimate(symbol, period);
        }

        // Calculate daily returns
        double[] returns = new double[period];
        int startIdx = history.size() - period;

        for (int i = 0; i < period; i++) {
            double prevClose = history.get(startIdx + i - 1).getClose();
            double currClose = history.get(startIdx + i).getClose();
            
            if (prevClose > 0) {
                returns[i] = (currClose - prevClose) / prevClose;
            } else {
                returns[i] = 0.0;
            }
        }

        // Calculate mean return
        double meanReturn = 0.0;
        for (double r : returns) {
            meanReturn += r;
        }
        meanReturn /= period;

        // Calculate variance
        double variance = 0.0;
        for (double r : returns) {
            variance += Math.pow(r - meanReturn, 2);
        }
        variance /= (period - 1); // Sample variance

        // Daily and annualized volatility
        double dailyVol = Math.sqrt(variance);
        double annualizedVol = dailyVol * Math.sqrt(TRADING_DAYS_PER_YEAR);

        // Classify regime
        VolatilityRegime regime = classifyRegime(annualizedVol, DEFAULT_TARGET_VOL);

        VolatilityEstimate estimate = new VolatilityEstimate(
                symbol, dailyVol, annualizedVol, period, System.currentTimeMillis(), regime
        );

        // Cache the estimate
        volatilityCache.put(symbol, estimate);

        log.info("📊 Volatility for {}: daily={:.4f}, annualized={:.2f}% ({})",
                symbol, dailyVol, annualizedVol * 100, regime);

        return estimate;
    }

    /**
     * Calculate volatility with default 20-day period.
     */
    public VolatilityEstimate calculateVolatility(List<MarketData> history, String symbol) {
        return calculateVolatility(history, symbol, DEFAULT_VOL_PERIOD);
    }

    /**
     * Get cached volatility estimate if valid, otherwise return null.
     */
    public VolatilityEstimate getCachedVolatility(String symbol) {
        VolatilityEstimate cached = volatilityCache.get(symbol);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        return null;
    }

    /**
     * Calculate position scaling factor based on volatility targeting.
     * 
     * <p>Formula: scaleFactor = targetVol / currentVol
     * 
     * <p>When volatility is high, scale down positions. When low, scale up.
     * 
     * @param currentVol current annualized volatility
     * @param targetVol target annualized volatility
     * @return scaling result with factor and regime
     */
    public ScalingResult calculateScalingFactor(double currentVol, double targetVol) {
        if (currentVol <= 0 || targetVol <= 0) {
            return new ScalingResult(1.0, VolatilityRegime.NORMAL, "Invalid volatility, using 1.0");
        }

        double rawScale = targetVol / currentVol;
        VolatilityRegime regime = classifyRegime(currentVol, targetVol);

        // Apply regime-specific adjustments
        double adjustedScale = switch (regime) {
            case CRISIS -> Math.min(rawScale, MIN_SCALE); // Max reduction in crisis
            case HIGH -> Math.min(rawScale, 0.5); // Cap at 50% in high vol
            case NORMAL -> Math.min(Math.max(rawScale, MIN_SCALE), MAX_SCALE);
            case LOW -> Math.min(rawScale, MAX_SCALE); // Cap leverage in low vol
        };

        String reasoning = String.format(
                "Vol=%.2f%% vs target=%.2f%%, raw=%.2f, adjusted=%.2f (%s)",
                currentVol * 100, targetVol * 100, rawScale, adjustedScale, regime
        );

        log.info("📊 Volatility scaling: {} -> scale={:.2f} ({})", 
                String.format("%.2f%%", currentVol * 100), adjustedScale, regime);

        return new ScalingResult(adjustedScale, regime, reasoning);
    }

    /**
     * Calculate scaling factor using default target volatility.
     */
    public ScalingResult calculateScalingFactor(double currentVol) {
        return calculateScalingFactor(currentVol, DEFAULT_TARGET_VOL);
    }

    /**
     * Apply volatility scaling to position size.
     * 
     * @param baseShares original share count
     * @param currentVol current annualized volatility
     * @param targetVol target annualized volatility
     * @return scaled share count
     */
    public int scalePosition(int baseShares, double currentVol, double targetVol) {
        ScalingResult scaling = calculateScalingFactor(currentVol, targetVol);
        int scaledShares = (int) Math.round(baseShares * scaling.scaleFactor());
        return Math.max(1, scaledShares); // Minimum 1 share
    }

    /**
     * Scale position using default target volatility.
     */
    public int scalePosition(int baseShares, double currentVol) {
        return scalePosition(baseShares, currentVol, DEFAULT_TARGET_VOL);
    }

    /**
     * Calculate portfolio volatility from individual position volatilities.
     * 
     * <p>Simplified calculation assuming zero correlation (conservative).
     * For more accurate results, use CorrelationTracker.
     * 
     * @param positionVols map of symbol to (weight, volatility)
     * @return portfolio volatility
     */
    public double calculatePortfolioVolatility(Map<String, PositionVol> positionVols) {
        if (positionVols == null || positionVols.isEmpty()) {
            return 0.0;
        }

        // Sum of squared weighted volatilities (assuming zero correlation)
        double sumSquared = 0.0;
        for (PositionVol pv : positionVols.values()) {
            sumSquared += Math.pow(pv.weight() * pv.volatility(), 2);
        }

        return Math.sqrt(sumSquared);
    }

    /**
     * Position volatility record.
     */
    public record PositionVol(String symbol, double weight, double volatility) {}

    /**
     * Classify volatility regime.
     */
    private VolatilityRegime classifyRegime(double currentVol, double targetVol) {
        double ratio = currentVol / targetVol;

        if (ratio >= CRISIS_VOL_THRESHOLD) {
            return VolatilityRegime.CRISIS;
        } else if (ratio >= HIGH_VOL_THRESHOLD) {
            return VolatilityRegime.HIGH;
        } else if (ratio <= 0.5) {
            return VolatilityRegime.LOW;
        } else {
            return VolatilityRegime.NORMAL;
        }
    }

    /**
     * Create default estimate when data is insufficient.
     */
    private VolatilityEstimate createDefaultEstimate(String symbol, int period) {
        // Assume normal market volatility (20% annualized)
        double defaultAnnualizedVol = 0.20;
        double defaultDailyVol = defaultAnnualizedVol / Math.sqrt(TRADING_DAYS_PER_YEAR);

        return new VolatilityEstimate(
                symbol, 
                defaultDailyVol, 
                defaultAnnualizedVol, 
                period, 
                System.currentTimeMillis(), 
                VolatilityRegime.NORMAL
        );
    }

    /**
     * Clear volatility cache.
     */
    public void clearCache() {
        volatilityCache.clear();
        log.info("📊 Volatility cache cleared");
    }

    /**
     * Get the default target volatility.
     */
    public double getDefaultTargetVolatility() {
        return DEFAULT_TARGET_VOL;
    }

    /**
     * Get the default volatility period.
     */
    public int getDefaultVolatilityPeriod() {
        return DEFAULT_VOL_PERIOD;
    }
}
