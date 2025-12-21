package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

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
 */
@Slf4j
public class SeasonalMomentumStrategy implements IStrategy {
    
    private final Set<String> strongMonths;
    private final Set<String> weakMonths;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public SeasonalMomentumStrategy(String[] strongMonths, String[] weakMonths) {
        this.strongMonths = new HashSet<>(Arrays.asList(strongMonths));
        this.weakMonths = new HashSet<>(Arrays.asList(weakMonths));
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
        
        // Calculate short-term momentum for confirmation
        double momentum = 0;
        if (prices.size() >= 20) {
            Double[] priceArray = prices.toArray(new Double[0]);
            double currentPrice = priceArray[priceArray.length - 1];
            double pastPrice = priceArray[priceArray.length - 20];
            momentum = (currentPrice - pastPrice) / pastPrice;
        }
        
        int position = portfolio.getPosition(symbol);
        
        // Strong month + positive momentum = Long
        if (strongMonths.contains(monthAbbrev)) {
            if (momentum >= 0 && position <= 0) {
                return TradeSignal.longSignal(0.70,
                    String.format("Seasonal bullish: %s is a strong month (momentum: %.2f%%)", 
                        monthAbbrev, momentum * 100));
            }
        }
        
        // Weak month = Reduce exposure or go short
        if (weakMonths.contains(monthAbbrev)) {
            if (position > 0) {
                return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.65,
                    String.format("Seasonal exit: %s is historically weak", monthAbbrev));
            }
            if (momentum < 0 && position >= 0) {
                return TradeSignal.shortSignal(0.60,
                    String.format("Seasonal bearish: %s + negative momentum (%.2f%%)", 
                        monthAbbrev, momentum * 100));
            }
        }
        
        // Exit shorts during strong months
        if (position < 0 && strongMonths.contains(monthAbbrev)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.65,
                String.format("Exit short: %s is a strong month", monthAbbrev));
        }
        
        return TradeSignal.neutral(String.format("Month: %s, Momentum: %.2f%%", 
            monthAbbrev, momentum * 100));
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
