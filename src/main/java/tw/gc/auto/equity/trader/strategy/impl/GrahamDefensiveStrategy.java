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
 * GrahamDefensiveStrategy
 * Type: Value Investing
 * 
 * Academic Foundation:
 * - Graham & Dodd (1934) - 'Security Analysis'
 * 
 * Logic:
 * Benjamin Graham's defensive investor criteria using price-based proxies:
 * - Current ratio proxy: price stability (low volatility)
 * - P/E proxy: trading below historical average
 * - Dividend yield proxy: consistent positive returns
 */
@Slf4j
public class GrahamDefensiveStrategy implements IStrategy {
    
    private final double minCurrentRatio;
    private final double maxPE;
    private final double minDividendYield;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public GrahamDefensiveStrategy(double minCurrentRatio, double maxPE, double minDividendYield) {
        this.minCurrentRatio = minCurrentRatio;
        this.maxPE = maxPE;
        this.minDividendYield = minDividendYield;
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
        
        // Calculate metrics
        double avgPrice = 0;
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;
        for (Double p : priceArray) {
            avgPrice += p;
            if (p < minPrice) minPrice = p;
            if (p > maxPrice) maxPrice = p;
        }
        avgPrice /= priceArray.length;
        
        // Volatility (stability proxy for current ratio)
        double sumSqDiff = 0;
        for (Double p : priceArray) {
            sumSqDiff += Math.pow(p - avgPrice, 2);
        }
        double volatility = Math.sqrt(sumSqDiff / priceArray.length) / avgPrice;
        double stabilityRatio = 1 / (1 + volatility * 10);
        
        // P/E proxy: price relative to earnings proxy (average price)
        double impliedPE = (currentPrice / avgPrice) * maxPE;
        
        // Dividend yield proxy: positive returns count
        int positiveReturns = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i - 1]) positiveReturns++;
        }
        double positiveRate = (double) positiveReturns / (priceArray.length - 1);
        double impliedYield = positiveRate * 0.05;
        
        int position = portfolio.getPosition(symbol);
        
        // Graham criteria met: stable (high current ratio), low P/E, decent yield
        boolean meetsCurrentRatio = stabilityRatio >= minCurrentRatio / 3;
        boolean meetsPE = impliedPE <= maxPE;
        boolean meetsYield = impliedYield >= minDividendYield;
        
        int criteriamet = (meetsCurrentRatio ? 1 : 0) + (meetsPE ? 1 : 0) + (meetsYield ? 1 : 0);
        
        if (criteriamet >= 2 && position <= 0) {
            return TradeSignal.longSignal(0.65 + criteriamet * 0.05,
                String.format("Graham defensive: stability=%.2f, P/E=%.1f, yield=%.2f%%", 
                    stabilityRatio, impliedPE, impliedYield * 100));
        }
        
        // Exit if criteria deteriorate significantly
        if (position > 0 && criteriamet < 1) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Graham exit: only %d criteria met", criteriamet));
        }
        
        return TradeSignal.neutral(String.format("Graham: %d/3 criteria met", criteriamet));
    }

    @Override
    public String getName() {
        return String.format("Graham Defensive (CR>%.1f, PE<%.0f, Yield>%.1f%%)", 
            minCurrentRatio, maxPE, minDividendYield * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
