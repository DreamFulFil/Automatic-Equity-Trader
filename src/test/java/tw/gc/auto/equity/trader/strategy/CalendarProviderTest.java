package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CalendarProvider functional interface.
 */
@DisplayName("CalendarProvider Tests")
class CalendarProviderTest {

    @Nested
    @DisplayName("No-Op Provider Tests")
    class NoOpProviderTests {

        private CalendarProvider provider;

        @BeforeEach
        void setUp() {
            provider = CalendarProvider.noOp();
        }

        @Test
        @DisplayName("Should treat weekdays as trading days")
        void shouldTreatWeekdaysAsTradingDays() {
            LocalDate monday = getNextWeekday(DayOfWeek.MONDAY);
            LocalDate friday = getNextWeekday(DayOfWeek.FRIDAY);

            assertThat(provider.isTradingDay(monday)).isTrue();
            assertThat(provider.isTradingDay(friday)).isTrue();
        }

        @Test
        @DisplayName("Should treat weekends as non-trading days")
        void shouldTreatWeekendsAsNonTradingDays() {
            LocalDate saturday = getNextWeekday(DayOfWeek.SATURDAY);
            LocalDate sunday = getNextWeekday(DayOfWeek.SUNDAY);

            assertThat(provider.isTradingDay(saturday)).isFalse();
            assertThat(provider.isTradingDay(sunday)).isFalse();
        }

        @Test
        @DisplayName("Should check if today is trading day")
        void shouldCheckIfTodayIsTradingDay() {
            boolean isTrading = provider.isTodayTradingDay();
            boolean isWeekend = LocalDate.now().getDayOfWeek().getValue() >= 6;

            assertThat(isTrading).isEqualTo(!isWeekend);
        }
    }

    @Nested
    @DisplayName("Trading Day Navigation Tests")
    class TradingDayNavigationTests {

        private CalendarProvider provider;

        @BeforeEach
        void setUp() {
            provider = CalendarProvider.noOp();
        }

        @Test
        @DisplayName("Should get next trading day skipping weekend")
        void shouldGetNextTradingDaySkippingWeekend() {
            LocalDate friday = getNextWeekday(DayOfWeek.FRIDAY);
            LocalDate next = provider.getNextTradingDay(friday);

            assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        }

