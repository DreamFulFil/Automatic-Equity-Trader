package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.EconomicEvent;
import tw.gc.auto.equity.trader.entities.EconomicEvent.EventType;
import tw.gc.auto.equity.trader.entities.EconomicEvent.ImpactLevel;
import tw.gc.auto.equity.trader.repositories.EconomicEventRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EconomicCalendarService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EconomicCalendarService Tests")
class EconomicCalendarServiceTest {

    @Mock
    private EconomicEventRepository economicEventRepository;

    private EconomicCalendarService service;

    @BeforeEach
    void setUp() {
        service = new EconomicCalendarService(economicEventRepository);
    }

    @Nested
    @DisplayName("Upcoming Events Tests")
    class UpcomingEventsTests {

        @Test
        @DisplayName("Should get upcoming events within days")
        void shouldGetUpcomingEvents() {
            LocalDate today = LocalDate.now();
            List<EconomicEvent> events = List.of(
                createEvent("Event 1", today.plusDays(1), ImpactLevel.HIGH),
                createEvent("Event 2", today.plusDays(3), ImpactLevel.MEDIUM)
            );

            when(economicEventRepository.findUpcomingEvents(eq(today), any(LocalDate.class)))
                .thenReturn(events);

            List<EconomicEvent> result = service.getUpcomingEvents(7);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should get upcoming high impact events")
        void shouldGetUpcomingHighImpactEvents() {
            LocalDate today = LocalDate.now();
            List<EconomicEvent> events = List.of(
                createEvent("High Impact Event", today.plusDays(2), ImpactLevel.HIGH)
            );

            when(economicEventRepository.findUpcomingHighImpactEvents(eq(today), any(LocalDate.class)))
                .thenReturn(events);

            List<EconomicEvent> result = service.getUpcomingHighImpactEvents(7);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getImpactLevel()).isEqualTo(ImpactLevel.HIGH);
        }

        @Test
        @DisplayName("Should get today's events")
        void shouldGetTodaysEvents() {
            LocalDate today = LocalDate.now();
            List<EconomicEvent> events = List.of(
                createEvent("Today's Event", today, ImpactLevel.MEDIUM)
            );

            when(economicEventRepository.findTodaysEvents(today)).thenReturn(events);

            List<EconomicEvent> result = service.getTodaysEvents();

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should get events by country")
        void shouldGetEventsByCountry() {
            LocalDate today = LocalDate.now();
            List<EconomicEvent> events = List.of(
                createEvent("Taiwan Event", today.plusDays(1), ImpactLevel.HIGH)
            );

            when(economicEventRepository.findByCountryAndDateRange(eq("TW"), eq(today), any(LocalDate.class)))
                .thenReturn(events);

            List<EconomicEvent> result = service.getUpcomingEventsByCountry("TW", 7);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Market Holiday Tests")
    class MarketHolidayTests {

        @Test
        @DisplayName("Should identify weekend as holiday")
        void shouldIdentifyWeekendAsHoliday() {
            // Find next Saturday
            LocalDate saturday = LocalDate.now();
            while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
                saturday = saturday.plusDays(1);
            }

            assertThat(service.isMarketHoliday(saturday)).isTrue();
            assertThat(service.isMarketHoliday(saturday.plusDays(1))).isTrue(); // Sunday
        }

        @Test
        @DisplayName("Should check database for holidays")
        void shouldCheckDatabaseForHolidays() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday("TW", monday)).thenReturn(true);

            assertThat(service.isMarketHoliday(monday)).isTrue();
        }

        @Test
        @DisplayName("Should identify trading day")
        void shouldIdentifyTradingDay() {
            LocalDate wednesday = LocalDate.now();
            while (wednesday.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
                wednesday = wednesday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday("TW", wednesday)).thenReturn(false);

            assertThat(service.isTradingDay(wednesday)).isTrue();
        }
    }

    @Nested
    @DisplayName("Trading Day Navigation Tests")
    class TradingDayNavigationTests {

