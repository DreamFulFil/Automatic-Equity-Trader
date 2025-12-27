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
 * NetStockIssuanceStrategy
 * Type: Corporate Finance Anomaly
 * 
 * Academic Foundation:
 * - Pontiff & Woodgate (2008) - 'Share Issuance and Cross-Sectional Returns'
 * 
 * Logic:
 * Firms that buy back shares (declining share count) outperform issuers.
 * Use volume decline as proxy for buyback activity.
 */
@Slf4j
public class NetStockIssuanceStrategy implements IStrategy {
    
    private final int lookbackPeriod;
    private final double minBuybackRate;
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public NetStockIssuanceStrategy(int lookbackPeriod, double minBuybackRate) {
        this.lookbackPeriod = lookbackPeriod;
        this.minBuybackRate = minBuybackRate;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        volumes.addLast(data.getVolume());
        prices.addLast(data.getClose());
        if (volumes.size() > lookbackPeriod * 21) {
            volumes.removeFirst();
            prices.removeFirst();
        }
        
        if (volumes.size() < 60) {
            return TradeSignal.neutral("Warming up net issuance");
        }
        
        Long[] volumeArray = volumes.toArray(new Long[0]);
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate volume trend (declining = buyback proxy)
        int halfPeriod = volumeArray.length / 2;
        double earlyVolume = 0, lateVolume = 0;
        for (int i = 0; i < halfPeriod; i++) earlyVolume += volumeArray[i];
        for (int i = halfPeriod; i < volumeArray.length; i++) lateVolume += volumeArray[i];
        earlyVolume /= halfPeriod;
        lateVolume /= (volumeArray.length - halfPeriod);
        
        double volumeChange = (lateVolume - earlyVolume) / earlyVolume;
        
        // Net issuance proxy: volume decline with price stability = buyback
        double currentPrice = priceArray[priceArray.length - 1];
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        double priceDeviation = Math.abs(currentPrice - avgPrice) / avgPrice;
        
        // Buyback signal: declining volume + stable/rising price
        double buybackProxy = -volumeChange * (1 - priceDeviation);
        
        int position = portfolio.getPosition(symbol);
        
        // Buyback signal (net repurchaser)
        if (buybackProxy > minBuybackRate && currentPrice >= avgPrice * 0.95 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Buyback signal: vol change=%.1f%%, proxy=%.2f", 
                    volumeChange * 100, buybackProxy));
        }
        
        // Exit if issuance detected (volume increase + price drop)
        if (position > 0 && volumeChange > 0.2 && currentPrice < avgPrice * 0.95) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Issuance warning: vol change=%.1f%%", volumeChange * 100));
        }
        
        return TradeSignal.neutral(String.format("Buyback proxy: %.2f", buybackProxy));
    }

    @Override
    public String getName() {
        return String.format("Net Stock Issuance (%dmo, %.1f%%)", lookbackPeriod, minBuybackRate * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        volumeHistory.clear();
        priceHistory.clear();
    }
}
