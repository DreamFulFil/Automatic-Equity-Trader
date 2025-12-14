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
public class RelativeVigorIndexStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<MarketData>> dataHistory = new HashMap<>();
    
    public RelativeVigorIndexStrategy() {
        this(10);
    }
    
    public RelativeVigorIndexStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<MarketData> history = dataHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        history.addLast(data);
        if (history.size() > period) history.removeFirst();
        
        if (history.size() < period) {
            return TradeSignal.neutral("Warming up RVI");
        }
        
        double sumCO = 0, sumHL = 0;
        for (MarketData bar : history) {
            sumCO += bar.getClose() - bar.getOpen();
            sumHL += bar.getHigh() - bar.getLow();
        }
        
        double rvi = sumHL > 0 ? sumCO / sumHL : 0;
        
        int position = portfolio.getPosition(symbol);
        
        if (rvi > 0.1 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("RVI bullish (%.2f)", rvi));
        } else if (rvi < -0.1 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("RVI bearish (%.2f)", rvi));
        }
        
        return TradeSignal.neutral(String.format("RVI=%.2f", rvi));
    }

    @Override
    public String getName() {
        return "Relative Vigor Index";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        dataHistory.clear();
    }
}
