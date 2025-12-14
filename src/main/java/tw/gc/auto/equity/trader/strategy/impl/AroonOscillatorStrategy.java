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
public class AroonOscillatorStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    
    public AroonOscillatorStrategy() {
        this(25);
    }
    
    public AroonOscillatorStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        
        if (highs.size() > period) {
            highs.removeFirst();
            lows.removeFirst();
        }
        
        if (highs.size() < period) {
            return TradeSignal.neutral("Warming up Aroon");
        }
        
        Double[] h = highs.toArray(new Double[0]);
        Double[] l = lows.toArray(new Double[0]);
        
        int highIdx = 0, lowIdx = 0;
        double maxHigh = h[0], minLow = l[0];
        for (int i = 1; i < h.length; i++) {
            if (h[i] >= maxHigh) { maxHigh = h[i]; highIdx = i; }
            if (l[i] <= minLow) { minLow = l[i]; lowIdx = i; }
        }
        
        double aroonUp = ((double)(period - (period - 1 - highIdx)) / period) * 100;
        double aroonDown = ((double)(period - (period - 1 - lowIdx)) / period) * 100;
        double aroonOsc = aroonUp - aroonDown;
        
        int position = portfolio.getPosition(symbol);
        
        if (aroonOsc > 50 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Aroon bullish (%.1f)", aroonOsc));
        } else if (aroonOsc < -50 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Aroon bearish (%.1f)", aroonOsc));
        }
        
        return TradeSignal.neutral(String.format("Aroon=%.1f", aroonOsc));
    }

    @Override
    public String getName() {
        return "Aroon Oscillator";
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
