package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.IStrategy;
import tw.gc.mtxfbot.strategy.Portfolio;
import tw.gc.mtxfbot.strategy.StrategyType;
import tw.gc.mtxfbot.strategy.TradeSignal;

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
        
        log.trace("[Bollinger Band] price={}, middle={}, upper={}, lower={}, bandwidth={}%",
                currentPrice, middle, upper, lower, bandwidth * 100);
        
        int currentPosition = portfolio.getPosition(symbol);
        double distanceToMiddle = Math.abs(currentPrice - middle) / middle;
        
        // Check for oversold condition (price below lower band)
        if (currentPrice <= lower && currentPosition == 0) {
            double confidence = 0.70 + Math.min((lower - currentPrice) / lower * 10, 0.25);
            log.info("[Bollinger Band] OVERSOLD: BUY signal (price={}, lower={}, distance={}%)",
                    currentPrice, lower, ((lower - currentPrice) / lower) * 100);
            return TradeSignal.longSignal(confidence,
                    String.format("Oversold: price below lower band (%.2f vs %.2f)", currentPrice, lower));
        }
        
        // Check for overbought condition (price above upper band)
        if (currentPrice >= upper && currentPosition == 0) {
            double confidence = 0.70 + Math.min((currentPrice - upper) / upper * 10, 0.25);
            log.info("[Bollinger Band] OVERBOUGHT: SHORT signal (price={}, upper={}, distance={}%)",
                    currentPrice, upper, ((currentPrice - upper) / upper) * 100);
            return TradeSignal.shortSignal(confidence,
                    String.format("Overbought: price above upper band (%.2f vs %.2f)", currentPrice, upper));
        }
        
        // Check for mean reversion exit (price returning to middle)
        if (currentPosition != 0 && distanceToMiddle <= reversionThreshold) {
            log.info("[Bollinger Band] MEAN REVERSION: EXIT signal (price={}, middle={}, distance={}%)",
                    currentPrice, middle, distanceToMiddle * 100);
            return TradeSignal.exitSignal(
                    currentPosition > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                    0.75,
                    String.format("Mean reversion: price returned to middle band (%.2f)", currentPrice));
        }
        
        // Check for stop-loss if price moves further against position
        if (currentPosition > 0 && currentPrice >= upper) {
            log.info("[Bollinger Band] STOP-LOSS: EXIT long (price hit upper band: {})", currentPrice);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.80,
                    "Stop-loss: price hit opposite band");
        }
        
        if (currentPosition < 0 && currentPrice <= lower) {
            log.info("[Bollinger Band] STOP-LOSS: EXIT short (price hit lower band: {})", currentPrice);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.80,
                    "Stop-loss: price hit opposite band");
        }
        
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
