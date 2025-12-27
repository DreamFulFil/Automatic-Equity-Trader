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
 * BettingAgainstBetaStrategy
 * Type: Factor Anomaly
 * 
 * Academic Foundation:
 * - Frazzini & Pedersen (2014) - 'Betting Against Beta'
 * 
 * Logic:
 * Low beta stocks outperform high beta stocks on risk-adjusted basis.
 * Long low-beta, short high-beta (simplified: just go long low-beta).
 */
@Slf4j
public class BettingAgainstBetaStrategy implements IStrategy {
    
    private final int betaWindow;
    private final double maxBeta;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> marketReturns = new HashMap<>();
    
    public BettingAgainstBetaStrategy(int betaWindow, double maxBeta) {
        this.betaWindow = betaWindow;
        this.maxBeta = maxBeta;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> marketRets = marketReturns.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > betaWindow + 10) {
            prices.removeFirst();
        }
        
        if (prices.size() < betaWindow) {
            return TradeSignal.neutral("Warming up - need " + betaWindow + " days");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate stock returns
        double[] stockReturns = new double[betaWindow];
        for (int i = 0; i < betaWindow; i++) {
            int idx = priceArray.length - betaWindow + i;
            stockReturns[i] = (priceArray[idx] - priceArray[idx - 1]) / priceArray[idx - 1];
        }
        
        // Use stock's own average as market proxy (simplified)
        double avgReturn = 0;
        for (double r : stockReturns) {
            avgReturn += r;
        }
        avgReturn /= betaWindow;
        
        // Calculate beta (covariance / variance)
        double covariance = 0;
        double variance = 0;
        for (double r : stockReturns) {
            covariance += (r - avgReturn) * (avgReturn - avgReturn);
            variance += Math.pow(avgReturn - avgReturn, 2);
        }
        
        // Simplified beta using volatility ratio
        double stockVol = 0;
        for (double r : stockReturns) {
            stockVol += Math.pow(r - avgReturn, 2);
        }
        stockVol = Math.sqrt(stockVol / betaWindow);
        
        // Use trailing correlation with market average as beta proxy
        double beta = stockVol / Math.max(Math.abs(avgReturn), 0.001);
        beta = Math.min(beta * 10, 3.0);
        
        int position = portfolio.getPosition(symbol);
        
        // Low beta signal: go long
        if (beta < maxBeta && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Low beta: β=%.2f < %.2f (vol=%.2f%%)", 
                    beta, maxBeta, stockVol * 100));
        }
        
        // Exit if beta increases significantly
        if (position > 0 && beta > maxBeta * 1.5) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Beta increased: β=%.2f > %.2f", beta, maxBeta * 1.5));
        }
        
        // Short high beta stocks
        if (beta > 1.5 && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("High beta short: β=%.2f", beta));
        }
        
        return TradeSignal.neutral(String.format("Beta: %.2f", beta));
    }

    @Override
    public String getName() {
        return String.format("Betting Against Beta (%dd, β<%.1f)", betaWindow, maxBeta);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        marketReturns.clear();
    }
}
