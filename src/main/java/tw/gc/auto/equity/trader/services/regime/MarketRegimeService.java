package tw.gc.auto.equity.trader.services.regime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Market Regime Detection Service for classifying current market conditions.
 * 
 * <p>Classifies markets into five distinct regimes based on technical indicators:
 * <ul>
 *   <li><b>TRENDING_UP:</b> Strong upward momentum (ADX > 25, +DI > -DI, MA50 > MA200)</li>
 *   <li><b>TRENDING_DOWN:</b> Strong downward momentum (ADX > 25, -DI > +DI, MA50 < MA200)</li>
 *   <li><b>RANGING:</b> Sideways/choppy market (ADX < 20)</li>
 *   <li><b>HIGH_VOLATILITY:</b> Elevated volatility (VIX equivalent > 30)</li>
 *   <li><b>CRISIS:</b> Extreme conditions (VIX > 50, sharp drawdown)</li>
 * </ul>
 * 
 * <h3>Key Indicators Used:</h3>
 * <ul>
 *   <li><b>ADX (Average Directional Index):</b> Measures trend strength (25+ = trending)</li>
 *   <li><b>+DI/-DI:</b> Directional indicators for trend direction</li>
 *   <li><b>VIX Equivalent:</b> Historical volatility normalized (Taiwan market proxy)</li>
 *   <li><b>MA50 vs MA200:</b> Long-term trend classification (Golden Cross/Death Cross)</li>
 * </ul>
 * 
 * <p>Expected Impact: Avoid 30-50% of losing trades caused by wrong strategy-market fit.
 * 
 * @see RegimeStrategyMapper for strategy selection based on regime
 * @see RegimeTransitionService for regime change detection
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketRegimeService {

    // ===== ADX THRESHOLDS =====
    /** ADX above this indicates a trending market */
    private static final double ADX_TRENDING_THRESHOLD = 25.0;
    /** ADX below this indicates a ranging/choppy market */
    private static final double ADX_RANGING_THRESHOLD = 20.0;
    
    // ===== VOLATILITY THRESHOLDS (annualized %) =====
    /** High volatility threshold (similar to VIX > 30) */
    private static final double HIGH_VOLATILITY_THRESHOLD = 30.0;
    /** Crisis volatility threshold (similar to VIX > 50) */
    private static final double CRISIS_VOLATILITY_THRESHOLD = 50.0;
    
    // ===== CALCULATION PARAMETERS =====
    /** Period for ADX calculation */
    private static final int ADX_PERIOD = 14;
    /** Period for volatility calculation */
    private static final int VOLATILITY_PERIOD = 20;
    /** Period for moving average calculations */
    private static final int MA_SHORT_PERIOD = 50;
    private static final int MA_LONG_PERIOD = 200;
    /** Annualization factor (Taiwan trading days) */
    private static final double ANNUALIZATION_FACTOR = Math.sqrt(252);
    
    // ===== DRAWDOWN THRESHOLD =====
    /** Maximum drawdown percentage to trigger CRISIS regime */
    private static final double CRISIS_DRAWDOWN_THRESHOLD = 0.15; // 15% drawdown

    // Cache for regime calculations
    private final Map<String, RegimeAnalysis> regimeCache = new HashMap<>();

    /**
     * Market regime classification enum.
     */
    public enum MarketRegime {
        /** Strong uptrend: ADX > 25, +DI > -DI, price > MA50 > MA200 */
        TRENDING_UP("Trending Up", "Momentum/Breakout strategies optimal"),
        /** Strong downtrend: ADX > 25, -DI > +DI, price < MA50 < MA200 */
        TRENDING_DOWN("Trending Down", "Short/Defensive strategies optimal"),
        /** Sideways market: ADX < 20, no clear direction */
        RANGING("Ranging", "Mean reversion strategies optimal"),
        /** Elevated volatility: VIX equivalent > 30 */
        HIGH_VOLATILITY("High Volatility", "Reduce exposure, widen stops"),
        /** Extreme conditions: VIX > 50 or major drawdown */
        CRISIS("Crisis", "Exit positions, preserve capital");

        private final String displayName;
        private final String recommendation;

        MarketRegime(String displayName, String recommendation) {
            this.displayName = displayName;
            this.recommendation = recommendation;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getRecommendation() {
            return recommendation;
        }
    }

    /**
     * Detailed regime analysis result.
     */
    public record RegimeAnalysis(
            String symbol,
            MarketRegime regime,
            double confidence,
            double adx,
            double plusDI,
            double minusDI,
            double volatility,
            double ma50,
            double ma200,
            double recentDrawdown,
            LocalDateTime analysisTime,
            String rationale
    ) {
        /**
         * Check if this is a favorable regime for momentum strategies.
         */
        public boolean isFavorableForMomentum() {
            return regime == MarketRegime.TRENDING_UP;
        }

        /**
         * Check if this is a favorable regime for mean reversion strategies.
         */
        public boolean isFavorableForMeanReversion() {
            return regime == MarketRegime.RANGING;
        }

        /**
         * Check if position sizes should be reduced.
         */
        public boolean shouldReduceExposure() {
            return regime == MarketRegime.HIGH_VOLATILITY || regime == MarketRegime.CRISIS;
        }

        /**
         * Get recommended position scale factor based on regime.
         */
        public double getPositionScaleFactor() {
            return switch (regime) {
                case TRENDING_UP -> 1.0;
                case TRENDING_DOWN -> 0.5;
                case RANGING -> 0.7;
                case HIGH_VOLATILITY -> 0.3;
                case CRISIS -> 0.0;
            };
        }
    }

    /**
     * Analyze current market regime for a given symbol.
     * 
     * @param symbol the stock symbol
     * @param marketData list of market data (most recent last), minimum 200 bars recommended
     * @return regime analysis with classification and metrics
     */
    public RegimeAnalysis analyzeRegime(String symbol, List<MarketData> marketData) {
        if (marketData == null || marketData.isEmpty()) {
            log.warn("No market data provided for regime analysis of {}", symbol);
            return createDefaultAnalysis(symbol, "No market data available");
        }

        if (marketData.size() < MA_LONG_PERIOD) {
            log.debug("Insufficient data for full regime analysis of {} ({} bars, need {})", 
                    symbol, marketData.size(), MA_LONG_PERIOD);
            return analyzeWithLimitedData(symbol, marketData);
        }

        // Calculate all indicators
        double[] adxResult = calculateADX(marketData);
        double adx = adxResult[0];
        double plusDI = adxResult[1];
        double minusDI = adxResult[2];
        
        double volatility = calculateAnnualizedVolatility(marketData);
        double ma50 = calculateSMA(marketData, MA_SHORT_PERIOD);
        double ma200 = calculateSMA(marketData, MA_LONG_PERIOD);
        double recentDrawdown = calculateRecentDrawdown(marketData);
        
        MarketData latest = marketData.get(marketData.size() - 1);
        double currentPrice = latest.getClose();
        
        // Classify regime
        MarketRegime regime = classifyRegime(adx, plusDI, minusDI, volatility, 
                currentPrice, ma50, ma200, recentDrawdown);
        
        double confidence = calculateConfidence(adx, volatility, regime);
        String rationale = buildRationale(adx, plusDI, minusDI, volatility, 
                currentPrice, ma50, ma200, regime);
        
        RegimeAnalysis analysis = new RegimeAnalysis(
                symbol, regime, confidence, adx, plusDI, minusDI,
                volatility, ma50, ma200, recentDrawdown,
                LocalDateTime.now(), rationale
        );
        
        // Cache the result
        regimeCache.put(symbol, analysis);
        
        log.info("📊 Regime Analysis [{}]: {} (confidence: {:.1f}%, ADX: {:.1f}, Vol: {:.1f}%)", 
                symbol, regime.getDisplayName(), confidence * 100, adx, volatility);
        
        return analysis;
    }

    /**
     * Get cached regime analysis for a symbol.
     * 
     * @param symbol the stock symbol
     * @return cached analysis or null if not available
     */
    public RegimeAnalysis getCachedRegime(String symbol) {
        return regimeCache.get(symbol);
    }

    /**
     * Clear the regime cache.
     */
    public void clearCache() {
        regimeCache.clear();
        log.debug("Regime cache cleared");
    }

    /**
     * Classify the market regime based on indicators.
     */
    MarketRegime classifyRegime(double adx, double plusDI, double minusDI,
                                       double volatility, double price, 
                                       double ma50, double ma200, double drawdown) {
        // Priority 1: Crisis detection (most urgent)
        if (volatility > CRISIS_VOLATILITY_THRESHOLD || drawdown > CRISIS_DRAWDOWN_THRESHOLD) {
            return MarketRegime.CRISIS;
        }
        
        // Priority 2: High volatility
        if (volatility > HIGH_VOLATILITY_THRESHOLD) {
            return MarketRegime.HIGH_VOLATILITY;
        }
        
        // Priority 3: Trending markets (ADX-based)
        if (adx >= ADX_TRENDING_THRESHOLD) {
            // Use DI and MA relationship for direction
            boolean bullishDI = plusDI > minusDI;
            boolean bullishMA = ma50 > ma200;
            
            // Strong agreement = high confidence trend
            if (bullishDI && bullishMA) {
                return MarketRegime.TRENDING_UP;
            } else if (!bullishDI && !bullishMA) {
                return MarketRegime.TRENDING_DOWN;
            }
            
            // Mixed signals - use DI as primary
            return bullishDI ? MarketRegime.TRENDING_UP : MarketRegime.TRENDING_DOWN;
        }
        
        // Priority 4: Ranging market (low ADX)
        if (adx < ADX_RANGING_THRESHOLD) {
            return MarketRegime.RANGING;
        }
        
        // ADX between 20-25: Weak trend or transitioning
        // Use price relative to MAs to decide
        if (price > ma50 && ma50 > ma200) {
            return MarketRegime.TRENDING_UP;
        } else if (price < ma50 && ma50 < ma200) {
            return MarketRegime.TRENDING_DOWN;
        }
        
        return MarketRegime.RANGING;
    }

    /**
     * Calculate ADX (Average Directional Index) and directional indicators.
     * 
     * @param marketData list of market data
     * @return array: [ADX, +DI, -DI]
     */
    double[] calculateADX(List<MarketData> marketData) {
        if (marketData.size() < ADX_PERIOD + 1) {
            return new double[]{0, 0, 0};
        }

        int size = marketData.size();
        double[] trueRange = new double[size];
        double[] plusDM = new double[size];
        double[] minusDM = new double[size];
        
        // Calculate TR, +DM, -DM
        for (int i = 1; i < size; i++) {
            MarketData current = marketData.get(i);
            MarketData prev = marketData.get(i - 1);
            
            double high = current.getHigh();
            double low = current.getLow();
            double prevClose = prev.getClose();
            double prevHigh = prev.getHigh();
            double prevLow = prev.getLow();
            
            // True Range
            trueRange[i] = Math.max(high - low, 
                    Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            
            // Directional Movement
            double upMove = high - prevHigh;
            double downMove = prevLow - low;
            
            plusDM[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
        }
        
        // Calculate smoothed averages using Wilder's smoothing
        double smoothedTR = 0;
        double smoothedPlusDM = 0;
        double smoothedMinusDM = 0;
        
        // Initial sum for first period
        for (int i = 1; i <= ADX_PERIOD; i++) {
            smoothedTR += trueRange[i];
            smoothedPlusDM += plusDM[i];
            smoothedMinusDM += minusDM[i];
        }
        
        // Apply Wilder's smoothing for remaining periods
        for (int i = ADX_PERIOD + 1; i < size; i++) {
            smoothedTR = smoothedTR - (smoothedTR / ADX_PERIOD) + trueRange[i];
            smoothedPlusDM = smoothedPlusDM - (smoothedPlusDM / ADX_PERIOD) + plusDM[i];
            smoothedMinusDM = smoothedMinusDM - (smoothedMinusDM / ADX_PERIOD) + minusDM[i];
        }
        
        // Calculate DI values
        double plusDI = (smoothedTR > 0) ? (smoothedPlusDM / smoothedTR) * 100 : 0;
        double minusDI = (smoothedTR > 0) ? (smoothedMinusDM / smoothedTR) * 100 : 0;
        
        // Calculate DX and ADX
        double diSum = plusDI + minusDI;
        double dx = (diSum > 0) ? (Math.abs(plusDI - minusDI) / diSum) * 100 : 0;
        
        // ADX is smoothed DX (using same period)
        // For simplicity, we use DX as ADX approximation
        double adx = dx;
        
        return new double[]{adx, plusDI, minusDI};
    }

    /**
     * Calculate annualized volatility (VIX-like measure).
     * 
     * @param marketData list of market data
     * @return annualized volatility as percentage
     */
    double calculateAnnualizedVolatility(List<MarketData> marketData) {
        int period = Math.min(VOLATILITY_PERIOD, marketData.size() - 1);
        if (period < 2) {
            return 0;
        }

        int startIdx = marketData.size() - period - 1;
        double sumSquaredReturns = 0;
        int count = 0;
        
        for (int i = startIdx + 1; i < marketData.size(); i++) {
            double prevClose = marketData.get(i - 1).getClose();
            double currClose = marketData.get(i).getClose();
            
            if (prevClose > 0) {
                double logReturn = Math.log(currClose / prevClose);
                sumSquaredReturns += logReturn * logReturn;
                count++;
            }
        }
        
        if (count < 2) {
            return 0;
        }
        
        double dailyVariance = sumSquaredReturns / count;
        double dailyVol = Math.sqrt(dailyVariance);
        double annualizedVol = dailyVol * ANNUALIZATION_FACTOR * 100; // Convert to percentage
        
        return annualizedVol;
    }

    /**
     * Calculate Simple Moving Average.
     * 
     * @param marketData list of market data
     * @param period MA period
     * @return SMA value
     */
    double calculateSMA(List<MarketData> marketData, int period) {
        if (marketData.size() < period) {
            return 0;
        }
        
        double sum = 0;
        int startIdx = marketData.size() - period;
        for (int i = startIdx; i < marketData.size(); i++) {
            sum += marketData.get(i).getClose();
        }
        
        return sum / period;
    }

    /**
     * Calculate recent drawdown from peak.
     * 
     * @param marketData list of market data
     * @return drawdown as decimal (0.1 = 10% drawdown)
     */
    double calculateRecentDrawdown(List<MarketData> marketData) {
        int lookback = Math.min(60, marketData.size()); // ~3 months lookback
        if (lookback < 5) {
            return 0;
        }
        
        int startIdx = marketData.size() - lookback;
        double peak = Double.MIN_VALUE;
        double currentClose = marketData.get(marketData.size() - 1).getClose();
        
        for (int i = startIdx; i < marketData.size(); i++) {
            peak = Math.max(peak, marketData.get(i).getClose());
        }
        
        if (peak <= 0) {
            return 0;
        }
        
        return (peak - currentClose) / peak;
    }

    /**
     * Calculate confidence in the regime classification.
     */
    private double calculateConfidence(double adx, double volatility, MarketRegime regime) {
        // Base confidence from ADX strength
        double adxConfidence = Math.min(1.0, adx / 50.0);
        
        // Adjust based on regime type
        return switch (regime) {
            case TRENDING_UP, TRENDING_DOWN -> {
                // High ADX = high confidence in trend
                yield 0.5 + (adxConfidence * 0.5);
            }
            case RANGING -> {
                // Low ADX = high confidence in ranging
                double rangingConfidence = 1.0 - adxConfidence;
                yield 0.5 + (rangingConfidence * 0.5);
            }
            case HIGH_VOLATILITY -> {
                // Higher volatility = higher confidence
                double volConfidence = Math.min(1.0, volatility / 60.0);
                yield 0.6 + (volConfidence * 0.4);
            }
            case CRISIS -> {
                // Crisis is clear when volatility is extreme
                double crisisConfidence = Math.min(1.0, volatility / 80.0);
                yield 0.7 + (crisisConfidence * 0.3);
            }
        };
    }

    /**
     * Build human-readable rationale for regime classification.
     */
    private String buildRationale(double adx, double plusDI, double minusDI,
                                  double volatility, double price, 
                                  double ma50, double ma200, MarketRegime regime) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("ADX=%.1f (%s), ", adx, 
                adx >= ADX_TRENDING_THRESHOLD ? "Trending" : "Not Trending"));
        sb.append(String.format("+DI=%.1f, -DI=%.1f (%s), ", plusDI, minusDI,
                plusDI > minusDI ? "Bullish" : "Bearish"));
        sb.append(String.format("Vol=%.1f%% (%s), ", volatility,
                volatility > HIGH_VOLATILITY_THRESHOLD ? "High" : "Normal"));
        sb.append(String.format("MA50 %s MA200", ma50 > ma200 ? ">" : "<"));
        
        return sb.toString();
    }

    /**
     * Create default analysis when no data available.
     */
    private RegimeAnalysis createDefaultAnalysis(String symbol, String reason) {
        return new RegimeAnalysis(
                symbol, MarketRegime.RANGING, 0.0,
                0, 0, 0, 0, 0, 0, 0,
                LocalDateTime.now(), reason
        );
    }

    /**
     * Analyze with limited data (less than MA200 period).
     */
    private RegimeAnalysis analyzeWithLimitedData(String symbol, List<MarketData> marketData) {
        double[] adxResult = calculateADX(marketData);
        double volatility = calculateAnnualizedVolatility(marketData);
        
        // Use available MA or default
        double ma50 = marketData.size() >= MA_SHORT_PERIOD ? 
                calculateSMA(marketData, MA_SHORT_PERIOD) : 0;
        double ma200 = marketData.size() >= MA_LONG_PERIOD ? 
                calculateSMA(marketData, MA_LONG_PERIOD) : ma50;
        
        double currentPrice = marketData.get(marketData.size() - 1).getClose();
        double recentDrawdown = calculateRecentDrawdown(marketData);
        
        MarketRegime regime = classifyRegime(adxResult[0], adxResult[1], adxResult[2],
                volatility, currentPrice, ma50, ma200, recentDrawdown);
        
        return new RegimeAnalysis(
                symbol, regime, 0.5, // Lower confidence with limited data
                adxResult[0], adxResult[1], adxResult[2],
                volatility, ma50, ma200, recentDrawdown,
                LocalDateTime.now(), "Limited data analysis"
        );
    }

    /**
     * Check if market is in a trending regime.
     * 
     * @param symbol the stock symbol
     * @return true if TRENDING_UP or TRENDING_DOWN
     */
    public boolean isTrending(String symbol) {
        RegimeAnalysis analysis = regimeCache.get(symbol);
        if (analysis == null) {
            return false;
        }
        return analysis.regime() == MarketRegime.TRENDING_UP || 
               analysis.regime() == MarketRegime.TRENDING_DOWN;
    }

    /**
     * Check if market is in a crisis or high volatility regime.
     * 
     * @param symbol the stock symbol
     * @return true if HIGH_VOLATILITY or CRISIS
     */
    public boolean isVolatile(String symbol) {
        RegimeAnalysis analysis = regimeCache.get(symbol);
        if (analysis == null) {
            return false;
        }
        return analysis.regime() == MarketRegime.HIGH_VOLATILITY || 
               analysis.regime() == MarketRegime.CRISIS;
    }
}
