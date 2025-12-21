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
 * AccrualAnomalyStrategy
 * Type: Accounting Anomaly
 * 
 * Academic Foundation:
 * - Sloan (1996) - 'Do Stock Prices Fully Reflect Information in Accruals'
 * 
 * Logic:
 * Low accruals outperform high accruals. Use price volatility and volume
 * patterns as proxy for earnings quality (cash vs accrual).
 */
@Slf4j
public class AccrualAnomalyStrategy implements IStrategy {
    
    private final double maxAccrualRatio;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public AccrualAnomalyStrategy(double maxAccrualRatio) {
        this.maxAccrualRatio = maxAccrualRatio;
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
            return TradeSignal.neutral("Warming up - need 60 days");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate price volatility (high vol = high accruals proxy)
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        double variance = 0;
        for (Double p : priceArray) variance += Math.pow(p - avgPrice, 2);
        double volatility = Math.sqrt(variance / priceArray.length) / avgPrice;
        
        // Volume consistency (consistent = cash-based, erratic = accrual-based)
        double avgVol = 0;
        for (Long v : volumeArray) avgVol += v;
        avgVol /= volumeArray.length;
        
        double volVariance = 0;
        for (Long v : volumeArray) volVariance += Math.pow(v - avgVol, 2);
        double volConsistency = 1 / (1 + Math.sqrt(volVariance / volumeArray.length) / avgVol);
        
        // Accrual ratio proxy: high volatility + inconsistent volume = high accruals
        double accrualProxy = volatility * (1 - volConsistency);
        
        int position = portfolio.getPosition(symbol);
        
        // Low accrual signal (quality earnings)
        if (accrualProxy < maxAccrualRatio && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Low accruals: proxy=%.3f < %.3f (quality)", 
                    accrualProxy, maxAccrualRatio));
        }
        
        // Exit if accruals increase
        if (position > 0 && accrualProxy > maxAccrualRatio * 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Accrual increase: proxy=%.3f", accrualProxy));
        }
        
        // Short high accrual stocks
        if (accrualProxy > maxAccrualRatio * 3 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("High accruals: proxy=%.3f", accrualProxy));
        }
        
        return TradeSignal.neutral(String.format("Accrual proxy: %.3f", accrualProxy));
    }

    @Override
    public String getName() {
        return String.format("Accrual Anomaly (max=%.2f)", maxAccrualRatio);
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
