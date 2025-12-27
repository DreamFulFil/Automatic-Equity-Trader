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
 * VolatilityArbitrageStrategy
 * Type: Volatility Trading
 * 
 * Academic Foundation:
 * - Bakshi & Kapadia (2003) - 'Delta-Hedged Gains and Volatility'
 * 
 * Logic:
 * Trade realized vs implied volatility spread. Buy when realized < implied.
 */
@Slf4j
public class VolatilityArbitrageStrategy implements IStrategy {
    
    private final int realizedWindow;
    private final double spreadThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> volHistory = new HashMap<>();
    
    public VolatilityArbitrageStrategy(int realizedWindow, double spreadThreshold) {
        this.realizedWindow = realizedWindow;
        this.spreadThreshold = spreadThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> vols = volHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > realizedWindow * 2) prices.removeFirst();
        
        if (prices.size() < realizedWindow) {
            return TradeSignal.neutral("Warming up vol arb");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate realized volatility
        double sumReturns = 0;
        double sumSqReturns = 0;
        for (int i = priceArray.length - realizedWindow; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i-1]) / priceArray[i-1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgReturn = sumReturns / realizedWindow;
        double variance = (sumSqReturns / realizedWindow) - (avgReturn * avgReturn);
        double realizedVol = Math.sqrt(variance) * Math.sqrt(252);
        
        // Track volatility history for implied vol proxy
        vols.addLast(realizedVol);
        if (vols.size() > 30) vols.removeFirst();
        
        // Implied volatility proxy: historical average
        double impliedVol = 0;
        for (Double v : vols) impliedVol += v;
        impliedVol /= vols.size();
        
        double volSpread = impliedVol - realizedVol;
        
        int position = portfolio.getPosition(symbol);
        
        // Realized < Implied (volatility premium): go long, expect vol expansion
        if (volSpread > spreadThreshold && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Vol arb long: realized=%.1f%%, implied=%.1f%%, spread=%.1f%%", 
                    realizedVol * 100, impliedVol * 100, volSpread * 100));
        }
        
        // Realized > Implied: exit long
        if (position > 0 && volSpread < -spreadThreshold / 2) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Vol spread collapsed: %.1f%%", volSpread * 100));
        }
        
        // Short when realized >> implied (mean reversion expected)
        if (volSpread < -spreadThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Vol arb short: realized=%.1f%% > implied=%.1f%%", 
                    realizedVol * 100, impliedVol * 100));
        }
        
        return TradeSignal.neutral(String.format("Vol spread: %.1f%%", volSpread * 100));
    }

    @Override
    public String getName() {
        return String.format("Volatility Arbitrage (%dd, spread>%.1f%%)", 
            realizedWindow, spreadThreshold * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        volHistory.clear();
    }
}
