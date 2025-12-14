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
public class PriceRateOfChangeStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public PriceRateOfChangeStrategy() {
        this(12);
    }
    
    public PriceRateOfChangeStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > period + 1) prices.removeFirst();
        
        if (prices.size() < period + 1) {
            return TradeSignal.neutral("Warming up ROC");
        }
        
        Double[] p = prices.toArray(new Double[0]);
        double roc = ((p[p.length-1] - p[0]) / p[0]) * 100;
        
        int position = portfolio.getPosition(symbol);
        
        if (roc > 5 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("ROC strong momentum (%.1f%%)", roc));
        } else if (roc < -5 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("ROC weak momentum (%.1f%%)", roc));
        } else if (Math.abs(roc) < 1 && position != 0) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.7, "ROC momentum fading");
        }
        
        return TradeSignal.neutral(String.format("ROC=%.1f%%", roc));
    }

    @Override
    public String getName() {
        return "Price Rate of Change";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
