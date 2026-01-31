package tw.gc.auto.equity.trader.services.regime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.MarketRegime;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.RegimeAnalysis;
import tw.gc.auto.equity.trader.strategy.StrategyType;

import java.util.*;

/**
 * Maps trading strategies to optimal market regimes.
 * 
 * <p>Strategy-regime alignment is crucial for profitability:
 * <ul>
 *   <li>Momentum strategies excel in trending markets but fail in ranging markets</li>
 *   <li>Mean reversion strategies work in ranging markets but get crushed in trends</li>
 *   <li>Volatility strategies profit from regime transitions</li>
 * </ul>
 * 
 * <h3>Regime-Strategy Mapping:</h3>
 * <pre>
 * TRENDING_UP    → Momentum, Breakout, Trend Following
 * TRENDING_DOWN  → Short strategies, Defensive, Cash
 * RANGING        → Mean Reversion, Bollinger Bands, RSI
 * HIGH_VOLATILITY → Reduced exposure, Wider stops
 * CRISIS         → Exit all, Cash preservation
 * </pre>
 * 
 * @see MarketRegimeService for regime classification
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegimeStrategyMapper {

    // Strategy names that map to StrategyType and specific implementations
    public static final String MOMENTUM = "MomentumStrategy";
    public static final String BREAKOUT = "BreakoutStrategy";
    public static final String TREND_FOLLOWING = "TrendFollowingStrategy";
    public static final String ADX_TREND = "ADXTrendStrategy";
    public static final String MEAN_REVERSION = "MeanReversionStrategy";
    public static final String BOLLINGER_BAND = "BollingerBandStrategy";
    public static final String RSI_STRATEGY = "RSIStrategy";
    public static final String MACD_STRATEGY = "MACDStrategy";
    public static final String DEFENSIVE = "DefensiveStrategy";
    public static final String CASH = "CashPreservation";

    private final MarketRegimeService marketRegimeService;

    /**
     * Strategy recommendation with confidence and reasoning.
     */
    public record StrategyRecommendation(
            String strategyName,
            StrategyType strategyType,
            double fitness,
            double regimeConfidence,
            MarketRegime currentRegime,
            String rationale
    ) {
        /**
         * Combined score considering both strategy fitness and regime confidence.
         */
        public double getScore() {
            return fitness * regimeConfidence;
        }
    }

    /**
     * Portfolio allocation recommendation based on regime.
     */
    public record RegimeAllocation(
            MarketRegime regime,
            double equityAllocation,
            double cashAllocation,
            double maxPositionSize,
            double stopLossMultiplier,
            String guidance
    ) {}

    /**
     * Get recommended strategies for the current market regime.
     * 
     * @param symbol the stock symbol
     * @return list of strategy recommendations sorted by fitness (best first)
     */
    public List<StrategyRecommendation> getRecommendedStrategies(String symbol) {
        RegimeAnalysis analysis = marketRegimeService.getCachedRegime(symbol);
        
        if (analysis == null) {
            log.warn("No cached regime analysis for {}. Using default recommendations.", symbol);
            return getDefaultRecommendations();
        }
        
        return getStrategiesForRegime(analysis);
    }

    /**
     * Get recommended strategies based on regime analysis.
     * 
     * @param analysis the regime analysis
     * @return list of strategy recommendations
     */
    public List<StrategyRecommendation> getStrategiesForRegime(RegimeAnalysis analysis) {
        List<StrategyRecommendation> recommendations = new ArrayList<>();
        MarketRegime regime = analysis.regime();
        double confidence = analysis.confidence();
        
        switch (regime) {
            case TRENDING_UP -> {
                recommendations.add(createRecommendation(MOMENTUM, StrategyType.INTRADAY, 
                        1.0, confidence, regime, "Strong uptrend favors momentum"));
                recommendations.add(createRecommendation(BREAKOUT, StrategyType.INTRADAY, 
                        0.9, confidence, regime, "Breakouts likely to follow through"));
                recommendations.add(createRecommendation(ADX_TREND, StrategyType.SWING, 
                        0.85, confidence, regime, "ADX confirms trend strength"));
                recommendations.add(createRecommendation(TREND_FOLLOWING, StrategyType.SWING, 
                        0.8, confidence, regime, "Ride the trend"));
                // Lower fitness for counter-trend
                recommendations.add(createRecommendation(MEAN_REVERSION, StrategyType.SWING, 
                        0.3, confidence, regime, "Counter-trend risky in uptrend"));
            }
            case TRENDING_DOWN -> {
                recommendations.add(createRecommendation(DEFENSIVE, StrategyType.SWING, 
                        1.0, confidence, regime, "Protect capital in downtrend"));
                recommendations.add(createRecommendation(CASH, StrategyType.SWING, 
                        0.9, confidence, regime, "Cash is a position"));
                // If shorting allowed
                recommendations.add(createRecommendation(MOMENTUM, StrategyType.INTRADAY, 
                        0.5, confidence, regime, "Short momentum only if allowed"));
                recommendations.add(createRecommendation(MEAN_REVERSION, StrategyType.SWING, 
                        0.2, confidence, regime, "Avoid catching falling knives"));
            }
            case RANGING -> {
                recommendations.add(createRecommendation(MEAN_REVERSION, StrategyType.SWING, 
                        1.0, confidence, regime, "Mean reversion optimal in ranging"));
                recommendations.add(createRecommendation(BOLLINGER_BAND, StrategyType.SWING, 
                        0.95, confidence, regime, "Fade the bands"));
                recommendations.add(createRecommendation(RSI_STRATEGY, StrategyType.SWING, 
                        0.9, confidence, regime, "RSI overbought/oversold signals"));
                recommendations.add(createRecommendation(MACD_STRATEGY, StrategyType.SWING, 
                        0.7, confidence, regime, "MACD crossovers in range"));
                // Lower fitness for trend following
                recommendations.add(createRecommendation(MOMENTUM, StrategyType.INTRADAY, 
                        0.3, confidence, regime, "Momentum fails in choppy markets"));
                recommendations.add(createRecommendation(BREAKOUT, StrategyType.INTRADAY, 
                        0.25, confidence, regime, "Many false breakouts expected"));
            }
            case HIGH_VOLATILITY -> {
                recommendations.add(createRecommendation(DEFENSIVE, StrategyType.SWING, 
                        1.0, confidence, regime, "Reduce exposure in high vol"));
                recommendations.add(createRecommendation(CASH, StrategyType.SWING, 
                        0.9, confidence, regime, "High cash allocation"));
                // Volatility can create opportunities but risky
                recommendations.add(createRecommendation(MEAN_REVERSION, StrategyType.SWING, 
                        0.5, confidence, regime, "Quick mean reversion possible"));
                recommendations.add(createRecommendation(MOMENTUM, StrategyType.INTRADAY, 
                        0.3, confidence, regime, "Momentum risky with wide swings"));
            }
            case CRISIS -> {
                recommendations.add(createRecommendation(CASH, StrategyType.SWING, 
                        1.0, confidence, regime, "CRISIS: Maximum capital preservation"));
                recommendations.add(createRecommendation(DEFENSIVE, StrategyType.SWING, 
                        0.5, confidence, regime, "Only defensive positions if any"));
                // All others highly discouraged
                recommendations.add(createRecommendation(MOMENTUM, StrategyType.INTRADAY, 
                        0.0, confidence, regime, "DO NOT use momentum in crisis"));
            }
        }
        
        // Sort by score (fitness * confidence)
        recommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        log.debug("Generated {} strategy recommendations for {} in {} regime", 
                recommendations.size(), analysis.symbol(), regime.getDisplayName());
        
        return recommendations;
    }

    /**
     * Check if a specific strategy is suitable for the current regime.
     * 
     * @param strategyName the strategy name
     * @param symbol the stock symbol
     * @return fitness score (0.0 to 1.0)
     */
    public double getStrategyFitness(String strategyName, String symbol) {
        List<StrategyRecommendation> recommendations = getRecommendedStrategies(symbol);
        
        return recommendations.stream()
                .filter(r -> r.strategyName().equals(strategyName))
                .findFirst()
                .map(StrategyRecommendation::fitness)
                .orElse(0.5); // Default moderate fitness if not found
    }

    /**
     * Check if a strategy should be blocked in the current regime.
     * 
     * @param strategyName the strategy name
     * @param symbol the stock symbol
     * @return true if strategy should be avoided
     */
    public boolean shouldBlockStrategy(String strategyName, String symbol) {
        double fitness = getStrategyFitness(strategyName, symbol);
        RegimeAnalysis analysis = marketRegimeService.getCachedRegime(symbol);
        
        // Block if fitness is very low or in crisis
        if (analysis != null && analysis.regime() == MarketRegime.CRISIS) {
            return !CASH.equals(strategyName) && !DEFENSIVE.equals(strategyName);
        }
        
        return fitness < 0.2;
    }

    /**
     * Get portfolio allocation recommendation based on regime.
     * 
     * @param regime the market regime
     * @return allocation recommendation
     */
    public RegimeAllocation getAllocationForRegime(MarketRegime regime) {
        return switch (regime) {
            case TRENDING_UP -> new RegimeAllocation(
                    regime, 1.0, 0.0, 0.10, 1.0,
                    "Full equity allocation in uptrend, standard stops");
            case TRENDING_DOWN -> new RegimeAllocation(
                    regime, 0.3, 0.7, 0.05, 0.8,
                    "Reduced equity, tight stops, preserve capital");
            case RANGING -> new RegimeAllocation(
                    regime, 0.7, 0.3, 0.07, 1.2,
                    "Moderate equity, wider stops for noise");
            case HIGH_VOLATILITY -> new RegimeAllocation(
                    regime, 0.4, 0.6, 0.05, 1.5,
                    "Low equity, much wider stops, small positions");
            case CRISIS -> new RegimeAllocation(
                    regime, 0.0, 1.0, 0.0, 2.0,
                    "CRISIS: Exit all positions, 100% cash");
        };
    }

    /**
     * Get allocation for a symbol based on cached regime.
     * 
     * @param symbol the stock symbol
     * @return allocation recommendation
     */
    public RegimeAllocation getAllocation(String symbol) {
        RegimeAnalysis analysis = marketRegimeService.getCachedRegime(symbol);
        
        if (analysis == null) {
            // Default to conservative allocation
            return getAllocationForRegime(MarketRegime.RANGING);
        }
        
        return getAllocationForRegime(analysis.regime());
    }

    /**
     * Map strategy type to compatible regimes.
     * 
     * @param strategyType the strategy type
     * @return set of compatible regimes
     */
    public Set<MarketRegime> getCompatibleRegimes(StrategyType strategyType) {
        return switch (strategyType) {
            case INTRADAY -> Set.of(MarketRegime.TRENDING_UP, MarketRegime.RANGING);
            case SWING -> Set.of(MarketRegime.TRENDING_UP, MarketRegime.RANGING, 
                                  MarketRegime.TRENDING_DOWN);
            case SHORT_TERM -> Set.of(MarketRegime.RANGING, MarketRegime.HIGH_VOLATILITY);
            case LONG_TERM -> Set.of(MarketRegime.TRENDING_UP);
        };
    }

    /**
     * Get regime-adjusted position size multiplier.
     * 
     * @param symbol the stock symbol
     * @param baseSize the base position size
     * @return adjusted position size
     */
    public double adjustPositionForRegime(String symbol, double baseSize) {
        RegimeAnalysis analysis = marketRegimeService.getCachedRegime(symbol);
        
        if (analysis == null) {
            return baseSize;
        }
        
        double scaleFactor = analysis.getPositionScaleFactor();
        double adjusted = baseSize * scaleFactor;
        
        log.debug("Position adjusted for {} regime: {:.2f} → {:.2f} (factor: {:.2f})", 
                analysis.regime().getDisplayName(), baseSize, adjusted, scaleFactor);
        
        return adjusted;
    }

    private StrategyRecommendation createRecommendation(String name, StrategyType type,
                                                        double fitness, double confidence,
                                                        MarketRegime regime, String rationale) {
        return new StrategyRecommendation(name, type, fitness, confidence, regime, rationale);
    }

    private List<StrategyRecommendation> getDefaultRecommendations() {
        // Conservative default when no regime data
        return List.of(
                new StrategyRecommendation(MEAN_REVERSION, StrategyType.SWING, 
                        0.7, 0.5, MarketRegime.RANGING, "Default: Mean reversion is safest"),
                new StrategyRecommendation(DEFENSIVE, StrategyType.SWING, 
                        0.6, 0.5, MarketRegime.RANGING, "Default: Conservative approach")
        );
    }
}
