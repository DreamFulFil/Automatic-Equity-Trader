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
 * ATR Channel Breakout Strategy
 * Type: Volatility / Trend
 * 
 * Logic:
 * - Calculate ATR (Average True Range)
 * - Buy if Price > MA + (Multiplier * ATR)
 * - Sell if Price < MA - (Multiplier * ATR)
 * 
 * When to use:
 * - Breakout trading
 * - Catching explosive moves
 * 
 * Who uses it:
 * - Turtle Traders
 * - Volatility traders
 * 
 * Details: https://www.investopedia.com/terms/a/atr.asp
 */
@Slf4j
public class ATRChannelStrategy implements IStrategy {
    
    private final int period;
    private final double multiplier;
    private final Map<String, Deque<Double>> closes = new HashMap<>();
    private final Map<String, Deque<Double>> highs = new HashMap<>();
    private final Map<String, Deque<Double>> lows = new HashMap<>();
    
    public ATRChannelStrategy(int period, double multiplier) {
        this.period = period;
        this.multiplier = multiplier;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        updateHistory(symbol, data.getHigh(), data.getLow(), data.getClose());
        
        if (closes.get(symbol).size() < period + 1) {
            return TradeSignal.neutral("Warming up ATR");
        }
        
        double atr = calculateATR(symbol);
        double ma = calculateSMA(symbol);
        double upperBand = ma + (multiplier * atr);
        double lowerBand = ma - (multiplier * atr);
        double price = data.getClose();
        
        int position = portfolio.getPosition(symbol);
        
        if (price > upperBand) {
            if (position <= 0) {
                return TradeSignal.longSignal(0.8, String.format("Price %.0f > Upper Band %.0f (ATR Breakout)", price, upperBand));
            }
        } else if (price < lowerBand) {
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.8, String.format("Price %.0f < Lower Band %.0f", price, lowerBand));
            } else if (position == 0) {
                return TradeSignal.shortSignal(0.8, String.format("Price %.0f < Lower Band %.0f", price, lowerBand));
            }
        }
        
        return TradeSignal.neutral(String.format("In Channel: %.0f < %.0f < %.0f", lowerBand, price, upperBand));
    }
    
    private void updateHistory(String symbol, double h, double l, double c) {
        Deque<Double> hList = highs.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lList = lows.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> cList = closes.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        hList.addLast(h > 0 ? h : c);
        lList.addLast(l > 0 ? l : c);
        cList.addLast(c);
        
        if (hList.size() > period + 1) hList.removeFirst();
        if (lList.size() > period + 1) lList.removeFirst();
        if (cList.size() > period + 1) cList.removeFirst();
    }
    
    private double calculateATR(String symbol) {
        Double[] h = highs.get(symbol).toArray(new Double[0]);
        Double[] l = lows.get(symbol).toArray(new Double[0]);
        Double[] c = closes.get(symbol).toArray(new Double[0]);
        
        double sumTR = 0;
        for (int i = 1; i < h.length; i++) {
            double tr1 = h[i] - l[i];
            double tr2 = Math.abs(h[i] - c[i-1]);
            double tr3 = Math.abs(l[i] - c[i-1]);
            sumTR += Math.max(tr1, Math.max(tr2, tr3));
        }
        return sumTR / (h.length - 1);
    }
    
    private double calculateSMA(String symbol) {
        return closes.get(symbol).stream().mapToDouble(v -> v).average().orElse(0.0);
    }

    @Override
    public String getName() {
        return String.format("ATR Channel (%d, x%.1f)", period, multiplier);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        highs.clear();
        lows.clear();
        closes.clear();
    }
}
