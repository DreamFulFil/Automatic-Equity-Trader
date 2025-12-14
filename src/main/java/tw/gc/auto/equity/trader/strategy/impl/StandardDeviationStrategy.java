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

@Slf4j
public class StandardDeviationStrategy implements IStrategy {
    
    private final int period;
    private final double threshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public StandardDeviationStrategy() {
        this(20, 1.5);
    }
    
    public StandardDeviationStrategy(int period, double threshold) {
        this.period = period;
        this.threshold = threshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period) prices.removeFirst();
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up StdDev");
        }
        
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = prices.stream()
            .mapToDouble(p -> Math.pow(p - mean, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        double currentPrice = data.getClose();
        double zScore = stdDev > 0 ? (currentPrice - mean) / stdDev : 0;
        
        int position = portfolio.getPosition(symbol);
        
        if (zScore < -threshold && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Price %+.1f std devs below mean", zScore));
        } else if (zScore > threshold && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Price %+.1f std devs above mean", zScore));
        } else if (Math.abs(zScore) < 0.5 && position != 0) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.7, "Price returned to mean");
        }
        
        return TradeSignal.neutral(String.format("Z-Score=%.1f", zScore));
    }

    @Override
    public String getName() {
        return "Standard Deviation Mean Reversion";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
