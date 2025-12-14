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
 * Triple EMA Crossover Strategy
 * Type: Trend Following
 * 
 * Logic:
 * - Buy when fast EMA > mid EMA > slow EMA (aligned uptrend)
 * - Sell when fast EMA < mid EMA < slow EMA (aligned downtrend)
 */
@Slf4j
public class TripleEMAStrategy implements IStrategy {
    
    private final int fastPeriod;
    private final int midPeriod;
    private final int slowPeriod;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public TripleEMAStrategy() {
        this(5, 13, 26);
    }
    
    public TripleEMAStrategy(int fastPeriod, int midPeriod, int slowPeriod) {
        this.fastPeriod = fastPeriod;
        this.midPeriod = midPeriod;
        this.slowPeriod = slowPeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > slowPeriod * 2) prices.removeFirst();
        
        if (prices.size() < slowPeriod) {
            return TradeSignal.neutral("Warming up Triple EMA");
        }
        
        double fastEMA = calculateEMA(prices, fastPeriod);
        double midEMA = calculateEMA(prices, midPeriod);
        double slowEMA = calculateEMA(prices, slowPeriod);
        
        int position = portfolio.getPosition(symbol);
        
        if (fastEMA > midEMA && midEMA > slowEMA && position <= 0) {
            return TradeSignal.longSignal(0.8, "Triple EMA aligned bullish");
        } else if (fastEMA < midEMA && midEMA < slowEMA && position >= 0) {
            return TradeSignal.shortSignal(0.8, "Triple EMA aligned bearish");
        }
        
        return TradeSignal.neutral("EMAs not aligned");
    }
    
    private double calculateEMA(Deque<Double> prices, int period) {
        Double[] p = prices.toArray(new Double[0]);
        double k = 2.0 / (period + 1);
        double ema = p[0];
        for (int i = 1; i < p.length; i++) {
            ema = (p[i] * k) + (ema * (1 - k));
        }
        return ema;
    }

    @Override
    public String getName() {
        return "Triple EMA Crossover";
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
