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
public class KaufmanAdaptiveMAStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Double> previousKAMA = new HashMap<>();
    
    public KaufmanAdaptiveMAStrategy() {
        this(10);
    }
    
    public KaufmanAdaptiveMAStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period + 1) prices.removeFirst();
        
        if (prices.size() < period + 1) {
            return TradeSignal.neutral("Warming up KAMA");
        }
        
        Double[] p = prices.toArray(new Double[0]);
        double change = Math.abs(p[p.length-1] - p[0]);
        double volatility = 0;
        for (int i = 1; i < p.length; i++) {
            volatility += Math.abs(p[i] - p[i-1]);
        }
        
        double er = volatility > 0 ? change / volatility : 0;
        double fastSC = 2.0 / (2 + 1);
        double slowSC = 2.0 / (30 + 1);
        double sc = Math.pow(er * (fastSC - slowSC) + slowSC, 2);
        
        double prevKAMA = previousKAMA.getOrDefault(symbol, p[0]);
        double kama = prevKAMA + sc * (data.getClose() - prevKAMA);
        previousKAMA.put(symbol, kama);
        
        int position = portfolio.getPosition(symbol);
        
        if (data.getClose() > kama && prevKAMA < kama && position <= 0) {
            return TradeSignal.longSignal(0.75, "KAMA bullish");
        } else if (data.getClose() < kama && prevKAMA > kama && position >= 0) {
            return TradeSignal.shortSignal(0.75, "KAMA bearish");
        }
        
        return TradeSignal.neutral("KAMA no signal");
    }

    @Override
    public String getName() {
        return "Kaufman Adaptive MA";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        previousKAMA.clear();
    }
}
