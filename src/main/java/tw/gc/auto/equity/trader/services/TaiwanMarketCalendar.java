package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tw.gc.auto.equity.trader.entities.EconomicEvent;
import tw.gc.auto.equity.trader.entities.EconomicEvent.EventType;
import tw.gc.auto.equity.trader.entities.EconomicEvent.ImpactLevel;
import tw.gc.auto.equity.trader.repositories.EconomicEventRepository;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Taiwan-specific market calendar utilities.
 * 
 * <h3>Features:</h3>
 * <ul>
 *   <li>Taiwan Stock Exchange (TWSE) holidays</li>
 *   <li>Taiwan Futures Exchange (TAIFEX) settlement dates</li>
 *   <li>Lunar calendar events (Chinese New Year, etc.)</li>
 *   <li>Taiwan-specific trading patterns</li>
 * </ul>
 * 
 * <h3>Trading Hours:</h3>
 * <ul>
 *   <li>TWSE: 09:00 - 13:30 (Taiwan Time, UTC+8)</li>
 *   <li>TAIFEX: 08:45 - 13:45</li>
 *   <li>After-hours: 14:00 - 14:30 (odd lot trading)</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 4 Data Improvement Plan
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaiwanMarketCalendar {

    private final EconomicEventRepository economicEventRepository;

    // Taiwan timezone
    public static final ZoneId TAIWAN_ZONE = ZoneId.of("Asia/Taipei");
    
    // Trading hours (Taiwan local time)
    public static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    public static final LocalTime MARKET_CLOSE = LocalTime.of(13, 30);
    public static final LocalTime FUTURES_OPEN = LocalTime.of(8, 45);
    public static final LocalTime FUTURES_CLOSE = LocalTime.of(13, 45);
    public static final LocalTime ODD_LOT_OPEN = LocalTime.of(14, 0);
    public static final LocalTime ODD_LOT_CLOSE = LocalTime.of(14, 30);

    // Fixed holidays (month, day) - approximate, some vary by year
    private static final Set<MonthDay> FIXED_HOLIDAYS = Set.of(
        MonthDay.of(1, 1),   // New Year's Day
        MonthDay.of(2, 28),  // Peace Memorial Day
        MonthDay.of(4, 4),   // Children's Day
        MonthDay.of(4, 5),   // Tomb Sweeping Day (approximate)
        MonthDay.of(5, 1),   // Labor Day
        MonthDay.of(10, 10)  // Double Ten Day (National Day)
    );

    // ========== Trading Hours ==========

    /**
     * Check if the market is currently open
     */
    public boolean isMarketOpen() {
        return isMarketOpenAt(ZonedDateTime.now(TAIWAN_ZONE));
    }

    /**
     * Check if market is open at a specific time
     */
    public boolean isMarketOpenAt(ZonedDateTime dateTime) {
        ZonedDateTime taiwanTime = dateTime.withZoneSameInstant(TAIWAN_ZONE);
        LocalDate date = taiwanTime.toLocalDate();
        LocalTime time = taiwanTime.toLocalTime();
        
        // Check if trading day
        if (isHoliday(date)) {
            return false;
        }
        
        // Check trading hours
        return !time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE);
    }

    /**
     * Get time until market opens (in minutes)
     * @return Minutes until open, or 0 if already open, or -1 if closed for the day
     */
    public int getMinutesUntilOpen() {
        ZonedDateTime now = ZonedDateTime.now(TAIWAN_ZONE);
        LocalTime time = now.toLocalTime();
        LocalDate date = now.toLocalDate();
        
        // If holiday, market won't open
        if (isHoliday(date)) {
            return -1;
        }
        
        // Already open
        if (!time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE)) {
            return 0;
        }
        
        // Before open
        if (time.isBefore(MARKET_OPEN)) {
            return (int) Duration.between(time, MARKET_OPEN).toMinutes();
        }
        
        // After close
        return -1;
    }

    /**
     * Get time until market closes (in minutes)
     * @return Minutes until close, or -1 if already closed
     */
    public int getMinutesUntilClose() {
        ZonedDateTime now = ZonedDateTime.now(TAIWAN_ZONE);
        LocalTime time = now.toLocalTime();
        
        if (time.isBefore(MARKET_OPEN) || time.isAfter(MARKET_CLOSE)) {
            return -1;
        }
        
        return (int) Duration.between(time, MARKET_CLOSE).toMinutes();
    }

    /**
     * Get next market open date and time
     */
    public ZonedDateTime getNextMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(TAIWAN_ZONE);
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();
        
        // If before today's open and today is trading day
        if (time.isBefore(MARKET_OPEN) && !isHoliday(date)) {
            return ZonedDateTime.of(date, MARKET_OPEN, TAIWAN_ZONE);
        }
        
        // Find next trading day
        LocalDate nextDay = date.plusDays(1);
        while (isHoliday(nextDay)) {
            nextDay = nextDay.plusDays(1);
        }
        
        return ZonedDateTime.of(nextDay, MARKET_OPEN, TAIWAN_ZONE);
    }

    // ========== Holiday Checking ==========

    /**
     * Check if a date is a Taiwan market holiday
     */
    public boolean isHoliday(LocalDate date) {
        // Weekend check
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return true;
        }
        
        // Check fixed holidays
        MonthDay md = MonthDay.from(date);
        if (FIXED_HOLIDAYS.contains(md)) {
            return true;
        }
        
        // Check database for dynamic holidays
        return economicEventRepository.isMarketHoliday("TW", date);
    }

    /**
     * Check if a date is a trading day
     */
    public boolean isTradingDay(LocalDate date) {
        return !isHoliday(date);
    }

    /**
     * Get holidays for a year
     */
    public List<LocalDate> getHolidays(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        
        return economicEventRepository.findMarketHolidaysByCountry("TW", start, end)
            .stream()
            .map(EconomicEvent::getEventDate)
            .collect(Collectors.toList());
    }

    // ========== Futures Settlement ==========

    /**
     * Calculate TAIFEX futures settlement date for a month
     * Rule: Third Wednesday of each month
     */
    public LocalDate getFuturesSettlementDate(YearMonth month) {
        LocalDate firstDay = month.atDay(1);
        LocalDate thirdWednesday = firstDay
            .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
        
        // If it falls on a holiday, move to previous trading day
        while (isHoliday(thirdWednesday)) {
            thirdWednesday = getPreviousTradingDay(thirdWednesday);
        }
        
        return thirdWednesday;
    }

    /**
     * Get next futures settlement date
     */
    public LocalDate getNextFuturesSettlement() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate currentMonthSettlement = getFuturesSettlementDate(currentMonth);
        
        if (today.isBefore(currentMonthSettlement) || today.equals(currentMonthSettlement)) {
            return currentMonthSettlement;
        }
        
        return getFuturesSettlementDate(currentMonth.plusMonths(1));
    }

    /**
     * Get days until next settlement
     */
    public int getDaysToNextSettlement() {
        LocalDate next = getNextFuturesSettlement();
        return (int) (next.toEpochDay() - LocalDate.now().toEpochDay());
    }

    /**
     * Check if today is settlement day
     */
    public boolean isSettlementDay() {
        return isSettlementDay(LocalDate.now());
    }

    /**
     * Check if a date is settlement day
     */
    public boolean isSettlementDay(LocalDate date) {
        YearMonth month = YearMonth.from(date);
        LocalDate settlement = getFuturesSettlementDate(month);
        return date.equals(settlement);
    }

    /**
     * Check if we're in the settlement week (3 days before settlement)
     */
    public boolean isSettlementWeek() {
        LocalDate nextSettlement = getNextFuturesSettlement();
        int daysToSettlement = (int) (nextSettlement.toEpochDay() - LocalDate.now().toEpochDay());
        return daysToSettlement >= 0 && daysToSettlement <= 3;
    }

    // ========== Trading Day Navigation ==========

    /**
     * Get next trading day
     */
    public LocalDate getNextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (isHoliday(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Get previous trading day
     */
    public LocalDate getPreviousTradingDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (isHoliday(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    /**
     * Get N-th trading day from a date
     */
    public LocalDate getNthTradingDay(LocalDate fromDate, int n) {
        if (n == 0) return isTradingDay(fromDate) ? fromDate : getNextTradingDay(fromDate);
        
        LocalDate current = fromDate;
        int direction = n > 0 ? 1 : -1;
        int remaining = Math.abs(n);
        
        while (remaining > 0) {
            current = current.plusDays(direction);
            if (isTradingDay(current)) {
                remaining--;
            }
        }
        return current;
    }

    /**
     * Get trading days in a month
     */
    public List<LocalDate> getTradingDaysInMonth(YearMonth month) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate date = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();
        
        while (!date.isAfter(endDate)) {
            if (isTradingDay(date)) {
                tradingDays.add(date);
            }
            date = date.plusDays(1);
        }
        return tradingDays;
    }

    /**
     * Count trading days between two dates
     */
    public int countTradingDays(LocalDate startDate, LocalDate endDate) {
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

    // ========== Special Trading Periods ==========

    /**
     * Check if we're in earnings season (typically Jan-Feb, Apr-May, Jul-Aug, Oct-Nov)
     */
    public boolean isEarningsSeason() {
        int month = LocalDate.now().getMonthValue();
        return month == 1 || month == 2 || month == 4 || month == 5 ||
               month == 7 || month == 8 || month == 10 || month == 11;
    }

    /**
     * Check if we're near Chinese New Year (typically high volatility)
     * Returns true for the 2 weeks before and 1 week after CNY
     */
    public boolean isNearChineseNewYear() {
        // CNY varies by year, check from database
        LocalDate today = LocalDate.now();
        List<EconomicEvent> cnyEvents = economicEventRepository.findByCountryAndDateRange(
            "TW", today.minusDays(7), today.plusDays(14));
        
        return cnyEvents.stream()
            .anyMatch(e -> e.getEventName().toLowerCase().contains("chinese new year") ||
                          e.getEventName().toLowerCase().contains("lunar new year"));
    }

    /**
     * Check if month-end rebalancing period (last 3 trading days)
     */
    public boolean isMonthEndRebalancing() {
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = YearMonth.from(today).atEndOfMonth();
        
        // Get last 3 trading days of the month
        LocalDate thirdLastDay = getPreviousTradingDay(
            getPreviousTradingDay(
                getPreviousTradingDay(monthEnd.plusDays(1))));
        
        return !today.isBefore(thirdLastDay) && !today.isAfter(monthEnd);
    }

    /**
     * Check if quarter-end period (last 5 trading days of quarter)
     */
    public boolean isQuarterEnd() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        
        // Quarter end months: 3, 6, 9, 12
        if (month != 3 && month != 6 && month != 9 && month != 12) {
            return false;
        }
        
        LocalDate quarterEnd = YearMonth.from(today).atEndOfMonth();
        LocalDate fifthLastDay = getNthTradingDay(quarterEnd, -5);
        
        return !today.isBefore(fifthLastDay) && !today.isAfter(quarterEnd);
    }

    // ========== Holiday Data Management ==========

    /**
     * Create a market holiday event
     */
    public EconomicEvent createHolidayEvent(LocalDate date, String name) {
        String eventCode = String.format("TW_HOLIDAY_%s", date.toString());
        
        return EconomicEvent.builder()
            .eventType(EventType.MARKET_HOLIDAY)
            .eventName(name)
            .eventCode(eventCode)
            .eventDate(date)
            .country("TW")
            .currency("TWD")
            .impactLevel(ImpactLevel.HOLIDAY)
            .dataSource("TWSE")
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    /**
     * Generate 2026 Taiwan holidays (example data)
     */
    public List<EconomicEvent> generate2026Holidays() {
        List<EconomicEvent> holidays = new ArrayList<>();
        
        // 2026 Taiwan holidays
        holidays.add(createHolidayEvent(LocalDate.of(2026, 1, 1), "New Year's Day"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 1, 2), "New Year Holiday"));
        
        // Chinese New Year 2026 (approximate - Feb 17 is Chinese New Year)
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 13), "CNY Eve Observance"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 16), "Chinese New Year Eve"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 17), "Chinese New Year Day 1"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 18), "Chinese New Year Day 2"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 19), "Chinese New Year Day 3"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 20), "Chinese New Year Holiday"));
        
        holidays.add(createHolidayEvent(LocalDate.of(2026, 2, 28), "Peace Memorial Day"));
        
        holidays.add(createHolidayEvent(LocalDate.of(2026, 4, 3), "Children's Day Observed"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 4, 4), "Children's Day"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 4, 5), "Tomb Sweeping Day"));
        holidays.add(createHolidayEvent(LocalDate.of(2026, 4, 6), "Tomb Sweeping Holiday"));
        
        holidays.add(createHolidayEvent(LocalDate.of(2026, 5, 1), "Labor Day"));
        
        // Dragon Boat Festival (approximate - June 19, 2026)
        holidays.add(createHolidayEvent(LocalDate.of(2026, 6, 19), "Dragon Boat Festival"));
        
        // Mid-Autumn Festival (approximate - October 3, 2026)
        holidays.add(createHolidayEvent(LocalDate.of(2026, 10, 3), "Mid-Autumn Festival"));
        
        holidays.add(createHolidayEvent(LocalDate.of(2026, 10, 10), "Double Ten Day"));
        
        return holidays;
    }
}
