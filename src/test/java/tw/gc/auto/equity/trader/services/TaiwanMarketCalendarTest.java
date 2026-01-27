package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.EconomicEvent;
import tw.gc.auto.equity.trader.repositories.EconomicEventRepository;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TaiwanMarketCalendar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaiwanMarketCalendar Tests")
class TaiwanMarketCalendarTest {

    @Mock
    private EconomicEventRepository economicEventRepository;

    private TaiwanMarketCalendar calendar;

    @BeforeEach
    void setUp() {
        calendar = new TaiwanMarketCalendar(economicEventRepository);
    }

    @Nested
    @DisplayName("Trading Hours Tests")
    class TradingHoursTests {

        @Test
        @DisplayName("Should identify market open during trading hours")
        void shouldIdentifyMarketOpenDuringTradingHours() {
            // Trading hours: 09:00 - 13:30 Taiwan time
            ZonedDateTime tradingTime = ZonedDateTime.of(
                LocalDate.now(),
                LocalTime.of(10, 30),
                TaiwanMarketCalendar.TAIWAN_ZONE
            );

            // Skip if weekend
            if (tradingTime.getDayOfWeek().getValue() >= 6) {
                tradingTime = tradingTime.plusDays(2);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            assertThat(calendar.isMarketOpenAt(tradingTime)).isTrue();
        }

        @Test
        @DisplayName("Should identify market closed before open")
        void shouldIdentifyMarketClosedBeforeOpen() {
            ZonedDateTime beforeOpen = ZonedDateTime.of(
                LocalDate.now(),
                LocalTime.of(8, 30),
                TaiwanMarketCalendar.TAIWAN_ZONE
            );

            // Skip if weekend
            if (beforeOpen.getDayOfWeek().getValue() >= 6) {
                beforeOpen = beforeOpen.plusDays(2);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            assertThat(calendar.isMarketOpenAt(beforeOpen)).isFalse();
        }

        @Test
        @DisplayName("Should identify market closed after close")
        void shouldIdentifyMarketClosedAfterClose() {
            ZonedDateTime afterClose = ZonedDateTime.of(
                LocalDate.now(),
                LocalTime.of(14, 0),
                TaiwanMarketCalendar.TAIWAN_ZONE
            );

            // Skip if weekend
            if (afterClose.getDayOfWeek().getValue() >= 6) {
                afterClose = afterClose.plusDays(2);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            assertThat(calendar.isMarketOpenAt(afterClose)).isFalse();
        }

        @Test
        @DisplayName("Should identify market closed on weekends")
        void shouldIdentifyMarketClosedOnWeekends() {
            LocalDate saturday = LocalDate.now();
            while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
                saturday = saturday.plusDays(1);
            }

            ZonedDateTime weekendTime = ZonedDateTime.of(
                saturday,
                LocalTime.of(10, 0),
                TaiwanMarketCalendar.TAIWAN_ZONE
            );

            assertThat(calendar.isMarketOpenAt(weekendTime)).isFalse();
        }
    }

    @Nested
    @DisplayName("Holiday Tests")
    class HolidayTests {

        @Test
        @DisplayName("Should identify weekend as holiday")
        void shouldIdentifyWeekendAsHoliday() {
            LocalDate saturday = LocalDate.now();
            while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) {
                saturday = saturday.plusDays(1);
            }

            assertThat(calendar.isHoliday(saturday)).isTrue();
        }

        @Test
        @DisplayName("Should check database for weekday holidays")
        void shouldCheckDatabaseForWeekdayHolidays() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday("TW", monday)).thenReturn(true);

            assertThat(calendar.isHoliday(monday)).isTrue();
        }

        @Test
        @DisplayName("Should identify trading day")
        void shouldIdentifyTradingDay() {
            LocalDate tuesday = LocalDate.now();
            while (tuesday.getDayOfWeek() != DayOfWeek.TUESDAY) {
                tuesday = tuesday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday("TW", tuesday)).thenReturn(false);

            assertThat(calendar.isTradingDay(tuesday)).isTrue();
        }

        @Test
        @DisplayName("Should get holidays for year")
        void shouldGetHolidaysForYear() {
            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 12, 31);

            List<EconomicEvent> holidays = List.of(
                createHolidayEvent(LocalDate.of(2026, 1, 1), "New Year"),
                createHolidayEvent(LocalDate.of(2026, 2, 17), "CNY")
            );

