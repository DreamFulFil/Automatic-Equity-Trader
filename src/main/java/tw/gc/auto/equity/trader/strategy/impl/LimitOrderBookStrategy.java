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
 * LimitOrderBookStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Cont, Kukanov & Stoikov (2014) - 'The Price Impact of Order Book Events'
 * 
 * Logic:
 * Trade based on order flow imbalance using volume patterns as proxy.
 */
@Slf4j
public class LimitOrderBookStrategy implements IStrategy {
    
    private final int depthLevels;
    private final double imbalanceRatio;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public LimitOrderBookStrategy(int depthLevels, double imbalanceRatio) {
        this.depthLevels = depthLevels;
        this.imbalanceRatio = imbalanceRatio;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > depthLevels * 2) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < depthLevels) {
            return TradeSignal.neutral("Warming up LOB");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate buy/sell volume imbalance proxy
        long upVolume = 0, downVolume = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) upVolume += volumeArray[i];
            else downVolume += volumeArray[i];
        }
        
        double imbalance = (upVolume - downVolume) / (double)(upVolume + downVolume + 1);
        
        double currentPrice = priceArray[priceArray.length - 1];
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        int position = portfolio.getPosition(symbol);
        
        // Strong buy imbalance
        if (imbalance > imbalanceRatio && currentPrice >= avgPrice && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("LOB buy imbalance: %.2f > %.2f", imbalance, imbalanceRatio));
        }
        
        // Strong sell imbalance
        if (imbalance < -imbalanceRatio && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("LOB sell imbalance: %.2f", imbalance));
        }
        
        // Short on strong sell imbalance
        if (imbalance < -imbalanceRatio && currentPrice < avgPrice && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("LOB short: imbalance=%.2f", imbalance));
        }
        
        return TradeSignal.neutral(String.format("LOB imbalance: %.2f", imbalance));
    }

    @Override
    public String getName() {
        return String.format("Limit Order Book (%d levels, %.1f)", depthLevels, imbalanceRatio);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
    }
}
