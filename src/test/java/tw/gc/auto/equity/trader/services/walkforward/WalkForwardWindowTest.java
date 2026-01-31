package tw.gc.auto.equity.trader.services.walkforward;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WalkForwardWindow}.
 */
class WalkForwardWindowTest {
    
    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {
        
        @Test
        @DisplayName("should create valid walk-forward window")
        void shouldCreateValidWindow() {
            LocalDate trainStart = LocalDate.of(2025, 1, 1);
            LocalDate trainEnd = LocalDate.of(2025, 2, 28);
            LocalDate testStart = LocalDate.of(2025, 3, 1);
            LocalDate testEnd = LocalDate.of(2025, 3, 31);
            
            var window = new WalkForwardWindow(0, trainStart, trainEnd, testStart, testEnd);
            
            assertThat(window.windowIndex()).isZero();
            assertThat(window.trainStart()).isEqualTo(trainStart);
            assertThat(window.trainEnd()).isEqualTo(trainEnd);
            assertThat(window.testStart()).isEqualTo(testStart);
            assertThat(window.testEnd()).isEqualTo(testEnd);
        }
        
        @Test
        @DisplayName("should reject negative window index")
        void shouldRejectNegativeWindowIndex() {
            LocalDate trainStart = LocalDate.of(2025, 1, 1);
            LocalDate trainEnd = LocalDate.of(2025, 2, 28);
            LocalDate testStart = LocalDate.of(2025, 3, 1);
            LocalDate testEnd = LocalDate.of(2025, 3, 31);
            
            assertThatThrownBy(() -> new WalkForwardWindow(-1, trainStart, trainEnd, testStart, testEnd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowIndex");
        }
        
        @Test
        @DisplayName("should reject null dates")
        void shouldRejectNullDates() {
            LocalDate validDate = LocalDate.of(2025, 1, 1);
            
            assertThatThrownBy(() -> new WalkForwardWindow(0, null, validDate, validDate, validDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-null");
                
            assertThatThrownBy(() -> new WalkForwardWindow(0, validDate, null, validDate, validDate))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("should reject train start after train end")
        void shouldRejectInvalidTrainPeriod() {
            assertThatThrownBy(() -> new WalkForwardWindow(0,
                    LocalDate.of(2025, 2, 1), // trainStart AFTER trainEnd
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 3, 1),
                    LocalDate.of(2025, 3, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trainStart");
        }
        
        @Test
        @DisplayName("should reject test start after test end")
        void shouldRejectInvalidTestPeriod() {
            assertThatThrownBy(() -> new WalkForwardWindow(0,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 2, 28),
                    LocalDate.of(2025, 4, 1), // testStart AFTER testEnd
                    LocalDate.of(2025, 3, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("testStart");
        }
        
        @Test
        @DisplayName("should reject overlapping train and test periods")
        void shouldRejectOverlappingPeriods() {
            assertThatThrownBy(() -> new WalkForwardWindow(0,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 3, 15), // trainEnd AFTER testStart
                    LocalDate.of(2025, 3, 1),
                    LocalDate.of(2025, 3, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trainEnd");
        }
        
        @Test
        @DisplayName("should allow consecutive train and test periods")
        void shouldAllowConsecutivePeriods() {
            // Train ends on Feb 28, test starts on Mar 1 (consecutive days)
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31));
            
            assertThat(window.trainEnd()).isBefore(window.testStart());
        }
        
        @Test
        @DisplayName("should allow same day train end and test start")
        void shouldAllowSameDayTrainEndTestStart() {
            // Edge case: train ends and test starts on same day (technically valid)
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 2, 28), // same as trainEnd
                LocalDate.of(2025, 3, 31));
            
            assertThat(window).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("Duration Calculations")
    class DurationTests {
        
        @Test
        @DisplayName("should calculate correct train days")
        void shouldCalculateTrainDays() {
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28));
            
            assertThat(window.trainDays()).isEqualTo(31); // Jan has 31 days, inclusive
        }
        
        @Test
        @DisplayName("should calculate correct test days")
        void shouldCalculateTestDays() {
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28));
            
            assertThat(window.testDays()).isEqualTo(28); // Feb 2025 has 28 days, inclusive
        }
        
        @Test
        @DisplayName("should calculate correct train/test ratio")
        void shouldCalculateTrainTestRatio() {
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 3, 1),   // 60 days train
                LocalDate.of(2025, 3, 2),
                LocalDate.of(2025, 3, 21)); // 20 days test
            
            assertThat(window.trainTestRatio()).isCloseTo(3.0, within(0.1));
        }
        
        @Test
        @DisplayName("should handle single day periods")
        void shouldHandleSingleDayPeriods() {
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1),  // 1 day train
                LocalDate.of(2025, 1, 2),
                LocalDate.of(2025, 1, 2)); // 1 day test
            
            assertThat(window.trainDays()).isEqualTo(1);
            assertThat(window.testDays()).isEqualTo(1);
            assertThat(window.trainTestRatio()).isCloseTo(1.0, within(0.001));
        }
    }
    
    @Nested
    @DisplayName("Period Membership Tests")
    class PeriodMembershipTests {
        
        private final WalkForwardWindow window = new WalkForwardWindow(0,
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 1, 31),
            LocalDate.of(2025, 2, 1),
            LocalDate.of(2025, 2, 28));
        
        @Test
        @DisplayName("should correctly identify dates in train period")
        void shouldIdentifyTrainPeriodDates() {
            assertThat(window.isInTrainPeriod(LocalDate.of(2025, 1, 1))).isTrue();   // start
            assertThat(window.isInTrainPeriod(LocalDate.of(2025, 1, 15))).isTrue();  // middle
            assertThat(window.isInTrainPeriod(LocalDate.of(2025, 1, 31))).isTrue();  // end
            
            assertThat(window.isInTrainPeriod(LocalDate.of(2024, 12, 31))).isFalse(); // before
            assertThat(window.isInTrainPeriod(LocalDate.of(2025, 2, 1))).isFalse();   // after (in test)
        }
        
        @Test
        @DisplayName("should correctly identify dates in test period")
        void shouldIdentifyTestPeriodDates() {
            assertThat(window.isInTestPeriod(LocalDate.of(2025, 2, 1))).isTrue();   // start
            assertThat(window.isInTestPeriod(LocalDate.of(2025, 2, 14))).isTrue();  // middle
            assertThat(window.isInTestPeriod(LocalDate.of(2025, 2, 28))).isTrue();  // end
            
            assertThat(window.isInTestPeriod(LocalDate.of(2025, 1, 31))).isFalse(); // before (in train)
            assertThat(window.isInTestPeriod(LocalDate.of(2025, 3, 1))).isFalse();  // after
        }
    }
    
    @Nested
    @DisplayName("Description")
    class DescriptionTests {
        
        @Test
        @DisplayName("should provide human-readable description")
        void shouldProvideHumanReadableDescription() {
            var window = new WalkForwardWindow(0,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 2),
                LocalDate.of(2025, 3, 21));
            
            String desc = window.describe();
            
            assertThat(desc).contains("Window 0");
            assertThat(desc).contains("Train");
            assertThat(desc).contains("Test");
            assertThat(desc).contains("2025-01-01");
            assertThat(desc).contains("Ratio");
        }
    }
}
