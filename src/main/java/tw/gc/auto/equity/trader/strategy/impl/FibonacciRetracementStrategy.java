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
 * Fibonacci Retracement Strategy
 * Type: Support/Resistance
 * 
 * Logic:
 * - Buy near 38.2%, 50%, or 61.8% retracement levels
 * - Sell when price reaches swing high
 */
@Slf4j
public class FibonacciRetracementStrategy implements IStrategy {
    
    private final int lookbackPeriod;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    
    public FibonacciRetracementStrategy() {
        this(50);
    }
    
    public FibonacciRetracementStrategy(int lookbackPeriod) {
        this.lookbackPeriod = lookbackPeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        
        if (highs.size() > lookbackPeriod) {
            highs.removeFirst();
            lows.removeFirst();
        }
        
        if (highs.size() < lookbackPeriod) {
            return TradeSignal.neutral("Warming up Fibonacci");
        }
        
        double swingHigh = highs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double swingLow = lows.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double range = swingHigh - swingLow;
        
        double fib382 = swingHigh - (range * 0.382);
        double fib500 = swingHigh - (range * 0.500);
        double fib618 = swingHigh - (range * 0.618);
        
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        double tolerance = range * 0.02;
        
        if (Math.abs(currentPrice - fib618) < tolerance && position <= 0) {
            return TradeSignal.longSignal(0.8, "Price at 61.8% Fibonacci level");
        } else if (Math.abs(currentPrice - fib500) < tolerance && position <= 0) {
            return TradeSignal.longSignal(0.75, "Price at 50% Fibonacci level");
        } else if (Math.abs(currentPrice - fib382) < tolerance && position <= 0) {
            return TradeSignal.longSignal(0.7, "Price at 38.2% Fibonacci level");
        } else if (currentPrice >= swingHigh * 0.98 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.8, "Price reached swing high");
        }
        
        return TradeSignal.neutral("Not at Fibonacci level");
    }

    @Override
    public String getName() {
        return "Fibonacci Retracement";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
    }
}
