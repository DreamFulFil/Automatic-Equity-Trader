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
 * MultiFactorRankingStrategy
 * Type: Factor Investing
 * 
 * Academic Foundation:
 * - Fama & French (2015) - 'A Five-Factor Asset Pricing Model'
 * 
 * Logic:
 * Composite score from multiple factors (value, momentum, quality, size).
 */
@Slf4j
public class MultiFactorRankingStrategy implements IStrategy {
    
    private final double[] factorWeights;
    private final int rebalancePeriod;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> daysSinceRebalance = new HashMap<>();
    
    public MultiFactorRankingStrategy(double[] factorWeights, int rebalancePeriod) {
        this.factorWeights = factorWeights;
        this.rebalancePeriod = rebalancePeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 120) prices.removeFirst();
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up multi-factor");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate factor scores
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        // Value factor: price below average
        double valueScore = (avgPrice - currentPrice) / avgPrice;
        
        // Momentum factor: recent return
        double momentum = (currentPrice - priceArray[Math.max(0, priceArray.length - 30)]) / 
                         priceArray[Math.max(0, priceArray.length - 30)];
        double momentumScore = momentum;
        
        // Quality factor: low volatility
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double volatility = Math.sqrt(variance / priceArray.length) / avgPrice;
        double qualityScore = 1 - Math.min(volatility * 5, 1);
        
        // Size factor: not directly available, use 0.5 as neutral
        double sizeScore = 0.5;
        
        // Composite score
        double compositeScore = 0;
        double[] scores = {valueScore, momentumScore, qualityScore, sizeScore};
        for (int i = 0; i < Math.min(factorWeights.length, scores.length); i++) {
            compositeScore += factorWeights[i] * scores[i];
        }
        
        int days = daysSinceRebalance.getOrDefault(symbol, rebalancePeriod);
        daysSinceRebalance.put(symbol, days + 1);
        
        int position = portfolio.getPosition(symbol);
        
        // Rebalance if period elapsed
        if (days >= rebalancePeriod) {
            daysSinceRebalance.put(symbol, 0);
            
            if (compositeScore > 0.1 && position <= 0) {
                return TradeSignal.longSignal(0.70,
                    String.format("Multi-factor long: score=%.2f (V=%.2f, M=%.2f, Q=%.2f)", 
                        compositeScore, valueScore, momentumScore, qualityScore));
            }
            
            if (compositeScore < -0.1 && position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                    String.format("Multi-factor exit: score=%.2f", compositeScore));
            }
        }
        
        return TradeSignal.neutral(String.format("Score=%.2f, days=%d", compositeScore, days));
    }

    @Override
    public String getName() {
        return String.format("Multi-Factor Ranking (%dd rebal)", rebalancePeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        daysSinceRebalance.clear();
    }
}
