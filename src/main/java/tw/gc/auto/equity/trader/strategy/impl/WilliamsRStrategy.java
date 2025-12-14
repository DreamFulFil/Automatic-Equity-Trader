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

/**
 * Williams %R Oscillator Strategy
 * Type: Momentum Oscillator
 * 
 * Logic:
 * - Buy when %R crosses above -80 (oversold)
 * - Sell when %R crosses below -20 (overbought)
 */
@Slf4j
public class WilliamsRStrategy implements IStrategy {
    
    private final int period;
    private final double oversoldLevel;
    private final double overboughtLevel;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    private final Map<String, Deque<Double>> closeHistory = new HashMap<>();
    
    public WilliamsRStrategy() {
        this(14, -80, -20);
    }
    
    public WilliamsRStrategy(int period, double oversoldLevel, double overboughtLevel) {
        this.period = period;
        this.oversoldLevel = oversoldLevel;
        this.overboughtLevel = overboughtLevel;
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
        
        if (highs.size() > period) {
            highs.removeFirst();
            lows.removeFirst();
            closes.removeFirst();
        }
        
        if (highs.size() < period) {
            return TradeSignal.neutral("Warming up Williams %R");
        }
        
        double highestHigh = highs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double lowestLow = lows.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double currentClose = data.getClose();
        
        double williamsR = ((highestHigh - currentClose) / (highestHigh - lowestLow)) * -100;
        
        int position = portfolio.getPosition(symbol);
        
        if (williamsR > oversoldLevel && williamsR < -50 && position <= 0) {
            return TradeSignal.longSignal(0.7, String.format("Williams %%R oversold (%.1f)", williamsR));
        } else if (williamsR < overboughtLevel && williamsR > -50 && position >= 0) {
            return TradeSignal.shortSignal(0.7, String.format("Williams %%R overbought (%.1f)", williamsR));
        }
        
        return TradeSignal.neutral(String.format("Williams %%R=%.1f", williamsR));
    }

    @Override
    public String getName() {
        return "Williams %R";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
        closeHistory.clear();
    }
}
