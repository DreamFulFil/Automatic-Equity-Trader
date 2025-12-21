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
 * ExecutionShortfallStrategy
 * Type: Execution Algorithm
 * 
 * Academic Foundation:
 * - Perold (1988) - 'The Implementation Shortfall'
 * 
 * Logic:
 * Minimize market impact by trading in slices.
 * Adjust execution speed based on urgency and market conditions.
 */
@Slf4j
public class ExecutionShortfallStrategy implements IStrategy {
    
    private final int totalVolume;
    private final int numSlices;
    private final double urgency;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Double> decisionPrices = new HashMap<>();
    private final Map<String, Integer> executedSlices = new HashMap<>();
    
    public ExecutionShortfallStrategy(int totalVolume, int numSlices, double urgency) {
        this.totalVolume = totalVolume;
        this.numSlices = numSlices;
        this.urgency = urgency;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) {
            prices.removeFirst();
        }
        
        double currentPrice = data.getClose();
        
        // Set decision price on first call
        if (!decisionPrices.containsKey(symbol)) {
            decisionPrices.put(symbol, currentPrice);
            executedSlices.put(symbol, 0);
        }
        
        double decisionPrice = decisionPrices.get(symbol);
        int slicesExecuted = executedSlices.get(symbol);
        
        // Calculate implementation shortfall
        double shortfall = (currentPrice - decisionPrice) / decisionPrice;
        
        // Calculate volatility for adaptive execution
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up execution");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double sumReturns = 0;
        double sumSqReturns = 0;
        for (int i = 1; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i-1]) / priceArray[i-1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgRet = sumReturns / (priceArray.length - 1);
        double volatility = Math.sqrt((sumSqReturns / (priceArray.length - 1)) - avgRet * avgRet);
        
        int position = portfolio.getPosition(symbol);
        
        // Adaptive execution: trade more aggressively when favorable, slower when adverse
        double favorability = -shortfall;
        double adjustedUrgency = urgency * (1 + favorability * 2);
        adjustedUrgency = Math.max(0.1, Math.min(1.0, adjustedUrgency));
        
        // Execute slice if conditions favorable
        if (slicesExecuted < numSlices) {
            boolean shouldExecute = favorability > -volatility || 
                                   (adjustedUrgency > 0.7 && slicesExecuted < numSlices / 2);
            
            if (shouldExecute && position <= 0) {
                executedSlices.put(symbol, slicesExecuted + 1);
                return TradeSignal.longSignal(0.60 + adjustedUrgency * 0.2,
                    String.format("Execution slice %d/%d: shortfall=%.2f%%, urgency=%.0f%%", 
                        slicesExecuted + 1, numSlices, shortfall * 100, adjustedUrgency * 100));
            }
        }
        
        // Exit if shortfall becomes too negative
        if (position > 0 && shortfall < -volatility * 3) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Shortfall limit: %.2f%%", shortfall * 100));
        }
        
        return TradeSignal.neutral(String.format("Shortfall: %.2f%%, slices: %d/%d", 
            shortfall * 100, slicesExecuted, numSlices));
    }

    @Override
    public String getName() {
        return String.format("Execution Shortfall (%d slices, %.0f%% urgency)", numSlices, urgency * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        decisionPrices.clear();
        executedSlices.clear();
    }
}
