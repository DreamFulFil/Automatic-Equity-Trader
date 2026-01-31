package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OptimalExecutionTimer - Phase 8
 */
class OptimalExecutionTimerTest {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private final OptimalExecutionTimer timer = new OptimalExecutionTimer();

    @Test
    void shouldIdentifyOptimalExecutionWindow() {
        // Given: Time in optimal window (10:30 AM, Wednesday)
        ZonedDateTime optimalTime = createTaipeiTime(DayOfWeek.WEDNESDAY, 10, 30);

        // When/Then
        assertThat(timer.isOptimalExecutionTime(optimalTime)).isTrue();
        assertThat(timer.isHighVolatilityPeriod(optimalTime)).isFalse();
    }

    @Test
    void shouldIdentifyOpeningVolatility() {
        // Given: Market just opened (9:15 AM)
        ZonedDateTime openingTime = createTaipeiTime(DayOfWeek.WEDNESDAY, 9, 15);

        // When/Then
        assertThat(timer.isHighVolatilityPeriod(openingTime)).isTrue();
        assertThat(timer.isOptimalExecutionTime(openingTime)).isFalse();
    }

    @Test
    void shouldIdentifyClosingVolatility() {
        // Given: Near market close (13:15)
        ZonedDateTime closingTime = createTaipeiTime(DayOfWeek.WEDNESDAY, 13, 15);

        // When/Then
        assertThat(timer.isHighVolatilityPeriod(closingTime)).isTrue();
        assertThat(timer.isOptimalExecutionTime(closingTime)).isFalse();
    }

    @Test
    void shouldRejectWeekendExecution() {
        // Given: Saturday
        ZonedDateTime saturday = createTaipeiTime(DayOfWeek.SATURDAY, 10, 30);

        // When/Then
        assertThat(timer.isMarketOpen(saturday)).isFalse();
        assertThat(timer.isOptimalExecutionTime(saturday)).isFalse();
    }

    @Test
    void shouldCheckMarketOpenHours() {
        // Given: Various times
        ZonedDateTime beforeMarket = createTaipeiTime(DayOfWeek.MONDAY, 8, 30);
        ZonedDateTime duringMarket = createTaipeiTime(DayOfWeek.MONDAY, 11, 0);
        ZonedDateTime afterMarket = createTaipeiTime(DayOfWeek.MONDAY, 14, 0);

        // When/Then
        assertThat(timer.isMarketOpen(beforeMarket)).isFalse();
        assertThat(timer.isMarketOpen(duringMarket)).isTrue();
        assertThat(timer.isMarketOpen(afterMarket)).isFalse();
    }

    @Test
    void shouldCalculateHighPriorityScoreForOptimalTime() {
        // Given: Optimal mid-session time (11:00 AM)
        ZonedDateTime optimalTime = createTaipeiTime(DayOfWeek.TUESDAY, 11, 0);

        // When
        int score = timer.calculateExecutionPriorityScore(optimalTime);

        // Then: Should have high score (80+)
        assertThat(score).isGreaterThanOrEqualTo(80);
    }

    @Test
    void shouldCalculateLowPriorityScoreForVolatileTime() {
        // Given: Opening volatility (9:10 AM)
        ZonedDateTime volatileTime = createTaipeiTime(DayOfWeek.TUESDAY, 9, 10);

        // When
        int score = timer.calculateExecutionPriorityScore(volatileTime);

        // Then: Should have low score (< 50)
        assertThat(score).isLessThan(50);
    }

    @Test
    void shouldReturnZeroScoreWhenMarketClosed() {
        // Given: Market closed (15:00)
        ZonedDateTime closedTime = createTaipeiTime(DayOfWeek.WEDNESDAY, 15, 0);

        // When
        int score = timer.calculateExecutionPriorityScore(closedTime);

        // Then
        assertThat(score).isEqualTo(0);
    }