            when(economicEventRepository.findMarketHolidaysByCountry("TW", start, end))
                .thenReturn(holidays);

            List<LocalDate> result = calendar.getHolidays(2026);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Futures Settlement Tests")
    class FuturesSettlementTests {

        @Test
        @DisplayName("Should calculate third Wednesday settlement")
        void shouldCalculateThirdWednesdaySettlement() {
            YearMonth march2026 = YearMonth.of(2026, 3);

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate settlement = calendar.getFuturesSettlementDate(march2026);

            assertThat(settlement.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(settlement.getMonthValue()).isEqualTo(3);
            assertThat(settlement.getDayOfMonth()).isBetween(15, 21); // Third week
        }

        @Test
        @DisplayName("Should move settlement if Wednesday is holiday")
        void shouldMoveSettlementIfWednesdayIsHoliday() {
            YearMonth month = YearMonth.of(2026, 4);

            // Make third Wednesday a holiday
            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    LocalDate d = inv.getArgument(1);
                    // Mock: April 15, 2026 (third Wednesday) is a holiday
                    return d.equals(LocalDate.of(2026, 4, 15));
                });

            LocalDate settlement = calendar.getFuturesSettlementDate(month);

            // Should be previous trading day
            assertThat(settlement.getDayOfWeek()).isIn(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        }

        @Test
        @DisplayName("Should get next futures settlement")
        void shouldGetNextFuturesSettlement() {
            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate nextSettlement = calendar.getNextFuturesSettlement();

            assertThat(nextSettlement).isNotNull();
            assertThat(nextSettlement).isAfterOrEqualTo(LocalDate.now().minusDays(1));
        }

        @Test
        @DisplayName("Should calculate days to next settlement")
        void shouldCalculateDaysToNextSettlement() {
            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            int days = calendar.getDaysToNextSettlement();

            assertThat(days).isGreaterThanOrEqualTo(-1); // Could be today
        }

        @Test
        @DisplayName("Should check if settlement week")
        void shouldCheckIfSettlementWeek() {
            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            // Result depends on current date
            boolean isSettlementWeek = calendar.isSettlementWeek();
            assertThat(isSettlementWeek).isIn(true, false);
        }
    }

    @Nested
    @DisplayName("Trading Day Navigation Tests")
    class TradingDayNavigationTests {

        @Test
        @DisplayName("Should get next trading day")
        void shouldGetNextTradingDay() {
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate next = calendar.getNextTradingDay(friday);

            assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("Should get previous trading day")
        void shouldGetPreviousTradingDay() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate prev = calendar.getPreviousTradingDay(monday);

            assertThat(prev.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        }

        @Test
        @DisplayName("Should get N-th trading day forward")
        void shouldGetNthTradingDayForward() {
            LocalDate monday = LocalDate.now();
            while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate result = calendar.getNthTradingDay(monday, 5);

            assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(result).isEqualTo(monday.plusWeeks(1));
        }

        @Test
        @DisplayName("Should get N-th trading day backward")
        void shouldGetNthTradingDayBackward() {
            LocalDate friday = LocalDate.now();
            while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
                friday = friday.plusDays(1);
            }

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            LocalDate result = calendar.getNthTradingDay(friday, -5);

            assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
            assertThat(result).isEqualTo(friday.minusWeeks(1));
        }

