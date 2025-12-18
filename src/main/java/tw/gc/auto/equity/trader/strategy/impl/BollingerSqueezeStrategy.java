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
 * Bollinger Squeeze Strategy
 * Type: Volatility Breakout
 * 
 * Academic Foundation:
 * - Bollinger (2002): "Bollinger on Bollinger Bands"
 * - Trading concept: Low volatility periods followed by high volatility
 * 
 * Logic:
 * - Bollinger Bands contract (squeeze) during low volatility
 * - Breakout above upper band = bullish
 * - Breakout below lower band = bearish
 * - Wait for squeeze, then trade the breakout direction
 * 
 * When to use:
 * - Before major news/events
 * - Consolidation periods
 * - Expecting volatility expansion
 */
@Slf4j
public class BollingerSqueezeStrategy implements IStrategy {
    
    private final int period;
    private final double stdDevMultiplier;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Boolean> squeezDetected = new HashMap<>();
    
    public BollingerSqueezeStrategy(int period, double stdDevMultiplier) {
        this.period = period;
        this.stdDevMultiplier = stdDevMultiplier;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(data.getClose());
        
        if (prices.size() > period * 2) {
            prices.removeFirst();
        }
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up");
        }
        
        Double[] p = prices.toArray(new Double[0]);
        double mean = 0;
        for (int i = p.length - period; i < p.length; i++) {
            mean += p[i];
        }
        mean /= period;
        
        double variance = 0;
        for (int i = p.length - period; i < p.length; i++) {
            variance += Math.pow(p[i] - mean, 2);
        }
        variance /= period;
        double stdDev = Math.sqrt(variance);
        
        double upperBand = mean + (stdDevMultiplier * stdDev);
        double lowerBand = mean - (stdDevMultiplier * stdDev);
        double bandWidth = (upperBand - lowerBand) / mean;
        
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        // Detect squeeze (bands narrowing)
        boolean isSqueezing = bandWidth < 0.04; // 4% bandwidth threshold
        
        if (isSqueezing) {
            squeezDetected.put(symbol, true);
            return TradeSignal.neutral(String.format("Squeeze detected (bandwidth %.2f%%)", bandWidth * 100));
        }
        
        // Breakout after squeeze
        if (Boolean.TRUE.equals(squeezDetected.get(symbol))) {
            if (currentPrice > upperBand && position <= 0) {
                squeezDetected.put(symbol, false);
                return TradeSignal.longSignal(0.8, "Bollinger squeeze breakout (bullish)");
            } else if (currentPrice < lowerBand && position >= 0) {
                squeezDetected.put(symbol, false);
                return TradeSignal.shortSignal(0.8, "Bollinger squeeze breakout (bearish)");
            }
        }
        
        // Exit on reversion to mean
        if (position > 0 && currentPrice < mean) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.7, "Price reverted below mean");
        } else if (position < 0 && currentPrice > mean) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.7, "Price reverted above mean");
        }
        
        return TradeSignal.neutral(String.format("Bandwidth %.2f%%", bandWidth * 100));
    }

    @Override
    public String getName() {
        return String.format("Bollinger Squeeze (%d, %.1fσ)", period, stdDevMultiplier);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        squeezDetected.clear();
    }
}
