package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.FundamentalData;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.FundamentalDataProvider;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * BookToMarketStrategy
 * Type: Value Factor
 * 
 * Academic Foundation:
 * - Fama & French (1992) - 'The Cross-Section of Expected Stock Returns'
 * 
 * Logic:
 * Buy high book-to-market (value) stocks. Book-to-Market (B/M) = Book Value per Share / Price.
 * Uses real fundamental data when available (via FundamentalDataProvider),
 * falling back to price-based proxy (distance from 52-week high) when unavailable.
 * 
 * @since 2026-01-26 - Updated to use real fundamental data
 */
@Slf4j
public class BookToMarketStrategy implements IStrategy {
    
    private final double minBookToMarket;
    private final int rebalanceDays;
    private final FundamentalDataProvider fundamentalDataProvider;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> holdingDays = new HashMap<>();
    
    /**
     * Constructor with fundamental data provider for real B/M calculation.
     */
    public BookToMarketStrategy(double minBookToMarket, int rebalanceDays,
                                FundamentalDataProvider fundamentalDataProvider) {
        this.minBookToMarket = minBookToMarket;
        this.rebalanceDays = rebalanceDays;
        this.fundamentalDataProvider = fundamentalDataProvider;
    }
    
    /**
     * Legacy constructor - uses price-based proxy (backward compatible).
     */
    public BookToMarketStrategy(double minBookToMarket, int rebalanceDays) {
        this(minBookToMarket, rebalanceDays, FundamentalDataProvider.noOp());
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
        
        // Try to get real book-to-market from fundamental data
        Optional<FundamentalData> fundamentalOpt = fundamentalDataProvider.apply(symbol);
        double bookToMarket;
        boolean usingRealData = false;
        
        if (fundamentalOpt.isPresent()) {
            FundamentalData fd = fundamentalOpt.get();
            Double realBM = fd.getBookToMarket();
            
            if (realBM != null && realBM > 0) {
                bookToMarket = realBM;
                usingRealData = true;
                log.trace("Using real B/M for {}: {:.2f}", symbol, bookToMarket);
            } else {
                // Fall back to proxy
                bookToMarket = calculateProxyBookToMarket(priceArray, currentPrice);
            }
        } else {
            // Fall back to proxy
            bookToMarket = calculateProxyBookToMarket(priceArray, currentPrice);
        }
        
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
        
        // Value signal: high B/M with positive short-term reversal
        boolean isValue = bookToMarket > minBookToMarket;
        boolean hasReversal = shortTermReturn > 0.01;
        
        if (isValue && hasReversal && position <= 0) {
            holdingDays.put(symbol, 0);
            String dataSource = usingRealData ? "real B/M" : "proxy";
            return TradeSignal.longSignal(usingRealData ? 0.80 : 0.70,
                String.format("Value entry (%s): B/M=%.2f, reversal %.2f%%", 
                    dataSource, bookToMarket, shortTermReturn * 100));
        }
        
        // Exit after holding period or if B/M becomes too low
        if (position > 0) {
            double exitThreshold = usingRealData ? minBookToMarket * 0.5 : 0.1;
            if (days >= rebalanceDays || bookToMarket < exitThreshold) {
                holdingDays.put(symbol, 0);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                    String.format("Value exit: held %d days, B/M=%.2f", 
                        days, bookToMarket));
            }
        }
        
        return TradeSignal.neutral(String.format("Book-to-Market: %.2f", bookToMarket));
    }
    
    /**
     * Calculate proxy B/M from price data.
     * Uses distance from 52-week high as proxy (value stocks trade near lows).
     */
    private double calculateProxyBookToMarket(Double[] priceArray, double currentPrice) {
        double yearHigh = Double.MIN_VALUE;
        for (Double p : priceArray) {
            if (p > yearHigh) yearHigh = p;
        }
        // Distance from high as proxy for B/M (inverted relationship)
        return (yearHigh - currentPrice) / yearHigh;
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

