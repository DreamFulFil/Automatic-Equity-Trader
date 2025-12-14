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
public class DPOStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public DPOStrategy() {
        this(20);
    }
    
    public DPOStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period + 10) prices.removeFirst();
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up DPO");
        }
        
        double sma = prices.stream().skip(Math.max(0, prices.size() - period))
            .mapToDouble(Double::doubleValue).average().orElse(0);
        
        int shift = period / 2 + 1;
        Double[] p = prices.toArray(new Double[0]);
        double shiftedPrice = p[Math.max(0, p.length - shift)];
        double dpo = shiftedPrice - sma;
        
        int position = portfolio.getPosition(symbol);
        
        if (dpo > 0 && position <= 0) {
            return TradeSignal.longSignal(0.7, "DPO cycle low");
        } else if (dpo < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.7, "DPO cycle high");
        }
        
        return TradeSignal.neutral(String.format("DPO=%.2f", dpo));
    }

    @Override
    public String getName() {
        return "Detrended Price Oscillator";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
