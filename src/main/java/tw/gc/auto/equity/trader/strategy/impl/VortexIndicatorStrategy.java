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
public class VortexIndicatorStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    private final Map<String, Deque<Double>> closeHistory = new HashMap<>();
    
    public VortexIndicatorStrategy() {
        this(14);
    }
    
    public VortexIndicatorStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> closes = closeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        closes.addLast(data.getClose());
        
        if (highs.size() > period + 1) {
            highs.removeFirst();
            lows.removeFirst();
            closes.removeFirst();
        }
        
        if (highs.size() < period + 1) {
            return TradeSignal.neutral("Warming up Vortex");
        }
        
        Double[] h = highs.toArray(new Double[0]);
        Double[] l = lows.toArray(new Double[0]);
        Double[] c = closes.toArray(new Double[0]);
        
        double sumVMPlus = 0, sumVMMinus = 0, sumTR = 0;
        for (int i = 1; i < h.length; i++) {
            sumVMPlus += Math.abs(h[i] - l[i-1]);
            sumVMMinus += Math.abs(l[i] - h[i-1]);
            double tr = Math.max(h[i] - l[i], Math.max(Math.abs(h[i] - c[i-1]), Math.abs(l[i] - c[i-1])));
            sumTR += tr;
        }
        
        double viPlus = sumTR > 0 ? sumVMPlus / sumTR : 0;
        double viMinus = sumTR > 0 ? sumVMMinus / sumTR : 0;
        
        int position = portfolio.getPosition(symbol);
        
        if (viPlus > viMinus && viPlus > 1.0 && position <= 0) {
            return TradeSignal.longSignal(0.75, "Vortex bullish crossover");
        } else if (viMinus > viPlus && viMinus > 1.0 && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Vortex bearish crossover");
        }
        
        return TradeSignal.neutral("Vortex no signal");
    }

    @Override
    public String getName() {
        return "Vortex Indicator";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
        closeHistory.clear();
    }
}
