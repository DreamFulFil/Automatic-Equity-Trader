package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.CalendarProvider;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SeasonalMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Heston & Sadka (2008) - 'Seasonality in the Cross-Section'
 * - Bouman & Jacobsen (2002) - 'The Halloween Indicator'
 * 
 * Logic:
 * Calendar-based momentum exploiting seasonal patterns.
 * Go long during historically strong months, reduce exposure during weak months.
 * 
 * Enhanced with CalendarProvider (Phase 4):
 * - Uses real seasonal strength data
 * - Adjusts position sizing around futures expiration
 * - Considers event risk for timing
 */
@Slf4j
public class SeasonalMomentumStrategy implements IStrategy {
    
    private final Set<String> strongMonths;
    private final Set<String> weakMonths;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final CalendarProvider calendarProvider;
    
    public SeasonalMomentumStrategy(String[] strongMonths, String[] weakMonths) {
        this(strongMonths, weakMonths, CalendarProvider.noOp());
    }
    
    public SeasonalMomentumStrategy(String[] strongMonths, String[] weakMonths, CalendarProvider calendarProvider) {
        this.strongMonths = new HashSet<>(Arrays.asList(strongMonths));
        this.weakMonths = new HashSet<>(Arrays.asList(weakMonths));
        this.calendarProvider = calendarProvider != null ? calendarProvider : CalendarProvider.noOp();
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > 30) {
            prices.removeFirst();
        }
        
        // Get current month from timestamp
        Month currentMonth = data.getTimestamp().getMonth();
        String monthAbbrev = getMonthAbbreviation(currentMonth);
        int monthValue = currentMonth.getValue();
        
        // Get seasonal strength from provider (enhanced with real data)
        double seasonalStrength = calendarProvider.getSeasonalStrength(monthValue);
        boolean isProviderStrongMonth = calendarProvider.isStrongMonth();
        boolean isProviderWeakMonth = calendarProvider.isWeakMonth();
        
        // Calculate short-term momentum for confirmation
        double momentum = 0;
        if (prices.size() >= 20) {
            Double[] priceArray = prices.toArray(new Double[0]);
            double currentPrice = priceArray[priceArray.length - 1];
            double pastPrice = priceArray[priceArray.length - 20];
            momentum = (currentPrice - pastPrice) / pastPrice;
        }
        
        // Get position sizing from calendar (reduce around expiration/events)
        LocalDate tradeDate = data.getTimestamp().toLocalDate();
        double positionMultiplier = calendarProvider.getPositionSizeMultiplier(tradeDate);
        
        // Reduce exposure during settlement week
        if (calendarProvider.isSettlementWeek()) {
            positionMultiplier *= 0.7; // 30% reduction during settlement week
        }
        
        int position = portfolio.getPosition(symbol);
        
        // Use provider's seasonal analysis if available, fallback to original logic
        boolean useStrongMonthSignal = strongMonths.contains(monthAbbrev) || 
                                        (isProviderStrongMonth && seasonalStrength > 0.4);
        boolean useWeakMonthSignal = weakMonths.contains(monthAbbrev) || 
                                      (isProviderWeakMonth && seasonalStrength < -0.3);
        
        // Strong month + positive momentum = Long
        if (useStrongMonthSignal) {
            if (momentum >= 0 && position <= 0) {
                double confidence = Math.min(0.85, 0.60 + seasonalStrength * 0.3);
                confidence *= positionMultiplier; // Adjust for event risk
                return TradeSignal.longSignal(confidence,
                    String.format("Seasonal bullish: %s is a strong month (strength: %.2f, momentum: %.2f%%)", 
                        monthAbbrev, seasonalStrength, momentum * 100));
            }
        }
        
        // Weak month = Reduce exposure or go short
        if (useWeakMonthSignal) {
            if (position > 0) {
                double confidence = Math.min(0.80, 0.55 - seasonalStrength * 0.3);
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, confidence,
                    String.format("Seasonal exit: %s is historically weak (strength: %.2f)", 
                        monthAbbrev, seasonalStrength));
            }
            if (momentum < 0 && position >= 0) {
                double confidence = Math.min(0.75, 0.50 - seasonalStrength * 0.3);
                confidence *= positionMultiplier;
                return TradeSignal.shortSignal(confidence,
                    String.format("Seasonal bearish: %s + negative momentum (strength: %.2f, momentum: %.2f%%)", 
                        monthAbbrev, seasonalStrength, momentum * 100));
            }
        }
        
        // Exit shorts during strong months
        if (position < 0 && useStrongMonthSignal) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.65,
                String.format("Exit short: %s is a strong month (strength: %.2f)", 
                    monthAbbrev, seasonalStrength));
        }
        
        return TradeSignal.neutral(String.format("Month: %s (strength: %.2f), Momentum: %.2f%%", 
            monthAbbrev, seasonalStrength, momentum * 100));
    }
    
    private String getMonthAbbreviation(Month month) {
        return switch (month) {
            case JANUARY -> "Jan";
            case FEBRUARY -> "Feb";
            case MARCH -> "Mar";
            case APRIL -> "Apr";
            case MAY -> "May";
            case JUNE -> "Jun";
            case JULY -> "Jul";
            case AUGUST -> "Aug";
            case SEPTEMBER -> "Sep";
            case OCTOBER -> "Oct";
            case NOVEMBER -> "Nov";
            case DECEMBER -> "Dec";
        };
    }

    @Override
    public String getName() {
        return "Seasonal Momentum Strategy";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
