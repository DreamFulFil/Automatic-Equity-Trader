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
 * Pivot Point Reversal Strategy
 * Type: Support/Resistance
 * 
 * Logic:
 * - Calculate Pivot Points based on previous day (High, Low, Close)
 * - Buy if price touches Support (S1/S2) and bounces
 * - Sell if price touches Resistance (R1/R2) and rejects
 * 
 * When to use:
 * - Intraday trading
 * - Identifying key price levels
 * 
 * Who uses it:
 * - Floor traders
 * - Day traders
 * 
 * Details: https://www.investopedia.com/terms/p/pivotpoint.asp
 * 
 * Note: Since we are streaming intraday data, we simulate "Previous Day" by using a rolling window or provided data.
 * For this demo, we assume the first data point sets the "Previous Day" reference or we update it dynamically.
 */
@Slf4j
public class PivotPointStrategy implements IStrategy {
    
    private double pivot, r1, s1, r2, s2;
    private boolean levelsCalculated = false;
    
    // In a real system, these would come from the previous trading session
    // Here we will just use a simple heuristic: reset levels every N bars
    private int barCount = 0;
    private final int resetPeriod = 60; // Reset levels every hour (approx)
    
    private double sessionHigh = Double.MIN_VALUE;
    private double sessionLow = Double.MAX_VALUE;
    private double sessionOpen = 0;
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        double price = data.getClose();
        
        // Update session stats
        if (barCount == 0) sessionOpen = price;
        sessionHigh = Math.max(sessionHigh, data.getHigh() > 0 ? data.getHigh() : price);
        sessionLow = Math.min(sessionLow, data.getLow() > 0 ? data.getLow() : price);
        barCount++;
        
        // Calculate levels if needed (e.g., start of day or period)
        if (!levelsCalculated || barCount >= resetPeriod) {
            calculateLevels(sessionHigh, sessionLow, price); // Use current close as "close"
            // Reset for next session
            sessionHigh = price;
            sessionLow = price;
            barCount = 0;
            levelsCalculated = true;
            return TradeSignal.neutral("Calculated new Pivot Levels");
        }
        
        int position = portfolio.getPosition(data.getSymbol());
        
        // Mean Reversion logic off pivots
        // Buy at S1
        if (price <= s1 * 1.001 && price >= s1 * 0.999) {
            if (position <= 0) {
                return TradeSignal.longSignal(0.6, String.format("Touching S1 Support %.0f", s1));
            }
        }
        
        // Sell at R1
        if (price >= r1 * 0.999 && price <= r1 * 1.001) {
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.6, String.format("Touching R1 Resistance %.0f", r1));
            } else if (position == 0) {
                return TradeSignal.shortSignal(0.6, String.format("Touching R1 Resistance %.0f", r1));
            }
        }
        
        return TradeSignal.neutral(String.format("P:%.0f S1:%.0f R1:%.0f", pivot, s1, r1));
    }
    
    private void calculateLevels(double h, double l, double c) {
        pivot = (h + l + c) / 3;
        r1 = (2 * pivot) - l;
        s1 = (2 * pivot) - h;
        r2 = pivot + (h - l);
        s2 = pivot - (h - l);
        log.debug("New Pivots: P={} R1={} S1={}", pivot, r1, s1);
    }

    @Override
    public String getName() {
        return "Pivot Points (Intraday)";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }

    @Override
    public void reset() {
        levelsCalculated = false;
        barCount = 0;
    }
}
