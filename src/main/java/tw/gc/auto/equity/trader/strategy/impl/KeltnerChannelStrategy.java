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
 * Keltner Channel Breakout Strategy
 * Type: Volatility Breakout
 * 
 * Logic:
 * - Buy when price breaks above upper Keltner Channel
 * - Sell when price breaks below lower Keltner Channel
 * - Uses EMA and ATR for channel calculation
 */
@Slf4j
public class KeltnerChannelStrategy implements IStrategy {
    
    private final int emaPeriod;
    private final int atrPeriod;
    private final double multiplier;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    
    public KeltnerChannelStrategy() {
        this(20, 10, 2.0);
    }
    
    public KeltnerChannelStrategy(int emaPeriod, int atrPeriod, double multiplier) {
        this.emaPeriod = emaPeriod;
        this.atrPeriod = atrPeriod;
        this.multiplier = multiplier;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        
        if (prices.size() > Math.max(emaPeriod, atrPeriod) * 2) {
            prices.removeFirst();
            highs.removeFirst();
            lows.removeFirst();
        }
        
        if (prices.size() < Math.max(emaPeriod, atrPeriod)) {
            return TradeSignal.neutral("Warming up");
        }
        
        double ema = calculateEMA(prices, emaPeriod);
        double atr = calculateATR(highs, lows, prices, atrPeriod);
        double upper = ema + (multiplier * atr);
        double lower = ema - (multiplier * atr);
        
        double currentPrice = data.getClose();
        int position = portfolio.getPosition(symbol);
        
        if (currentPrice > upper && position <= 0) {
            return TradeSignal.longSignal(0.75, "Price broke above Keltner Channel");
        } else if (currentPrice < lower && position >= 0) {
            return TradeSignal.shortSignal(0.75, "Price broke below Keltner Channel");
        }
        
        return TradeSignal.neutral("Price within channel");
    }
    
    private double calculateEMA(Deque<Double> prices, int period) {
        Double[] p = prices.toArray(new Double[0]);
        double k = 2.0 / (period + 1);
        double ema = p[0];
        for (int i = 1; i < p.length; i++) {
            ema = (p[i] * k) + (ema * (1 - k));
        }
        return ema;
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
        return "Keltner Channel Breakout";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        highHistory.clear();
        lowHistory.clear();
    }
}
