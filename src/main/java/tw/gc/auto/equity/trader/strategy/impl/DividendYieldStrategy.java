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
 * DividendYieldStrategy
 * Type: Income / Value
 * 
 * Academic Foundation:
 * - Litzenberger & Ramaswamy (1979) - 'The Effect of Personal Taxes'
 * 
 * Logic:
 * Buy stocks with high implied dividend yield (proxy: price stability + low volatility).
 * Stable, low-vol stocks trading below average often have high yields.
 */
@Slf4j
public class DividendYieldStrategy implements IStrategy {
    
    private final double minDividendYield;
    private final int minConsecutiveYears;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public DividendYieldStrategy(double minDividendYield, int minConsecutiveYears) {
        this.minDividendYield = minDividendYield;
        this.minConsecutiveYears = minConsecutiveYears;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        int lookback = minConsecutiveYears * 252;
        if (prices.size() > lookback) {
            prices.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days of prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate average price over history
        double avgPrice = 0;
        for (Double p : priceArray) {
            avgPrice += p;
        }
        avgPrice /= priceArray.length;
        
        // Calculate volatility
        double sumSqDiff = 0;
        for (Double p : priceArray) {
            sumSqDiff += Math.pow(p - avgPrice, 2);
        }
        double stdDev = Math.sqrt(sumSqDiff / priceArray.length);
        double annualizedVol = (stdDev / avgPrice) * Math.sqrt(252);
        
        // Price trend stability (count days above/below average)
        int daysAbove = 0, daysBelow = 0;
        for (Double p : priceArray) {
            if (p > avgPrice) daysAbove++;
            else daysBelow++;
        }
        double stability = 1 - Math.abs(daysAbove - daysBelow) / (double) priceArray.length;
        
        // Implied dividend yield proxy: stable price + low vol + trading below average
        double valueDiscount = (avgPrice - currentPrice) / avgPrice;
        double impliedYield = (valueDiscount > 0 ? valueDiscount * 0.5 : 0) + 
                             (0.05 / Math.max(annualizedVol, 0.1)) * stability * 0.05;
        
        int position = portfolio.getPosition(symbol);
        
        // High yield signal
        if (impliedYield > minDividendYield && annualizedVol < 0.30 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("High dividend yield: implied=%.2f%%, vol=%.1f%%, stability=%.2f", 
                    impliedYield * 100, annualizedVol * 100, stability));
        }
        
        // Exit if yield deteriorates or volatility increases
        if (position > 0 && (impliedYield < minDividendYield / 2 || annualizedVol > 0.40)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Yield exit: implied=%.2f%%, vol=%.1f%%", 
                    impliedYield * 100, annualizedVol * 100));
        }
        
        return TradeSignal.neutral(String.format("Implied yield: %.2f%%", impliedYield * 100));
    }

    @Override
    public String getName() {
        return String.format("Dividend Yield (%.1f%%, %dy)", minDividendYield * 100, minConsecutiveYears);
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
