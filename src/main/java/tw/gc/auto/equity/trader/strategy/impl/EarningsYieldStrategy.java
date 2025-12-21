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
 * EarningsYieldStrategy
 * Type: Value Factor
 * 
 * Academic Foundation:
 * - Basu (1977) - 'Investment Performance of Common Stocks'
 * 
 * Logic:
 * Buy stocks with high earnings yield (E/P ratio). Uses price stability
 * and low volatility as proxy for earnings quality.
 */
@Slf4j
public class EarningsYieldStrategy implements IStrategy {
    
    private final double minEarningsYield;
    private final int holdingPeriod;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Integer> holdingDays = new HashMap<>();
    
    public EarningsYieldStrategy(double minEarningsYield, int holdingPeriod) {
        this.minEarningsYield = minEarningsYield;
        this.holdingPeriod = holdingPeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 120) {
            prices.removeFirst();
        }
        
        if (prices.size() < 60) {
            return TradeSignal.neutral("Warming up - need 60 days of prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate price volatility (proxy for earnings stability)
        double sumReturns = 0;
        double sumSqReturns = 0;
        for (int i = 1; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i - 1]) / priceArray[i - 1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgReturn = sumReturns / (priceArray.length - 1);
        double variance = (sumSqReturns / (priceArray.length - 1)) - (avgReturn * avgReturn);
        double volatility = Math.sqrt(variance);
        double annualizedVol = volatility * Math.sqrt(252);
        
        // Earnings yield proxy: low volatility + trading below recent average
        double avgPrice = 0;
        for (Double p : priceArray) {
            avgPrice += p;
        }
        avgPrice /= priceArray.length;
        
        double priceToAvg = currentPrice / avgPrice;
        double impliedYield = (1 / priceToAvg - 1) + (0.15 / Math.max(annualizedVol, 0.1));
        
        int position = portfolio.getPosition(symbol);
        int days = holdingDays.getOrDefault(symbol, 0);
        
        if (position != 0) {
            holdingDays.put(symbol, days + 1);
        }
        
        // High earnings yield signal: low volatility, trading below average
        boolean highYield = impliedYield > minEarningsYield;
        boolean lowVol = annualizedVol < 0.30;
        
        if (highYield && lowVol && position <= 0) {
            holdingDays.put(symbol, 0);
            return TradeSignal.longSignal(0.70,
                String.format("High earnings yield: implied=%.2f%%, vol=%.1f%%", 
                    impliedYield * 100, annualizedVol * 100));
        }
        
        // Exit after holding period or if yield becomes too low
        if (position > 0) {
            if (days >= holdingPeriod || impliedYield < minEarningsYield / 2) {
                holdingDays.put(symbol, 0);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                    String.format("Yield exit: held %d days, yield=%.2f%%", 
                        days, impliedYield * 100));
            }
        }
        
        return TradeSignal.neutral(String.format("Implied yield: %.2f%%", impliedYield * 100));
    }

    @Override
    public String getName() {
        return String.format("Earnings Yield (%.1f%%, %dd)", minEarningsYield * 100, holdingPeriod);
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
