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
 * LowVolatilityAnomalyStrategy
 * Type: Anomaly-based
 * 
 * Academic Foundation:
 * - Ang et al. (2006) - 'The Cross-Section of Volatility and Expected Returns'
 * 
 * Logic:
 * Low volatility stocks outperform on risk-adjusted basis (anomaly).
 * Buy stocks with below-average volatility, avoid high-volatility stocks.
 */
@Slf4j
public class LowVolatilityAnomalyStrategy implements IStrategy {
    
    private final int volatilityWindow;
    private final int topPercentile;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> volHistory = new HashMap<>();
    
    public LowVolatilityAnomalyStrategy(int volatilityWindow, int topPercentile) {
        this.volatilityWindow = volatilityWindow;
        this.topPercentile = topPercentile;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> vols = volHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > volatilityWindow * 2) {
            prices.removeFirst();
        }
        
        if (volatilityWindow < 2) {
            return TradeSignal.neutral("Invalid volatility window");
        }

        // Need at least volatilityWindow + 1 prices to compute volatilityWindow returns
        if (prices.size() <= volatilityWindow) {
            return TradeSignal.neutral("Warming up - need " + (volatilityWindow + 1) + " days");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate realized volatility
        double sumReturns = 0;
        double sumSqReturns = 0;
        for (int i = priceArray.length - volatilityWindow; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i - 1]) / priceArray[i - 1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgReturn = sumReturns / volatilityWindow;
        double variance = (sumSqReturns / volatilityWindow) - (avgReturn * avgReturn);
        double volatility = Math.sqrt(variance) * Math.sqrt(252);
        
        // Track volatility history
        vols.addLast(volatility);
        if (vols.size() > 60) {
            vols.removeFirst();
        }
        
        // Calculate volatility percentile
        Double[] volArray = vols.toArray(new Double[0]);
        double[] sortedVols = new double[volArray.length];
        for (int i = 0; i < volArray.length; i++) {
            sortedVols[i] = volArray[i];
        }
        java.util.Arrays.sort(sortedVols);
        
        int percentile = 0;
        for (int i = 0; i < sortedVols.length; i++) {
            if (volatility <= sortedVols[i]) {
                percentile = (i * 100) / sortedVols.length;
                break;
            }
        }
        if (percentile == 0) percentile = 100;
        
        int position = portfolio.getPosition(symbol);
        
        // Low volatility signal: buy stocks in bottom percentile
        if (percentile <= topPercentile && position <= 0) {
            return TradeSignal.longSignal(0.75,
                String.format("Low vol anomaly: vol=%.1f%% (percentile=%d)", 
                    volatility * 100, percentile));
        }
        
        // Exit if volatility rises significantly
        if (position > 0 && percentile > 100 - topPercentile) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Vol increased: vol=%.1f%% (percentile=%d)", 
                    volatility * 100, percentile));
        }
        
        // Avoid/short high volatility stocks
        if (percentile > 100 - topPercentile && position >= 0) {
            return TradeSignal.shortSignal(0.60,
                String.format("High vol avoid: vol=%.1f%% (percentile=%d)", 
                    volatility * 100, percentile));
        }
        
        return TradeSignal.neutral(String.format("Vol: %.1f%% (p%d)", volatility * 100, percentile));
    }

    @Override
    public String getName() {
        return String.format("Low Volatility Anomaly (%dd, top%d)", volatilityWindow, topPercentile);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volHistory.clear();
    }
}
