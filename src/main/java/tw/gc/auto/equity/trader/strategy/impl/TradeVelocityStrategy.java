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
 * TradeVelocityStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Easley, Lopez de Prado & O'Hara (2012) - 'Flow Toxicity and Liquidity'
 * 
 * Logic:
 * Measure speed of trades to detect toxic flow. High velocity = informed trading.
 */
@Slf4j
public class TradeVelocityStrategy implements IStrategy {
    
    private final int measurementWindow;
    private final double toxicityThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public TradeVelocityStrategy(int measurementWindow, double toxicityThreshold) {
        this.measurementWindow = measurementWindow;
        this.toxicityThreshold = toxicityThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > measurementWindow + 5) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < measurementWindow) {
            return TradeSignal.neutral("Warming up velocity");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate price velocity (price change per unit volume)
        double totalPriceChange = Math.abs(priceArray[priceArray.length - 1] - priceArray[0]);
        long totalVolume = 0;
        for (Long v : volumeArray) totalVolume += v;
        
        double velocity = totalVolume > 0 ? totalPriceChange / (totalVolume / 1000.0) : 0;
        
        // Calculate average velocity for toxicity comparison
        double avgVelocity = 0;
        int count = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (volumeArray[i] > 0) {
                avgVelocity += Math.abs(priceArray[i] - priceArray[i-1]) / (volumeArray[i] / 1000.0);
                count++;
            }
        }
        avgVelocity = count > 0 ? avgVelocity / count : velocity;
        
        double toxicity = avgVelocity > 0 ? velocity / avgVelocity : 1;
        
        int position = portfolio.getPosition(symbol);
        
        // Low toxicity + upward trend = safe to buy
        double priceChange = (priceArray[priceArray.length - 1] - priceArray[0]) / priceArray[0];
        if (toxicity < toxicityThreshold && priceChange > 0 && position <= 0) {
            return TradeSignal.longSignal(0.65,
                String.format("Low toxicity buy: tox=%.2f, change=%.2f%%", 
                    toxicity, priceChange * 100));
        }
        
        // High toxicity = avoid/exit
        if (toxicity > toxicityThreshold * 2 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("High toxicity exit: %.2f", toxicity));
        }
        
        // Short on toxic downtrend
        if (toxicity > toxicityThreshold && priceChange < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("Toxic flow short: tox=%.2f, change=%.2f%%", 
                    toxicity, priceChange * 100));
        }
        
        return TradeSignal.neutral(String.format("Toxicity: %.2f", toxicity));
    }

    @Override
    public String getName() {
        return String.format("Trade Velocity (%d, tox<%.1f)", measurementWindow, toxicityThreshold);
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
