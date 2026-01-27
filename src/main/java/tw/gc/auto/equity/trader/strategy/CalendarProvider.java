package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.EconomicEvent;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Functional interface for providing calendar data to trading strategies.
 * 
 * <h3>Design Pattern:</h3>
 * Similar to FundamentalDataProvider, this interface decouples strategies
 * from direct service dependencies, enabling easier testing and flexible
 * calendar data sources.
 * 
 * <h3>Usage in Strategies:</h3>
 * <ul>
 *   <li><b>SeasonalMomentumStrategy</b>: Get seasonal strength and trading day info</li>
 *   <li><b>CalendarSpreadStrategy</b>: Get futures expiration dates</li>
 *   <li><b>Risk Management</b>: Adjust position sizes around high-impact events</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 4 Data Improvement Plan
 */
@FunctionalInterface
public interface CalendarProvider {

    /**
     * Check if a date is a trading day
     * @param date The date to check
     * @return true if markets are open, false if holiday/weekend
     */
    boolean isTradingDay(LocalDate date);

    // ========== Default Implementations ==========

    /**
     * Check if today is a trading day
     */
    default boolean isTodayTradingDay() {
        return isTradingDay(LocalDate.now());
    }

    /**
     * Get next trading day
     */
    default LocalDate getNextTradingDay(LocalDate fromDate) {
        LocalDate next = fromDate.plusDays(1);
        int maxIterations = 30; // Safety limit
        while (!isTradingDay(next) && maxIterations-- > 0) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Get previous trading day
     */
    default LocalDate getPreviousTradingDay(LocalDate fromDate) {
        LocalDate prev = fromDate.minusDays(1);
        int maxIterations = 30; // Safety limit
        while (!isTradingDay(prev) && maxIterations-- > 0) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    /**
     * Get N-th trading day from a date
     */
    default LocalDate getNthTradingDay(LocalDate fromDate, int n) {
        if (n == 0) return isTradingDay(fromDate) ? fromDate : getNextTradingDay(fromDate);
        
        LocalDate current = fromDate;
        int direction = n > 0 ? 1 : -1;
        int remaining = Math.abs(n);
        int maxIterations = Math.abs(n) * 3; // Safety limit
        
        while (remaining > 0 && maxIterations-- > 0) {
            current = current.plusDays(direction);
            if (isTradingDay(current)) {
                remaining--;
            }
        }
        return current;
    }

    /**
     * Count trading days between two dates (inclusive)
     */
    default int countTradingDays(LocalDate startDate, LocalDate endDate) {
        int count = 0;
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (isTradingDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * Get seasonal strength for current month
     * @return -1.0 (weak) to 1.0 (strong), 0.0 for neutral
     */
    default double getSeasonalStrength() {
        return getSeasonalStrength(LocalDate.now().getMonthValue());
    }

    /**
     * Get seasonal strength for a specific month
     * Based on historical patterns (January effect, Sell in May, etc.)
     */
    default double getSeasonalStrength(int month) {
        return switch (month) {
            case 1 -> 0.6;   // January effect
            case 2 -> 0.3;   // Post-January momentum
            case 3 -> 0.2;   // Quarter end
            case 4 -> 0.4;   // Pre-earnings
            case 5 -> -0.3;  // Sell in May
            case 6 -> -0.4;  // Summer doldrums
            case 7 -> -0.2;  // Summer continues
            case 8 -> -0.1;  // Late summer
            case 9 -> -0.5;  // Historically worst month
            case 10 -> 0.2;  // Recovery
            case 11 -> 0.5;  // Pre-holiday rally
            case 12 -> 0.6;  // Santa Claus rally
            default -> 0.0;
        };
    }

    /**
     * Check if current month is historically strong
     */
    default boolean isStrongMonth() {
        return getSeasonalStrength() > 0.3;
    }

    /**
     * Check if current month is historically weak
     */
    default boolean isWeakMonth() {
        return getSeasonalStrength() < -0.2;
    }

    /**
     * Get next futures expiration date
     */
    default Optional<LocalDate> getNextFuturesExpiration() {
        // Default implementation: third Wednesday of current or next month
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        LocalDate thirdWed = month.atDay(1).plusDays(
            (10 + (3 - month.atDay(1).getDayOfWeek().getValue() + 7) % 7));
        
        // If past this month's expiration, get next month's
        if (today.isAfter(thirdWed)) {
            month = month.plusMonths(1);
            thirdWed = month.atDay(1).plusDays(
                (10 + (3 - month.atDay(1).getDayOfWeek().getValue() + 7) % 7));
        }
        
        return Optional.of(thirdWed);
    }

    /**
     * Get days until next futures expiration
     */
    default int getDaysToExpiration() {
        return getNextFuturesExpiration()
            .map(exp -> (int) (exp.toEpochDay() - LocalDate.now().toEpochDay()))
            .orElse(-1);
    }

    /**
     * Check if today is a futures settlement day
     */
    default boolean isSettlementDay() {
        return getNextFuturesExpiration()
            .map(exp -> exp.equals(LocalDate.now()))
            .orElse(false);
    }

    /**
     * Check if we're in settlement week (within 3 trading days)
     */
    default boolean isSettlementWeek() {
        int days = getDaysToExpiration();
        return days >= 0 && days <= 3;
    }

    /**
     * Get event risk level for a date
     * @return 0.0 (no risk) to 1.0 (high risk/holiday)
     */
    default double getEventRiskLevel(LocalDate date) {
        if (!isTradingDay(date)) {
            return 1.0; // Holiday
        }
        return 0.0; // Default: no special events
    }

    /**
     * Get recommended position size multiplier based on event risk
     * @return 0.0 (don't trade) to 1.0 (full size)
     */
    default double getPositionSizeMultiplier(LocalDate date) {
        return Math.max(0.0, 1.0 - getEventRiskLevel(date));
    }

    /**
     * Check if month-end rebalancing period
     */
    default boolean isMonthEndRebalancing() {
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = YearMonth.from(today).atEndOfMonth();
        int tradingDaysLeft = countTradingDays(today, monthEnd);
        return tradingDaysLeft <= 3;
    }

    /**
     * Check if quarter-end period
     */
    default boolean isQuarterEnd() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        if (month != 3 && month != 6 && month != 9 && month != 12) {
            return false;
        }
        LocalDate quarterEnd = YearMonth.from(today).atEndOfMonth();
        int tradingDaysLeft = countTradingDays(today, quarterEnd);
        return tradingDaysLeft <= 5;
    }

    // ========== Factory Methods ==========

    /**
     * Create a no-op provider (all days are trading days, neutral seasonality)
     */
    static CalendarProvider noOp() {
        return date -> {
            // Only weekends are non-trading
            return date.getDayOfWeek().getValue() < 6;
        };
    }

    /**
     * Create a provider with a fixed set of holidays
     */
    static CalendarProvider withHolidays(Set<LocalDate> holidays) {
        return date -> {
            if (date.getDayOfWeek().getValue() >= 6) {
                return false; // Weekend
            }
            return !holidays.contains(date);
        };
    }

    /**
     * Create a provider that always returns trading day
     */
    static CalendarProvider alwaysTradingDay() {
        return date -> true;
    }

    /**
     * Create a provider with custom seasonal strength map
     */
    static CalendarProvider withSeasonalStrength(Map<Integer, Double> monthStrength) {
        return new CalendarProvider() {
            @Override
            public boolean isTradingDay(LocalDate date) {
                return date.getDayOfWeek().getValue() < 6;
            }

            @Override
            public double getSeasonalStrength(int month) {
                return monthStrength.getOrDefault(month, 0.0);
            }
        };
    }

    /**
     * Create a provider with fixed futures expiration dates
     */
    static CalendarProvider withFuturesExpirations(List<LocalDate> expirations) {
        Set<LocalDate> expSet = new HashSet<>(expirations);
        return new CalendarProvider() {
            @Override
            public boolean isTradingDay(LocalDate date) {
                return date.getDayOfWeek().getValue() < 6;
            }

            @Override
            public Optional<LocalDate> getNextFuturesExpiration() {
                LocalDate today = LocalDate.now();
                return expirations.stream()
                    .filter(d -> !d.isBefore(today))
                    .findFirst();
            }

            @Override
            public boolean isSettlementDay() {
                return expSet.contains(LocalDate.now());
            }
        };
    }

    /**
     * Create a provider with event risk levels
     */
    static CalendarProvider withEventRisk(Map<LocalDate, Double> riskLevels) {
        return new CalendarProvider() {
            @Override
            public boolean isTradingDay(LocalDate date) {
                return date.getDayOfWeek().getValue() < 6 && 
                       riskLevels.getOrDefault(date, 0.0) < 1.0;
            }

            @Override
            public double getEventRiskLevel(LocalDate date) {
                return riskLevels.getOrDefault(date, 0.0);
            }
        };
    }
}
