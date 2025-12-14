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
public class KlingerOscillatorStrategy implements IStrategy {
    
    private final Map<String, Deque<Double>> vfHistory = new HashMap<>();
    private final Map<String, Double> previousHLC = new HashMap<>();
    private final Map<String, Integer> trend = new HashMap<>();
    
    public KlingerOscillatorStrategy() {
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> vfs = vfHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double hlc = (data.getHigh() + data.getLow() + data.getClose()) / 3;
        Double prevHLC = previousHLC.get(symbol);
        
        if (prevHLC == null) {
            previousHLC.put(symbol, hlc);
            trend.put(symbol, 1);
            return TradeSignal.neutral("Initializing Klinger");
        }
        
        int currentTrend = hlc > prevHLC ? 1 : -1;
        int prevTrend = trend.get(symbol);
        
        if (currentTrend != prevTrend) {
            trend.put(symbol, currentTrend);
        }
        
        double dm = data.getHigh() - data.getLow();
        double cm = trend.get(symbol) > 0 ? dm : -dm;
        double vf = data.getVolume() * cm * 100;
        
        vfs.addLast(vf);
        if (vfs.size() > 34) vfs.removeFirst();
        
        previousHLC.put(symbol, hlc);
        
        if (vfs.size() < 34) {
            return TradeSignal.neutral("Warming up Klinger");
        }
        
        double ema34 = calculateEMA(vfs, 34);
        double ema55 = calculateEMA(vfs, Math.min(55, vfs.size()));
        double ko = ema34 - ema55;
        
        int position = portfolio.getPosition(symbol);
        
        if (ko > 0 && position <= 0) {
            return TradeSignal.longSignal(0.75, "Klinger bullish");
        } else if (ko < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Klinger bearish");
        }
        
        return TradeSignal.neutral("Klinger neutral");
    }
    
    private double calculateEMA(Deque<Double> values, int period) {
        Double[] v = values.toArray(new Double[0]);
        if (v.length < period) return 0;
        double k = 2.0 / (period + 1);
        double ema = v[0];
        for (int i = 1; i < v.length; i++) {
            ema = (v[i] * k) + (ema * (1 - k));
        }
        return ema;
    }

    @Override
    public String getName() {
        return "Klinger Oscillator";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        vfHistory.clear();
        previousHLC.clear();
        trend.clear();
    }
}
