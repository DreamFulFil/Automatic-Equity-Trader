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
public class PriceVolumeRankStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<MarketData>> dataHistory = new HashMap<>();
    
    public PriceVolumeRankStrategy() {
        this(20);
    }
    
    public PriceVolumeRankStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<MarketData> history = dataHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        history.addLast(data);
        if (history.size() > period) history.removeFirst();
        
        if (history.size() < period) {
            return TradeSignal.neutral("Warming up PVR");
        }
        
        double avgVolume = history.stream().mapToLong(MarketData::getVolume).average().orElse(0);
        double avgPrice = history.stream().mapToDouble(MarketData::getClose).average().orElse(0);
        
        double currentVolume = data.getVolume();
        double currentPrice = data.getClose();
        
        boolean volumeSpike = currentVolume > avgVolume * 1.5;
        boolean priceAboveAvg = currentPrice > avgPrice;
        boolean priceBelowAvg = currentPrice < avgPrice;
        
        int position = portfolio.getPosition(symbol);
        
        if (volumeSpike && priceAboveAvg && position <= 0) {
            return TradeSignal.longSignal(0.8, "Strong volume breakout above average");
        } else if (volumeSpike && priceBelowAvg && position >= 0) {
            return TradeSignal.shortSignal(0.8, "Strong volume breakdown below average");
        }
        
        return TradeSignal.neutral("No volume-price divergence");
    }

    @Override
    public String getName() {
        return "Price Volume Rank";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }

    @Override
    public void reset() {
        dataHistory.clear();
    }
}