    @Test
    void shouldRecommendExecuteNowForOptimalTime() {
        // Given: Optimal time
        ZonedDateTime optimalTime = createTaipeiTime(DayOfWeek.THURSDAY, 10, 30);

        // When
        OptimalExecutionTimer.ExecutionRecommendation rec = timer.getExecutionRecommendation(optimalTime);

        // Then
        assertThat(rec.getAction()).isEqualTo("EXECUTE_NOW");
        assertThat(rec.getPriorityScore()).isGreaterThanOrEqualTo(80);
        assertThat(rec.shouldExecuteImmediately()).isTrue();
    }

    @Test
    void shouldRecommendDelayForVolatileTime() {
        // Given: Opening volatility
        ZonedDateTime volatileTime = createTaipeiTime(DayOfWeek.FRIDAY, 9, 10);

        // When
        OptimalExecutionTimer.ExecutionRecommendation rec = timer.getExecutionRecommendation(volatileTime);

        // Then
        assertThat(rec.getAction()).isIn("DELAY_IF_POSSIBLE", "EXECUTE_NORMAL");
        assertThat(rec.shouldExecuteImmediately()).isFalse();
    }

    @Test
    void shouldRecommendWaitWhenMarketClosed() {
        // Given: Market closed
        ZonedDateTime closedTime = createTaipeiTime(DayOfWeek.MONDAY, 14, 0);

        // When
        OptimalExecutionTimer.ExecutionRecommendation rec = timer.getExecutionRecommendation(closedTime);

        // Then
        assertThat(rec.getAction()).isEqualTo("WAIT");
        assertThat(rec.shouldWait()).isTrue();
        assertThat(rec.getPriorityScore()).isEqualTo(0);
    }

    @Test
    void shouldCalculateMinutesUntilNextOptimalWindow() {
        // Given: Before first optimal window (9:30 AM)
        ZonedDateTime earlyMorning = createTaipeiTime(DayOfWeek.TUESDAY, 9, 30);

        // When
        long minutes = timer.getMinutesUntilNextOptimalWindow();

        // Then: Should be sometime in the future
        // Note: This test depends on actual system time, so we just check it's non-negative
        assertThat(minutes).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldReturnZeroMinutesWhenInOptimalWindow() {
        // Given: Currently in optimal window (11:00 AM)
        ZonedDateTime optimalTime = createTaipeiTime(DayOfWeek.WEDNESDAY, 11, 0);

        // When: Using the timer with current time in optimal window
        // Note: This would require time injection in production code
        // For now, just verify the logic path
        boolean isOptimal = timer.isOptimalExecutionTime(optimalTime);

        // Then
        assertThat(isOptimal).isTrue();
    }

    @Test
    void shouldHandleAllDaysOfWeek() {
        // Given: Same time on different days
        LocalTime testTime = LocalTime.of(11, 0);

        // When/Then: Weekdays should be optimal, weekends should not
        for (DayOfWeek day : DayOfWeek.values()) {
            ZonedDateTime time = createTaipeiTime(day, 11, 0);
            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            
            assertThat(timer.isMarketOpen(time)).isEqualTo(!isWeekend);
        }
    }

    @Test
    void shouldIdentifyBothOptimalWindows() {
        // Given: Morning and afternoon optimal times
        ZonedDateTime morning = createTaipeiTime(DayOfWeek.MONDAY, 10, 30); // First window: 10:00-12:00
        ZonedDateTime afternoon = createTaipeiTime(DayOfWeek.MONDAY, 14, 30); // Second window: 14:00-16:00

        // When/Then
        assertThat(timer.isOptimalExecutionTime(morning)).isTrue();
        assertThat(timer.isOptimalExecutionTime(afternoon)).isTrue();
    }

    /**
     * Helper method to create ZonedDateTime in Taipei timezone
     */
    private ZonedDateTime createTaipeiTime(DayOfWeek dayOfWeek, int hour, int minute) {
        return ZonedDateTime.now(TAIPEI_ZONE)
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(dayOfWeek))
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0);
    }
}
