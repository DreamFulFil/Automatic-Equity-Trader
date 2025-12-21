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
 * AssetGrowthAnomalyStrategy
 * Type: Accounting Anomaly
 * 
 * Academic Foundation:
 * - Cooper, Gulen & Schill (2008) - 'Asset Growth and the Cross-Section'
 * 
 * Logic:
 * Short high asset growth, long low asset growth.
 * Use market cap growth proxy (price * volume trends).
 */
@Slf4j
public class AssetGrowthAnomalyStrategy implements IStrategy {
    
    private final int lookbackYears;
    private final double growthThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    public AssetGrowthAnomalyStrategy(int lookbackYears, double growthThreshold) {
        this.lookbackYears = lookbackYears;
        this.growthThreshold = growthThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        int lookbackDays = lookbackYears * 252;
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > lookbackDays) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate market cap proxy growth
        double currentMktCap = priceArray[priceArray.length - 1] * volumeArray[volumeArray.length - 1];
        int pastIdx = Math.max(0, priceArray.length - Math.min(lookbackDays, priceArray.length));
        double pastMktCap = priceArray[pastIdx] * volumeArray[pastIdx];
        
        double assetGrowth = pastMktCap > 0 ? (currentMktCap - pastMktCap) / pastMktCap : 0;
        
        // Also consider price growth
        double priceGrowth = (priceArray[priceArray.length - 1] - priceArray[pastIdx]) / priceArray[pastIdx];
        
        double combinedGrowth = (assetGrowth + priceGrowth) / 2;
        
        int position = portfolio.getPosition(symbol);
        
        // Low asset growth = long (value)
        if (combinedGrowth < growthThreshold && combinedGrowth > -0.5 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Low asset growth: %.2f%% < %.2f%%", 
                    combinedGrowth * 100, growthThreshold * 100));
        }
        
        // High asset growth = short (overvalued)
        if (combinedGrowth > growthThreshold * 3 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("High asset growth: %.2f%% (anomaly short)", 
                    combinedGrowth * 100));
        }
        
        // Exit long if growth accelerates
        if (position > 0 && combinedGrowth > growthThreshold * 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.60,
                String.format("Growth accelerated: %.2f%%", combinedGrowth * 100));
        }
        
        return TradeSignal.neutral(String.format("Asset growth: %.2f%%", combinedGrowth * 100));
    }

    @Override
    public String getName() {
        return String.format("Asset Growth Anomaly (%dy, %.0f%%)", lookbackYears, growthThreshold * 100);
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
