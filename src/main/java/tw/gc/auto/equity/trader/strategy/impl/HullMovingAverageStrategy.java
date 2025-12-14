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
public class HullMovingAverageStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Double> previousHMA = new HashMap<>();
    
    public HullMovingAverageStrategy() {
        this(20);
    }
    
    public HullMovingAverageStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period * 2) prices.removeFirst();
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up HMA");
        }
        
        double wma1 = calculateWMA(prices, period / 2);
        double wma2 = calculateWMA(prices, period);
        double rawHMA = 2 * wma1 - wma2;
        
        Double prevHMA = previousHMA.get(symbol);
        previousHMA.put(symbol, rawHMA);
        
        if (prevHMA == null) return TradeSignal.neutral("Initializing HMA");
        
        int position = portfolio.getPosition(symbol);
        double currentPrice = data.getClose();
        
        if (currentPrice > rawHMA && prevHMA < rawHMA && position <= 0) {
            return TradeSignal.longSignal(0.75, "HMA bullish crossover");
        } else if (currentPrice < rawHMA && prevHMA > rawHMA && position >= 0) {
            return TradeSignal.shortSignal(0.75, "HMA bearish crossover");
        }
        
        return TradeSignal.neutral("HMA no crossover");
    }
    
    private double calculateWMA(Deque<Double> prices, int period) {
        Double[] p = prices.toArray(new Double[0]);
        int start = Math.max(0, p.length - period);
        double sum = 0, weightSum = 0;
        for (int i = start; i < p.length; i++) {
            int weight = i - start + 1;
            sum += p[i] * weight;
            weightSum += weight;
        }
        return weightSum > 0 ? sum / weightSum : 0;
    }

    @Override
    public String getName() {
        return "Hull Moving Average";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        previousHMA.clear();
    }
}
