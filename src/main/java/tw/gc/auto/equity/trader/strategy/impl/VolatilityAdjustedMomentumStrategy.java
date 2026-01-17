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
 * VolatilityAdjustedMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Barroso & Santa-Clara (2015) - 'Momentum Has Its Moments'
 * 
 * Logic:
 * Scale momentum position size by inverse of realized volatility.
 * Reduces exposure during high-vol periods, increases during low-vol.
 */
@Slf4j
public class VolatilityAdjustedMomentumStrategy implements IStrategy {
    
    private final int momentumPeriod;
    private final int volatilityPeriod;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public VolatilityAdjustedMomentumStrategy(int momentumPeriod, int volatilityPeriod) {
        this.momentumPeriod = momentumPeriod;
        this.volatilityPeriod = volatilityPeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        int maxPeriod = Math.max(momentumPeriod, volatilityPeriod);
        if (prices.size() > maxPeriod + 10) {
            prices.removeFirst();
        }
        
        if (momentumPeriod < 1 || volatilityPeriod < 2) {
            return TradeSignal.neutral("Invalid momentum/volatility periods");
        }

        // Need at least maxPeriod + 1 prices to reference N periods ago and compute returns (i-1)
        if (prices.size() <= maxPeriod) {
            return TradeSignal.neutral("Warming up - need " + (maxPeriod + 1) + " prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        
        // Calculate momentum
        double pastPrice = priceArray[priceArray.length - 1 - momentumPeriod];
        double momentum = (currentPrice - pastPrice) / pastPrice;
        
        // Calculate realized volatility (standard deviation of returns)
        double sumReturns = 0;
        double sumSqReturns = 0;
        for (int i = priceArray.length - volatilityPeriod; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i - 1]) / priceArray[i - 1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgReturn = sumReturns / volatilityPeriod;
        double variance = (sumSqReturns / volatilityPeriod) - (avgReturn * avgReturn);
        double volatility = Math.sqrt(variance);
        
        // Annualized volatility (assuming daily data)
        double annualizedVol = volatility * Math.sqrt(252);
        
        // Target volatility: 15% annualized
        double targetVol = 0.15;
        double volScalar = targetVol / Math.max(annualizedVol, 0.01);
        
        // Volatility-adjusted momentum signal
        double adjustedMomentum = momentum * Math.min(volScalar, 2.0);
        
        // Confidence scaled by inverse volatility (higher confidence in low-vol environments)
        double confidence = Math.min(0.9, 0.5 + (0.15 / Math.max(annualizedVol, 0.05)));
        
        int position = portfolio.getPosition(symbol);
        
        // Long when adjusted momentum is positive
        if (adjustedMomentum > 0.02 && position <= 0) {
            return TradeSignal.longSignal(confidence,
                String.format("Vol-adj momentum long: mom=%.2f%%, vol=%.1f%%, scalar=%.2f", 
                    momentum * 100, annualizedVol * 100, volScalar));
        }
        
        // Short when adjusted momentum is strongly negative
        if (adjustedMomentum < -0.02 && position >= 0) {
            return TradeSignal.shortSignal(confidence * 0.9,
                String.format("Vol-adj momentum short: mom=%.2f%%, vol=%.1f%%, scalar=%.2f", 
                    momentum * 100, annualizedVol * 100, volScalar));
        }
        
        // Exit long when momentum turns negative
        if (position > 0 && momentum < 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Momentum reversal: %.2f%%", momentum * 100));
        }
        
        // Exit short when momentum turns positive
        if (position < 0 && momentum > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.70,
                String.format("Momentum reversal: %.2f%%", momentum * 100));
        }
        
        return TradeSignal.neutral(String.format("Mom=%.2f%%, Vol=%.1f%%", 
            momentum * 100, annualizedVol * 100));
    }

    @Override
    public String getName() {
        return String.format("Vol-Adjusted Momentum (%d/%d)", momentumPeriod, volatilityPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
