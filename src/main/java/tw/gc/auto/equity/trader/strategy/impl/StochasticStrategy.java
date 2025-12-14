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
 * Stochastic Oscillator Strategy
 * Type: Momentum
 * 
 * Logic:
 * - %K crosses %D
 * 
 * When to use:
 * - Volatile markets
 * - Finding entry points in a trend
 * 
 * Who uses it:
 * - Day traders
 * - Scalpers
 * 
 * Details: https://www.investopedia.com/terms/s/stochasticoscillator.asp
 */
@Slf4j
public class StochasticStrategy implements IStrategy {
    
    private final int kPeriod;
    private final int dPeriod;
    private final double overbought;
    private final double oversold;
    
    private final Map<String, Deque<Double>> highs = new HashMap<>();
    private final Map<String, Deque<Double>> lows = new HashMap<>();
    private final Map<String, Deque<Double>> closes = new HashMap<>();
    
    public StochasticStrategy(int kPeriod, int dPeriod, double overbought, double oversold) {
        this.kPeriod = kPeriod;
        this.dPeriod = dPeriod;
        this.overbought = overbought;
        this.oversold = oversold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        updateHistory(symbol, data.getHigh(), data.getLow(), data.getClose());
        
        if (closes.get(symbol).size() < kPeriod) {
            return TradeSignal.neutral("Warming up Stochastic");
        }
        
        double currentK = calculateK(symbol);
        
        int position = portfolio.getPosition(symbol);
        
        if (currentK < oversold) {
            if (position <= 0) {
                return TradeSignal.longSignal(0.75, String.format("Stoch %%K %.2f < %.0f", currentK, oversold));
            }
        } else if (currentK > overbought) {
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.75, String.format("Stoch %%K %.2f > %.0f", currentK, overbought));
            }
        }
        
        return TradeSignal.neutral(String.format("Stoch %%K: %.2f", currentK));
    }
    
    private void updateHistory(String symbol, double h, double l, double c) {
        Deque<Double> hList = highs.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lList = lows.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> cList = closes.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        hList.addLast(h > 0 ? h : c); // Fallback if high/low not provided
        lList.addLast(l > 0 ? l : c);
        cList.addLast(c);
        
        if (hList.size() > kPeriod) hList.removeFirst();
        if (lList.size() > kPeriod) lList.removeFirst();
        if (cList.size() > kPeriod) cList.removeFirst();
    }
    
    private double calculateK(String symbol) {
        Deque<Double> hList = highs.get(symbol);
        Deque<Double> lList = lows.get(symbol);
        double currentClose = closes.get(symbol).getLast();
        
        double highestHigh = hList.stream().mapToDouble(v -> v).max().orElse(currentClose);
        double lowestLow = lList.stream().mapToDouble(v -> v).min().orElse(currentClose);
        
        if (highestHigh == lowestLow) return 50.0;
        
        return 100.0 * (currentClose - lowestLow) / (highestHigh - lowestLow);
    }

    @Override
    public String getName() {
        return String.format("Stochastic (%d,%d)", kPeriod, dPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }

    @Override
    public void reset() {
        highs.clear();
        lows.clear();
        closes.clear();
    }
}
