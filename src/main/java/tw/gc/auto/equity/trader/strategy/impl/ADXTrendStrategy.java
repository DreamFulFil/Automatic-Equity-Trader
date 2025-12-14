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
 * ADX (Average Directional Index) Trend Strategy
 * Type: Trend Strength
 * 
 * Logic:
 * - Buy when ADX > threshold and +DI > -DI (strong uptrend)
 * - Sell when ADX > threshold and -DI > +DI (strong downtrend)
 */
@Slf4j
public class ADXTrendStrategy implements IStrategy {
    
    private final int period;
    private final double adxThreshold;
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    private final Map<String, Deque<Double>> closeHistory = new HashMap<>();
    
    public ADXTrendStrategy() {
        this(14, 25.0);
    }
    
    public ADXTrendStrategy(int period, double adxThreshold) {
        this.period = period;
        this.adxThreshold = adxThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> closes = closeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        closes.addLast(data.getClose());
        
        if (highs.size() > period * 2) {
            highs.removeFirst();
            lows.removeFirst();
            closes.removeFirst();
        }
        
        if (highs.size() < period + 1) {
            return TradeSignal.neutral("Warming up ADX");
        }
        
        Double[] h = highs.toArray(new Double[0]);
        Double[] l = lows.toArray(new Double[0]);
        
        double plusDM = 0, minusDM = 0;
        for (int i = 1; i < h.length; i++) {
            double upMove = h[i] - h[i-1];
            double downMove = l[i-1] - l[i];
            plusDM += (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDM += (downMove > upMove && downMove > 0) ? downMove : 0;
        }
        
        plusDM /= (h.length - 1);
        minusDM /= (l.length - 1);
        
        double atr = calculateATR(highs, lows, closes, period);
        double plusDI = (atr > 0) ? (plusDM / atr) * 100 : 0;
        double minusDI = (atr > 0) ? (minusDM / atr) * 100 : 0;
        double dx = (plusDI + minusDI > 0) ? Math.abs(plusDI - minusDI) / (plusDI + minusDI) * 100 : 0;
        
        int position = portfolio.getPosition(symbol);
        
        if (dx > adxThreshold && plusDI > minusDI && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("Strong uptrend (ADX=%.1f)", dx));
        } else if (dx > adxThreshold && minusDI > plusDI && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("Strong downtrend (ADX=%.1f)", dx));
        }
        
        return TradeSignal.neutral(String.format("ADX=%.1f below threshold", dx));
    }
    
    private double calculateATR(Deque<Double> highs, Deque<Double> lows, Deque<Double> closes, int period) {
        Double[] h = highs.toArray(new Double[0]);
        Double[] l = lows.toArray(new Double[0]);
        Double[] c = closes.toArray(new Double[0]);
        
        double sum = 0;
        int count = 0;
        for (int i = 1; i < h.length && count < period; i++) {
            double tr = Math.max(h[i] - l[i], Math.max(Math.abs(h[i] - c[i-1]), Math.abs(l[i] - c[i-1])));
            sum += tr;
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    @Override
    public String getName() {
        return "ADX Trend Strength";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
        closeHistory.clear();
    }
}
