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
 * Pairs Trading / Statistical Arbitrage Strategy
 * Type: Market Neutral / Arbitrage
 * 
 * Academic Foundation:
 * - Gatev, Goetzmann & Rouwenhorst (2006): "Pairs Trading: Performance of a Relative-Value Arbitrage Rule"
 * - Vidyamurthy (2004): "Pairs Trading: Quantitative Methods and Analysis"
 * 
 * Logic:
 * - Identify two cointegrated securities (historically correlated)
 * - Long underperforming stock, short outperforming stock when spread widens
 * - Close positions when spread returns to mean
 * 
 * When to use:
 * - Market-neutral strategies
 * - Hedged exposure
 * - Low correlation to broader market
 * 
 * Note: Simplified version - production would need cointegration testing
 */
@Slf4j
public class PairsCorrelationStrategy implements IStrategy {
    
    private final String primarySymbol;
    private final String pairSymbol;
    private final int lookbackPeriod;
    private final double entryThreshold;
    
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public PairsCorrelationStrategy(String primarySymbol, String pairSymbol, int lookbackPeriod, double entryThreshold) {
        this.primarySymbol = primarySymbol;
        this.pairSymbol = pairSymbol;
        this.lookbackPeriod = lookbackPeriod;
        this.entryThreshold = entryThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        
        if (!symbol.equals(primarySymbol) && !symbol.equals(pairSymbol)) {
            return TradeSignal.neutral("Not part of pair");
        }
        
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(data.getClose());
        
        if (prices.size() > lookbackPeriod) {
            prices.removeFirst();
        }
        
        if (prices.size() < lookbackPeriod) {
            return TradeSignal.neutral("Warming up pair data");
        }
        
        // Simplified: Calculate price ratio between pairs
        // Production: Use cointegration test (Engle-Granger)
        Deque<Double> primaryPrices = priceHistory.get(primarySymbol);
        Deque<Double> pairPrices = priceHistory.get(pairSymbol);
        
        if (primaryPrices == null || pairPrices == null ||
            primaryPrices.size() < lookbackPeriod || pairPrices.size() < lookbackPeriod) {
            return TradeSignal.neutral("Waiting for both pairs data");
        }
        
        // Calculate ratio and its z-score
        Double[] primaryArr = primaryPrices.toArray(new Double[0]);
        Double[] pairArr = pairPrices.toArray(new Double[0]);
        
        double[] ratios = new double[lookbackPeriod];
        for (int i = 0; i < lookbackPeriod; i++) {
            ratios[i] = primaryArr[i] / pairArr[i];
        }
        
        double meanRatio = 0;
        for (double r : ratios) meanRatio += r;
        meanRatio /= ratios.length;
        
        double variance = 0;
        for (double r : ratios) variance += Math.pow(r - meanRatio, 2);
        variance /= ratios.length;
        double stdDev = Math.sqrt(variance);
        
        double currentRatio = primaryArr[primaryArr.length - 1] / pairArr[pairArr.length - 1];
        double zScore = (currentRatio - meanRatio) / stdDev;
        
        int position = portfolio.getPosition(symbol);
        
        // Trade primary symbol based on spread
        if (symbol.equals(primarySymbol)) {
            if (zScore < -entryThreshold && position <= 0) {
                return TradeSignal.longSignal(0.7, String.format("Pairs: primary undervalued, z=%.2f", zScore));
            } else if (zScore > entryThreshold && position >= 0) {
                return TradeSignal.shortSignal(0.7, String.format("Pairs: primary overvalued, z=%.2f", zScore));
            } else if (position != 0 && Math.abs(zScore) < 0.5) {
                return TradeSignal.exitSignal(
                    position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                    0.6,
                    "Pairs: spread converged"
                );
            }
        }
        
        return TradeSignal.neutral(String.format("Pairs z-score: %.2f", zScore));
    }

    @Override
    public String getName() {
        return String.format("Pairs Trading (%s/%s)", primarySymbol, pairSymbol);
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
