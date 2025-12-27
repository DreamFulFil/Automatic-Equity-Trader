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
 * CalendarSpreadStrategy
 * Type: Volatility Trading
 * 
 * Academic Foundation:
 * - Egloff, Leippold & Wu (2010) - 'The Term Structure of VIX'
 * 
 * Logic:
 * Trade based on volatility term structure. When short-term volatility
 * is higher than long-term (backwardation), expect mean reversion.
 * Uses realized volatility over different time windows as proxy.
 */
@Slf4j
public class CalendarSpreadStrategy implements IStrategy {
    
    private final int nearMonth;
    private final int farMonth;
    private final double entrySpread;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public CalendarSpreadStrategy(int nearMonth, int farMonth, double entrySpread) {
        this.nearMonth = nearMonth * 21;
        this.farMonth = farMonth * 21;
        this.entrySpread = entrySpread;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > farMonth + 10) {
            prices.removeFirst();
        }
        
        if (prices.size() < farMonth) {
            return TradeSignal.neutral("Warming up - need " + farMonth + " days");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Calculate near-term realized volatility
        double nearVol = calculateVolatility(priceArray, nearMonth);
        
        // Calculate far-term realized volatility
        double farVol = calculateVolatility(priceArray, farMonth);
        
        // Term structure spread
        double spread = nearVol - farVol;
        double spreadRatio = nearVol / Math.max(farVol, 0.001);
        
        int position = portfolio.getPosition(symbol);
        
        // Backwardation (near > far): expect volatility to decrease, go long
        if (spread > entrySpread && spreadRatio > 1.2 && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Vol backwardation: near=%.1f%%, far=%.1f%%, spread=%.1f%%", 
                    nearVol * 100, farVol * 100, spread * 100));
        }
        
        // Contango (near < far): expect volatility to increase, go short or exit
        if (spread < -entrySpread && spreadRatio < 0.8 && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Vol contango: near=%.1f%%, far=%.1f%%, spread=%.1f%%", 
                    nearVol * 100, farVol * 100, spread * 100));
        }
        
        // Exit when term structure normalizes
        if (position != 0 && Math.abs(spread) < entrySpread / 2) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.65,
                String.format("Term structure normalized: spread=%.1f%%", spread * 100));
        }
        
        return TradeSignal.neutral(String.format("Vol spread: %.1f%%", spread * 100));
    }
    
    private double calculateVolatility(Double[] prices, int period) {
        if (prices.length < period + 1) return 0;
        
        int start = prices.length - period;
        double sumReturns = 0;
        double sumSqReturns = 0;
        
        for (int i = start; i < prices.length; i++) {
            double ret = (prices[i] - prices[i - 1]) / prices[i - 1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        
        double avgReturn = sumReturns / period;
        double variance = (sumSqReturns / period) - (avgReturn * avgReturn);
        return Math.sqrt(variance) * Math.sqrt(252);
    }

    @Override
    public String getName() {
        return String.format("Calendar Spread (%d/%d, %.1f%%)", 
            nearMonth / 21, farMonth / 21, entrySpread * 100);
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
