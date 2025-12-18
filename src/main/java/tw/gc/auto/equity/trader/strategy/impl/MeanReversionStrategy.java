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
 * Mean Reversion Strategy
 * Type: Statistical Arbitrage / Mean Reversion
 * 
 * Academic Foundation:
 * - Fama & French (1988): "Permanent and Temporary Components of Stock Prices"
 * - Poterba & Summers (1988): "Mean Reversion in Stock Prices"
 * 
 * Logic:
 * - Calculate rolling mean and standard deviation
 * - Buy when price deviates significantly below mean (oversold)
 * - Sell when price deviates significantly above mean (overbought)
 * - Based on principle that prices revert to their historical average
 * 
 * When to use:
 * - Sideways/ranging markets
 * - Highly liquid, mean-reverting assets
 * - Low volatility environments
 * 
 * Risk: Fails in strong trending markets (can keep losing as price trends away)
 */
@Slf4j
public class MeanReversionStrategy implements IStrategy {
    
    private final int lookbackPeriod;
    private final double entryThreshold;
    private final double exitThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public MeanReversionStrategy(int lookbackPeriod, double entryThreshold, double exitThreshold) {
        this.lookbackPeriod = lookbackPeriod;
        this.entryThreshold = entryThreshold;
        this.exitThreshold = exitThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(data.getClose());
        
        if (prices.size() > lookbackPeriod) {
            prices.removeFirst();
        }
        
        if (prices.size() < lookbackPeriod) {
            return TradeSignal.neutral("Warming up - need " + lookbackPeriod + " prices");
        }
        
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = prices.stream().mapToDouble(p -> Math.pow(p - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        double currentPrice = data.getClose();
        double zScore = (currentPrice - mean) / stdDev;
        
        int position = portfolio.getPosition(symbol);
        
        // Entry logic
        if (zScore < -entryThreshold && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Mean reversion: z-score %.2f (%.2f std dev below mean)", zScore, Math.abs(zScore)));
        } else if (zScore > entryThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Mean reversion: z-score %.2f (%.2f std dev above mean)", zScore, zScore));
        }
        
        // Exit logic
        if (position != 0 && Math.abs(zScore) < exitThreshold) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.7,
                String.format("Mean reversion exit: z-score %.2f (returned to mean)", zScore)
            );
        }
        
        return TradeSignal.neutral(String.format("z-score %.2f", zScore));
    }

    @Override
    public String getName() {
        return String.format("Mean Reversion (%d, %.1fσ)", lookbackPeriod, entryThreshold);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
