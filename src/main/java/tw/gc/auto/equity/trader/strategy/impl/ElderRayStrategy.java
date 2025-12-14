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
public class ElderRayStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> closeHistory = new HashMap<>();
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    
    public ElderRayStrategy() {
        this(13);
    }
    
    public ElderRayStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> closes = closeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        closes.addLast(data.getClose());
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        
        if (closes.size() > period * 2) {
            closes.removeFirst();
            highs.removeFirst();
            lows.removeFirst();
        }
        
        if (closes.size() < period) {
            return TradeSignal.neutral("Warming up Elder Ray");
        }
        
        double ema = calculateEMA(closes, period);
        double bullPower = data.getHigh() - ema;
        double bearPower = data.getLow() - ema;
        
        int position = portfolio.getPosition(symbol);
        
        if (bullPower > 0 && bearPower < 0 && bearPower > -1.0 && position <= 0) {
            return TradeSignal.longSignal(0.75, "Elder Ray bullish");
        } else if (bearPower < 0 && bullPower > 0 && bullPower < 1.0 && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Elder Ray bearish");
        }
        
        return TradeSignal.neutral("Elder Ray neutral");
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
        return "Elder Ray Index";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        closeHistory.clear();
        highHistory.clear();
        lowHistory.clear();
    }
}
