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
import java.util.TreeMap;

/**
 * VolumeProfileStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Easley & O'Hara (1987) - 'Price, Trade Size, and Information'
 * 
 * Logic:
 * Trade around high-volume price levels (value areas).
 * Buy at lower edge of value area, sell at upper edge.
 */
@Slf4j
public class VolumeProfileStrategy implements IStrategy {
    
    private final int profileBins;
    private final double valueAreaPercent;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public VolumeProfileStrategy(int profileBins, double valueAreaPercent) {
        this.profileBins = profileBins;
        this.valueAreaPercent = valueAreaPercent;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        
        if (prices.size() > profileBins * 3) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < profileBins) {
            return TradeSignal.neutral("Warming up - need " + profileBins + " bars");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Build volume profile
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (Double p : priceArray) {
            if (p < minPrice) minPrice = p;
            if (p > maxPrice) maxPrice = p;
        }
        
        double binSize = (maxPrice - minPrice) / profileBins;
        if (binSize <= 0) {
            return TradeSignal.neutral("Insufficient price range");
        }
        
        // Aggregate volume by price bin
        TreeMap<Integer, Long> volumeByBin = new TreeMap<>();
        long totalVolume = 0;
        for (int i = 0; i < priceArray.length; i++) {
            int bin = (int) ((priceArray[i] - minPrice) / binSize);
            bin = Math.min(bin, profileBins - 1);
            volumeByBin.merge(bin, volumeArray[i], Long::sum);
            totalVolume += volumeArray[i];
        }
        
        // Find Point of Control (POC) - highest volume bin
        int pocBin = 0;
        long maxBinVolume = 0;
        for (Map.Entry<Integer, Long> entry : volumeByBin.entrySet()) {
            if (entry.getValue() > maxBinVolume) {
                maxBinVolume = entry.getValue();
                pocBin = entry.getKey();
            }
        }
        
        double pocPrice = minPrice + (pocBin + 0.5) * binSize;
        
        // Calculate value area (covers valueAreaPercent of volume)
        long targetVolume = (long) (totalVolume * valueAreaPercent);
        long accumulatedVolume = volumeByBin.getOrDefault(pocBin, 0L);
        int valLow = pocBin, valHigh = pocBin;
        
        while (accumulatedVolume < targetVolume && (valLow > 0 || valHigh < profileBins - 1)) {
            Long lowVol = valLow > 0 ? volumeByBin.getOrDefault(valLow - 1, 0L) : 0L;
            Long highVol = valHigh < profileBins - 1 ? volumeByBin.getOrDefault(valHigh + 1, 0L) : 0L;
            
            if (lowVol >= highVol && valLow > 0) {
                valLow--;
                accumulatedVolume += lowVol;
            } else if (valHigh < profileBins - 1) {
                valHigh++;
                accumulatedVolume += highVol;
            } else {
                break;
            }
        }
        
        double valLowPrice = minPrice + valLow * binSize;
        double valHighPrice = minPrice + (valHigh + 1) * binSize;
        
        int position = portfolio.getPosition(symbol);
        
        // Buy at lower value area edge
        if (currentPrice <= valLowPrice * 1.01 && currentPrice > minPrice && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Volume profile buy: price=%.2f near VAL=%.2f (POC=%.2f)", 
                    currentPrice, valLowPrice, pocPrice));
        }
        
        // Sell at upper value area edge
        if (currentPrice >= valHighPrice * 0.99 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Volume profile sell: price=%.2f near VAH=%.2f", 
                    currentPrice, valHighPrice));
        }
        
        // Short if price breaks above value area with weak volume
        long currentVol = volumeArray[volumeArray.length - 1];
        double avgVol = (double) totalVolume / priceArray.length;
        if (currentPrice > valHighPrice && currentVol < avgVol * 0.8 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("Weak breakout short: price=%.2f > VAH=%.2f, vol weak", 
                    currentPrice, valHighPrice));
        }
        
        return TradeSignal.neutral(String.format("POC=%.2f, VA=[%.2f-%.2f]", 
            pocPrice, valLowPrice, valHighPrice));
    }

    @Override
    public String getName() {
        return String.format("Volume Profile (%d bins, %.0f%% VA)", profileBins, valueAreaPercent * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
    }
}
