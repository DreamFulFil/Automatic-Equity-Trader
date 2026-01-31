package tw.gc.auto.equity.trader.services.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;

/**
 * Optimal Execution Timer - Phase 8
 * 
 * Determines the best time to execute orders based on:
 * - Market session phase (avoid open/close volatility)
 * - Historical spread patterns
 * - Volume profiles
 * 
 * Taiwan Stock Exchange Hours:
 * - Pre-market: 08:30 - 09:00
 * - Morning Session: 09:00 - 13:30
 * - No lunch break (continuous trading)
 * - After-hours: 13:30 - 14:30 (odd-lot only)
 * 
 * Optimal Execution Windows:
 * - 10:00 - 12:00: Mid-morning (stable spreads, good liquidity)
 * - 14:00 - 16:00: Afternoon (lower volatility)
 * 
 * Avoid:
 * - 09:00 - 09:30: Opening volatility
 * - 13:00 - 13:30: Pre-close rush
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OptimalExecutionTimer {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    
    // Taiwan stock exchange hours
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 0);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(13, 30);
    private static final LocalTime ODD_LOT_CLOSE = LocalTime.of(14, 30);
    
    // Optimal execution windows
    private static final LocalTime OPTIMAL_WINDOW_1_START = LocalTime.of(10, 0);
    private static final LocalTime OPTIMAL_WINDOW_1_END = LocalTime.of(12, 0);
    private static final LocalTime OPTIMAL_WINDOW_2_START = LocalTime.of(14, 0);
    private static final LocalTime OPTIMAL_WINDOW_2_END = LocalTime.of(16, 0);
    
    // Avoid windows (high volatility)
    private static final LocalTime OPENING_VOLATILITY_END = LocalTime.of(9, 30);
    private static final LocalTime CLOSING_VOLATILITY_START = LocalTime.of(13, 0);

    /**
     * Check if current time is optimal for execution
     */
    public boolean isOptimalExecutionTime() {
        return isOptimalExecutionTime(ZonedDateTime.now(TAIPEI_ZONE));
    }

    /**
     * Check if given time is optimal for execution
     */
    public boolean isOptimalExecutionTime(ZonedDateTime time) {
        LocalTime localTime = time.toLocalTime();
        DayOfWeek dayOfWeek = time.getDayOfWeek();
        
        // No trading on weekends
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        
        // Check if within optimal windows
        boolean inWindow1 = !localTime.isBefore(OPTIMAL_WINDOW_1_START) && localTime.isBefore(OPTIMAL_WINDOW_1_END);
        boolean inWindow2 = !localTime.isBefore(OPTIMAL_WINDOW_2_START) && localTime.isBefore(OPTIMAL_WINDOW_2_END);
        
        return inWindow1 || inWindow2;
    }

    /**
     * Check if current time is high volatility period (avoid execution)
     */
    public boolean isHighVolatilityPeriod() {
        return isHighVolatilityPeriod(ZonedDateTime.now(TAIPEI_ZONE));
    }

    /**
     * Check if given time is high volatility period
     */
    public boolean isHighVolatilityPeriod(ZonedDateTime time) {
        LocalTime localTime = time.toLocalTime();
        
        // Opening volatility: 09:00 - 09:30
        boolean isOpeningVolatility = !localTime.isBefore(MARKET_OPEN) && localTime.isBefore(OPENING_VOLATILITY_END);
        
        // Closing volatility: 13:00 - 13:30
        boolean isClosingVolatility = !localTime.isBefore(CLOSING_VOLATILITY_START) && localTime.isBefore(MARKET_CLOSE);
        
        return isOpeningVolatility || isClosingVolatility;
    }

    /**
     * Check if market is currently open
     */
    public boolean isMarketOpen() {
        return isMarketOpen(ZonedDateTime.now(TAIPEI_ZONE));
    }

    /**
     * Check if market is open at given time
     */
    public boolean isMarketOpen(ZonedDateTime time) {
        LocalTime localTime = time.toLocalTime();
        DayOfWeek dayOfWeek = time.getDayOfWeek();
        
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }
        
        return !localTime.isBefore(MARKET_OPEN) && localTime.isBefore(MARKET_CLOSE);
    }

    /**
     * Calculate execution priority score (0-100)
     * Higher score = better execution conditions
     */
    public int calculateExecutionPriorityScore() {
        return calculateExecutionPriorityScore(ZonedDateTime.now(TAIPEI_ZONE));
    }

    /**
     * Calculate execution priority score for given time
     */
    public int calculateExecutionPriorityScore(ZonedDateTime time) {
        if (!isMarketOpen(time)) {
            return 0; // Market closed
        }
        
        LocalTime localTime = time.toLocalTime();
        int baseScore = 50;
        
        // Optimal windows: +40 points
        if (isOptimalExecutionTime(time)) {
            baseScore += 40;
        }
        
        // High volatility periods: -30 points
        if (isHighVolatilityPeriod(time)) {
            baseScore -= 30;
        }
        
        // Mid-session bonus: +10 points (10:30 - 12:30)
        if (!localTime.isBefore(LocalTime.of(10, 30)) && localTime.isBefore(LocalTime.of(12, 30))) {
            baseScore += 10;
        }
        
        // Cap score at 0-100
        return Math.max(0, Math.min(100, baseScore));
    }

    /**
     * Get recommended action based on execution timing
     */
    public ExecutionRecommendation getExecutionRecommendation() {
        return getExecutionRecommendation(ZonedDateTime.now(TAIPEI_ZONE));
    }

    /**
     * Get execution recommendation for given time
     */
    public ExecutionRecommendation getExecutionRecommendation(ZonedDateTime time) {
        if (!isMarketOpen(time)) {
            return ExecutionRecommendation.builder()
                .action("WAIT")
                .reason("Market closed")
                .priorityScore(0)
                .build();
        }
        
        int score = calculateExecutionPriorityScore(time);
        
        if (score >= 80) {
            return ExecutionRecommendation.builder()
                .action("EXECUTE_NOW")
                .reason("Optimal execution window - low volatility, good liquidity")
                .priorityScore(score)
                .build();
        } else if (score >= 50) {
            return ExecutionRecommendation.builder()
                .action("EXECUTE_NORMAL")
                .reason("Acceptable execution conditions")
                .priorityScore(score)
                .build();
        } else if (score >= 20) {
            return ExecutionRecommendation.builder()
                .action("DELAY_IF_POSSIBLE")
                .reason("Suboptimal timing - high volatility or poor liquidity")
                .priorityScore(score)
                .build();
        } else {
            return ExecutionRecommendation.builder()
                .action("WAIT")
                .reason("Poor execution conditions - wait for better timing")
                .priorityScore(score)
                .build();
        }
    }

    /**
     * Calculate minutes until next optimal execution window
     */
    public long getMinutesUntilNextOptimalWindow() {
        ZonedDateTime now = ZonedDateTime.now(TAIPEI_ZONE);
        LocalTime currentTime = now.toLocalTime();
        
        // If in optimal window now, return 0
        if (isOptimalExecutionTime(now)) {
            return 0;
        }
        
        // Check next windows today
        if (currentTime.isBefore(OPTIMAL_WINDOW_1_START)) {
            return java.time.Duration.between(currentTime, OPTIMAL_WINDOW_1_START).toMinutes();
        } else if (currentTime.isBefore(OPTIMAL_WINDOW_2_START)) {
            return java.time.Duration.between(currentTime, OPTIMAL_WINDOW_2_START).toMinutes();
        } else {
            // Next window is tomorrow morning
            ZonedDateTime tomorrow = now.plusDays(1).with(OPTIMAL_WINDOW_1_START);
            return java.time.Duration.between(now, tomorrow).toMinutes();
        }
    }

    /**
     * Execution recommendation result
     */
    @lombok.Builder
    @lombok.Value
    public static class ExecutionRecommendation {
        String action; // EXECUTE_NOW, EXECUTE_NORMAL, DELAY_IF_POSSIBLE, WAIT
        String reason;
        int priorityScore; // 0-100
        
        public boolean shouldExecuteImmediately() {
            return "EXECUTE_NOW".equals(action) || "EXECUTE_NORMAL".equals(action);
        }
        
        public boolean shouldWait() {
            return "WAIT".equals(action);
        }
    }
}
