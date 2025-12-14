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
public class ForceIndexStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> fiHistory = new HashMap<>();
    private final Map<String, Double> previousClose = new HashMap<>();
    
    public ForceIndexStrategy() {
        this(13);
    }
    
    public ForceIndexStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> fis = fiHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        Double prevClose = previousClose.get(symbol);
        if (prevClose == null) {
            previousClose.put(symbol, data.getClose());
            return TradeSignal.neutral("Initializing Force Index");
        }
        
        double fi = (data.getClose() - prevClose) * data.getVolume();
        fis.addLast(fi);
        
        if (fis.size() > period) fis.removeFirst();
        
        previousClose.put(symbol, data.getClose());
        
        if (fis.size() < period) {
            return TradeSignal.neutral("Warming up Force Index");
        }
        
        double emaFI = calculateEMA(fis, period);
        
        int position = portfolio.getPosition(symbol);
        
        if (emaFI > 0 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Force Index bullish (%.0f)", emaFI));
        } else if (emaFI < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Force Index bearish (%.0f)", emaFI));
        }
        
        return TradeSignal.neutral(String.format("FI=%.0f", emaFI));
    }
    
    private double calculateEMA(Deque<Double> values, int period) {
        Double[] v = values.toArray(new Double[0]);
        double k = 2.0 / (period + 1);
        double ema = v[0];
        for (int i = 1; i < v.length; i++) {
            ema = (v[i] * k) + (ema * (1 - k));
        }
        return ema;
    }

    @Override
    public String getName() {
        return "Force Index";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        fiHistory.clear();
        previousClose.clear();
    }
}
