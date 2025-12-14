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
public class BalanceOfPowerStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> bopHistory = new HashMap<>();
    
    public BalanceOfPowerStrategy() {
        this(14);
    }
    
    public BalanceOfPowerStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> bops = bopHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double range = data.getHigh() - data.getLow();
        double bop = range > 0 ? (data.getClose() - data.getOpen()) / range : 0;
        
        bops.addLast(bop);
        if (bops.size() > period) bops.removeFirst();
        
        if (bops.size() < period) {
            return TradeSignal.neutral("Warming up BOP");
        }
        
        double avgBOP = bops.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        int position = portfolio.getPosition(symbol);
        
        if (avgBOP > 0.2 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Balance of Power bullish (%.2f)", avgBOP));
        } else if (avgBOP < -0.2 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Balance of Power bearish (%.2f)", avgBOP));
        }
        
        return TradeSignal.neutral(String.format("BOP=%.2f", avgBOP));
    }

    @Override
    public String getName() {
        return "Balance of Power";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        bopHistory.clear();
    }
}
