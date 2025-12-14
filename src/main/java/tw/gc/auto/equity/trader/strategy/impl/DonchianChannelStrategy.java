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
 * Donchian Channel Breakout Strategy
 * Type: Breakout
 * 
 * Logic:
 * - Buy when price breaks above upper channel (highest high)
 * - Sell when price breaks below lower channel (lowest low)
 */
@Slf4j
public class DonchianChannelStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    
    public DonchianChannelStrategy() {
        this(20);
    }
    
    public DonchianChannelStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        
        if (highs.size() > period + 1) {
            highs.removeFirst();
            lows.removeFirst();
        }
        
        if (highs.size() < period) {
            return TradeSignal.neutral("Warming up Donchian");
        }
        
        Double[] h = highs.toArray(new Double[0]);
        Double[] l = lows.toArray(new Double[0]);
        
        double upperChannel = 0;
        double lowerChannel = Double.MAX_VALUE;
        for (int i = 0; i < h.length - 1; i++) {
            if (h[i] > upperChannel) upperChannel = h[i];
            if (l[i] < lowerChannel) lowerChannel = l[i];
        }
        
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        if (data.getHigh() > upperChannel && position <= 0) {
            return TradeSignal.longSignal(0.8, "Breakout above Donchian upper channel");
        } else if (data.getLow() < lowerChannel && position >= 0) {
            return TradeSignal.shortSignal(0.8, "Breakout below Donchian lower channel");
        }
        
        return TradeSignal.neutral("Price within Donchian channel");
    }

    @Override
    public String getName() {
        return "Donchian Channel Breakout";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
    }
}
