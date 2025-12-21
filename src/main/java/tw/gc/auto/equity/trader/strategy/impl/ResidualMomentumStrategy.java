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
 * ResidualMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Blitz, Huij & Martens (2011) - 'Residual Momentum'
 * 
 * Logic:
 * Trade on idiosyncratic (residual) momentum after removing market beta exposure.
 * Uses simplified beta estimation based on correlation with market proxy.
 */
@Slf4j
public class ResidualMomentumStrategy implements IStrategy {
    
    private final int lookback;
    private final double betaAdjustment;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> returnHistory = new HashMap<>();
    
    public ResidualMomentumStrategy(int lookback, double betaAdjustment) {
        this.lookback = lookback;
        this.betaAdjustment = betaAdjustment;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> returns = returnHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double currentPrice = data.getClose();
        
        // Calculate daily return
        if (!prices.isEmpty()) {
            double lastPrice = prices.getLast();
            double dailyReturn = (currentPrice - lastPrice) / lastPrice;
            returns.addLast(dailyReturn);
            if (returns.size() > lookback + 10) {
                returns.removeFirst();
            }
        }
        
        prices.addLast(currentPrice);
        if (prices.size() > lookback + 10) {
            prices.removeFirst();
        }
        
        if (returns.size() < lookback) {
            return TradeSignal.neutral("Warming up - need " + lookback + " returns");
        }
        
        Double[] retArray = returns.toArray(new Double[0]);
        
        // Calculate average return (proxy for market return in single-stock context)
        double avgReturn = 0;
        for (Double r : retArray) {
            avgReturn += r;
        }
        avgReturn /= retArray.length;
        
        // Calculate variance of returns for beta estimation
        double variance = 0;
        for (Double r : retArray) {
            variance += Math.pow(r - avgReturn, 2);
        }
        variance /= retArray.length;
        double stdDev = Math.sqrt(variance);
        
        // Simple beta approximation (normalized by volatility)
        double recentReturn = retArray[retArray.length - 1];
        double estimatedBeta = betaAdjustment;
        
        // Residual return = actual return - beta * market return
        double residualReturn = recentReturn - (estimatedBeta * avgReturn);
        
        // Calculate cumulative residual momentum over lookback
        double cumResidual = 0;
        for (int i = Math.max(0, retArray.length - lookback); i < retArray.length; i++) {
            cumResidual += retArray[i] - (estimatedBeta * avgReturn);
        }
        
        int position = portfolio.getPosition(symbol);
        
        // Long signal: positive residual momentum (stock outperforming after beta adjustment)
        if (cumResidual > stdDev && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Positive residual momentum: %.2f%% (std: %.2f%%)", 
                    cumResidual * 100, stdDev * 100));
        }
        
        // Short signal: negative residual momentum
        if (cumResidual < -stdDev && position >= 0) {
            return TradeSignal.shortSignal(0.70,
                String.format("Negative residual momentum: %.2f%% (std: %.2f%%)", 
                    cumResidual * 100, stdDev * 100));
        }
        
        // Exit when residual momentum reverts to mean
        if (position > 0 && cumResidual < 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Residual momentum reversal: %.2f%%", cumResidual * 100));
        }
        
        if (position < 0 && cumResidual > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.65,
                String.format("Residual momentum reversal: %.2f%%", cumResidual * 100));
        }
        
        return TradeSignal.neutral(String.format("Residual momentum: %.2f%%", cumResidual * 100));
    }

    @Override
    public String getName() {
        return String.format("Residual Momentum (%d, β=%.1f)", lookback, betaAdjustment);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        returnHistory.clear();
    }
}
