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
 * BookToMarketStrategy
 * Type: Value Factor
 * 
 * Academic Foundation:
 * - Fama & French (1992) - 'The Cross-Section of Expected Stock Returns'
 * 
 * Logic:
 * Buy high book-to-market (value) stocks. Uses price-based proxy:
 * stocks trading near 52-week lows with positive momentum reversal signals.
 */
@Slf4j
public class BookToMarketStrategy implements IStrategy {
    
    private final double minBookToMarket;
    private final int rebalanceDays;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> holdingDays = new HashMap<>();
    
    public BookToMarketStrategy(double minBookToMarket, int rebalanceDays) {
        this.minBookToMarket = minBookToMarket;
        this.rebalanceDays = rebalanceDays;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 252) {
            prices.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days of prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate 52-week high and low (or available history)
        double yearHigh = Double.MIN_VALUE;
        double yearLow = Double.MAX_VALUE;
        for (Double p : priceArray) {
            if (p > yearHigh) yearHigh = p;
            if (p < yearLow) yearLow = p;
        }
        
        // Book-to-market proxy: distance from 52-week high (value stocks trade near lows)
        double distanceFromHigh = (yearHigh - currentPrice) / yearHigh;
        double distanceFromLow = (currentPrice - yearLow) / yearLow;
        
        // Short-term momentum for reversal confirmation
        double shortTermReturn = 0;
        if (priceArray.length >= 20) {
            shortTermReturn = (currentPrice - priceArray[priceArray.length - 20]) / priceArray[priceArray.length - 20];
        }
        
        int position = portfolio.getPosition(symbol);
        int days = holdingDays.getOrDefault(symbol, 0);
        
        // Increment holding days if in position
        if (position != 0) {
            holdingDays.put(symbol, days + 1);
        }
        
        // Value signal: trading in lower portion of range with positive short-term reversal
        boolean isValue = distanceFromHigh > minBookToMarket;
        boolean hasReversal = shortTermReturn > 0.01;
        
        if (isValue && hasReversal && position <= 0) {
            holdingDays.put(symbol, 0);
            return TradeSignal.longSignal(0.70,
                String.format("Value entry: %.1f%% below high, reversal %.2f%%", 
                    distanceFromHigh * 100, shortTermReturn * 100));
        }
        
        // Exit after holding period or if price approaches high (no longer value)
        if (position > 0) {
            if (days >= rebalanceDays || distanceFromHigh < 0.1) {
                holdingDays.put(symbol, 0);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                    String.format("Value exit: held %d days, %.1f%% below high", 
                        days, distanceFromHigh * 100));
            }
        }
        
        return TradeSignal.neutral(String.format("Distance from high: %.1f%%", 
            distanceFromHigh * 100));
    }

    @Override
    public String getName() {
        return String.format("Book-to-Market (%.0f%%, %dd)", minBookToMarket * 100, rebalanceDays);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        holdingDays.clear();
    }
}
