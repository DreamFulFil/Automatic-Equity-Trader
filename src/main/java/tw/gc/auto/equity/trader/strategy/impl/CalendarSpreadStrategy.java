package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.CalendarProvider;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
 * 
 * Enhanced with CalendarProvider (Phase 4):
 * - Uses real futures expiration dates for timing
 * - Adjusts signals around settlement periods
 * - Considers event risk for position sizing
 */
@Slf4j
public class CalendarSpreadStrategy implements IStrategy {
    
    private final int nearMonth;
    private final int farMonth;
    private final double entrySpread;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final CalendarProvider calendarProvider;
    
    public CalendarSpreadStrategy(int nearMonth, int farMonth, double entrySpread) {
        this(nearMonth, farMonth, entrySpread, CalendarProvider.noOp());
    }
    
    public CalendarSpreadStrategy(int nearMonth, int farMonth, double entrySpread, CalendarProvider calendarProvider) {
        this.nearMonth = nearMonth * 21;
        this.farMonth = farMonth * 21;
        this.entrySpread = entrySpread;
        this.calendarProvider = calendarProvider != null ? calendarProvider : CalendarProvider.noOp();
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
        
        // Get calendar context for enhanced decision making
        LocalDate tradeDate = data.getTimestamp().toLocalDate();
        int daysToExpiration = calendarProvider.getDaysToExpiration();
        boolean isSettlementWeek = calendarProvider.isSettlementWeek();
        boolean isSettlementDay = calendarProvider.isSettlementDay();
        double positionMultiplier = calendarProvider.getPositionSizeMultiplier(tradeDate);
        
        // Avoid new positions on settlement day (high volatility, unpredictable)
        if (isSettlementDay) {
            if (position != 0) {
                // Consider closing positions on settlement day
                return TradeSignal.exitSignal(
                    position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                    0.70,
                    String.format("Settlement day exit: closing position to avoid settlement volatility"));
            }
            return TradeSignal.neutral("Settlement day - avoiding new positions");
        }
        
        // Adjust thresholds based on proximity to expiration
        double adjustedEntrySpread = entrySpread;
        double adjustedSpreadRatio = 1.2;
        
        if (isSettlementWeek) {
            // Tighter spreads required near settlement (more conservative)
            adjustedEntrySpread *= 1.3;
            adjustedSpreadRatio = 1.3;
        } else if (daysToExpiration > 0 && daysToExpiration <= 7) {
            // Approaching settlement - be more selective
            adjustedEntrySpread *= 1.15;
        }
        
        // Calculate confidence based on spread magnitude and calendar context
        double baseConfidence = Math.min(0.85, 0.55 + Math.abs(spread) * 5);
        double confidence = baseConfidence * positionMultiplier;
        
        // Backwardation (near > far): expect volatility to decrease, go long
        if (spread > adjustedEntrySpread && spreadRatio > adjustedSpreadRatio && position <= 0) {
            return TradeSignal.longSignal(confidence,
                String.format("Vol backwardation: near=%.1f%%, far=%.1f%%, spread=%.1f%% (days to exp: %d)", 
                    nearVol * 100, farVol * 100, spread * 100, daysToExpiration));
        }
        
        // Contango (near < far): expect volatility to increase, go short or exit
        if (spread < -adjustedEntrySpread && spreadRatio < 0.8 && position >= 0) {
            return TradeSignal.shortSignal(Math.min(confidence, 0.75),
                String.format("Vol contango: near=%.1f%%, far=%.1f%%, spread=%.1f%% (days to exp: %d)", 
                    nearVol * 100, farVol * 100, spread * 100, daysToExpiration));
        }
        
        // Exit when term structure normalizes
        if (position != 0 && Math.abs(spread) < adjustedEntrySpread / 2) {
            return TradeSignal.exitSignal(
                position > 0 ? TradeSignal.SignalDirection.SHORT : TradeSignal.SignalDirection.LONG,
                0.65,
                String.format("Term structure normalized: spread=%.1f%%", spread * 100));
        }
        
        // Additional context in neutral signal
        Optional<LocalDate> nextExp = calendarProvider.getNextFuturesExpiration();
        String expInfo = nextExp.map(d -> String.format(", next exp: %s", d)).orElse("");
        
        return TradeSignal.neutral(String.format("Vol spread: %.1f%%%s", spread * 100, expInfo));
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
