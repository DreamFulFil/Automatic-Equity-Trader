package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.IndexData;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.IndexDataProvider;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * IndexArbitrageStrategy
 * Type: Statistical Arbitrage
 * 
 * Academic Foundation:
 * - Pontiff (1996) - 'Costly Arbitrage: Evidence from Closed-End Funds'
 * 
 * Logic:
 * Trade deviation from fair value relative to index when price diverges.
 * Uses real TAIEX index data when available via IndexDataProvider.
 * 
 * @since 2026-01-26 - Phase 3: Updated to use real index data
 */
@Slf4j
public class IndexArbitrageStrategy implements IStrategy {
    
    private final String indexSymbol;
    private final double threshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Double> lastIndexRatio = new HashMap<>();
    private IndexDataProvider indexDataProvider;
    
    /**
     * Legacy constructor (uses moving average proxy for index).
     */
    public IndexArbitrageStrategy(String indexSymbol, double threshold) {
        this.indexSymbol = indexSymbol;
        this.threshold = threshold;
        this.indexDataProvider = IndexDataProvider.noOp();
    }

    /**
     * Constructor with real index data support.
     * 
     * @param indexSymbol Index to compare against (e.g., "^TWII")
     * @param threshold Deviation threshold for signals (e.g., 0.02 for 2%)
     * @param indexDataProvider Provider for real index data
     */
    public IndexArbitrageStrategy(String indexSymbol, double threshold, IndexDataProvider indexDataProvider) {
        this.indexSymbol = indexSymbol;
        this.threshold = threshold;
        this.indexDataProvider = indexDataProvider != null ? indexDataProvider : IndexDataProvider.noOp();
    }

    /**
     * Set the index data provider (for dependency injection after construction).
     */
    public void setIndexDataProvider(IndexDataProvider provider) {
        this.indexDataProvider = provider != null ? provider : IndexDataProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 60) prices.removeFirst();
        
        if (prices.size() < 20) {
            return TradeSignal.neutral("Warming up index arb");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Try to use real index data
        Optional<IndexData> indexData = indexDataProvider.getLatestIndex(indexSymbol);
        
        double deviation;
        String deviationSource;
        
        if (indexData.isPresent() && indexData.get().getCloseValue() != null) {
            // Use real index comparison
            double indexValue = indexData.get().getCloseValue();
            
            // Calculate stock/index ratio
            double currentRatio = currentPrice / indexValue;
            
            // Get historical average ratio
            double avgRatio = lastIndexRatio.getOrDefault(symbol, currentRatio);
            
            // Update with exponential moving average
            double newAvgRatio = avgRatio * 0.95 + currentRatio * 0.05;
            lastIndexRatio.put(symbol, newAvgRatio);
            
            // Deviation from historical ratio
            deviation = (currentRatio - avgRatio) / avgRatio;
            deviationSource = String.format("vs %s (%.0f)", indexSymbol, indexValue);
            
            log.debug("Index arb {}: ratio={:.4f}, avg={:.4f}, dev={:.2f}%", 
                    symbol, currentRatio, avgRatio, deviation * 100);
        } else {
            // Fallback: use moving average as proxy
            double fairValue = 0;
            for (Double p : priceArray) fairValue += p;
            fairValue /= priceArray.length;
            
            deviation = (currentPrice - fairValue) / fairValue;
            deviationSource = "vs MA proxy";
        }
        
        int position = portfolio.getPosition(symbol);
        
        // Check market trend for additional context
        int marketTrend = indexDataProvider.getMarketTrend();
        
        // Adjust threshold based on market conditions
        double adjustedThreshold = threshold;
        if (marketTrend == -1) {
            // More conservative in bear market
            adjustedThreshold *= 1.5;
        }
        
        // Stock undervalued vs index
        if (deviation < -adjustedThreshold && position <= 0) {
            double confidence = Math.min(0.9, 0.6 + Math.abs(deviation) * 2);
            return TradeSignal.longSignal(confidence,
                String.format("Index arb long: %.2f%% below fair value %s", 
                    Math.abs(deviation) * 100, deviationSource));
        }
        
        // Stock overvalued vs index - exit long
        if (deviation > adjustedThreshold && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Index arb exit: %.2f%% above fair value %s", 
                    deviation * 100, deviationSource));
        }
        
        // Short overvalued (only in neutral/bear market)
        if (deviation > adjustedThreshold * 2 && position >= 0 && marketTrend <= 0) {
            double confidence = Math.min(0.8, 0.5 + Math.abs(deviation));
            return TradeSignal.shortSignal(confidence,
                String.format("Index arb short: %.2f%% overvalued %s", 
                    deviation * 100, deviationSource));
        }
        
        return TradeSignal.neutral(String.format("Index deviation: %.2f%% %s", 
            deviation * 100, deviationSource));
    }

    @Override
    public String getName() {
        return String.format("Index Arbitrage (%s, %.1f%%)", indexSymbol, threshold * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        lastIndexRatio.clear();
    }
}
