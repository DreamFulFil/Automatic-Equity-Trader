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
 * CointegrationPairsStrategy
 * Type: Statistical Arbitrage
 * 
 * Academic Foundation:
 * - Engle & Granger (1987) - 'Co-Integration and Error Correction'
 * 
 * Logic:
 * Trade mean-reverting spread between cointegrated pairs.
 * Simplified: use price ratio z-score for entry/exit.
 */
@Slf4j
public class CointegrationPairsStrategy implements IStrategy {
    
    private final String stock1;
    private final String stock2;
    private final double entryThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Deque<Double> ratioHistory = new ArrayDeque<>();
    
    public CointegrationPairsStrategy(String stock1, String stock2, double entryThreshold) {
        this.stock1 = stock1;
        this.stock2 = stock2;
        this.entryThreshold = entryThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) {
            prices.removeFirst();
        }
        
        if (prices.size() < 30) {
            return TradeSignal.neutral("Warming up cointegration");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate price ratio relative to historical average (simplified cointegration)
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        double ratio = currentPrice / avgPrice;
        ratioHistory.addLast(ratio);
        if (ratioHistory.size() > 60) {
            ratioHistory.removeFirst();
        }
        
        // Calculate z-score of ratio
        Double[] ratioArray = ratioHistory.toArray(new Double[0]);
        double ratioMean = 0;
        for (Double r : ratioArray) ratioMean += r;
        ratioMean /= ratioArray.length;
        
        double variance = 0;
        for (Double r : ratioArray) variance += Math.pow(r - ratioMean, 2);
        double stdDev = Math.sqrt(variance / ratioArray.length);
        
        double zScore = stdDev > 0 ? (ratio - ratioMean) / stdDev : 0;
        
        int position = portfolio.getPosition(symbol);
        
        // Long when z-score is significantly negative (undervalued)
        if (zScore < -entryThreshold && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Cointegration long: z=%.2f (ratio=%.3f)", zScore, ratio));
        }
        
        // Short when z-score is significantly positive (overvalued)
        if (zScore > entryThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.70,
                String.format("Cointegration short: z=%.2f (ratio=%.3f)", zScore, ratio));
        }
        
        // Exit when z-score reverts to mean
        if (position != 0 && Math.abs(zScore) < entryThreshold / 2) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.70,
                String.format("Mean reversion exit: z=%.2f", zScore));
        }
        
        return TradeSignal.neutral(String.format("Z-score: %.2f", zScore));
    }

    @Override
    public String getName() {
        return String.format("Cointegration Pairs (%s/%s)", stock1, stock2);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        ratioHistory.clear();
    }
}
