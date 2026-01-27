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
 * TradeVelocityStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Easley, Lopez de Prado & O'Hara (2012) - 'Flow Toxicity and Liquidity'
 * 
 * Logic:
 * Measure speed of trades to detect toxic flow using order book data when available.
 * High velocity with thin order book = informed trading (toxic).
 * Low velocity with deep order book = uninformed trading (safe).
 * 
 * @since 2026-01-27 - Enhanced with OrderBookProvider for real L2 data
 */
@Slf4j
public class TradeVelocityStrategy implements IStrategy {
    
    private final int measurementWindow;
    private final double toxicityThreshold;
    private final OrderBookProvider orderBookProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    private final Map<String, Deque<Double>> spreadHistory = new HashMap<>();
    
    /**
     * Legacy constructor - uses price/volume velocity only.
     */
    public TradeVelocityStrategy(int measurementWindow, double toxicityThreshold) {
        this(measurementWindow, toxicityThreshold, OrderBookProvider.noOp());
    }
    
    /**
     * Enhanced constructor with OrderBookProvider for spread-based toxicity.
     */
    public TradeVelocityStrategy(int measurementWindow, double toxicityThreshold,
                                  OrderBookProvider orderBookProvider) {
        this.measurementWindow = measurementWindow;
        this.toxicityThreshold = toxicityThreshold;
        this.orderBookProvider = orderBookProvider != null ? orderBookProvider : OrderBookProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        int position = portfolio.getPosition(symbol);
        
        // Always update price/volume history for velocity calculation
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > measurementWindow + 5) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < measurementWindow) {
            return TradeSignal.neutral("Warming up velocity");
        }
        
        // Try to use order book data for enhanced toxicity detection
        Optional<OrderBookData> orderBook = orderBookProvider.getOrderBook(symbol);
        if (orderBook.isPresent() && orderBook.get().isValid()) {
            return executeWithOrderBook(symbol, position, prices, volumes, data, orderBook.get());
        }
        
        // Fallback to volume-based velocity
        return executeWithVolumeProxy(symbol, position, prices, volumes);
    }
    
    /**
     * Execute using real order book data for enhanced toxicity detection.
     * Toxicity increases when: high velocity + wide spread + thin depth
     */
    private TradeSignal executeWithOrderBook(String symbol, int position,
                                              Deque<Double> prices, Deque<Long> volumes,
                                              MarketData data, OrderBookData orderBook) {
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate base velocity
        double totalPriceChange = Math.abs(priceArray[priceArray.length - 1] - priceArray[0]);
        long totalVolume = 0;
        for (Long v : volumeArray) totalVolume += v;
        double velocity = totalVolume > 0 ? totalPriceChange / (totalVolume / 1000.0) : 0;
        
        // Get order book metrics
        Double spreadBps = orderBook.getSpreadBps();
        Double imbalance = orderBook.getImbalance();
        Long totalBidVol = orderBook.getTotalBidVolume();
        Long totalAskVol = orderBook.getTotalAskVolume();
        int depthLevels = orderBook.getDepthLevels();
        
        // Track spread history for trend analysis
        Deque<Double> spreads = spreadHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        if (spreadBps != null) {
            spreads.addLast(spreadBps);
            if (spreads.size() > measurementWindow) {
                spreads.removeFirst();
            }
        }
        
        // Calculate spread trend (widening = more toxic)
        double spreadTrend = 0;
        if (spreads.size() >= 3) {
            Double[] spreadArray = spreads.toArray(new Double[0]);
            spreadTrend = spreadArray[spreadArray.length - 1] - spreadArray[0];
        }
        
        // Calculate average velocity for baseline
        double avgVelocity = calculateAverageVelocity(priceArray, volumeArray);
        double velocityRatio = avgVelocity > 0 ? velocity / avgVelocity : 1;
        
        // Enhanced toxicity calculation:
        // - Base: velocity ratio
        // - Modifier 1: Wide spread increases toxicity
        // - Modifier 2: Thin depth increases toxicity
        // - Modifier 3: Spread widening increases toxicity
        double spreadFactor = spreadBps != null && spreadBps > 30 ? 1.2 : 1.0;
        double depthFactor = depthLevels < 3 ? 1.3 : 1.0;
        double trendFactor = spreadTrend > 5 ? 1.15 : (spreadTrend < -5 ? 0.9 : 1.0);
        
        double toxicity = velocityRatio * spreadFactor * depthFactor * trendFactor;
        
        // Price direction
        double priceChange = (priceArray[priceArray.length - 1] - priceArray[0]) / priceArray[0];
        
        // Low toxicity + upward trend + buy pressure = strong buy
        if (toxicity < toxicityThreshold && priceChange > 0 && position <= 0) {
            double confidence = 0.65;
            if (imbalance != null && imbalance > 0.2) {
                confidence = 0.75; // Higher confidence with buy pressure
            }
            return TradeSignal.longSignal(confidence,
                String.format("Low toxicity L2: tox=%.2f, spread=%.1fbps, depth=%d, change=%.2f%%", 
                    toxicity, spreadBps != null ? spreadBps : 0.0, depthLevels, priceChange * 100));
        }
        
        // High toxicity = avoid/exit immediately
        if (toxicity > toxicityThreshold * 2 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.80,
                String.format("High toxicity L2 exit: tox=%.2f, spread widening=%.1f", 
                    toxicity, spreadTrend));
        }
        
        // Toxic downtrend with sell pressure = short
        if (toxicity > toxicityThreshold && priceChange < 0 && position >= 0) {
            double confidence = 0.60;
            if (imbalance != null && imbalance < -0.2) {
                confidence = 0.70; // Higher confidence with sell pressure
            }
            return TradeSignal.shortSignal(confidence,
                String.format("Toxic flow L2 short: tox=%.2f, imb=%.2f", 
                    toxicity, imbalance != null ? imbalance : 0.0));
        }
        
        return TradeSignal.neutral(String.format("Toxicity L2: %.2f, spread=%.1fbps", 
            toxicity, spreadBps != null ? spreadBps : 0.0));
    }
    
    /**
     * Fallback: Calculate toxicity using volume-based velocity only.
     */
    private TradeSignal executeWithVolumeProxy(String symbol, int position,
                                                Deque<Double> prices, Deque<Long> volumes) {
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate price velocity (price change per unit volume)
        double totalPriceChange = Math.abs(priceArray[priceArray.length - 1] - priceArray[0]);
        long totalVolume = 0;
        for (Long v : volumeArray) totalVolume += v;
        
        double velocity = totalVolume > 0 ? totalPriceChange / (totalVolume / 1000.0) : 0;
        double avgVelocity = calculateAverageVelocity(priceArray, volumeArray);
        double toxicity = avgVelocity > 0 ? velocity / avgVelocity : 1;
        
        // Low toxicity + upward trend = safe to buy
        double priceChange = (priceArray[priceArray.length - 1] - priceArray[0]) / priceArray[0];
        if (toxicity < toxicityThreshold && priceChange > 0 && position <= 0) {
            return TradeSignal.longSignal(0.65,
                String.format("Low toxicity buy: tox=%.2f, change=%.2f%%", 
                    toxicity, priceChange * 100));
        }
        
        // High toxicity = avoid/exit
        if (toxicity > toxicityThreshold * 2 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("High toxicity exit: %.2f", toxicity));
        }
        
        // Short on toxic downtrend
        if (toxicity > toxicityThreshold && priceChange < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("Toxic flow short: tox=%.2f, change=%.2f%%", 
                    toxicity, priceChange * 100));
        }
        
        return TradeSignal.neutral(String.format("Toxicity: %.2f", toxicity));
    }
    
    /**
     * Calculate average tick velocity for baseline comparison.
     */
    private double calculateAverageVelocity(Double[] priceArray, Long[] volumeArray) {
        double avgVelocity = 0;
        int count = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (volumeArray[i] > 0) {
                avgVelocity += Math.abs(priceArray[i] - priceArray[i-1]) / (volumeArray[i] / 1000.0);
                count++;
            }
        }
        return count > 0 ? avgVelocity / count : 0;
    }

    @Override
    public String getName() {
        return String.format("Trade Velocity (%d, tox<%.1f)", measurementWindow, toxicityThreshold);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
        spreadHistory.clear();
    }
}
