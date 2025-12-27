package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * SmartOrderRoutingStrategy
 * Type: Execution
 * 
 * Academic Foundation:
 * - Foucault & Menkveld (2008) - 'Competition for Order Flow'
 * 
 * Logic:
 * Optimize execution by trading at favorable prices relative to recent range.
 */
@Slf4j
public class SmartOrderRoutingStrategy implements IStrategy {
    
    private final String[] venues;
    private final double[] venueCosts;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public SmartOrderRoutingStrategy(String[] venues, double[] venueCosts) {
        this.venues = venues;
        this.venueCosts = venueCosts;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) prices.removeFirst();
        
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up SOR");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate price range
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (Double p : priceArray) {
            if (p < minPrice) minPrice = p;
            if (p > maxPrice) maxPrice = p;
        }
        
        double range = maxPrice - minPrice;
        double positionInRange = range > 0 ? (currentPrice - minPrice) / range : 0.5;
        
        // Calculate best execution cost
        double avgCost = 0;
        for (double cost : venueCosts) avgCost += cost;
        avgCost /= venueCosts.length;
        
        // Execution favorability
        double executionScore = (1 - positionInRange) - avgCost;
        
        int position = portfolio.getPosition(symbol);
        
        // Good buy opportunity (low in range)
        if (positionInRange < 0.3 && position <= 0) {
            return TradeSignal.longSignal(0.65,
                String.format("SOR buy: %.0f%% of range, exec score=%.2f", 
                    positionInRange * 100, executionScore));
        }
        
        // Good sell opportunity (high in range)
        if (positionInRange > 0.7 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("SOR sell: %.0f%% of range", positionInRange * 100));
        }
        
        return TradeSignal.neutral(String.format("Position in range: %.0f%%", positionInRange * 100));
    }

    @Override
    public String getName() {
        return String.format("Smart Order Routing (%d venues)", venues.length);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
