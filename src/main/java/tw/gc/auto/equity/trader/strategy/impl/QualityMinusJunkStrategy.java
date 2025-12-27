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
 * QualityMinusJunkStrategy
 * Type: Quality Factor
 * 
 * Academic Foundation:
 * - Asness, Frazzini & Pedersen (2019) - 'Quality Minus Junk'
 * 
 * Logic:
 * Long quality stocks (stable, profitable), short junk stocks (volatile, declining).
 */
@Slf4j
public class QualityMinusJunkStrategy implements IStrategy {
    
    private final int profitabilityWindow;
    private final int growthWindow;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public QualityMinusJunkStrategy(int profitabilityWindow, int growthWindow) {
        this.profitabilityWindow = profitabilityWindow;
        this.growthWindow = growthWindow;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        int maxWindow = Math.max(profitabilityWindow, growthWindow);
        if (prices.size() > maxWindow + 10) prices.removeFirst();
        
        if (prices.size() < maxWindow) {
            return TradeSignal.neutral("Warming up QMJ");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Profitability: win rate
        int positiveReturns = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            if (i > 0 && priceArray[i] > priceArray[i-1]) positiveReturns++;
        }
        double profitability = (double) positiveReturns / profitabilityWindow;
        
        // Growth: price trend
        double startPrice = priceArray[priceArray.length - growthWindow];
        double growth = (currentPrice - startPrice) / startPrice;
        
        // Stability: low volatility
        double avgPrice = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            avgPrice += priceArray[i];
        }
        avgPrice /= profitabilityWindow;
        double variance = 0;
        for (int i = priceArray.length - profitabilityWindow; i < priceArray.length; i++) {
            variance += Math.pow(priceArray[i] - avgPrice, 2);
        }
        double stability = 1 / (1 + Math.sqrt(variance / profitabilityWindow) / avgPrice * 5);
        
        // Quality score
        double qualityScore = profitability * 0.4 + (growth > 0 ? 0.3 : 0) + stability * 0.3;
        
        int position = portfolio.getPosition(symbol);
        
        // Quality stock
        if (qualityScore > 0.6 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Quality: score=%.2f (prof=%.1f%%, growth=%.1f%%, stab=%.2f)", 
                    qualityScore, profitability * 100, growth * 100, stability));
        }
        
        // Junk stock (short)
        if (qualityScore < 0.3 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Junk: score=%.2f", qualityScore));
        }
        
        // Exit quality if deteriorating
        if (position > 0 && qualityScore < 0.4) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Quality declining: %.2f", qualityScore));
        }
        
        return TradeSignal.neutral(String.format("QMJ score: %.2f", qualityScore));
    }

    @Override
    public String getName() {
        return String.format("Quality Minus Junk (%d/%d)", profitabilityWindow, growthWindow);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
