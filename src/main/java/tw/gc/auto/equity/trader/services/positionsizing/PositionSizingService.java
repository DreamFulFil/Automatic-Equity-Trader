package tw.gc.auto.equity.trader.services.positionsizing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.util.List;

/**
 * Position Sizing Service implementing multiple risk-adjusted sizing algorithms.
 * 
 * <p>Provides intelligent position sizing based on:
 * <ul>
 *   <li><b>Kelly Criterion:</b> Optimal growth sizing based on win rate and payoff ratio</li>
 *   <li><b>ATR-based:</b> Volatility-adjusted sizing using Average True Range</li>
 *   <li><b>Risk Parity:</b> Fixed risk per trade (% of equity)</li>
 * </ul>
 * 
 * <p>All methods respect the hard cap of 10% equity per position.
 * 
 * @see VolatilityTargetingService for portfolio-level volatility targeting
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositionSizingService {

    /**
     * Maximum position size as percentage of equity (hard cap)
     */
    private static final double MAX_POSITION_PCT = 0.10;

    /**
     * Minimum position size (in shares) to avoid tiny positions
     */
    private static final int MIN_SHARES = 1;

    /**
     * Default ATR multiplier for stop-loss calculation
     */
    private static final double DEFAULT_ATR_MULTIPLIER = 2.0;

    /**
     * Configuration record for position sizing parameters.
     */
    public record PositionSizeConfig(
            double equity,
            double stockPrice,
            double winRate,
            double avgWin,
            double avgLoss,
            double atr,
            double riskPerTradePct,
            Double customMaxPositionPct
    ) {
        /**
         * Create config for ATR-based sizing.
         */
        public static PositionSizeConfig forAtr(double equity, double stockPrice, double atr, double riskPerTradePct) {
            return new PositionSizeConfig(equity, stockPrice, 0, 0, 0, atr, riskPerTradePct, null);
        }

        /**
         * Create config for Kelly sizing.
         */
        public static PositionSizeConfig forKelly(double equity, double stockPrice, double winRate, double avgWin, double avgLoss) {
            return new PositionSizeConfig(equity, stockPrice, winRate, avgWin, avgLoss, 0, 0, null);
        }

        /**
         * Create config for fixed risk sizing.
         */
        public static PositionSizeConfig forFixedRisk(double equity, double stockPrice, double riskPerTradePct) {
            return new PositionSizeConfig(equity, stockPrice, 0, 0, 0, 0, riskPerTradePct, null);
        }
    }

    /**
     * Result record containing position size and reasoning.
     */
    public record PositionSizeResult(
            int shares,
            double positionValue,
            double positionPct,
            String method,
            String reasoning
    ) {
        public static PositionSizeResult of(int shares, double stockPrice, double equity, String method, String reasoning) {
            double positionValue = shares * stockPrice;
            double positionPct = equity > 0 ? positionValue / equity : 0;
            return new PositionSizeResult(shares, positionValue, positionPct, method, reasoning);
        }
    }

    /**
     * Calculate position size using Kelly Criterion.
     * 
     * <p>Formula: f* = (bp - q) / b
     * where:
     * <ul>
     *   <li>b = odds (avgWin / avgLoss)</li>
     *   <li>p = win probability</li>
     *   <li>q = loss probability (1 - p)</li>
     * </ul>
     * 
     * @param config position sizing configuration
     * @return position size result
     */
    public PositionSizeResult calculateKelly(PositionSizeConfig config) {
        validateConfig(config);

        if (config.avgLoss() <= 0) {
            log.warn("Invalid avgLoss {} for Kelly, returning minimum position", config.avgLoss());
            return PositionSizeResult.of(MIN_SHARES, config.stockPrice(), config.equity(), 
                    "KELLY", "Invalid avgLoss, using minimum");
        }

        // b = odds ratio (how much you win per dollar risked)
        double b = config.avgWin() / config.avgLoss();
        double p = config.winRate();
        double q = 1.0 - p;

        // Kelly formula: f* = (bp - q) / b
        double kellyFraction = (b * p - q) / b;

        // Clamp to [0, MAX_POSITION_PCT]
        double maxPct = config.customMaxPositionPct() != null ? config.customMaxPositionPct() : MAX_POSITION_PCT;
        kellyFraction = Math.max(0, Math.min(kellyFraction, maxPct));

        double positionValue = config.equity() * kellyFraction;
        int shares = (int) (positionValue / config.stockPrice());
        shares = Math.max(MIN_SHARES, shares);

        String reasoning = String.format(
                "Kelly f*=%.2f%% (b=%.2f, p=%.2f%%, q=%.2f%%)",
                kellyFraction * 100, b, p * 100, q * 100
        );

        log.info("📊 Kelly position: {} shares (f*={:.2f}%, equity={}, price={})",
                shares, kellyFraction * 100, config.equity(), config.stockPrice());

        return PositionSizeResult.of(shares, config.stockPrice(), config.equity(), "KELLY", reasoning);
    }

    /**
     * Calculate position size using Half-Kelly for conservative sizing.
     * 
     * <p>Uses half of the Kelly fraction to reduce volatility while
     * still capturing most of the growth benefit.
     * 
     * @param config position sizing configuration
     * @return position size result
     */
    public PositionSizeResult calculateHalfKelly(PositionSizeConfig config) {
        PositionSizeResult fullKelly = calculateKelly(config);
        
        int halfKellyShares = Math.max(MIN_SHARES, fullKelly.shares() / 2);
        String reasoning = "Half-Kelly: " + fullKelly.reasoning();

        log.info("📊 Half-Kelly position: {} shares (half of {})", halfKellyShares, fullKelly.shares());

        return PositionSizeResult.of(halfKellyShares, config.stockPrice(), config.equity(), "HALF_KELLY", reasoning);
    }

    /**
     * Calculate position size using ATR-based method.
     * 
     * <p>Formula: shares = risk_per_trade / (ATR * multiplier)
     * 
     * <p>This method sizes positions based on volatility, taking smaller
     * positions in volatile stocks and larger in stable ones.
     * 
     * @param config position sizing configuration
     * @return position size result
     */
    public PositionSizeResult calculateAtrBased(PositionSizeConfig config) {
        return calculateAtrBased(config, DEFAULT_ATR_MULTIPLIER);
    }

    /**
     * Calculate position size using ATR-based method with custom multiplier.
     * 
     * @param config position sizing configuration
     * @param atrMultiplier multiplier for ATR (default 2.0)
     * @return position size result
     */
    public PositionSizeResult calculateAtrBased(PositionSizeConfig config, double atrMultiplier) {
        validateConfig(config);

        if (config.atr() <= 0) {
            log.warn("Invalid ATR {} for ATR-based sizing, returning minimum position", config.atr());
            return PositionSizeResult.of(MIN_SHARES, config.stockPrice(), config.equity(),
                    "ATR", "Invalid ATR, using minimum");
        }

        // Risk per trade in dollars
        double riskPct = config.riskPerTradePct() > 0 ? config.riskPerTradePct() / 100.0 : 0.02; // Default 2%
        double riskPerTrade = config.equity() * riskPct;

        // Stop loss distance based on ATR
        double stopDistance = config.atr() * atrMultiplier;

        // Calculate shares based on risk and stop distance
        int shares = (int) (riskPerTrade / stopDistance);
        shares = Math.max(MIN_SHARES, shares);

        // Apply max position constraint
        double maxPct = config.customMaxPositionPct() != null ? config.customMaxPositionPct() : MAX_POSITION_PCT;
        double maxShares = (config.equity() * maxPct) / config.stockPrice();
        shares = Math.min(shares, (int) maxShares);

        String reasoning = String.format(
                "ATR-based: risk=%.0f TWD (%.1f%%), ATR=%.2f, stop=%.2f, multiplier=%.1f",
                riskPerTrade, riskPct * 100, config.atr(), stopDistance, atrMultiplier
        );

        log.info("📊 ATR position: {} shares (risk={:.0f}, ATR={:.2f}, multiplier={:.1f})",
                shares, riskPerTrade, config.atr(), atrMultiplier);

        return PositionSizeResult.of(shares, config.stockPrice(), config.equity(), "ATR", reasoning);
    }

    /**
     * Calculate position size using fixed risk per trade.
     * 
     * <p>Simple method: position = equity * riskPct / stockPrice
     * 
     * @param config position sizing configuration
     * @return position size result
     */
    public PositionSizeResult calculateFixedRisk(PositionSizeConfig config) {
        validateConfig(config);

        double riskPct = config.riskPerTradePct() > 0 ? config.riskPerTradePct() / 100.0 : 0.02;
        double positionValue = config.equity() * riskPct;

        // Apply max position constraint
        double maxPct = config.customMaxPositionPct() != null ? config.customMaxPositionPct() : MAX_POSITION_PCT;
        double maxValue = config.equity() * maxPct;
        positionValue = Math.min(positionValue, maxValue);

        int shares = (int) (positionValue / config.stockPrice());
        shares = Math.max(MIN_SHARES, shares);

        String reasoning = String.format("Fixed risk: %.1f%% of equity", riskPct * 100);

        log.info("📊 Fixed risk position: {} shares (risk={:.1f}%, value={:.0f})",
                shares, riskPct * 100, positionValue);

        return PositionSizeResult.of(shares, config.stockPrice(), config.equity(), "FIXED_RISK", reasoning);
    }

    /**
     * Calculate ATR from market data history.
     * 
     * @param history list of market data (newest last)
     * @param period number of periods for ATR calculation
     * @return ATR value
     */
    public double calculateAtr(List<MarketData> history, int period) {
        if (history == null || history.size() < period + 1) {
            return 0.0;
        }

        double sumTR = 0.0;
        int startIdx = history.size() - period;

        for (int i = startIdx; i < history.size(); i++) {
            MarketData current = history.get(i);
            MarketData previous = history.get(i - 1);

            double high = current.getHigh() > 0 ? current.getHigh() : current.getClose();
            double low = current.getLow() > 0 ? current.getLow() : current.getClose();
            double prevClose = previous.getClose();

            double tr1 = high - low;
            double tr2 = Math.abs(high - prevClose);
            double tr3 = Math.abs(low - prevClose);

            sumTR += Math.max(tr1, Math.max(tr2, tr3));
        }

        return sumTR / period;
    }

    /**
     * Recommend the best position sizing method based on available data.
     * 
     * @param config position sizing configuration
     * @return recommended position size result
     */
    public PositionSizeResult recommend(PositionSizeConfig config) {
        validateConfig(config);

        // If we have Kelly inputs (win rate, avg win/loss), use Half-Kelly
        if (config.winRate() > 0 && config.avgWin() > 0 && config.avgLoss() > 0) {
            log.info("📊 Using Half-Kelly (trade statistics available)");
            return calculateHalfKelly(config);
        }

        // If we have ATR, use ATR-based
        if (config.atr() > 0) {
            log.info("📊 Using ATR-based sizing (volatility data available)");
            return calculateAtrBased(config);
        }

        // Default to fixed risk
        log.info("📊 Using fixed risk sizing (limited data)");
        return calculateFixedRisk(config);
    }

    /**
     * Validate position sizing configuration.
     */
    private void validateConfig(PositionSizeConfig config) {
        if (config.equity() <= 0) {
            throw new IllegalArgumentException("Equity must be positive");
        }
        if (config.stockPrice() <= 0) {
            throw new IllegalArgumentException("Stock price must be positive");
        }
    }

    /**
     * Check if position exceeds maximum allowed percentage.
     * 
     * @param positionValue current position value
     * @param equity total equity
     * @return true if position exceeds limit
     */
    public boolean exceedsMaxPosition(double positionValue, double equity) {
        return positionValue / equity > MAX_POSITION_PCT;
    }

    /**
     * Get the maximum position size in shares for given parameters.
     * 
     * @param equity total equity
     * @param stockPrice stock price
     * @return maximum shares allowed
     */
    public int getMaxShares(double equity, double stockPrice) {
        return (int) ((equity * MAX_POSITION_PCT) / stockPrice);
    }
}
