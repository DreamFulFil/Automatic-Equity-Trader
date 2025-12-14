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
public class LinearRegressionStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public LinearRegressionStrategy() {
        this(14);
    }
    
    public LinearRegressionStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period) prices.removeFirst();
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up Linear Regression");
        }
        
        Double[] p = prices.toArray(new Double[0]);
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < p.length; i++) {
            sumX += i;
            sumY += p[i];
            sumXY += i * p[i];
            sumX2 += i * i;
        }
        
        int n = p.length;
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;
        double predictedPrice = slope * (n - 1) + intercept;
        
        double currentPrice = data.getClose();
        double deviation = (currentPrice - predictedPrice) / predictedPrice;
        
        int position = portfolio.getPosition(symbol);
        
        if (slope > 0 && deviation < -0.02 && position <= 0) {
            return TradeSignal.longSignal(0.75, "Price below uptrend line");
        } else if (slope < 0 && deviation > 0.02 && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Price above downtrend line");
        } else if (Math.abs(deviation) < 0.005 && position != 0) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.7, "Price at regression line");
        }
        
        return TradeSignal.neutral(String.format("Deviation=%.2f%%", deviation * 100));
    }

    @Override
    public String getName() {
        return "Linear Regression Channel";
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
