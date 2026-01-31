package tw.gc.auto.equity.trader.services.walkforward;

import java.time.LocalDate;

/**
 * Represents a single walk-forward optimization window with training and test periods.
 * 
 * <p>Walk-forward optimization uses a sliding window approach:
 * <pre>
 * ┌──────────────┬──────────────┬──────────────┬──────────────┐
 * │  Training 1  │   Test 1     │  Training 2  │   Test 2     │
 * │  (Optimize)  │   (Verify)   │  (Optimize)  │   (Verify)   │
 * └──────────────┴──────────────┴──────────────┴──────────────┘
 *      60 days      20 days        60 days        20 days
 * </pre>
 * 
 * <p>The training (in-sample) period is used for parameter optimization.
 * The test (out-of-sample) period is used to verify the parameters work on unseen data.
 * 
 * @param windowIndex Zero-based index of this window in the sequence
 * @param trainStart Start date of the training (in-sample) period, inclusive
 * @param trainEnd End date of the training period, inclusive
 * @param testStart Start date of the test (out-of-sample) period, inclusive
 * @param testEnd End date of the test period, inclusive
 * 
 * @since 2026-01-30 Phase 1: Walk-Forward Optimization Framework
 */
public record WalkForwardWindow(
    int windowIndex,
    LocalDate trainStart,
    LocalDate trainEnd,
    LocalDate testStart,
    LocalDate testEnd
) {
    /**
     * Compact constructor with validation.
     */
    public WalkForwardWindow {
        if (windowIndex < 0) {
            throw new IllegalArgumentException("windowIndex must be non-negative, got: %d".formatted(windowIndex));
        }
        if (trainStart == null || trainEnd == null || testStart == null || testEnd == null) {
            throw new IllegalArgumentException("All dates must be non-null");
        }
        if (trainStart.isAfter(trainEnd)) {
            throw new IllegalArgumentException("trainStart (%s) must be before or equal to trainEnd (%s)"
                .formatted(trainStart, trainEnd));
        }
        if (testStart.isAfter(testEnd)) {
            throw new IllegalArgumentException("testStart (%s) must be before or equal to testEnd (%s)"
                .formatted(testStart, testEnd));
        }
        if (trainEnd.isAfter(testStart)) {
            throw new IllegalArgumentException("trainEnd (%s) must be before or equal to testStart (%s)"
                .formatted(trainEnd, testStart));
        }
    }
    
    /**
     * Calculates the number of calendar days in the training period.
     * 
     * @return Number of days in the training window
     */
    public long trainDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(trainStart, trainEnd) + 1;
    }
    
    /**
     * Calculates the number of calendar days in the test period.
     * 
     * @return Number of days in the test window
     */
    public long testDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(testStart, testEnd) + 1;
    }
    
    /**
     * Calculates the train/test ratio (e.g., 3:1 means train is 3x longer than test).
     * 
     * @return The ratio of training days to test days
     */
    public double trainTestRatio() {
        long testD = testDays();
        if (testD == 0) return Double.POSITIVE_INFINITY;
        return (double) trainDays() / testD;
    }
    
    /**
     * Checks if a date falls within the training period.
     * 
     * @param date The date to check
     * @return true if the date is within the training period (inclusive)
     */
    public boolean isInTrainPeriod(LocalDate date) {
        return !date.isBefore(trainStart) && !date.isAfter(trainEnd);
    }
    
    /**
     * Checks if a date falls within the test period.
     * 
     * @param date The date to check
     * @return true if the date is within the test period (inclusive)
     */
    public boolean isInTestPeriod(LocalDate date) {
        return !date.isBefore(testStart) && !date.isAfter(testEnd);
    }
    
    /**
     * Returns a human-readable description of this window.
     * 
     * @return Formatted string describing the window
     */
    public String describe() {
        return "Window %d: Train [%s → %s] (%d days) | Test [%s → %s] (%d days) | Ratio %.1f:1"
            .formatted(
                windowIndex,
                trainStart, trainEnd, trainDays(),
                testStart, testEnd, testDays(),
                trainTestRatio()
            );
    }
}
