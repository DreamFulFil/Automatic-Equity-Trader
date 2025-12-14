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
public class SupertrendStrategy implements IStrategy {
    
    private final int period;
    private final double multiplier;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    private final Map<String, Deque<Double>> closeHistory = new HashMap<>();
    private final Map<String, Boolean> isUptrend = new HashMap<>();
    
    public SupertrendStrategy() {
        this(10, 3.0);
    }
    
    public SupertrendStrategy(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
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
        
        if (highs.size() > period * 2) {
            highs.removeFirst();
            lows.removeFirst();
            closes.removeFirst();
        }
        
        if (highs.size() < period) {
            return TradeSignal.neutral("Warming up Supertrend");
        }
        
        double atr = calculateATR(highs, lows, closes, period);
        double hl2 = (data.getHigh() + data.getLow()) / 2;
        double basicUpper = hl2 + (multiplier * atr);
        double basicLower = hl2 - (multiplier * atr);
        
        boolean uptrend = isUptrend.getOrDefault(symbol, true);
        double currentPrice = data.getClose();
        
        boolean newUptrend = currentPrice > basicLower;
        boolean trendChange = uptrend != newUptrend;
        
        isUptrend.put(symbol, newUptrend);
        
        int position = portfolio.getPosition(symbol);
        
        if (trendChange && newUptrend && position <= 0) {
            return TradeSignal.longSignal(0.8, "Supertrend bullish");
        } else if (trendChange && !newUptrend && position >= 0) {
            return TradeSignal.shortSignal(0.8, "Supertrend bearish");
        }
        
        return TradeSignal.neutral("Supertrend no change");
    }
    
    private double calculateATR(Deque<Double> highs, Deque<Double> lows, Deque<Double> closes, int period) {
        Double[] h = highs.toArray(new Double[0]);
        Double[] l = lows.toArray(new Double[0]);
        Double[] c = closes.toArray(new Double[0]);
        
        double sum = 0;
        int count = 0;
        for (int i = 1; i < h.length && count < period; i++) {
            double tr = Math.max(h[i] - l[i], Math.max(Math.abs(h[i] - c[i-1]), Math.abs(l[i] - c[i-1])));
            sum += tr;
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    @Override
    public String getName() {
        return "Supertrend";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
        closeHistory.clear();
        isUptrend.clear();
    }
}
