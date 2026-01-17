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
public class TrixStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Double> previousTripleEMA = new HashMap<>();
    private final Map<String, Double> previousTrix = new HashMap<>();
    
    public TrixStrategy() {
        this(15);
    }
    
    public TrixStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period * 3) prices.removeFirst();
        
        if (prices.size() < period * 3) {
            return TradeSignal.neutral("Warming up TRIX");
        }
        
        double ema1 = calculateEMA(prices, period);
        Deque<Double> ema1List = new ArrayDeque<>();
        ema1List.add(ema1);
        double ema2 = calculateEMA(ema1List, period);
        Deque<Double> ema2List = new ArrayDeque<>();
        ema2List.add(ema2);
        double ema3 = calculateEMA(ema2List, period);
        
        Double prevEma3 = previousTripleEMA.get(symbol);
        double trix = 0.0;
        if (prevEma3 != null && prevEma3 != 0.0) {
            trix = 10000.0 * (ema3 - prevEma3) / prevEma3;
        }
        previousTripleEMA.put(symbol, ema3);
        
        Double prevTrix = previousTrix.get(symbol);
        previousTrix.put(symbol, trix);
        
        if (prevTrix == null) return TradeSignal.neutral("Initializing TRIX");
        
        int position = portfolio.getPosition(symbol);
        
        if (trix > 0 && prevTrix <= 0 && position <= 0) {
            return TradeSignal.longSignal(0.75, "TRIX bullish crossover");
        } else if (trix < 0 && prevTrix >= 0 && position >= 0) {
            return TradeSignal.shortSignal(0.75, "TRIX bearish crossover");
        }
        
        return TradeSignal.neutral(String.format("TRIX=%.2f", trix));
    }
    
    public double calculateEMA(Deque<Double> values, int period) {
        Double[] v = values.toArray(new Double[0]);
        if (v.length < 1) return 0;
        double k = 2.0 / (period + 1);
        double ema = v[0];
        for (int i = 1; i < v.length; i++) {
            ema = (v[i] * k) + (ema * (1 - k));
        }
        return ema;
    }

    @Override
    public String getName() {
        return "TRIX";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        previousTripleEMA.clear();
        previousTrix.clear();
    }
}
