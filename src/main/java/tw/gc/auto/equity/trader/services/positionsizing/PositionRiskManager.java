package tw.gc.auto.equity.trader.services.positionsizing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.util.*;

/**
 * Position Risk Manager - Central coordinator for position sizing decisions.
 * 
 * <p>Integrates all position sizing components to provide unified risk-adjusted
 * position sizing recommendations. This service acts as the main entry point
 * for position sizing decisions.
 * 
 * <p>Key responsibilities:
 * <ul>
 *   <li>Combine Kelly/ATR sizing with volatility targeting</li>
 *   <li>Apply correlation-based adjustments</li>
 *   <li>Enforce concentration limits (max 25% single stock, 10% per trade)</li>
 *   <li>Reject trades exceeding position limits</li>
 * </ul>
 * 
 * @see PositionSizingService for individual position sizing
 * @see VolatilityTargetingService for volatility adjustments
 * @see CorrelationTracker for correlation analysis
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositionRiskManager {

    private final PositionSizingService positionSizingService;
    private final VolatilityTargetingService volatilityTargetingService;
    private final CorrelationTracker correlationTracker;

    /**
     * Maximum single position as percentage of portfolio.
     */
    private static final double MAX_SINGLE_POSITION_PCT = 0.25;

    /**
     * Maximum per-trade risk as percentage of equity.
     */
    private static final double MAX_PER_TRADE_RISK_PCT = 0.10;

    /**
     * Minimum position scaling factor.
     */
    private static final double MIN_SCALE_FACTOR = 0.1;

    /**
     * Position sizing request containing all inputs.
     */
    public record PositionRequest(
            String symbol,
            double stockPrice,
            double equity,
            double winRate,
            double avgWin,
            double avgLoss,
            double atr,
            double riskPerTradePct,
            List<MarketData> priceHistory,
            List<CorrelationTracker.PositionInfo> existingPositions,
            String sector,
            SizingMethod preferredMethod
    ) {
        /**
         * Builder for creating position requests.
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String symbol;
            private double stockPrice;
            private double equity;
            private double winRate = 0;
            private double avgWin = 0;
            private double avgLoss = 0;
            private double atr = 0;
            private double riskPerTradePct = 2.0;
            private List<MarketData> priceHistory = List.of();
            private List<CorrelationTracker.PositionInfo> existingPositions = List.of();
            private String sector;
            private SizingMethod preferredMethod = SizingMethod.AUTO;

            public Builder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public Builder stockPrice(double stockPrice) {
                this.stockPrice = stockPrice;
                return this;
            }

            public Builder equity(double equity) {
                this.equity = equity;
                return this;
            }

            public Builder winRate(double winRate) {
                this.winRate = winRate;
                return this;
            }

            public Builder avgWin(double avgWin) {
                this.avgWin = avgWin;
                return this;
            }

            public Builder avgLoss(double avgLoss) {
                this.avgLoss = avgLoss;
                return this;
            }

            public Builder atr(double atr) {
                this.atr = atr;
                return this;
            }

            public Builder riskPerTradePct(double riskPerTradePct) {
                this.riskPerTradePct = riskPerTradePct;
                return this;
            }

            public Builder priceHistory(List<MarketData> priceHistory) {
                this.priceHistory = priceHistory != null ? priceHistory : List.of();
                return this;
            }

            public Builder existingPositions(List<CorrelationTracker.PositionInfo> existingPositions) {
                this.existingPositions = existingPositions != null ? existingPositions : List.of();
                return this;
            }

            public Builder sector(String sector) {
                this.sector = sector;
                return this;
            }

            public Builder preferredMethod(SizingMethod preferredMethod) {
                this.preferredMethod = preferredMethod;
                return this;
            }

            public PositionRequest build() {
                Objects.requireNonNull(symbol, "Symbol is required");
                if (stockPrice <= 0) throw new IllegalArgumentException("Stock price must be positive");
                if (equity <= 0) throw new IllegalArgumentException("Equity must be positive");
                
                return new PositionRequest(
                        symbol, stockPrice, equity, winRate, avgWin, avgLoss,
                        atr, riskPerTradePct, priceHistory, existingPositions, sector, preferredMethod
                );
            }
        }
    }

    /**
     * Position sizing method.
     */
    public enum SizingMethod {
        AUTO,
        KELLY,
        HALF_KELLY,
        ATR,
        FIXED_RISK,
        VOLATILITY_TARGET
    }

    /**
     * Position sizing recommendation result.
     */
    public record PositionRecommendation(
            int recommendedShares,
            int maxAllowedShares,
            double positionValue,
            double positionPctOfPortfolio,
            SizingMethod methodUsed,
            double volatilityScale,
            double correlationScale,
            String reasoning,
            List<String> warnings,
            boolean approved
    ) {}

    /**
     * Calculate recommended position size with all risk adjustments.
     * 
     * @param request position sizing request
     * @return position recommendation with full analysis
     */
    public PositionRecommendation calculatePosition(PositionRequest request) {
        log.info("📊 Calculating position for {} at price {} with equity {}",
                request.symbol(), request.stockPrice(), request.equity());

        List<String> warnings = new ArrayList<>();
        StringBuilder reasoning = new StringBuilder();

        // Step 1: Calculate base position using preferred method
        PositionSizingService.PositionSizeResult baseResult = calculateBasePosition(request);
        int baseShares = baseResult.shares();
        reasoning.append("Base: ").append(baseResult.reasoning());

        // Step 2: Apply volatility targeting
        double volatilityScale = calculateVolatilityScale(request);
        int volAdjustedShares = (int) Math.round(baseShares * volatilityScale);
        volAdjustedShares = Math.max(1, volAdjustedShares);
        reasoning.append(" | Vol scale: ").append(String.format("%.2f", volatilityScale));

        if (volatilityScale < 0.5) {
            warnings.add("High volatility - position reduced by " + 
                    String.format("%.0f%%", (1 - volatilityScale) * 100));
        }

        // Step 3: Apply correlation-based adjustment
        double correlationScale = calculateCorrelationScale(request);
        int corrAdjustedShares = (int) Math.round(volAdjustedShares * correlationScale);
        corrAdjustedShares = Math.max(1, corrAdjustedShares);
        reasoning.append(" | Corr scale: ").append(String.format("%.2f", correlationScale));

        if (correlationScale < 1.0) {
            warnings.add("High correlation with portfolio - position reduced by " +
                    String.format("%.0f%%", (1 - correlationScale) * 100));
        }

        // Step 4: Apply concentration limits
        int maxAllowedShares = calculateMaxAllowedShares(request);
        int finalShares = Math.min(corrAdjustedShares, maxAllowedShares);

        if (finalShares < corrAdjustedShares) {
            warnings.add("Position capped due to concentration limits");
        }

        // Step 5: Check if position should be approved
        List<String> concentrationWarnings = correlationTracker.checkConcentrationLimits(
                request.symbol(),
                (finalShares * request.stockPrice()) / request.equity(),
                request.existingPositions()
        );
        warnings.addAll(concentrationWarnings);

        boolean approved = warnings.stream()
                .noneMatch(w -> w.contains("Critical") || w.contains("exceeds max"));

        // Calculate final values
        double positionValue = finalShares * request.stockPrice();
        double positionPct = positionValue / request.equity();

        log.info("📊 Position recommendation for {}: {} shares (base={}, vol={}, corr={}, max={}), approved={}",
                request.symbol(), finalShares, baseShares, volAdjustedShares, 
                corrAdjustedShares, maxAllowedShares, approved);

        return new PositionRecommendation(
                finalShares,
                maxAllowedShares,
                positionValue,
                positionPct,
                request.preferredMethod() == SizingMethod.AUTO ? 
                        determineBestMethod(request) : request.preferredMethod(),
                volatilityScale,
                correlationScale,
                reasoning.toString(),
                warnings,
                approved
        );
    }

    /**
     * Calculate base position using the appropriate method.
     */
    private PositionSizingService.PositionSizeResult calculateBasePosition(PositionRequest request) {
        SizingMethod method = request.preferredMethod() == SizingMethod.AUTO ?
                determineBestMethod(request) : request.preferredMethod();

        PositionSizingService.PositionSizeConfig config = switch (method) {
            case KELLY, HALF_KELLY -> PositionSizingService.PositionSizeConfig.forKelly(
                    request.equity(), request.stockPrice(),
                    request.winRate(), request.avgWin(), request.avgLoss()
            );
            case ATR -> PositionSizingService.PositionSizeConfig.forAtr(
                    request.equity(), request.stockPrice(),
                    request.atr(), request.riskPerTradePct()
            );
            default -> PositionSizingService.PositionSizeConfig.forFixedRisk(
                    request.equity(), request.stockPrice(), request.riskPerTradePct()
            );
        };

        return switch (method) {
            case KELLY -> positionSizingService.calculateKelly(config);
            case HALF_KELLY -> positionSizingService.calculateHalfKelly(config);
            case ATR -> positionSizingService.calculateAtrBased(config);
            case FIXED_RISK, VOLATILITY_TARGET, AUTO -> positionSizingService.calculateFixedRisk(config);
        };
    }

    /**
     * Determine the best sizing method based on available data.
     */
    private SizingMethod determineBestMethod(PositionRequest request) {
        // Prefer Half-Kelly if we have trade statistics
        if (request.winRate() > 0 && request.avgWin() > 0 && request.avgLoss() > 0) {
            return SizingMethod.HALF_KELLY;
        }

        // Use ATR-based if we have volatility data
        if (request.atr() > 0) {
            return SizingMethod.ATR;
        }

        // Default to fixed risk
        return SizingMethod.FIXED_RISK;
    }

    /**
     * Calculate volatility scaling factor.
     */
    private double calculateVolatilityScale(PositionRequest request) {
        if (request.priceHistory().isEmpty()) {
            return 1.0;
        }

        VolatilityTargetingService.VolatilityEstimate volEstimate =
                volatilityTargetingService.calculateVolatility(
                        request.priceHistory(), request.symbol()
                );

        VolatilityTargetingService.ScalingResult scaling =
                volatilityTargetingService.calculateScalingFactor(volEstimate.annualizedVol());

        return Math.max(MIN_SCALE_FACTOR, scaling.scaleFactor());
    }

    /**
     * Calculate correlation scaling factor.
     */
    private double calculateCorrelationScale(PositionRequest request) {
        if (request.existingPositions().isEmpty()) {
            return 1.0;
        }

        Set<String> existingSymbols = new HashSet<>();
        for (CorrelationTracker.PositionInfo pos : request.existingPositions()) {
            existingSymbols.add(pos.symbol());
        }

        double avgCorrelation = correlationTracker.calculateAverageCorrelationWithPortfolio(
                request.symbol(), existingSymbols
        );

        return correlationTracker.calculateCorrelationScaling(avgCorrelation);
    }

    /**
     * Calculate maximum allowed shares based on concentration limits.
     */
    private int calculateMaxAllowedShares(PositionRequest request) {
        // Max single position limit
        double maxPositionValue = request.equity() * MAX_SINGLE_POSITION_PCT;

        // Max per-trade risk limit
        double maxRiskValue = request.equity() * MAX_PER_TRADE_RISK_PCT;

        // Use the more restrictive limit
        double effectiveMax = Math.min(maxPositionValue, maxRiskValue);

        return Math.max(1, (int) (effectiveMax / request.stockPrice()));
    }

    /**
     * Quick check if a proposed trade would be approved.
     * 
     * @param symbol stock symbol
     * @param shares number of shares
     * @param stockPrice current price
     * @param equity total equity
     * @return true if trade would be approved
     */
    public boolean wouldApprove(String symbol, int shares, double stockPrice, double equity) {
        double positionValue = shares * stockPrice;
        double positionPct = positionValue / equity;

        if (positionPct > MAX_SINGLE_POSITION_PCT) {
            log.warn("Position {} exceeds max concentration: {:.1f}% > {:.1f}%",
                    symbol, positionPct * 100, MAX_SINGLE_POSITION_PCT * 100);
            return false;
        }

        if (positionPct > MAX_PER_TRADE_RISK_PCT) {
            log.warn("Position {} exceeds per-trade risk: {:.1f}% > {:.1f}%",
                    symbol, positionPct * 100, MAX_PER_TRADE_RISK_PCT * 100);
            return false;
        }

        return true;
    }

    /**
     * Get the maximum position size for a stock.
     * 
     * @param stockPrice stock price
     * @param equity total equity
     * @return maximum shares
     */
    public int getMaxPositionSize(double stockPrice, double equity) {
        double maxValue = Math.min(
                equity * MAX_SINGLE_POSITION_PCT,
                equity * MAX_PER_TRADE_RISK_PCT
        );
        return Math.max(1, (int) (maxValue / stockPrice));
    }

    /**
     * Calculate position size using simple fixed risk (convenience method).
     * 
     * @param equity total equity
     * @param stockPrice stock price
     * @param riskPct risk percentage per trade
     * @return number of shares
     */
    public int calculateSimplePosition(double equity, double stockPrice, double riskPct) {
        PositionRequest request = PositionRequest.builder()
                .symbol("SIMPLE")
                .equity(equity)
                .stockPrice(stockPrice)
                .riskPerTradePct(riskPct)
                .preferredMethod(SizingMethod.FIXED_RISK)
                .build();

        return calculatePosition(request).recommendedShares();
    }
}
