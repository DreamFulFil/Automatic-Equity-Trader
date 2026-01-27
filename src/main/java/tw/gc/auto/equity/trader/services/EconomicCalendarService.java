package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.EconomicEvent;
import tw.gc.auto.equity.trader.entities.EconomicEvent.EventType;
import tw.gc.auto.equity.trader.entities.EconomicEvent.ImpactLevel;
import tw.gc.auto.equity.trader.repositories.EconomicEventRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing economic calendar and market events.
 * 
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Retrieve upcoming economic events</li>
 *   <li>Check market holidays and trading days</li>
 *   <li>Calculate futures expiration dates</li>
 *   <li>Provide event-based trading recommendations</li>
 * </ul>
 * 
 * <h3>Scheduled Tasks:</h3>
 * <ul>
 *   <li>Weekly: Refresh economic calendar from external sources</li>
 *   <li>Yearly: Generate futures expiration dates</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 4 Data Improvement Plan
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EconomicCalendarService {

    private final EconomicEventRepository economicEventRepository;

    // Country codes
    public static final String TAIWAN = "TW";
    public static final String USA = "US";
    public static final String CHINA = "CN";
    public static final String JAPAN = "JP";

    // ========== Event Retrieval ==========

    /**
     * Get upcoming events within the next N days
     */
    public List<EconomicEvent> getUpcomingEvents(int days) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);
        return economicEventRepository.findUpcomingEvents(today, endDate);
    }

    /**
     * Get upcoming high-impact events
     */
    public List<EconomicEvent> getUpcomingHighImpactEvents(int days) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);
        return economicEventRepository.findUpcomingHighImpactEvents(today, endDate);
    }

    /**
     * Get today's events
     */
    public List<EconomicEvent> getTodaysEvents() {
        return economicEventRepository.findTodaysEvents(LocalDate.now());
    }

    /**
     * Get events by country
     */
    public List<EconomicEvent> getUpcomingEventsByCountry(String country, int days) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);
        return economicEventRepository.findByCountryAndDateRange(country, today, endDate);
    }

    /**
     * Get Taiwan-specific events
     */
    public List<EconomicEvent> getTaiwanEvents(int days) {
        return getUpcomingEventsByCountry(TAIWAN, days);
    }

    // ========== Market Hours & Trading Days ==========

    /**
     * Check if a date is a market holiday
     */
    public boolean isMarketHoliday(LocalDate date) {
        return isMarketHoliday(TAIWAN, date);
    }

    /**
     * Check if a date is a market holiday for a specific country
     */
    public boolean isMarketHoliday(String country, LocalDate date) {
        // Weekend check
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return true;
        }
        return economicEventRepository.isMarketHoliday(country, date);
    }

    /**
     * Check if a date is a trading day
     */
    public boolean isTradingDay(LocalDate date) {
        return !isMarketHoliday(date);
    }

    /**
     * Get the next trading day
     */
    public LocalDate getNextTradingDay(LocalDate fromDate) {
        LocalDate nextDay = fromDate.plusDays(1);
        while (isMarketHoliday(nextDay)) {
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }

    /**
     * Get the previous trading day
     */
    public LocalDate getPreviousTradingDay(LocalDate fromDate) {
        LocalDate prevDay = fromDate.minusDays(1);
        while (isMarketHoliday(prevDay)) {
            prevDay = prevDay.minusDays(1);
        }
        return prevDay;
    }

    /**
     * Get trading days between two dates
     */
    public List<LocalDate> getTradingDays(LocalDate startDate, LocalDate endDate) {
        List<LocalDate> tradingDays = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (isTradingDay(current)) {
                tradingDays.add(current);
            }
            current = current.plusDays(1);
        }
        return tradingDays;
    }

    /**
     * Count trading days between two dates
     */
    public int countTradingDays(LocalDate startDate, LocalDate endDate) {
        return getTradingDays(startDate, endDate).size();
    }

    /**
     * Get N-th trading day from a date
     */
    public LocalDate getNthTradingDay(LocalDate fromDate, int n) {
        if (n == 0) return fromDate;
        
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

    // ========== Futures Expiration ==========

    /**
     * Get next futures expiration date for Taiwan
     */
    public Optional<LocalDate> getNextFuturesExpiration() {
        return economicEventRepository.findNextFuturesExpiration(TAIWAN, LocalDate.now())
            .map(EconomicEvent::getEventDate);
    }

    /**
     * Get futures expiration dates within a date range
     */
    public List<LocalDate> getFuturesExpirations(LocalDate startDate, LocalDate endDate) {
        return economicEventRepository.findFuturesExpirations(startDate, endDate)
            .stream()
            .map(EconomicEvent::getEventDate)
            .collect(Collectors.toList());
    }

    /**
     * Check if a date is a futures expiration day
     */
    public boolean isFuturesExpiration(LocalDate date) {
        return economicEventRepository.findFuturesExpirations(date, date).size() > 0;
    }

    /**
     * Get days until next futures expiration
     */
    public OptionalInt getDaysToNextExpiration() {
        return getNextFuturesExpiration()
            .map(exp -> OptionalInt.of((int) (exp.toEpochDay() - LocalDate.now().toEpochDay())))
            .orElse(OptionalInt.empty());
    }

    /**
     * Calculate Taiwan futures expiration date for a month
     * Taiwan Futures Exchange: Third Wednesday of each month
     */
    public LocalDate calculateFuturesExpiration(YearMonth month) {
        LocalDate firstDay = month.atDay(1);
        LocalDate thirdWednesday = firstDay
            .with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.WEDNESDAY));
        
        // If third Wednesday is a holiday, move to previous trading day
        while (isMarketHoliday(thirdWednesday)) {
            thirdWednesday = getPreviousTradingDay(thirdWednesday);
        }
        return thirdWednesday;
    }

    // ========== Event Risk Assessment ==========

    /**
     * Get risk level for trading on a specific date
     * @return 0.0 (low risk) to 1.0 (high risk)
     */
    public double getEventRiskLevel(LocalDate date) {
        List<EconomicEvent> events = economicEventRepository.findByDateRange(date, date);
        
        if (events.isEmpty()) {
            return 0.0;
        }
        
        // Calculate risk based on impact levels
        double maxRisk = 0.0;
        for (EconomicEvent event : events) {
            double eventRisk = switch (event.getImpactLevel()) {
                case HIGH -> 0.9;
                case MEDIUM -> 0.5;
                case LOW -> 0.2;
                case HOLIDAY -> 1.0; // No trading
            };
            maxRisk = Math.max(maxRisk, eventRisk);
        }
        
        return maxRisk;
    }

    /**
     * Get risk-adjusted position size multiplier
     * @return 0.0 (don't trade) to 1.0 (full size)
     */
    public double getPositionSizeMultiplier(LocalDate date) {
        double risk = getEventRiskLevel(date);
        return Math.max(0.0, 1.0 - risk);
    }

    /**
     * Should reduce exposure before upcoming high-impact events?
     */
    public boolean shouldReduceExposure(int lookAheadDays) {
        long highImpactCount = economicEventRepository.countUpcomingHighImpactEvents(
            LocalDate.now(), LocalDate.now().plusDays(lookAheadDays));
        return highImpactCount > 0;
    }

    /**
     * Get recommended exposure level (0.0 to 1.0) based on upcoming events
     */
    public double getRecommendedExposure(int lookAheadDays) {
        List<EconomicEvent> events = getUpcomingHighImpactEvents(lookAheadDays);
        
        if (events.isEmpty()) {
            return 1.0; // Full exposure
        }
        
        // Find closest high-impact event
        OptionalLong minDays = events.stream()
            .mapToLong(EconomicEvent::getDaysUntil)
            .min();
        
        if (minDays.isEmpty()) {
            return 1.0;
        }
        
        // Linear reduction as event approaches
        // 7 days = 100%, 0 days = 30%
        double daysToEvent = minDays.getAsLong();
        double exposure = 0.3 + (0.7 * Math.min(daysToEvent, 7) / 7.0);
        
        return Math.max(0.3, Math.min(1.0, exposure));
    }

    // ========== Seasonal Analysis ==========

    /**
     * Get strong/weak months based on historical patterns
     * Based on "Sell in May" effect and January effect
     */
    public Set<Integer> getHistoricallyStrongMonths() {
        return Set.of(
            1,  // January - January effect
            4,  // April - Pre-earnings season
            11, // November - Pre-holiday rally
            12  // December - Santa Claus rally
        );
    }

    /**
     * Get historically weak months
     */
    public Set<Integer> getHistoricallyWeakMonths() {
        return Set.of(
            5,  // May - "Sell in May"
            6,  // June - Summer doldrums
            9   // September - Historically worst month
        );
    }

    /**
     * Get seasonal strength for current month
     * @return -1.0 (very weak) to 1.0 (very strong)
     */
    public double getSeasonalStrength() {
        int currentMonth = LocalDate.now().getMonthValue();
        
        if (getHistoricallyStrongMonths().contains(currentMonth)) {
            return 0.6;
        } else if (getHistoricallyWeakMonths().contains(currentMonth)) {
            return -0.4;
        }
        return 0.0;
    }

    // ========== Data Management ==========

    /**
     * Save or update an economic event
     */
    @Transactional
    public EconomicEvent saveEvent(EconomicEvent event) {
        if (event.getUpdatedAt() == null) {
            event.setUpdatedAt(OffsetDateTime.now());
        }
        return economicEventRepository.save(event);
    }

    /**
     * Save multiple events
     */
    @Transactional
    public List<EconomicEvent> saveEvents(List<EconomicEvent> events) {
        events.forEach(e -> {
            if (e.getUpdatedAt() == null) {
                e.setUpdatedAt(OffsetDateTime.now());
            }
        });
        return economicEventRepository.saveAll(events);
    }

    /**
     * Generate futures expiration events for a year
     */
    @Transactional
    public List<EconomicEvent> generateFuturesExpirations(int year) {
        List<EconomicEvent> events = new ArrayList<>();
        
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            LocalDate expDate = calculateFuturesExpiration(ym);
            
            String eventCode = String.format("TW_FUT_EXP_%d_%02d", year, month);
            
            // Skip if already exists
            if (economicEventRepository.existsByEventCode(eventCode)) {
                continue;
            }
            
            EconomicEvent event = EconomicEvent.builder()
                .eventType(EventType.FUTURES_EXPIRATION)
                .eventName(String.format("TAIEX Futures Settlement %s", ym))
                .eventCode(eventCode)
                .eventDate(expDate)
                .country(TAIWAN)
                .currency("TWD")
                .impactLevel(ImpactLevel.MEDIUM)
                .dataSource("TAIFEX")
                .notes("Third Wednesday settlement")
                .updatedAt(OffsetDateTime.now())
                .build();
            
            events.add(event);
        }
        
        return saveEvents(events);
    }

    /**
     * Scheduled task: Generate next year's futures expiration dates
     * Runs January 1st at 00:00
     */
    @Scheduled(cron = "0 0 0 1 1 *")
    @Transactional
    public void scheduledGenerateFuturesExpirations() {
        int currentYear = LocalDate.now().getYear();
        log.info("Generating futures expiration dates for year {}", currentYear);
        List<EconomicEvent> generated = generateFuturesExpirations(currentYear);
        log.info("Generated {} futures expiration events", generated.size());
    }

    /**
     * Scheduled task: Cleanup old events (older than 2 years)
     * Runs monthly on the 1st at 01:00
     */
    @Scheduled(cron = "0 0 1 1 * *")
    @Transactional
    public void scheduledCleanupOldEvents() {
        LocalDate cutoff = LocalDate.now().minusYears(2);
        log.info("Cleaning up events older than {}", cutoff);
        economicEventRepository.deleteOldEvents(cutoff);
    }
}
