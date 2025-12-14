package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ZigZagStrategy implements IStrategy {
    
    private final double threshold;
    private final Map<String, Double> lastPivot = new HashMap<>();
    private final Map<String, Boolean> isUptrend = new HashMap<>();
    
    public ZigZagStrategy() {
        this(0.05);
    }
    
    public ZigZagStrategy(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        
        if (!lastPivot.containsKey(symbol)) {
            lastPivot.put(symbol, data.getClose());
            isUptrend.put(symbol, true);
            return TradeSignal.neutral("Initializing ZigZag");
        }
        
        double pivot = lastPivot.get(symbol);
        boolean uptrend = isUptrend.get(symbol);
        double currentPrice = data.getClose();
        
        boolean trendChange = false;
        
        if (uptrend && (pivot - currentPrice) / pivot > threshold) {
            uptrend = false;
            pivot = currentPrice;
            trendChange = true;
        } else if (!uptrend && (currentPrice - pivot) / pivot > threshold) {
            uptrend = true;
            pivot = currentPrice;
            trendChange = true;
        }
        
        if (uptrend && currentPrice > pivot) pivot = currentPrice;
        if (!uptrend && currentPrice < pivot) pivot = currentPrice;
        
        lastPivot.put(symbol, pivot);
        isUptrend.put(symbol, uptrend);
        
        int position = portfolio.getPosition(symbol);
        
        if (trendChange && uptrend && position <= 0) {
            return TradeSignal.longSignal(0.8, "ZigZag reversal bullish");
        } else if (trendChange && !uptrend && position >= 0) {
            return TradeSignal.shortSignal(0.8, "ZigZag reversal bearish");
        }
        
        return TradeSignal.neutral("ZigZag no reversal");
    }

    @Override
    public String getName() {
        return "ZigZag Reversal";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        lastPivot.clear();
        isUptrend.clear();
    }
}
