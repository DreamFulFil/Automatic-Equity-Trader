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
 * OrderFlowImbalanceStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Chordia & Subrahmanyam (2004) - 'Order Imbalance and Individual Stock Returns'
 * 
 * Logic:
 * Trade based on buy/sell order flow imbalance using volume and price direction.
 */
@Slf4j
public class OrderFlowImbalanceStrategy implements IStrategy {
    
    private final int windowMinutes;
    private final double imbalanceThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public OrderFlowImbalanceStrategy(int windowMinutes, double imbalanceThreshold) {
        this.windowMinutes = windowMinutes;
        this.imbalanceThreshold = imbalanceThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > windowMinutes) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < windowMinutes / 2) {
            return TradeSignal.neutral("Warming up order flow");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate order flow imbalance
        long buyVolume = 0, sellVolume = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) buyVolume += volumeArray[i];
            else if (priceArray[i] < priceArray[i-1]) sellVolume += volumeArray[i];
        }
        
        long totalVolume = buyVolume + sellVolume;
        double imbalance = totalVolume > 0 ? (double)(buyVolume - sellVolume) / totalVolume : 0;
        
        int position = portfolio.getPosition(symbol);
        
        // Strong buy imbalance
        if (imbalance > imbalanceThreshold && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Order flow buy: imbalance=%.2f (buy=%d, sell=%d)", 
                    imbalance, buyVolume, sellVolume));
        }
        
        // Strong sell imbalance
        if (imbalance < -imbalanceThreshold && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Order flow sell: imbalance=%.2f", imbalance));
        }
        
        // Short on extreme sell imbalance
        if (imbalance < -imbalanceThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Order flow short: imbalance=%.2f", imbalance));
        }
        
        return TradeSignal.neutral(String.format("Order flow: %.2f", imbalance));
    }

    @Override
    public String getName() {
        return String.format("Order Flow Imbalance (%dmin, %.1f)", windowMinutes, imbalanceThreshold);
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
