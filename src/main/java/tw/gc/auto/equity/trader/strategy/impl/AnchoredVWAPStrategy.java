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
 * AnchoredVWAPStrategy
 * Type: Execution / Technical
 * 
 * Academic Foundation:
 * - Berkowitz, Logue & Noser (1988) - 'The Total Cost of Transactions'
 * 
 * Logic:
 * Calculate VWAP from session start (anchor point).
 * Buy when price is below VWAP, sell when above.
 */
@Slf4j
public class AnchoredVWAPStrategy implements IStrategy {
    
    private final String anchorEvent;
    private final double deviationThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    private final Map<String, Double> anchorVwap = new HashMap<>();
    
    public AnchoredVWAPStrategy(String anchorEvent, double deviationThreshold) {
        this.anchorEvent = anchorEvent;
        this.deviationThreshold = deviationThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > 60) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up VWAP");
        }
        
        // Calculate VWAP from anchor (session start)
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        double sumPV = 0;
        long sumV = 0;
        for (int i = 0; i < priceArray.length; i++) {
            sumPV += priceArray[i] * volumeArray[i];
            sumV += volumeArray[i];
        }
        double vwap = sumV > 0 ? sumPV / sumV : priceArray[priceArray.length - 1];
        anchorVwap.put(symbol, vwap);
        
        double currentPrice = priceArray[priceArray.length - 1];
        double deviation = (currentPrice - vwap) / vwap;
        
        int position = portfolio.getPosition(symbol);
        
        // Buy below VWAP
        if (deviation < -deviationThreshold && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Below VWAP: price=%.2f, VWAP=%.2f (%.2f%%)", 
                    currentPrice, vwap, deviation * 100));
        }
        
        // Sell above VWAP
        if (deviation > deviationThreshold && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Above VWAP: price=%.2f, VWAP=%.2f (%.2f%%)", 
                    currentPrice, vwap, deviation * 100));
        }
        
        // Short significantly above VWAP
        if (deviation > deviationThreshold * 2 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Short above VWAP: %.2f%% deviation", deviation * 100));
        }
        
        return TradeSignal.neutral(String.format("VWAP=%.2f, Dev=%.2f%%", vwap, deviation * 100));
    }

    @Override
    public String getName() {
        return String.format("Anchored VWAP (%s, %.1f%%)", anchorEvent, deviationThreshold * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
        anchorVwap.clear();
    }
}
