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
 * PriceTrendStrengthStrategy
 * Type: Trend Following
 * 
 * Academic Foundation:
 * - Faber (2007) - 'A Quantitative Approach to Tactical Asset Allocation'
 * 
 * Logic:
 * Measure trend quality (R-squared of price trend), not just direction.
 */
@Slf4j
public class PriceTrendStrengthStrategy implements IStrategy {
    
    private final int trendPeriod;
    private final double qualityThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public PriceTrendStrengthStrategy(int trendPeriod, double qualityThreshold) {
        this.trendPeriod = trendPeriod;
        this.qualityThreshold = qualityThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > trendPeriod + 10) prices.removeFirst();
        
        if (prices.size() < trendPeriod) {
            return TradeSignal.neutral("Warming up trend strength");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate linear regression for trend quality
        int n = Math.min(trendPeriod, priceArray.length);
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = priceArray[priceArray.length - n + i];
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }
        
        // Calculate R-squared (trend quality)
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;
        
        double ssRes = 0, ssTot = 0;
        double meanY = sumY / n;
        for (int i = 0; i < n; i++) {
            double y = priceArray[priceArray.length - n + i];
            double yPred = slope * i + intercept;
            ssRes += Math.pow(y - yPred, 2);
            ssTot += Math.pow(y - meanY, 2);
        }
        double rSquared = ssTot > 0 ? 1 - (ssRes / ssTot) : 0;
        
        // Trend direction and strength
        boolean uptrend = slope > 0;
        double trendStrength = rSquared * Math.abs(slope) / meanY * 100;
        
        int position = portfolio.getPosition(symbol);
        
        // Strong uptrend
        if (uptrend && rSquared > qualityThreshold && position <= 0) {
            return TradeSignal.longSignal(0.70 + rSquared * 0.2,
                String.format("Strong uptrend: R²=%.2f, slope=%.4f", rSquared, slope));
        }
        
        // Trend weakening or reversing
        if (position > 0 && (!uptrend || rSquared < qualityThreshold / 2)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Trend weakening: R²=%.2f", rSquared));
        }
        
        // Strong downtrend
        if (!uptrend && rSquared > qualityThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Strong downtrend: R²=%.2f, slope=%.4f", rSquared, slope));
        }
        
        return TradeSignal.neutral(String.format("Trend R²=%.2f, slope=%.4f", rSquared, slope));
    }

    @Override
    public String getName() {
        return String.format("Trend Strength (%dd, R²>%.1f)", trendPeriod, qualityThreshold);
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
