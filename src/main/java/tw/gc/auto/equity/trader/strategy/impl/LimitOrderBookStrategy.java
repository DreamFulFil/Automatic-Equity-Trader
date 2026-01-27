package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.OrderBookData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.OrderBookProvider;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LimitOrderBookStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Cont, Kukanov & Stoikov (2014) - 'The Price Impact of Order Book Events'
 * 
 * Logic:
 * Trade based on order flow imbalance using real L2 order book data when available,
 * falling back to volume patterns as proxy when order book data is unavailable.
 * 
 * @since 2026-01-27 - Enhanced with OrderBookProvider for real L2 data
 */
@Slf4j
public class LimitOrderBookStrategy implements IStrategy {
    
    private final int depthLevels;
    private final double imbalanceRatio;
    private final OrderBookProvider orderBookProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    
    /**
     * Legacy constructor - uses volume proxy for imbalance.
     */
    public LimitOrderBookStrategy(int depthLevels, double imbalanceRatio) {
        this(depthLevels, imbalanceRatio, OrderBookProvider.noOp());
    }
    
    /**
     * Enhanced constructor with OrderBookProvider for real L2 data.
     */
    public LimitOrderBookStrategy(int depthLevels, double imbalanceRatio, 
                                   OrderBookProvider orderBookProvider) {
        this.depthLevels = depthLevels;
        this.imbalanceRatio = imbalanceRatio;
        this.orderBookProvider = orderBookProvider != null ? orderBookProvider : OrderBookProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        // Try to use real order book data first
        Optional<OrderBookData> orderBook = orderBookProvider.getOrderBook(symbol);
        if (orderBook.isPresent() && orderBook.get().isValid()) {
            return executeWithRealOrderBook(symbol, currentPrice, position, orderBook.get());
        }
        
        // Fallback to volume proxy
        return executeWithVolumeProxy(symbol, currentPrice, position, data);
    }
    
    /**
     * Execute using real L2 order book data.
     */
    private TradeSignal executeWithRealOrderBook(String symbol, double currentPrice, 
                                                  int position, OrderBookData orderBook) {
        Double imbalance = orderBook.getImbalance();
        Double spreadBps = orderBook.getSpreadBps();
        Double midPrice = orderBook.getMidPrice();
        
        if (imbalance == null || midPrice == null) {
            return TradeSignal.neutral("Incomplete order book data");
        }
        
        // Calculate depth-weighted imbalance for deeper analysis
        double weightedImbalance = calculateDepthWeightedImbalance(orderBook);
        
        // Combine surface imbalance with depth-weighted imbalance
        double combinedImbalance = (imbalance + weightedImbalance) / 2.0;
        
        // Adjust confidence based on spread (tighter spread = higher confidence)
        double spreadFactor = spreadBps != null && spreadBps < 50 ? 1.1 : 
                              (spreadBps != null && spreadBps > 100 ? 0.85 : 1.0);
        
        // Strong buy imbalance with real order book
        if (combinedImbalance > imbalanceRatio && currentPrice >= midPrice && position <= 0) {
            double confidence = Math.min(0.85, 0.70 + Math.abs(combinedImbalance) * 0.1) * spreadFactor;
            return TradeSignal.longSignal(confidence,
                String.format("LOB L2 buy: imb=%.2f, depth_imb=%.2f, spread=%.1fbps", 
                    imbalance, weightedImbalance, spreadBps != null ? spreadBps : 0.0));
        }
        
        // Strong sell imbalance
        if (combinedImbalance < -imbalanceRatio && position > 0) {
            double confidence = Math.min(0.85, 0.70 + Math.abs(combinedImbalance) * 0.1) * spreadFactor;
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, confidence,
                String.format("LOB L2 sell: imb=%.2f", combinedImbalance));
        }
        
        // Short on strong sell imbalance
        if (combinedImbalance < -imbalanceRatio && currentPrice < midPrice && position >= 0) {
            double confidence = Math.min(0.80, 0.65 + Math.abs(combinedImbalance) * 0.1) * spreadFactor;
            return TradeSignal.shortSignal(confidence,
                String.format("LOB L2 short: imb=%.2f", combinedImbalance));
        }
        
        return TradeSignal.neutral(String.format("LOB L2: imb=%.2f, spread=%.1fbps", 
            combinedImbalance, spreadBps != null ? spreadBps : 0.0));
    }
    
    /**
     * Calculate depth-weighted imbalance across all order book levels.
     */
    private double calculateDepthWeightedImbalance(OrderBookData orderBook) {
        double weightedBidVol = 0;
        double weightedAskVol = 0;
        double totalWeight = 0;
        
        // Weight decreases for deeper levels (level 1 = 5x weight, level 5 = 1x weight)
        for (int level = 1; level <= Math.min(depthLevels, 5); level++) {
            double weight = 6 - level; // 5, 4, 3, 2, 1
            long bidVol = orderBook.getBidDepth(level);
            long askVol = orderBook.getAskDepth(level);
            
            weightedBidVol += bidVol * weight;
            weightedAskVol += askVol * weight;
            totalWeight += weight;
        }
        
        double totalVol = weightedBidVol + weightedAskVol;
        return totalVol > 0 ? (weightedBidVol - weightedAskVol) / totalVol : 0.0;
    }
    
    /**
     * Fallback: Execute using volume proxy for imbalance.
     */
    private TradeSignal executeWithVolumeProxy(String symbol, double currentPrice, 
                                               int position, MarketData data) {
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > depthLevels * 2) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < depthLevels) {
            return TradeSignal.neutral("Warming up LOB");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate buy/sell volume imbalance proxy
        long upVolume = 0, downVolume = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) upVolume += volumeArray[i];
            else downVolume += volumeArray[i];
        }
        
        double imbalance = (upVolume - downVolume) / (double)(upVolume + downVolume + 1);
        
        double avgPrice = 0;
        for (Double p : priceArray) avgPrice += p;
        avgPrice /= priceArray.length;
        
        // Strong buy imbalance
        if (imbalance > imbalanceRatio && currentPrice >= avgPrice && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("LOB buy imbalance: %.2f > %.2f", imbalance, imbalanceRatio));
        }
        
        // Strong sell imbalance
        if (imbalance < -imbalanceRatio && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("LOB sell imbalance: %.2f", imbalance));
        }
        
        // Short on strong sell imbalance
        if (imbalance < -imbalanceRatio && currentPrice < avgPrice && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("LOB short: imbalance=%.2f", imbalance));
        }
        
        return TradeSignal.neutral(String.format("LOB imbalance: %.2f", imbalance));
    }

    @Override
    public String getName() {
        return String.format("Limit Order Book (%d levels, %.1f)", depthLevels, imbalanceRatio);
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
