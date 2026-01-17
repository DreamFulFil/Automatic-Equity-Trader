package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Bollinger Band Mean Reversion Strategy
 * 
 * Statistical arbitrage strategy based on Bollinger Bands.
 * Price touching or exceeding bands indicates overbought/oversold conditions.
 * 
 * Strategy Logic:
 * 1. Calculate middle band (SMA), upper band (SMA + 2*stddev), lower band (SMA - 2*stddev)
 * 2. Buy when price touches/breaks below lower band (oversold)
 * 3. Sell when price touches/breaks above upper band (overbought)
 * 4. Exit when price returns to middle band (mean reversion complete)
 * 
 * Configuration:
 * - period: Moving average period (default: 20)
 * - stddevMultiplier: Standard deviation multiplier for bands (default: 2.0)
 * - reversionThreshold: How close to middle band before exit (default: 0.002 = 0.2%)
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
@Slf4j
public class BollingerBandStrategy implements IStrategy {
    
    private final int period;
    private final double stddevMultiplier;
    private final double reversionThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private static final double EPSILON = 1e-4; // Increased tolerance for floating-point comparisons
    
    public BollingerBandStrategy() {
        this(20, 2.0, 0.002);
    }
    
    public BollingerBandStrategy(int period, double stddevMultiplier, double reversionThreshold) {
        this.period = period;
        this.stddevMultiplier = stddevMultiplier;
        this.reversionThreshold = reversionThreshold;
        log.info("[Bollinger Band] Strategy initialized: period={}, stddev={}, reversionThreshold={}%",
                period, stddevMultiplier, reversionThreshold * 100);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            log.trace("[Bollinger Band] No market data available");
            return TradeSignal.neutral("No market data");
        }

        String symbol = data.getSymbol();
        double currentPrice = data.getClose();

        // Update price history
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(currentPrice);
        if (prices.size() > period) {
            prices.removeFirst();
        }

        // Need enough data
        if (prices.size() < period) {
            log.trace("[Bollinger Band] Insufficient data: {} / {} required", prices.size(), period);
            return TradeSignal.neutral(String.format("Warming up (%d/%d)", prices.size(), period));
        }

        // Calculate Bollinger Bands
        double middle = calculateSMA(prices);
        double stddev = calculateStdDev(prices, middle);
        double upper = middle + (stddevMultiplier * stddev);
        double lower = middle - (stddevMultiplier * stddev);
        double bandwidth = (upper - lower) / middle;

        log.trace("[Bollinger Band] price={}, middle={}, upper={}, lower={}, bandwidth=%{}", currentPrice, middle, upper, lower, bandwidth * 100);
        log.debug("[Bollinger Band Debug] Current Price: {}", currentPrice);
        log.debug("[Bollinger Band Debug] Middle Band: {}", middle);
        log.debug("[Bollinger Band Debug] Upper Band: {}", upper);
        log.debug("[Bollinger Band Debug] Lower Band: {}", lower);

        int currentPosition = portfolio.getPosition(symbol);
        double distanceToMiddle = Math.abs(currentPrice - middle) / middle;

        // Use a relative tolerance for band comparisons to avoid noise-driven signals
        double bandToleranceFraction = 0.002; // 0.2% by default
        double bandTolerance = Math.max(EPSILON, bandToleranceFraction * Math.abs(middle));

        log.debug("[Bollinger Band Debug] Evaluating with bandTolerance={} (fraction={})", bandTolerance, bandToleranceFraction);

        // If there is an open position, prioritize exit / stop-loss logic
        if (currentPosition != 0) {
            log.debug("[Bollinger Band Debug] In position: {}, distanceToMiddle={}, reversionThreshold={}", currentPosition, distanceToMiddle, reversionThreshold);
            if (distanceToMiddle <= reversionThreshold + EPSILON) {
                log.info("[Bollinger Band] MEAN REVERSION: EXIT signal (price={}, middle={}, distance={}%)",
                        currentPrice, middle, distanceToMiddle * 100);
                return TradeSignal.exitSignal(
                        currentPosition > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                        0.75,
                        String.format("Mean reversion: price returned to middle band (%.2f)", currentPrice));
            }

            if (currentPosition > 0 && currentPrice >= upper - bandTolerance) {
                log.info("[Bollinger Band] STOP-LOSS: EXIT long (price hit upper band: {})", currentPrice);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.80, "Stop-loss: price hit opposite band");
            }

            if (currentPosition < 0 && currentPrice <= lower + bandTolerance) {
                log.info("[Bollinger Band] STOP-LOSS: EXIT short (price hit lower band: {})", currentPrice);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.80, "Stop-loss: price hit opposite band");
            }

            // No actionable exit/stop-loss, remain neutral while holding the position
            log.debug("[Bollinger Band Debug] Position open and no exit/stop condition met");
            return TradeSignal.neutral("Position open, no exit condition met");
        }

        // No position: check for oversold (buy) or overbought (short) with relative tolerance
        if (currentPrice < lower - bandTolerance && currentPosition == 0) {
            double confidence = 0.70 + Math.min((lower - currentPrice) / lower * 10, 0.25);
            log.info("[Bollinger Band] OVERSOLD: BUY signal (price={}, lower={}, distance={}%)",
                    currentPrice, lower, ((lower - currentPrice) / lower) * 100);
            return TradeSignal.longSignal(confidence,
                    String.format("Oversold: price below lower band (%.2f vs %.2f)", currentPrice, lower));
        }

        if (currentPrice > upper + bandTolerance && currentPosition == 0) {
            double confidence = 0.70 + Math.min((currentPrice - upper) / upper * 10, 0.25);
            log.info("[Bollinger Band] OVERBOUGHT: SHORT signal (price={}, upper={}, distance={}%)",
                    currentPrice, upper, ((currentPrice - upper) / upper) * 100);
            return TradeSignal.shortSignal(confidence,
                    String.format("Overbought: price above upper band (%.2f vs %.2f)", currentPrice, upper));
        }

        // Otherwise, price is within bands (considering tolerance)
        log.debug("[Bollinger Band] NEUTRAL: Price within bands (price={}, upper={}, lower={}, bandTolerance={})",
                currentPrice, upper, lower, bandTolerance);
        return TradeSignal.neutral("Price within bands, no action");
    }
    
    private double calculateSMA(Deque<Double> prices) {
        return prices.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }
    
    private double calculateStdDev(Deque<Double> prices, double mean) {
        double variance = prices.stream()
                .mapToDouble(p -> Math.pow(p - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
    
    @Override
    public String getName() {
        return "Bollinger Band Mean Reversion";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }
    
    @Override
    public void reset() {
        log.info("[Bollinger Band] Resetting strategy state");
        priceHistory.clear();
    }
    
    // Package-private for testing
    Deque<Double> getPriceHistory(String symbol) {
        return priceHistory.get(symbol);
    }
}
