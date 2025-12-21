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
 * CashFlowValueStrategy
 * Type: Value Factor
 * 
 * Academic Foundation:
 * - Desai, Rajgopal & Venkatachalam (2004) - 'Value-Glamour and Accruals'
 * 
 * Logic:
 * Focus on free cash flow yield. Uses price stability and volume
 * patterns as proxy for cash flow quality.
 */
@Slf4j
public class CashFlowValueStrategy implements IStrategy {
    
    private final double minFCFYield;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public CashFlowValueStrategy(double minFCFYield) {
        this.minFCFYield = minFCFYield;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        
        if (prices.size() > 90) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days of data");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate average price and price stability
        double avgPrice = 0;
        for (Double p : priceArray) {
            avgPrice += p;
        }
        avgPrice /= priceArray.length;
        
        // Price stability (low variance = high cash flow quality)
        double sumSqDiff = 0;
        for (Double p : priceArray) {
            sumSqDiff += Math.pow(p - avgPrice, 2);
        }
        double priceStability = 1 / (1 + Math.sqrt(sumSqDiff / priceArray.length) / avgPrice);
        
        // Volume trend (consistent volume = cash flow quality)
        double avgVolume = 0;
        for (Long v : volumeArray) {
            avgVolume += v;
        }
        avgVolume /= volumeArray.length;
        
        double recentVolume = 0;
        for (int i = volumeArray.length - 10; i < volumeArray.length; i++) {
            recentVolume += volumeArray[i];
        }
        recentVolume /= 10;
        
        double volumeConsistency = Math.min(recentVolume / avgVolume, 1.5);
        
        // FCF yield proxy: combination of price stability, volume consistency, and value
        double valueRatio = avgPrice / currentPrice;
        double impliedFCFYield = (valueRatio - 1) * priceStability * volumeConsistency;
        
        int position = portfolio.getPosition(symbol);
        
        // High FCF yield signal
        if (impliedFCFYield > minFCFYield && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("High FCF yield: %.2f%% (stability=%.2f, vol=%.2f)", 
                    impliedFCFYield * 100, priceStability, volumeConsistency));
        }
        
        // Exit when FCF yield deteriorates
        if (position > 0 && impliedFCFYield < minFCFYield / 3) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("FCF yield exit: %.2f%%", impliedFCFYield * 100));
        }
        
        // Short overvalued stocks with poor cash flow indicators
        if (impliedFCFYield < -minFCFYield && priceStability < 0.5 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("Low FCF yield: %.2f%% (poor quality)", impliedFCFYield * 100));
        }
        
        return TradeSignal.neutral(String.format("FCF yield: %.2f%%", impliedFCFYield * 100));
    }

    @Override
    public String getName() {
        return String.format("Cash Flow Value (%.1f%%)", minFCFYield * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
    }
}
