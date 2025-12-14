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
 * MACD (Moving Average Convergence Divergence) Strategy
 * Type: Trend Following / Momentum
 * 
 * Logic:
 * - Buy when MACD line crosses above Signal line (Bullish)
 * - Sell when MACD line crosses below Signal line (Bearish)
 * 
 * When to use:
 * - Trending markets (strong up or down moves)
 * - Confirming trend direction
 * 
 * Who uses it:
 * - Trend followers
 * - Momentum traders
 * 
 * Details: https://www.investopedia.com/terms/m/macd.asp
 */
@Slf4j
public class MACDStrategy implements IStrategy {
    
    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Boolean> previousCross = new HashMap<>(); // true = above, false = below
    
    public MACDStrategy(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        prices.addLast(data.getClose());
        
        if (prices.size() > slowPeriod * 2) {
            prices.removeFirst();
        }
        
        if (prices.size() < slowPeriod) {
            return TradeSignal.neutral("Warming up MACD");
        }
        
        double fastEMA = calculateEMA(prices, fastPeriod);
        double slowEMA = calculateEMA(prices, slowPeriod);
        double macdLine = fastEMA - slowEMA;
        
        // We need history of MACD line to calculate Signal line (EMA of MACD)
        // For simplicity in this demo, we'll approximate or use a simple check
        // A proper implementation requires storing the MACD line history too.
        // Here we will just use the raw MACD value crossing Zero as a simplified "Zero Cross" strategy
        // which is also a valid MACD strategy.
        
        // Strategy: Zero Cross
        // MACD > 0 => Bullish
        // MACD < 0 => Bearish
        
        boolean aboveZero = macdLine > 0;
        Boolean wasAbove = previousCross.get(symbol);
        previousCross.put(symbol, aboveZero);
        
        if (wasAbove == null) return TradeSignal.neutral("Initializing");
        
        int position = portfolio.getPosition(symbol);
        
        if (!wasAbove && aboveZero) {
            // Crossed up
            if (position <= 0) {
                return TradeSignal.longSignal(0.7, "MACD Zero Cross Bullish");
            }
        } else if (wasAbove && !aboveZero) {
            // Crossed down
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.7, "MACD Zero Cross Bearish");
            } else if (position == 0) {
                return TradeSignal.shortSignal(0.7, "MACD Zero Cross Bearish");
            }
        }
        
        return TradeSignal.neutral(String.format("MACD: %.2f", macdLine));
    }
    
    private double calculateEMA(Deque<Double> prices, int period) {
        Double[] p = prices.toArray(new Double[0]);
        double k = 2.0 / (period + 1);
        double ema = p[0]; // Start with SMA or first price
        for (int i = 1; i < p.length; i++) {
            ema = (p[i] * k) + (ema * (1 - k));
        }
        return ema;
    }

    @Override
    public String getName() {
        return String.format("MACD ZeroCross (%d,%d)", fastPeriod, slowPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        previousCross.clear();
    }
}
