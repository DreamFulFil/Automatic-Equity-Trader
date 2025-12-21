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
 * QualityValueStrategy
 * Type: Value + Quality
 * 
 * Academic Foundation:
 * - Piotroski (2000) - 'Value Investing: The Use of Historical Financial Information'
 * 
 * Logic:
 * Combine value (low price) with quality factors (F-Score proxy).
 */
@Slf4j
public class QualityValueStrategy implements IStrategy {
    
    private final double minBookToMarket;
    private final int minFScore;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public QualityValueStrategy(double minBookToMarket, int minFScore) {
        this.minBookToMarket = minBookToMarket;
        this.minFScore = minFScore;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 252) prices.removeFirst();
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up quality value");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate value (book-to-market proxy)
        double yearHigh = Double.MIN_VALUE;
        for (Double p : priceArray) if (p > yearHigh) yearHigh = p;
        double valueProxy = (yearHigh - currentPrice) / yearHigh;
        
        // Calculate F-Score proxy (0-9 scale)
        int fScore = 0;
        
        // Positive returns (profitability)
        int positiveReturns = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) positiveReturns++;
        }
        if ((double) positiveReturns / (priceArray.length - 1) > 0.5) fScore += 3;
        
        // Improving returns (recent vs past)
        double recentAvg = 0, pastAvg = 0;
        int half = priceArray.length / 2;
        for (int i = 0; i < half; i++) pastAvg += priceArray[i];
        for (int i = half; i < priceArray.length; i++) recentAvg += priceArray[i];
        pastAvg /= half;
        recentAvg /= (priceArray.length - half);
        if (recentAvg > pastAvg) fScore += 2;
        
        // Low volatility (leverage proxy)
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double volatility = Math.sqrt(variance / priceArray.length) / avgPrice;
        if (volatility < 0.03) fScore += 2;
        
        // Liquidity (volume proxy) - assume good
        fScore += 2;
        
        int position = portfolio.getPosition(symbol);
        
        // Quality value stock
        if (valueProxy >= minBookToMarket && fScore >= minFScore && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Quality Value: value=%.1f%%, F-Score=%d", 
                    valueProxy * 100, fScore));
        }
        
        // Exit if quality or value deteriorates
        if (position > 0 && (fScore < minFScore - 2 || valueProxy < minBookToMarket / 2)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("QV exit: F-Score=%d, value=%.1f%%", fScore, valueProxy * 100));
        }
        
        return TradeSignal.neutral(String.format("F-Score=%d, value=%.1f%%", fScore, valueProxy * 100));
    }

    @Override
    public String getName() {
        return String.format("Quality Value (B/M>%.0f%%, F>%d)", minBookToMarket * 100, minFScore);
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
