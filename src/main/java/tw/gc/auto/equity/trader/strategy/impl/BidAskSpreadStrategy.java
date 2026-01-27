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
 * BidAskSpreadStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Roll (1984) - 'A Simple Implicit Measure of the Bid-Ask Spread'
 * 
 * Logic:
 * Exploit temporary liquidity imbalances using real bid-ask spread when available,
 * falling back to implied spread from price reversals (Roll's measure).
 * 
 * @since 2026-01-27 - Enhanced with OrderBookProvider for real spread data
 */
@Slf4j
public class BidAskSpreadStrategy implements IStrategy {
    
    private final double normalSpread;
    private final double wideSpreadThreshold;
    private final OrderBookProvider orderBookProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> spreadHistory = new HashMap<>();
    
    /**
     * Legacy constructor - uses Roll's implied spread.
     */
    public BidAskSpreadStrategy(double normalSpread, double wideSpreadThreshold) {
        this(normalSpread, wideSpreadThreshold, OrderBookProvider.noOp());
    }
    
    /**
     * Enhanced constructor with OrderBookProvider for real spread data.
     */
    public BidAskSpreadStrategy(double normalSpread, double wideSpreadThreshold,
                                 OrderBookProvider orderBookProvider) {
        this.normalSpread = normalSpread;
        this.wideSpreadThreshold = wideSpreadThreshold;
        this.orderBookProvider = orderBookProvider != null ? orderBookProvider : OrderBookProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        int position = portfolio.getPosition(symbol);
        
        // Try to use real order book data first
        Optional<OrderBookData> orderBook = orderBookProvider.getOrderBook(symbol);
        if (orderBook.isPresent() && orderBook.get().isValid()) {
            return executeWithRealSpread(symbol, position, data, orderBook.get());
        }
        
        // Fallback to implied spread (Roll's measure)
        return executeWithImpliedSpread(symbol, position, data);
    }
    
    /**
     * Execute using real bid-ask spread from order book.
     */
    private TradeSignal executeWithRealSpread(String symbol, int position, 
                                               MarketData data, OrderBookData orderBook) {
        Double bidPrice = orderBook.getBidPrice1();
        Double askPrice = orderBook.getAskPrice1();
        Double midPrice = orderBook.getMidPrice();
        Double spreadBps = orderBook.getSpreadBps();
        Double imbalance = orderBook.getImbalance();
        double currentPrice = data.getClose();
        
        if (bidPrice == null || askPrice == null || midPrice == null || spreadBps == null) {
            return executeWithImpliedSpread(symbol, position, data);
        }
        
        // Convert spread threshold to bps for comparison
        double normalSpreadBps = normalSpread * 10000;
        double wideSpreadBps = wideSpreadThreshold * 10000;
        
        // Track spread history for trend analysis
        Deque<Double> spreads = spreadHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        spreads.addLast(spreadBps);
        if (spreads.size() > 20) {
            spreads.removeFirst();
        }
        
        // Calculate average spread for context
        double avgSpread = spreads.stream().mapToDouble(Double::doubleValue).average().orElse(spreadBps);
        
        // Determine if spread is currently abnormal
        boolean isWideSpread = spreadBps > wideSpreadBps;
        boolean isNarrowSpread = spreadBps < normalSpreadBps;
        boolean spreadWidening = spreads.size() > 3 && spreadBps > avgSpread * 1.2;
        boolean spreadNarrowing = spreads.size() > 3 && spreadBps < avgSpread * 0.8;
        
        // Calculate price position relative to bid/ask
        double distanceFromBid = (currentPrice - bidPrice) / bidPrice;
        double distanceFromAsk = (askPrice - currentPrice) / currentPrice;
        
        // Adjust confidence based on imbalance
        double imbalanceFactor = 1.0;
        if (imbalance != null) {
            imbalanceFactor = 1.0 + (imbalance * 0.15); // Range: 0.85 to 1.15
        }
        
        // Strategy 1: Wide spread + price near bid = buy (liquidity provision)
        if (isWideSpread && distanceFromBid < 0.001 && position <= 0) {
            double confidence = Math.min(0.80, 0.70 * imbalanceFactor);
            return TradeSignal.longSignal(confidence,
                String.format("Wide spread L2 buy: at bid %.2f, spread=%.1fbps (wide=%.1f)", 
                    bidPrice, spreadBps, wideSpreadBps));
        }
        
        // Strategy 2: Wide spread + price near ask = sell
        if (isWideSpread && distanceFromAsk < 0.001 && position > 0) {
            double confidence = Math.min(0.80, 0.70 * (2.0 - imbalanceFactor));
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, confidence,
                String.format("Wide spread L2 sell: near ask %.2f, spread=%.1fbps", 
                    askPrice, spreadBps));
        }
        
        // Strategy 3: Spread widening = volatility incoming, reduce exposure
        if (spreadWidening && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.60,
                String.format("Spread widening L2: %.1fbps (avg=%.1f), reduce exposure", 
                    spreadBps, avgSpread));
        }
        
        // Strategy 4: Spread narrowing + buy pressure = accumulation
        if (spreadNarrowing && imbalance != null && imbalance > 0.3 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Spread narrowing L2 buy: %.1fbps, imb=%.2f", 
                    spreadBps, imbalance));
        }
        
        // Strategy 5: Normal spread mean reversion
        if (isNarrowSpread) {
            Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
            prices.addLast(currentPrice);
            if (prices.size() > 10) prices.removeFirst();
            
            if (prices.size() >= 3) {
                double prevPrice = prices.toArray(new Double[0])[prices.size() - 2];
                double priceChange = (currentPrice - prevPrice) / prevPrice;
                
                // Buy on dips with tight spread
                if (priceChange < -normalSpread * 2 && position <= 0) {
                    return TradeSignal.longSignal(0.65,
                        String.format("Tight spread reversion L2: change=%.2f%%, spread=%.1fbps", 
                            priceChange * 100, spreadBps));
                }
            }
        }
        
        return TradeSignal.neutral(String.format("Spread L2: %.1fbps (avg=%.1f, norm=%.1f)", 
            spreadBps, avgSpread, normalSpreadBps));
    }
    
    /**
     * Fallback: Execute using Roll's implied spread measure.
     */
    private TradeSignal executeWithImpliedSpread(String symbol, int position, MarketData data) {
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) {
            prices.removeFirst();
        }
        
        if (prices.size() < 10) {
            return TradeSignal.neutral("Warming up spread calculation");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate implied spread using Roll's measure
        // Spread = 2 * sqrt(-cov(rt, rt-1)) where rt is return
        double sumCov = 0;
        int n = 0;
        for (int i = 2; i < priceArray.length; i++) {
            double r1 = (priceArray[i] - priceArray[i-1]) / priceArray[i-1];
            double r0 = (priceArray[i-1] - priceArray[i-2]) / priceArray[i-2];
            sumCov += r1 * r0;
            n++;
        }
        // n is guaranteed > 0 here because prices.size() >= 10 (loop runs at least 8 times)
        double cov = sumCov / n;
        double impliedSpread = cov < 0 ? 2 * Math.sqrt(-cov) : normalSpread;
        
        double currentPrice = priceArray[priceArray.length - 1];
        double prevPrice = priceArray[priceArray.length - 2];
        double priceChange = (currentPrice - prevPrice) / prevPrice;
        
        // Wide spread + down move = buy opportunity (liquidity providing)
        if (impliedSpread > wideSpreadThreshold && priceChange < -impliedSpread / 2 && position <= 0) {
            return TradeSignal.longSignal(0.65,
                String.format("Wide spread buy: spread=%.3f%%, change=%.2f%%", 
                    impliedSpread * 100, priceChange * 100));
        }
        
        // Wide spread + up move = sell opportunity
        if (impliedSpread > wideSpreadThreshold && priceChange > impliedSpread / 2 && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Wide spread sell: spread=%.3f%%", impliedSpread * 100));
        }
        
        // Normal spread = mean reversion trades
        if (impliedSpread <= normalSpread && Math.abs(priceChange) > normalSpread * 2) {
            if (priceChange < 0 && position <= 0) {
                return TradeSignal.longSignal(0.60,
                    String.format("Spread reversion long: change=%.2f%%", priceChange * 100));
            }
        }
        
        return TradeSignal.neutral(String.format("Implied spread: %.3f%%", impliedSpread * 100));
    }

    @Override
    public String getName() {
        return String.format("Bid-Ask Spread (%.2f%%/%.2f%%)", normalSpread * 100, wideSpreadThreshold * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        spreadHistory.clear();
    }
}
