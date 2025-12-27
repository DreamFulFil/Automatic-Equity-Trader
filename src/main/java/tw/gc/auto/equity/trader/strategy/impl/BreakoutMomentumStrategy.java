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
 * BreakoutMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - George & Hwang (2004) - '52-Week High and Momentum'
 * 
 * Logic:
 * Buy on N-period high breakouts with confirmation.
 * Use trailing stop based on recent swing low.
 */
@Slf4j
public class BreakoutMomentumStrategy implements IStrategy {
    
    private final int highPeriod;
    private final double breakoutMargin;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    private final Map<String, Double> entryPrices = new HashMap<>();
    
    public BreakoutMomentumStrategy(int highPeriod, double breakoutMargin) {
        this.highPeriod = highPeriod;
        this.breakoutMargin = breakoutMargin;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        
        if (highs.size() > highPeriod + 5) {
            highs.removeFirst();
            lows.removeFirst();
        }
        
        if (highs.size() < highPeriod) {
            return TradeSignal.neutral("Warming up - need " + highPeriod + " bars");
        }
        
        // Calculate N-period high (excluding current bar)
        double periodHigh = highs.stream()
            .limit(highs.size() - 1)
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0);
        
        double periodLow = lows.stream()
            .skip(Math.max(0, lows.size() - 10))
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(data.getLow());
        
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        // Breakout detection: price closes above period high + margin
        double breakoutLevel = periodHigh * (1 + breakoutMargin);
        
        if (currentPrice > breakoutLevel && position <= 0) {
            entryPrices.put(symbol, currentPrice);
            return TradeSignal.longSignal(0.80, 
                String.format("Breakout above %d-period high: %.2f > %.2f", 
                    highPeriod, currentPrice, breakoutLevel));
        }
        
        // Exit on breakdown below recent swing low (trailing stop)
        if (position > 0) {
            Double entry = entryPrices.get(symbol);
            double stopLevel = periodLow * 0.98;
            
            if (currentPrice < stopLevel) {
                double pnlPct = entry != null ? ((currentPrice - entry) / entry) * 100 : 0;
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.75,
                    String.format("Breakdown stop: %.2f < %.2f (PnL: %.2f%%)", 
                        currentPrice, stopLevel, pnlPct));
            }
        }
        
        return TradeSignal.neutral(String.format("Price=%.2f, %d-high=%.2f", 
            currentPrice, highPeriod, periodHigh));
    }

    @Override
    public String getName() {
        return String.format("Breakout Momentum (%d, %.1f%%)", highPeriod, breakoutMargin * 100);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
        entryPrices.clear();
    }
}
