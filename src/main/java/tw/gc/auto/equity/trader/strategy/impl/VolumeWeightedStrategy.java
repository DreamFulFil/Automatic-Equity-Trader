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
 * Volume Weighted Average Price Strategy
 * Type: Volume Analysis
 * 
 * Logic:
 * - Buy when price crosses above VWAP with high volume
 * - Sell when price crosses below VWAP with high volume
 */
@Slf4j
public class VolumeWeightedStrategy implements IStrategy {
    
    private final int period;
    private final double volumeThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public VolumeWeightedStrategy() {
        this(20, 1.5);
    }
    
    public VolumeWeightedStrategy(int period, double volumeThreshold) {
        this.period = period;
        this.volumeThreshold = volumeThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        
        if (prices.size() > period) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < period) {
            return TradeSignal.neutral("Warming up VWAP");
        }
        
        Double[] p = prices.toArray(new Double[0]);
        Long[] v = volumes.toArray(new Long[0]);
        
        double sumPV = 0, sumV = 0;
        for (int i = 0; i < p.length; i++) {
            sumPV += p[i] * v[i];
            sumV += v[i];
        }
        
        double vwap = sumPV / sumV;
        double avgVolume = volumes.stream().mapToLong(Long::longValue).average().orElse(0);
        double currentVolume = data.getVolume();
        
        int position = portfolio.getPosition(symbol);
        
        if (data.getClose() > vwap && currentVolume > avgVolume * volumeThreshold && position <= 0) {
            return TradeSignal.longSignal(0.75, "Price above VWAP with high volume");
        } else if (data.getClose() < vwap && currentVolume > avgVolume * volumeThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Price below VWAP with high volume");
        }
        
        return TradeSignal.neutral("Volume not significant");
    }

    @Override
    public String getName() {
        return "Volume Weighted";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
    }
}
