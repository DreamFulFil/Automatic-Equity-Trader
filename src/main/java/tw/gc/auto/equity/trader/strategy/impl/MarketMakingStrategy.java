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
 * MarketMakingStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Ho & Stoll (1981) - 'Optimal Dealer Pricing Under Transactions'
 * 
 * Logic:
 * Provide liquidity and capture spread using real order book data when available.
 * Buy on dips when price drops to bid, sell on pops when price rises to ask.
 * Uses real bid-ask spread for better entry/exit timing.
 * 
 * @since 2026-01-27 - Enhanced with OrderBookProvider for real L2 data
 */
@Slf4j
public class MarketMakingStrategy implements IStrategy {
    
    private final double spreadCapture;
    private final int maxInventory;
    private final OrderBookProvider orderBookProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> inventory = new HashMap<>();
    
    /**
     * Legacy constructor - uses price change as spread proxy.
     */
    public MarketMakingStrategy(double spreadCapture, int maxInventory) {
        this(spreadCapture, maxInventory, OrderBookProvider.noOp());
    }
    
    /**
     * Enhanced constructor with OrderBookProvider for real spread data.
     */
    public MarketMakingStrategy(double spreadCapture, int maxInventory,
                                 OrderBookProvider orderBookProvider) {
        this.spreadCapture = spreadCapture;
        this.maxInventory = maxInventory;
        this.orderBookProvider = orderBookProvider != null ? orderBookProvider : OrderBookProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        int position = portfolio.getPosition(symbol);
        int currentInventory = inventory.getOrDefault(symbol, 0);
        
        // Try to use real order book data first
        Optional<OrderBookData> orderBook = orderBookProvider.getOrderBook(symbol);
        if (orderBook.isPresent() && orderBook.get().isValid()) {
            return executeWithRealOrderBook(symbol, position, currentInventory, data, orderBook.get());
        }
        
        // Fallback to price change proxy
        return executeWithPriceProxy(symbol, position, currentInventory, data);
    }
    
    /**
     * Execute market making using real L2 order book data.
     */
    private TradeSignal executeWithRealOrderBook(String symbol, int position, 
                                                  int currentInventory, MarketData data,
                                                  OrderBookData orderBook) {
        Double bidPrice = orderBook.getBidPrice1();
        Double askPrice = orderBook.getAskPrice1();
        Double midPrice = orderBook.getMidPrice();
        Double spreadBps = orderBook.getSpreadBps();
        Double imbalance = orderBook.getImbalance();
        double currentPrice = data.getClose();
        
        if (bidPrice == null || askPrice == null || midPrice == null) {
            return executeWithPriceProxy(symbol, position, currentInventory, data);
        }
        
        // Calculate distance from mid-price
        double distanceFromMid = (currentPrice - midPrice) / midPrice;
        
        // Determine if spread is attractive for market making
        boolean attractiveSpread = spreadBps != null && spreadBps >= spreadCapture * 10000;
        
        // Adjust confidence based on order book imbalance
        // If buying into strong sell pressure (negative imbalance), lower confidence
        double imbalanceFactor = 1.0;
        if (imbalance != null) {
            imbalanceFactor = 1.0 + (imbalance * 0.2); // Range: 0.8 to 1.2
        }
        
        // Buy near bid when price drops (provide liquidity)
        if (currentPrice <= bidPrice * 1.001 && currentInventory < maxInventory && position <= 0) {
            if (attractiveSpread) {
                inventory.put(symbol, currentInventory + 1);
                double confidence = Math.min(0.80, 0.70 * imbalanceFactor);
                return TradeSignal.longSignal(confidence,
                    String.format("MM L2 buy: at bid %.2f, spread=%.1fbps, imb=%.2f", 
                        bidPrice, spreadBps != null ? spreadBps : 0.0, 
                        imbalance != null ? imbalance : 0.0));
            }
        }
        
        // Sell near ask when price rises (capture spread)
        if (currentPrice >= askPrice * 0.999 && currentInventory > 0 && position > 0) {
            inventory.put(symbol, currentInventory - 1);
            double confidence = Math.min(0.80, 0.70 * (2.0 - imbalanceFactor));
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, confidence,
                String.format("MM L2 sell: near ask %.2f, spread=%.1fbps", 
                    askPrice, spreadBps != null ? spreadBps : 0.0));
        }
        
        // Buy on larger dips (price significantly below mid)
        double dipThreshold = spreadCapture * 2; // More aggressive with real spread data
        if (distanceFromMid < -dipThreshold && currentInventory < maxInventory && position <= 0) {
            inventory.put(symbol, currentInventory + 1);
            double confidence = Math.min(0.75, 0.65 * imbalanceFactor);
            return TradeSignal.longSignal(confidence,
                String.format("MM L2 dip buy: %.2f below mid by %.2f%%", 
                    currentPrice, distanceFromMid * 100));
        }
        
        // Inventory management: flatten if too much inventory and price above mid
        if (currentInventory >= maxInventory && currentPrice >= midPrice) {
            inventory.put(symbol, currentInventory - 1);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("MM L2 flatten: inv=%d at mid=%.2f", currentInventory, midPrice));
        }
        
        return TradeSignal.neutral(String.format("MM L2: inv=%d, spread=%.1fbps, dist=%.2f%%", 
            currentInventory, spreadBps != null ? spreadBps : 0.0, distanceFromMid * 100));
    }
    
    /**
     * Fallback: Execute market making using price change as spread proxy.
     */
    private TradeSignal executeWithPriceProxy(String symbol, int position, 
                                               int currentInventory, MarketData data) {
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) prices.removeFirst();
        
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up market making");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        double prevPrice = priceArray[priceArray.length - 2];
        double priceChange = (currentPrice - prevPrice) / prevPrice;
        
        // Calculate mid-price (fair value)
        double midPrice = 0;
        for (Double p : priceArray) midPrice += p;
        midPrice /= priceArray.length;
        
        // Buy on dips (provide liquidity)
        if (priceChange < -spreadCapture && currentInventory < maxInventory && position <= 0) {
            inventory.put(symbol, currentInventory + 1);
            return TradeSignal.longSignal(0.65,
                String.format("MM buy: price dropped %.2f%%, inv=%d", 
                    priceChange * 100, currentInventory + 1));
        }
        
        // Sell on pops (capture spread)
        if (priceChange > spreadCapture && currentInventory > 0 && position > 0) {
            inventory.put(symbol, currentInventory - 1);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("MM sell: price rose %.2f%%, inv=%d", 
                    priceChange * 100, currentInventory - 1));
        }
        
        // Inventory management: flatten if too much inventory
        if (currentInventory >= maxInventory && currentPrice >= midPrice) {
            inventory.put(symbol, currentInventory - 1);
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.60,
                String.format("MM flatten: inv=%d at mid", currentInventory));
        }
        
        return TradeSignal.neutral(String.format("MM: inv=%d, change=%.2f%%", 
            currentInventory, priceChange * 100));
    }

    @Override
    public String getName() {
        return String.format("Market Making (%.2f%%, max=%d)", spreadCapture * 100, maxInventory);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        inventory.clear();
    }
}