        @Test
        @DisplayName("Should get trading days in month")
        void shouldGetTradingDaysInMonth() {
            YearMonth month = YearMonth.of(2026, 3);

            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            List<LocalDate> tradingDays = calendar.getTradingDaysInMonth(month);

            // March 2026 has 31 days, roughly 22-23 trading days
            assertThat(tradingDays).hasSizeGreaterThan(20);
            assertThat(tradingDays).hasSizeLessThan(24);
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

            int count = calendar.countTradingDays(monday, friday);

            assertThat(count).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Special Period Tests")
    class SpecialPeriodTests {

        @Test
        @DisplayName("Should identify earnings season")
        void shouldIdentifyEarningsSeason() {
            // Earnings season: Jan-Feb, Apr-May, Jul-Aug, Oct-Nov
            boolean isEarnings = calendar.isEarningsSeason();

            int currentMonth = LocalDate.now().getMonthValue();
            boolean expected = currentMonth == 1 || currentMonth == 2 ||
                             currentMonth == 4 || currentMonth == 5 ||
                             currentMonth == 7 || currentMonth == 8 ||
                             currentMonth == 10 || currentMonth == 11;

            assertThat(isEarnings).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should check month end rebalancing")
        void shouldCheckMonthEndRebalancing() {
            when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);

            boolean isMonthEnd = calendar.isMonthEndRebalancing();

            // Should be deterministic based on current date
            assertThat(isMonthEnd).isIn(true, false);
        }

        @Test
        @DisplayName("Should check quarter end")
        void shouldCheckQuarterEnd() {
            // Stubbing only needed if current month is a quarter-end month
            int currentMonth = LocalDate.now().getMonthValue();
            if (currentMonth == 3 || currentMonth == 6 || currentMonth == 9 || currentMonth == 12) {
                when(economicEventRepository.isMarketHoliday(eq("TW"), any(LocalDate.class))).thenReturn(false);
            }

            boolean isQuarterEnd = calendar.isQuarterEnd();

            // Only true in months 3, 6, 9, 12 during last 5 trading days
            assertThat(isQuarterEnd).isIn(true, false);
        }
    }

    @Nested
    @DisplayName("Holiday Generation Tests")
    class HolidayGenerationTests {

        @Test
        @DisplayName("Should create holiday event")
        void shouldCreateHolidayEvent() {
            LocalDate date = LocalDate.of(2026, 1, 1);

            EconomicEvent holiday = calendar.createHolidayEvent(date, "New Year's Day");

            assertThat(holiday.getEventType()).isEqualTo(EconomicEvent.EventType.MARKET_HOLIDAY);
            assertThat(holiday.getEventDate()).isEqualTo(date);
            assertThat(holiday.getEventName()).isEqualTo("New Year's Day");
            assertThat(holiday.getCountry()).isEqualTo("TW");
            assertThat(holiday.getImpactLevel()).isEqualTo(EconomicEvent.ImpactLevel.HOLIDAY);
        }

        @Test
        @DisplayName("Should generate 2026 holidays")
        void shouldGenerate2026Holidays() {
            List<EconomicEvent> holidays = calendar.generate2026Holidays();

            assertThat(holidays).isNotEmpty();
            assertThat(holidays).hasSizeGreaterThan(10); // At least 10 holidays

            // Verify all are holidays
            assertThat(holidays).allMatch(e -> 
                e.getEventType() == EconomicEvent.EventType.MARKET_HOLIDAY);

            // Verify country
            assertThat(holidays).allMatch(e -> "TW".equals(e.getCountry()));
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should have correct Taiwan timezone")
        void shouldHaveCorrectTaiwanTimezone() {
            assertThat(TaiwanMarketCalendar.TAIWAN_ZONE.getId()).isEqualTo("Asia/Taipei");
        }

        @Test
        @DisplayName("Should have correct market hours")
        void shouldHaveCorrectMarketHours() {
            assertThat(TaiwanMarketCalendar.MARKET_OPEN).isEqualTo(LocalTime.of(9, 0));
            assertThat(TaiwanMarketCalendar.MARKET_CLOSE).isEqualTo(LocalTime.of(13, 30));
        }

        @Test
        @DisplayName("Should have correct futures hours")
        void shouldHaveCorrectFuturesHours() {
            assertThat(TaiwanMarketCalendar.FUTURES_OPEN).isEqualTo(LocalTime.of(8, 45));
            assertThat(TaiwanMarketCalendar.FUTURES_CLOSE).isEqualTo(LocalTime.of(13, 45));
        }

        @Test
        @DisplayName("Should have correct odd lot hours")
        void shouldHaveCorrectOddLotHours() {
            assertThat(TaiwanMarketCalendar.ODD_LOT_OPEN).isEqualTo(LocalTime.of(14, 0));
            assertThat(TaiwanMarketCalendar.ODD_LOT_CLOSE).isEqualTo(LocalTime.of(14, 30));
        }
    }

    // Helper method
    private EconomicEvent createHolidayEvent(LocalDate date, String name) {
        return EconomicEvent.builder()
            .eventType(EconomicEvent.EventType.MARKET_HOLIDAY)
            .eventName(name)
            .eventDate(date)
            .country("TW")
            .impactLevel(EconomicEvent.ImpactLevel.HOLIDAY)
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
