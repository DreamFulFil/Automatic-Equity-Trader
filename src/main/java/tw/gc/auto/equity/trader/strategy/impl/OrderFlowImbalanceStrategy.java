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
 * OrderFlowImbalanceStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Chordia & Subrahmanyam (2004) - 'Order Imbalance and Individual Stock Returns'
 * 
 * Logic:
 * Trade based on buy/sell order flow imbalance using real L2 order book data 
 * when available, falling back to volume and price direction proxy.
 * 
 * @since 2026-01-27 - Enhanced with OrderBookProvider for real L2 data
 */
@Slf4j
public class OrderFlowImbalanceStrategy implements IStrategy {
    
    private final int windowMinutes;
    private final double imbalanceThreshold;
    private final OrderBookProvider orderBookProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Long>> volumeHistory = new HashMap<>();
    private final Map<String, Deque<Double>> imbalanceHistory = new HashMap<>();
    
    /**
     * Legacy constructor - uses volume proxy for imbalance.
     */
    public OrderFlowImbalanceStrategy(int windowMinutes, double imbalanceThreshold) {
        this(windowMinutes, imbalanceThreshold, OrderBookProvider.noOp());
    }
    
    /**
     * Enhanced constructor with OrderBookProvider for real L2 data.
     */
    public OrderFlowImbalanceStrategy(int windowMinutes, double imbalanceThreshold,
                                       OrderBookProvider orderBookProvider) {
        this.windowMinutes = windowMinutes;
        this.imbalanceThreshold = imbalanceThreshold;
        this.orderBookProvider = orderBookProvider != null ? orderBookProvider : OrderBookProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        int position = portfolio.getPosition(symbol);
        
        // Try to use real order book data first
        Optional<OrderBookData> orderBook = orderBookProvider.getOrderBook(symbol);
        if (orderBook.isPresent() && orderBook.get().isValid()) {
            return executeWithRealOrderBook(symbol, position, orderBook.get());
        }
        
        // Fallback to volume proxy
        return executeWithVolumeProxy(symbol, position, data);
    }
    
    /**
     * Execute using real L2 order book data.
     */
    private TradeSignal executeWithRealOrderBook(String symbol, int position, 
                                                  OrderBookData orderBook) {
        Double imbalance = orderBook.getImbalance();
        Long bidVol = orderBook.getTotalBidVolume();
        Long askVol = orderBook.getTotalAskVolume();
        
        if (imbalance == null) {
            return TradeSignal.neutral("No imbalance data available");
        }
        
        // Track imbalance history for momentum detection
        Deque<Double> history = imbalanceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        history.addLast(imbalance);
        if (history.size() > windowMinutes) {
            history.removeFirst();
        }
        
        // Calculate imbalance trend (is it increasing or decreasing?)
        double imbalanceTrend = 0;
        if (history.size() >= 3) {
            Double[] histArray = history.toArray(new Double[0]);
            double recent = histArray[histArray.length - 1];
            double older = histArray[0];
            imbalanceTrend = recent - older;
        }
        
        // Adjust confidence based on trend alignment
        double trendFactor = imbalanceTrend * imbalance > 0 ? 1.1 : 0.95; // Aligned = higher confidence
        
        // Strong buy imbalance with accelerating trend
        if (imbalance > imbalanceThreshold && position <= 0) {
            double confidence = Math.min(0.85, 0.70 + Math.abs(imbalance) * 0.1) * trendFactor;
            return TradeSignal.longSignal(confidence,
                String.format("Order flow L2 buy: imb=%.2f (bid=%d, ask=%d), trend=%.2f", 
                    imbalance, bidVol != null ? bidVol : 0, askVol != null ? askVol : 0, imbalanceTrend));
        }
        
        // Strong sell imbalance
        if (imbalance < -imbalanceThreshold && position > 0) {
            double confidence = Math.min(0.85, 0.70 + Math.abs(imbalance) * 0.1) * trendFactor;
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, confidence,
                String.format("Order flow L2 sell: imb=%.2f", imbalance));
        }
        
        // Short on extreme sell imbalance
        if (imbalance < -imbalanceThreshold && position >= 0) {
            double confidence = Math.min(0.80, 0.65 + Math.abs(imbalance) * 0.1) * trendFactor;
            return TradeSignal.shortSignal(confidence,
                String.format("Order flow L2 short: imb=%.2f", imbalance));
        }
        
        return TradeSignal.neutral(String.format("Order flow L2: %.2f, trend=%.2f", 
            imbalance, imbalanceTrend));
    }
    
    /**
     * Fallback: Execute using volume proxy for imbalance.
     */
    private TradeSignal executeWithVolumeProxy(String symbol, int position, MarketData data) {
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Long> volumes = volumeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        volumes.addLast(data.getVolume());
        if (prices.size() > windowMinutes) {
            prices.removeFirst();
            volumes.removeFirst();
        }
        
        if (prices.size() < windowMinutes / 2) {
            return TradeSignal.neutral("Warming up order flow");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        Long[] volumeArray = volumes.toArray(new Long[0]);
        
        // Calculate order flow imbalance
        long buyVolume = 0, sellVolume = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) buyVolume += volumeArray[i];
            else if (priceArray[i] < priceArray[i-1]) sellVolume += volumeArray[i];
        }
        
        long totalVolume = buyVolume + sellVolume;
        double imbalance = totalVolume > 0 ? (double)(buyVolume - sellVolume) / totalVolume : 0;
        
        // Strong buy imbalance
        if (imbalance > imbalanceThreshold && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Order flow buy: imbalance=%.2f (buy=%d, sell=%d)", 
                    imbalance, buyVolume, sellVolume));
        }
        
        // Strong sell imbalance
        if (imbalance < -imbalanceThreshold && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Order flow sell: imbalance=%.2f", imbalance));
        }
        
        // Short on extreme sell imbalance
        if (imbalance < -imbalanceThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Order flow short: imbalance=%.2f", imbalance));
        }
        
        return TradeSignal.neutral(String.format("Order flow: %.2f", imbalance));
    }

    @Override
    public String getName() {
        return String.format("Order Flow Imbalance (%dmin, %.1f)", windowMinutes, imbalanceThreshold);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volumeHistory.clear();
        imbalanceHistory.clear();
    }
}
