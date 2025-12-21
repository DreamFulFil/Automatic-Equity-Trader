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
 * InvestmentFactorStrategy
 * Type: Factor Investing
 * 
 * Academic Foundation:
 * - Titman, Wei & Xie (2004) - 'Capital Investments and Stock Returns'
 * 
 * Logic:
 * Conservative investment (low capex growth) outperforms aggressive.
 * Use price growth as proxy for capex growth.
 */
@Slf4j
public class InvestmentFactorStrategy implements IStrategy {
    
    private final double maxCapexGrowth;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public InvestmentFactorStrategy(double maxCapexGrowth) {
        this.maxCapexGrowth = maxCapexGrowth;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 252) prices.removeFirst();
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up investment factor");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate growth rate (capex proxy)
        int halfPeriod = priceArray.length / 2;
        double earlyAvg = 0, lateAvg = 0;
        for (int i = 0; i < halfPeriod; i++) earlyAvg += priceArray[i];
        for (int i = halfPeriod; i < priceArray.length; i++) lateAvg += priceArray[i];
        earlyAvg /= halfPeriod;
        lateAvg /= (priceArray.length - halfPeriod);
        
        double growthRate = (lateAvg - earlyAvg) / earlyAvg;
        
        int position = portfolio.getPosition(symbol);
        
        // Conservative (low growth) = good
        if (growthRate < maxCapexGrowth && growthRate > -0.3 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Conservative investment: growth=%.1f%% < %.1f%%", 
                    growthRate * 100, maxCapexGrowth * 100));
        }
        
        // Exit if growth accelerates
        if (position > 0 && growthRate > maxCapexGrowth * 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Growth accelerated: %.1f%%", growthRate * 100));
        }
        
        // Short aggressive growers
        if (growthRate > maxCapexGrowth * 3 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("Aggressive growth short: %.1f%%", growthRate * 100));
        }
        
        return TradeSignal.neutral(String.format("Growth: %.1f%%", growthRate * 100));
    }

    @Override
    public String getName() {
        return String.format("Investment Factor (max=%.0f%%)", maxCapexGrowth * 100);
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
