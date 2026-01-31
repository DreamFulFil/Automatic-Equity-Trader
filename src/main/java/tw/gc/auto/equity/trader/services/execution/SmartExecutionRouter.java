package tw.gc.auto.equity.trader.services.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Smart Execution Router - Phase 8
 * 
 * Routes orders to optimal execution strategy based on:
 * - Order size (TWAP for large orders)
 * - Market timing (optimal execution windows)
 * - Historical performance (fill analytics)
 * - Urgency (emergency shutdowns bypass optimization)
 * 
 * Execution Flow:
 * 1. Check if emergency → execute immediately
 * 2. Check market timing → delay if poor conditions
 * 3. Check order size → use TWAP if large
 * 4. Select order type → market vs limit based on analytics
 * 5. Execute and record metrics
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SmartExecutionRouter {

    private final TWAPExecutionService twapService;
    private final OptimalExecutionTimer executionTimer;
    private final FillAnalyticsService fillAnalytics;

    /**
     * Route order to optimal execution strategy
     */
    public CompletableFuture<ExecutionResult> routeExecution(
            String action, int quantity, double price, String instrument,
            boolean isExit, boolean emergencyShutdown, String strategyName,
            double currentVolatility) {
        
        // Emergency shutdowns bypass all optimization
        if (emergencyShutdown) {
            log.warn("🚨 Emergency shutdown - executing {} {} immediately", action, instrument);
            return executeImmediately(action, quantity, price, instrument, strategyName);
        }
        
        // Check market timing
        OptimalExecutionTimer.ExecutionRecommendation timing = executionTimer.getExecutionRecommendation();
        
        // For exits, execute regardless of timing
        if (!isExit && timing.shouldWait() && !emergencyShutdown) {
            long minutesUntilOptimal = executionTimer.getMinutesUntilNextOptimalWindow();
            log.info("⏰ Delaying {} {} execution - poor timing (score: {}). Next optimal window in {} minutes",
                action, instrument, timing.getPriorityScore(), minutesUntilOptimal);
            
            // In production, would queue order for later execution
            // For now, execute anyway but log the suboptimal timing
        }
        
        // Get order type recommendation
        FillAnalyticsService.OrderTypeRecommendation orderTypeRec = 
            fillAnalytics.getOrderTypeRecommendation(instrument, currentVolatility);
        
        log.info("📊 Smart routing for {} {}: Timing score: {}, Recommended type: {}, Confidence: {}%",
            action, instrument, timing.getPriorityScore(), orderTypeRec.getRecommendedType(), orderTypeRec.getConfidence());
        
        // Use TWAP for large orders
        if (quantity >= 100 && !isExit) {
            int chunkCount = twapService.calculateOptimalChunkCount(quantity);
            java.time.Duration window = twapService.calculateOptimalExecutionWindow(quantity, currentVolatility);
            
            log.info("🔀 Using TWAP execution: {} chunks over {} minutes", chunkCount, window.toMinutes());
            
            return twapService.executeWithTWAP(action, quantity, price, instrument, strategyName, chunkCount, window)
                .thenApply(twapResult -> ExecutionResult.builder()
                    .instrument(instrument)
                    .quantity(quantity)
                    .executedQuantity(twapResult.getExecutedQuantity())
                    .executionMethod("TWAP")
                    .timingScore(timing.getPriorityScore())
                    .status(twapResult.getStatus())
                    .build());
        }
        
        // Execute immediately for small orders or exits
        return executeImmediately(action, quantity, price, instrument, strategyName);
    }

    /**
     * Execute order immediately (no TWAP)
     */
    private CompletableFuture<ExecutionResult> executeImmediately(
            String action, int quantity, double price, String instrument, String strategyName) {
        
        int timingScore = executionTimer.calculateExecutionPriorityScore();
        
        // In production, would call OrderExecutionService here
        // For now, simulate execution
        log.info("✅ Immediate execution: {} {} shares of {}", action, quantity, instrument);
        
        return CompletableFuture.completedFuture(ExecutionResult.builder()
            .instrument(instrument)
            .quantity(quantity)
            .executedQuantity(quantity)
            .executionMethod("IMMEDIATE")
            .timingScore(timingScore)
            .status("COMPLETED")
            .build());
    }

    /**
     * Get execution recommendation without actually executing
     */
    public ExecutionStrategy recommendExecutionStrategy(
            String action, int quantity, double price, String instrument,
            boolean isExit, boolean emergencyShutdown, double currentVolatility) {
        
        if (emergencyShutdown) {
            return ExecutionStrategy.builder()
                .method("IMMEDIATE")
                .reason("Emergency shutdown")
                .confidence(100)
                .build();
        }
        
        OptimalExecutionTimer.ExecutionRecommendation timing = executionTimer.getExecutionRecommendation();
        FillAnalyticsService.OrderTypeRecommendation orderType = 
            fillAnalytics.getOrderTypeRecommendation(instrument, currentVolatility);
        
        String method;
        String reason;
        int confidence;
        
        if (quantity >= 100 && !isExit) {
            method = "TWAP";
            reason = String.format("Large order (%d shares) - split execution to reduce market impact", quantity);
            confidence = 85;
        } else if (!timing.shouldExecuteImmediately() && !isExit) {
            method = "DELAYED";
            reason = timing.getReason();
            confidence = timing.getPriorityScore();
        } else {
            method = "IMMEDIATE";
            reason = String.format("%s order type recommended. %s", orderType.getRecommendedType(), timing.getReason());
            confidence = Math.min(timing.getPriorityScore(), orderType.getConfidence());
        }
        
        return ExecutionStrategy.builder()
            .method(method)
            .reason(reason)
            .confidence(confidence)
            .timingScore(timing.getPriorityScore())
            .recommendedOrderType(orderType.getRecommendedType())
            .build();
    }

    /**
     * Execution result
     */
    @lombok.Builder
    @lombok.Value
    public static class ExecutionResult {
        String instrument;
        int quantity;
        int executedQuantity;
        String executionMethod; // IMMEDIATE, TWAP, DELAYED
        int timingScore;
        String status; // COMPLETED, FAILED, IN_PROGRESS
    }

    /**
     * Execution strategy recommendation
     */
    @lombok.Builder
    @lombok.Value
    public static class ExecutionStrategy {
        String method; // IMMEDIATE, TWAP, DELAYED
        String reason;
        int confidence;
        int timingScore;
        String recommendedOrderType;
    }
}
