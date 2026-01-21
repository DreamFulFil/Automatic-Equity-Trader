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
public class MassIndexStrategy implements IStrategy {
    
    private final Map<String, Deque<Double>> rangeHistory = new HashMap<>();
    private final Map<String, Deque<Double>> ema9History = new HashMap<>();
    private final Map<String, Deque<Double>> ratioHistory = new HashMap<>();
    private final Map<String, Double> previousMI = new HashMap<>();
    
    public MassIndexStrategy() {
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> ranges = rangeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> ema9s = ema9History.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> ratios = ratioHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double range = data.getHigh() - data.getLow();
        ranges.addLast(range);
        if (ranges.size() > 60) ranges.removeFirst();
        
        // Mass Index (Donald Dorsey): sum over 25 periods of EMA9(range) / EMA9(EMA9(range))
        double ema9 = calculateEMA(ranges, 9);
        ema9s.addLast(ema9);
        if (ema9s.size() > 60) ema9s.removeFirst();
        
        double ema9of9 = calculateEMA(ema9s, 9);
        double ratio = ema9of9 == 0 ? 1.0 : ema9 / ema9of9;
        
        ratios.addLast(ratio);
        if (ratios.size() > 25) ratios.removeFirst();
        
        if (ratios.size() < 25) {
            return TradeSignal.neutral("Warming up Mass Index");
        }
        
        double massIndex = ratios.stream().mapToDouble(Double::doubleValue).sum();
        
        Double prevMI = previousMI.get(symbol);
        previousMI.put(symbol, massIndex);
        
        if (prevMI == null) return TradeSignal.neutral("Initializing MI");
        
        int position = portfolio.getPosition(symbol);
        
        if (massIndex > 27 && prevMI < 27 && position <= 0) {
            return TradeSignal.longSignal(0.7, "Mass Index reversal bulge");
        } else if (massIndex < 26.5 && prevMI > 26.5 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.7, "Mass Index reversal complete");
        }
        
        return TradeSignal.neutral(String.format("MI=%.1f", massIndex));
    }
    
    private double calculateEMA(Deque<Double> values, int period) {
        Double[] v = values.toArray(new Double[0]);
        if (v.length < period) return v[v.length-1];
        double k = 2.0 / (period + 1);
        double ema = v[0];
        for (int i = 1; i < v.length; i++) {
            ema = (v[i] * k) + (ema * (1 - k));
        }
        return ema;
    }

    @Override
    public String getName() {
        return "Mass Index";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        rangeHistory.clear();
        ema9History.clear();
        ratioHistory.clear();
        previousMI.clear();
    }
}
