package tw.gc.auto.equity.trader.strategy.impl.library;

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
 * Relative Strength Index (RSI) Strategy
 * Type: Mean Reversion / Momentum
 * 
 * Logic:
 * - Buy when RSI < oversold (e.g., 30) -> Oversold, expect bounce
 * - Sell when RSI > overbought (e.g., 70) -> Overbought, expect pullback
 * 
 * When to use:
 * - Ranging markets (sideways price action)
 * - Detecting reversals at extremes
 * 
 * Who uses it:
 * - Swing traders looking for "cheap" entries
 * - Algorithmic traders fading moves
 * 
 * Details: https://www.investopedia.com/terms/r/rsi.asp
 */
@Slf4j
public class RSIStrategy implements IStrategy {
    
    private final int period;
    private final double overbought;
    private final double oversold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public RSIStrategy(int period, double overbought, double oversold) {
        this.period = period;
        this.overbought = overbought;
        this.oversold = oversold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(data.getClose());
        
        // Keep enough history for RSI calculation (need period + 1 for first calc, but more is better for smoothing)
        // Simple RSI needs 'period' gains/losses.
        if (prices.size() > period * 2) {
            prices.removeFirst();
        }
        
        if (prices.size() <= period) {
            return TradeSignal.neutral("Warming up RSI");
        }
        
        double rsi = calculateRSI(prices, period);
        int position = portfolio.getPosition(symbol);
        
        if (rsi < oversold) {
            if (position <= 0) {
                return TradeSignal.longSignal(0.8, String.format("RSI %.2f < %.0f (Oversold)", rsi, oversold));
            }
        } else if (rsi > overbought) {
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.8, String.format("RSI %.2f > %.0f (Overbought)", rsi, overbought));
            } else if (position == 0) {
                return TradeSignal.shortSignal(0.8, String.format("RSI %.2f > %.0f (Overbought)", rsi, overbought));
            }
        }
        
        return TradeSignal.neutral(String.format("RSI %.2f", rsi));
    }
    
    private double calculateRSI(Deque<Double> prices, int n) {
        // Simplified RSI calculation for demonstration
        // In production, use a proper indicator library or exponential moving average
        Double[] p = prices.toArray(new Double[0]);
        if (p.length < n + 1) return 50.0;
        
        double sumGain = 0;
        double sumLoss = 0;
        
        for (int i = p.length - n; i < p.length; i++) {
            double change = p[i] - p[i-1];
            if (change > 0) sumGain += change;
            else sumLoss -= change;
        }
        
        if (sumLoss == 0) return 100.0;
        double rs = sumGain / sumLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    @Override
    public String getName() {
        return String.format("RSI (%d, %.0f/%.0f)", period, oversold, overbought);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
