package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.BetaProvider;
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
 * BettingAgainstBetaStrategy
 * Type: Factor Anomaly
 * 
 * Academic Foundation:
 * - Frazzini &amp; Pedersen (2014) - 'Betting Against Beta'
 * 
 * Logic:
 * Low beta stocks outperform high beta stocks on risk-adjusted basis.
 * Long low-beta, short high-beta.
 * 
 * Uses real beta calculation from BetaCalculationService when available,
 * falls back to volatility-based proxy otherwise.
 * 
 * @since 2026-01-26 - Phase 3: Updated to use real beta calculations
 */
@Slf4j
public class BettingAgainstBetaStrategy implements IStrategy {
    
    private final int betaWindow;
    private final double maxBeta;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private BetaProvider betaProvider;
    
    /**
     * Legacy constructor (uses volatility-based beta proxy).
     */
    public BettingAgainstBetaStrategy(int betaWindow, double maxBeta) {
        this.betaWindow = betaWindow;
        this.maxBeta = maxBeta;
        this.betaProvider = BetaProvider.noOp();
    }

    /**
     * Constructor with real beta data support.
     * 
     * @param betaWindow Window for beta calculation (e.g., 60 days)
     * @param maxBeta Maximum beta for long signals (e.g., 0.8)
     * @param betaProvider Provider for real beta values
     */
    public BettingAgainstBetaStrategy(int betaWindow, double maxBeta, BetaProvider betaProvider) {
        this.betaWindow = betaWindow;
        this.maxBeta = maxBeta;
        this.betaProvider = betaProvider != null ? betaProvider : BetaProvider.noOp();
    }

    /**
     * Set the beta provider (for dependency injection after construction).
     */
    public void setBetaProvider(BetaProvider provider) {
        this.betaProvider = provider != null ? provider : BetaProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > betaWindow + 10) {
            prices.removeFirst();
        }
        
        if (betaWindow < 2) {
            return TradeSignal.neutral("Invalid beta window");
        }

        // Try to get real beta first
        Optional<Double> realBeta = betaProvider.getBeta(symbol);
        
        double beta;
        String betaSource;
        
        if (realBeta.isPresent()) {
            beta = realBeta.get();
            betaSource = "calculated";
            log.debug("Using real beta for {}: {:.2f}", symbol, beta);
        } else {
            // Fallback to volatility-based proxy
            if (prices.size() <= betaWindow) {
                return TradeSignal.neutral("Warming up - need " + (betaWindow + 1) + " days");
            }
            
            beta = calculateProxyBeta(prices);
            betaSource = "proxy";
        }
        
        int position = portfolio.getPosition(symbol);
        
        // Low beta signal: go long (defensive stocks)
        if (beta < maxBeta && position <= 0) {
            double confidence = Math.min(0.85, 0.65 + (maxBeta - beta) * 0.5);
            return TradeSignal.longSignal(confidence,
                String.format("Low beta (%s): β=%.2f < %.2f", 
                    betaSource, beta, maxBeta));
        }
        
        // Exit if beta increases significantly above threshold
        if (position > 0 && beta > maxBeta * 1.5) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                String.format("Beta increased (%s): β=%.2f > %.2f", 
                    betaSource, beta, maxBeta * 1.5));
        }
        
        // Short high beta stocks (aggressive stocks underperform risk-adjusted)
        if (beta > 1.5 && position >= 0) {
            double confidence = Math.min(0.75, 0.5 + (beta - 1.5) * 0.25);
            return TradeSignal.shortSignal(confidence,
                String.format("High beta short (%s): β=%.2f", betaSource, beta));
        }
        
        return TradeSignal.neutral(String.format("Beta (%s): %.2f", betaSource, beta));
    }

    /**
     * Calculate a beta proxy using volatility ratio.
     * This is a simplified approximation when real beta is unavailable.
     */
    private double calculateProxyBeta(Deque<Double> prices) {
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate stock returns
        double[] stockReturns = new double[betaWindow];
        for (int i = 0; i < betaWindow; i++) {
            int idx = priceArray.length - betaWindow + i;
            stockReturns[i] = (priceArray[idx] - priceArray[idx - 1]) / priceArray[idx - 1];
        }
        
        // Calculate average return
        double avgReturn = 0;
        for (double r : stockReturns) {
            avgReturn += r;
        }
        avgReturn /= betaWindow;
        
        // Calculate volatility
        double stockVol = 0;
        for (double r : stockReturns) {
            stockVol += Math.pow(r - avgReturn, 2);
        }
        stockVol = Math.sqrt(stockVol / betaWindow);
        
        // Use volatility ratio as beta proxy
        // Assumes market daily vol is ~1%, annualized ~16%
        double assumedMarketVol = 0.01;
        double beta = stockVol / assumedMarketVol;
        
        // Cap beta at reasonable range
        return Math.min(Math.max(beta, 0.1), 3.0);
    }

    @Override
    public String getName() {
        return String.format("Betting Against Beta (%dd, β<%.1f)", betaWindow, maxBeta);
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