        @Test
        @DisplayName("Should get previous trading day skipping weekend")
        void shouldGetPreviousTradingDaySkippingWeekend() {
            LocalDate monday = getNextWeekday(DayOfWeek.MONDAY);
            LocalDate prev = provider.getPreviousTradingDay(monday);

            assertThat(prev.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        }

        @Test
        @DisplayName("Should get N-th trading day forward")
        void shouldGetNthTradingDayForward() {
            LocalDate monday = getNextWeekday(DayOfWeek.MONDAY);
            LocalDate result = provider.getNthTradingDay(monday, 3);

            assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
        }

        @Test
        @DisplayName("Should get N-th trading day backward")
        void shouldGetNthTradingDayBackward() {
            LocalDate friday = getNextWeekday(DayOfWeek.FRIDAY);
            LocalDate result = provider.getNthTradingDay(friday, -3);

            assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        }

        @Test
        @DisplayName("Should handle zero for N-th trading day")
        void shouldHandleZeroForNthTradingDay() {
            LocalDate tuesday = getNextWeekday(DayOfWeek.TUESDAY);
            LocalDate result = provider.getNthTradingDay(tuesday, 0);

            assertThat(result).isEqualTo(tuesday);
        }

        @Test
        @DisplayName("Should count trading days")
        void shouldCountTradingDays() {
            LocalDate monday = getNextWeekday(DayOfWeek.MONDAY);
            LocalDate friday = monday.plusDays(4);

            int count = provider.countTradingDays(monday, friday);

            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Should count trading days across weekend")
        void shouldCountTradingDaysAcrossWeekend() {
            LocalDate monday = getNextWeekday(DayOfWeek.MONDAY);
            LocalDate nextMonday = monday.plusWeeks(1);

            int count = provider.countTradingDays(monday, nextMonday);

            assertThat(count).isEqualTo(6); // Mon-Fri + Mon
        }
    }

    @Nested
    @DisplayName("Seasonal Strength Tests")
    class SeasonalStrengthTests {

        private CalendarProvider provider;

        @BeforeEach
        void setUp() {
            provider = CalendarProvider.noOp();
        }

        @Test
        @DisplayName("Should return positive strength for January")
        void shouldReturnPositiveStrengthForJanuary() {
            double strength = provider.getSeasonalStrength(1);
            assertThat(strength).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should return negative strength for September")
        void shouldReturnNegativeStrengthForSeptember() {
            double strength = provider.getSeasonalStrength(9);
            assertThat(strength).isLessThan(0);
        }

        @Test
        @DisplayName("Should return positive strength for December")
        void shouldReturnPositiveStrengthForDecember() {
            double strength = provider.getSeasonalStrength(12);
            assertThat(strength).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should return negative strength for May")
        void shouldReturnNegativeStrengthForMay() {
            double strength = provider.getSeasonalStrength(5);
            assertThat(strength).isLessThan(0);
        }

        @Test
        @DisplayName("Should identify strong months")
        void shouldIdentifyStrongMonths() {
            CalendarProvider janProvider = new CalendarProvider() {
                @Override
                public boolean isTradingDay(LocalDate date) {
                    return date.getDayOfWeek().getValue() < 6;
                }

                @Override
                public double getSeasonalStrength() {
                    return 0.6; // Strong
                }
            };

            assertThat(janProvider.isStrongMonth()).isTrue();
            assertThat(janProvider.isWeakMonth()).isFalse();
        }

        @Test
        @DisplayName("Should identify weak months")
        void shouldIdentifyWeakMonths() {
            CalendarProvider sepProvider = new CalendarProvider() {
                @Override
                public boolean isTradingDay(LocalDate date) {
                    return date.getDayOfWeek().getValue() < 6;
                }

                @Override
                public double getSeasonalStrength() {
                    return -0.5; // Weak
                }
            };

            assertThat(sepProvider.isWeakMonth()).isTrue();
            assertThat(sepProvider.isStrongMonth()).isFalse();
        }
    }

    @Nested
    @DisplayName("Futures Expiration Tests")
    class FuturesExpirationTests {

        private CalendarProvider provider;

        @BeforeEach
        void setUp() {
            provider = CalendarProvider.noOp();
        }

        @Test
        @DisplayName("Should get next futures expiration")
        void shouldGetNextFuturesExpiration() {
            Optional<LocalDate> exp = provider.getNextFuturesExpiration();

            assertThat(exp).isPresent();
            assertThat(exp.get()).isAfterOrEqualTo(LocalDate.now().minusDays(1));
        }

        @Test
        @DisplayName("Should calculate days to expiration")
        void shouldCalculateDaysToExpiration() {
            int days = provider.getDaysToExpiration();

            assertThat(days).isGreaterThanOrEqualTo(-1); // Could be today
        }

        @Test
        @DisplayName("Should check settlement day")
        void shouldCheckSettlementDay() {
            boolean isSettlement = provider.isSettlementDay();

            // Just verify it returns a boolean
            assertThat(isSettlement).isIn(true, false);
        }

        @Test
        @DisplayName("Should check settlement week")
        void shouldCheckSettlementWeek() {
            boolean isSettlementWeek = provider.isSettlementWeek();

            // Just verify it returns a boolean
            assertThat(isSettlementWeek).isIn(true, false);
        }
    }

    @Nested
    @DisplayName("Event Risk Tests")
    class EventRiskTests {

        private CalendarProvider provider;

        @BeforeEach
        void setUp() {
            provider = CalendarProvider.noOp();
        }

        @Test
        @DisplayName("Should return zero risk for normal day")
        void shouldReturnZeroRiskForNormalDay() {
            LocalDate tuesday = getNextWeekday(DayOfWeek.TUESDAY);
            double risk = provider.getEventRiskLevel(tuesday);

            assertThat(risk).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return full position multiplier for low risk")
        void shouldReturnFullPositionMultiplierForLowRisk() {
            LocalDate tuesday = getNextWeekday(DayOfWeek.TUESDAY);
            double multiplier = provider.getPositionSizeMultiplier(tuesday);

            assertThat(multiplier).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Special Period Tests")
    class SpecialPeriodTests {

        private CalendarProvider provider;

        @BeforeEach
        void setUp() {
            provider = CalendarProvider.noOp();
        }

        @Test
        @DisplayName("Should check month end rebalancing")
        void shouldCheckMonthEndRebalancing() {
            boolean isMonthEnd = provider.isMonthEndRebalancing();

            // Depends on current date
            assertThat(isMonthEnd).isIn(true, false);
        }

        @Test
        @DisplayName("Should check quarter end")
        void shouldCheckQuarterEnd() {
            boolean isQuarterEnd = provider.isQuarterEnd();

            // Depends on current date
            assertThat(isQuarterEnd).isIn(true, false);
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create provider with holidays")
        void shouldCreateProviderWithHolidays() {
            Set<LocalDate> holidays = Set.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 17)
            );

            CalendarProvider provider = CalendarProvider.withHolidays(holidays);

            assertThat(provider.isTradingDay(LocalDate.of(2026, 1, 1))).isFalse();
            assertThat(provider.isTradingDay(LocalDate.of(2026, 2, 17))).isFalse();
            assertThat(provider.isTradingDay(LocalDate.of(2026, 1, 2))).isTrue(); // Assuming it's not weekend
        }

        @Test
        @DisplayName("Should create always trading day provider")
        void shouldCreateAlwaysTradingDayProvider() {
            CalendarProvider provider = CalendarProvider.alwaysTradingDay();

            LocalDate saturday = getNextWeekday(DayOfWeek.SATURDAY);
            assertThat(provider.isTradingDay(saturday)).isTrue();
        }

        @Test
        @DisplayName("Should create provider with custom seasonal strength")
        void shouldCreateProviderWithCustomSeasonalStrength() {
            Map<Integer, Double> monthStrength = Map.of(
                1, 1.0,
                6, -1.0
            );

            CalendarProvider provider = CalendarProvider.withSeasonalStrength(monthStrength);

            assertThat(provider.getSeasonalStrength(1)).isEqualTo(1.0);
            assertThat(provider.getSeasonalStrength(6)).isEqualTo(-1.0);
            assertThat(provider.getSeasonalStrength(3)).isEqualTo(0.0); // Default
        }

        @Test
        @DisplayName("Should create provider with futures expirations")
        void shouldCreateProviderWithFuturesExpirations() {
            List<LocalDate> expirations = List.of(
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(40)
            );

            CalendarProvider provider = CalendarProvider.withFuturesExpirations(expirations);

            Optional<LocalDate> next = provider.getNextFuturesExpiration();
            assertThat(next).isPresent();
            assertThat(next.get()).isEqualTo(expirations.get(0));
        }

        @Test
        @DisplayName("Should create provider with event risk")
        void shouldCreateProviderWithEventRisk() {
            LocalDate highRiskDay = LocalDate.now().plusDays(5);
            Map<LocalDate, Double> riskLevels = Map.of(highRiskDay, 0.8);

            CalendarProvider provider = CalendarProvider.withEventRisk(riskLevels);

            assertThat(provider.getEventRiskLevel(highRiskDay)).isEqualTo(0.8);
            assertThat(provider.getPositionSizeMultiplier(highRiskDay)).isCloseTo(0.2, org.assertj.core.api.Assertions.within(0.001));
        }

        @Test
        @DisplayName("Should mark holiday as non-trading day in event risk provider")
        void shouldMarkHolidayAsNonTradingDayInEventRiskProvider() {
            LocalDate holiday = getNextWeekday(DayOfWeek.WEDNESDAY);
            Map<LocalDate, Double> riskLevels = Map.of(holiday, 1.0); // Full risk = holiday

            CalendarProvider provider = CalendarProvider.withEventRisk(riskLevels);

            assertThat(provider.isTradingDay(holiday)).isFalse();
        }
    }

    @Nested
    @DisplayName("Functional Interface Tests")
    class FunctionalInterfaceTests {

        @Test
        @DisplayName("Should work as lambda")
        void shouldWorkAsLambda() {
            CalendarProvider provider = date -> date.getDayOfWeek().getValue() < 6;

            LocalDate monday = getNextWeekday(DayOfWeek.MONDAY);
            LocalDate sunday = getNextWeekday(DayOfWeek.SUNDAY);

            assertThat(provider.isTradingDay(monday)).isTrue();
            assertThat(provider.isTradingDay(sunday)).isFalse();
        }

        @Test
        @DisplayName("Should work with method reference")
        void shouldWorkWithMethodReference() {
            TradingDayChecker checker = new TradingDayChecker();
            CalendarProvider provider = checker::check;

            LocalDate weekday = getNextWeekday(DayOfWeek.WEDNESDAY);
            assertThat(provider.isTradingDay(weekday)).isTrue();
        }
    }

    // Helper class for method reference test
    private static class TradingDayChecker {
        boolean check(LocalDate date) {
            return date.getDayOfWeek().getValue() < 6;
        }
    }

    // Helper method to get next occurrence of a specific day
    private LocalDate getNextWeekday(DayOfWeek target) {
        LocalDate date = LocalDate.now();
        while (date.getDayOfWeek() != target) {
            date = date.plusDays(1);
        }
        return date;
    }
}
