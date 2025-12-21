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
 * DualMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Gary Antonacci - 'Dual Momentum Investing' (2012)
 * 
 * Logic:
 * Combines absolute momentum (time-series) and relative momentum (cross-sectional).
 * Enter long only when both absolute and relative momentum are positive.
 */
@Slf4j
public class DualMomentumStrategy implements IStrategy {
    
    private final int absolutePeriod;
    private final int relativePeriod;
    private final double threshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public DualMomentumStrategy(int absolutePeriod, int relativePeriod, double threshold) {
        this.absolutePeriod = absolutePeriod;
        this.relativePeriod = relativePeriod;
        this.threshold = threshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        int maxPeriod = Math.max(absolutePeriod, relativePeriod);
        if (prices.size() > maxPeriod + 10) {
            prices.removeFirst();
        }
        
        if (prices.size() < maxPeriod) {
            return TradeSignal.neutral("Warming up - need " + maxPeriod + " prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Absolute momentum: compare current price to price N periods ago
        double absolutePastPrice = priceArray[priceArray.length - 1 - absolutePeriod];
        double absoluteMomentum = (currentPrice - absolutePastPrice) / absolutePastPrice;
        
        // Relative momentum: compare short-term vs long-term returns
        double relPastPrice = priceArray[priceArray.length - 1 - relativePeriod];
        double relativeMomentum = (currentPrice - relPastPrice) / relPastPrice;
        
        // Calculate rolling average return for benchmark comparison
        double avgReturn = 0;
        for (int i = priceArray.length - relativePeriod; i < priceArray.length - 1; i++) {
            avgReturn += (priceArray[i + 1] - priceArray[i]) / priceArray[i];
        }
        avgReturn /= (relativePeriod - 1);
        
        int position = portfolio.getPosition(symbol);
        
        // Dual momentum entry: both absolute and relative momentum positive
        boolean absolutePositive = absoluteMomentum > threshold;
        boolean relativePositive = relativeMomentum > avgReturn * relativePeriod;
        
        if (absolutePositive && relativePositive && position <= 0) {
            return TradeSignal.longSignal(0.80, 
                String.format("Dual momentum: abs=%.2f%%, rel=%.2f%%", 
                    absoluteMomentum * 100, relativeMomentum * 100));
        }
        
        // Exit when absolute momentum turns negative
        if (position > 0 && absoluteMomentum < -threshold) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.75,
                String.format("Absolute momentum negative: %.2f%%", absoluteMomentum * 100));
        }
        
        // Short entry when both are negative (optional, for aggressive mode)
        if (absoluteMomentum < -threshold && relativeMomentum < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Dual momentum short: abs=%.2f%%, rel=%.2f%%", 
                    absoluteMomentum * 100, relativeMomentum * 100));
        }
        
        return TradeSignal.neutral(String.format("Abs=%.2f%%, Rel=%.2f%%", 
            absoluteMomentum * 100, relativeMomentum * 100));
    }

    @Override
    public String getName() {
        return String.format("Dual Momentum (%d/%d)", absolutePeriod, relativePeriod);
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