        @Test
        @DisplayName("Should get next trading day skipping weekend")
        void shouldGetNextTradingDaySkippingWeekend() {
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate nextDay = service.getNextTradingDay(friday);

            assertThat(nextDay.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("Should get previous trading day")
        void shouldGetPreviousTradingDay() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate prevDay = service.getPreviousTradingDay(monday);

            assertThat(prevDay.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        }

        @Test
        @DisplayName("Should get trading days in range")
        void shouldGetTradingDaysInRange() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }
            LocalDate friday = monday.plusDays(4);

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            List<LocalDate> tradingDays = service.getTradingDays(monday, friday);

            assertThat(tradingDays).hasSize(5); // Mon-Fri
        }

        @Test
        @DisplayName("Should count trading days")
        void shouldCountTradingDays() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }
            LocalDate friday = monday.plusDays(4);

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            int count = service.countTradingDays(monday, friday);

            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Should get N-th trading day")
        void shouldGetNthTradingDay() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate result = service.getNthTradingDay(monday, 3);

            assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
        }
    }

    @Nested
    @DisplayName("Futures Expiration Tests")
    class FuturesExpirationTests {

        @Test
        @DisplayName("Should get next futures expiration")
        void shouldGetNextFuturesExpiration() {
            LocalDate today = LocalDate.now();
            LocalDate expDate = today.plusDays(10);

            EconomicEvent expEvent = EconomicEvent.builder()
                .eventType(EventType.FUTURES_EXPIRATION)
                .eventDate(expDate)
                .country("TW")
                .impactLevel(ImpactLevel.MEDIUM)
                .eventName("Futures Expiration")
                .updatedAt(OffsetDateTime.now())
                .build();

            when(economicEventRepository.findNextFuturesExpiration("TW", today))
                .thenReturn(Optional.of(expEvent));

            Optional<LocalDate> result = service.getNextFuturesExpiration();

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(expDate);
        }

        @Test
        @DisplayName("Should check if futures expiration day")
        void shouldCheckIfFuturesExpirationDay() {
            LocalDate today = LocalDate.now();

            when(economicEventRepository.findFuturesExpirations(today, today))
                .thenReturn(List.of(createFuturesEvent(today)));

            assertThat(service.isFuturesExpiration(today)).isTrue();
        }

        @Test
        @DisplayName("Should calculate futures expiration for month")
        void shouldCalculateFuturesExpirationForMonth() {
            YearMonth month = YearMonth.of(2026, 3);

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate expDate = service.calculateFuturesExpiration(month);

            assertThat(expDate.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(expDate.getMonthValue()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Event Risk Assessment Tests")
    class EventRiskAssessmentTests {

        @Test
        @DisplayName("Should return zero risk for normal day")
        void shouldReturnZeroRiskForNormalDay() {
            LocalDate date = LocalDate.now();

            when(economicEventRepository.findByDateRange(date, date)).thenReturn(List.of());

            double risk = service.getEventRiskLevel(date);

            assertThat(risk).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return high risk for high impact event")
        void shouldReturnHighRiskForHighImpactEvent() {
            LocalDate date = LocalDate.now();

            when(economicEventRepository.findByDateRange(date, date))
                .thenReturn(List.of(createEvent("High Impact", date, ImpactLevel.HIGH)));

            double risk = service.getEventRiskLevel(date);

            assertThat(risk).isEqualTo(0.9);
        }

        @Test
        @DisplayName("Should return full risk for holiday")
        void shouldReturnFullRiskForHoliday() {
            LocalDate date = LocalDate.now();

            when(economicEventRepository.findByDateRange(date, date))
                .thenReturn(List.of(createHoliday(date)));

            double risk = service.getEventRiskLevel(date);

            assertThat(risk).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should calculate position size multiplier")
        void shouldCalculatePositionSizeMultiplier() {
            LocalDate date = LocalDate.now();

            when(economicEventRepository.findByDateRange(date, date))
                .thenReturn(List.of(createEvent("Medium Impact", date, ImpactLevel.MEDIUM)));

            double multiplier = service.getPositionSizeMultiplier(date);

            assertThat(multiplier).isEqualTo(0.5); // 1.0 - 0.5 risk
        }

        @Test
        @DisplayName("Should check if should reduce exposure")
        void shouldCheckIfShouldReduceExposure() {
            LocalDate today = LocalDate.now();

            when(economicEventRepository.countUpcomingHighImpactEvents(eq(today), any(LocalDate.class)))
                .thenReturn(2L);

            assertThat(service.shouldReduceExposure(7)).isTrue();
        }

        @Test
        @DisplayName("Should calculate recommended exposure")
        void shouldCalculateRecommendedExposure() {
            LocalDate today = LocalDate.now();
            List<EconomicEvent> events = List.of(
                createEvent("High Impact", today.plusDays(3), ImpactLevel.HIGH)
            );

            when(economicEventRepository.findUpcomingHighImpactEvents(eq(today), any(LocalDate.class)))
                .thenReturn(events);

            double exposure = service.getRecommendedExposure(7);

            // Should be reduced when event is close
            assertThat(exposure).isBetween(0.3, 1.0);
        }
    }

    @Nested
    @DisplayName("Seasonal Analysis Tests")
    class SeasonalAnalysisTests {

        @Test
        @DisplayName("Should return historically strong months")
        void shouldReturnHistoricallyStrongMonths() {
            Set<Integer> strongMonths = service.getHistoricallyStrongMonths();

            assertThat(strongMonths).contains(1, 4, 11, 12); // Jan, Apr, Nov, Dec
        }

        @Test
        @DisplayName("Should return historically weak months")
        void shouldReturnHistoricallyWeakMonths() {
            Set<Integer> weakMonths = service.getHistoricallyWeakMonths();

            assertThat(weakMonths).contains(5, 6, 9); // May, Jun, Sep
        }

        @Test
        @DisplayName("Should calculate seasonal strength")
        void shouldCalculateSeasonalStrength() {
            double strength = service.getSeasonalStrength();

            // Should return a value between -1 and 1
            assertThat(strength).isBetween(-1.0, 1.0);
        }
    }

    @Nested
    @DisplayName("Data Management Tests")
    class DataManagementTests {

        @Test
        @DisplayName("Should save event with updated timestamp")
        void shouldSaveEventWithUpdatedTimestamp() {
            EconomicEvent event = EconomicEvent.builder()
                .eventType(EventType.ECONOMIC_RELEASE)
                .eventName("Test Event")
                .eventDate(LocalDate.now())
                .country("TW")
                .impactLevel(ImpactLevel.LOW)
                .build();

            when(economicEventRepository.save(any(EconomicEvent.class))).thenReturn(event);

            EconomicEvent saved = service.saveEvent(event);

            assertThat(saved).isNotNull();
        }

        @Test
        @DisplayName("Should generate futures expirations for year")
        void shouldGenerateFuturesExpirationsForYear() {
            when(economicEventRepository.existsByEventCode(any())).thenReturn(false);
            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);
            when(economicEventRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<EconomicEvent> events = service.generateFuturesExpirations(2026);

            assertThat(events).hasSize(12); // 12 months
        }
    }

    // Helper methods
    private EconomicEvent createEvent(String name, LocalDate date, ImpactLevel impact) {
        return EconomicEvent.builder()
            .eventType(EventType.ECONOMIC_RELEASE)
            .eventName(name)
            .eventDate(date)
            .country("TW")
            .impactLevel(impact)
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private EconomicEvent createHoliday(LocalDate date) {
        return EconomicEvent.builder()
            .eventType(EventType.MARKET_HOLIDAY)
            .eventName("Holiday")
            .eventDate(date)
            .country("TW")
            .impactLevel(ImpactLevel.HOLIDAY)
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private EconomicEvent createFuturesEvent(LocalDate date) {
        return EconomicEvent.builder()
            .eventType(EventType.FUTURES_EXPIRATION)
            .eventName("Futures Expiration")
            .eventDate(date)
            .country("TW")
            .impactLevel(ImpactLevel.MEDIUM)
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
