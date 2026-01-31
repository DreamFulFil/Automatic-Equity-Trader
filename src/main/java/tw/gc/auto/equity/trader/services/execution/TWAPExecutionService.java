package tw.gc.auto.equity.trader.services.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.services.OrderExecutionService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Time-Weighted Average Price (TWAP) Execution Service - Phase 8
 * 
 * Splits large orders into smaller chunks and executes them over a time window
 * to reduce market impact and achieve better average prices.
 * 
 * Benefits:
 * - Reduces market impact for large orders
 * - Achieves closer-to-average execution prices
 * - Smooths execution over time
 * 
 * Usage:
 * - For orders > 100 shares: split into 3-5 chunks over 5-15 minutes
 * - For smaller orders: execute immediately (no TWAP needed)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TWAPExecutionService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    
    // TWAP thresholds
    private static final int MIN_QUANTITY_FOR_TWAP = 100;
    private static final int DEFAULT_CHUNK_COUNT = 5;
    private static final Duration DEFAULT_EXECUTION_WINDOW = Duration.ofMinutes(10);
    
    private final OrderExecutionService orderExecutionService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * Execute order using TWAP if quantity is large enough, otherwise execute immediately
     */
    @Async
    public CompletableFuture<TWAPExecutionResult> executeWithTWAP(
            String action, int quantity, double price, String instrument,
            boolean isExit, boolean emergencyShutdown, String strategyName) {
        
        if (quantity < MIN_QUANTITY_FOR_TWAP || isExit || emergencyShutdown) {
            // Execute immediately for small orders, exits, or emergencies
            log.info("🚀 Executing {} {} shares of {} immediately (no TWAP)", action, quantity, instrument);
            orderExecutionService.executeOrderWithRetry(action, quantity, price, instrument, isExit, emergencyShutdown, strategyName);
            
            return CompletableFuture.completedFuture(TWAPExecutionResult.builder()
                .instrument(instrument)
                .totalQuantity(quantity)
                .executedQuantity(quantity)
                .chunkCount(1)
                .status("COMPLETED")
                .startTime(LocalDateTime.now(TAIPEI_ZONE))
                .endTime(LocalDateTime.now(TAIPEI_ZONE))
                .build());
        }
        
        return executeWithTWAPInternal(action, quantity, price, instrument, strategyName, DEFAULT_CHUNK_COUNT, DEFAULT_EXECUTION_WINDOW);
    }

    /**
     * Execute order using TWAP with custom parameters
     */
    @Async
    public CompletableFuture<TWAPExecutionResult> executeWithTWAP(
            String action, int quantity, double price, String instrument,
            String strategyName, int chunkCount, Duration executionWindow) {
        
        return executeWithTWAPInternal(action, quantity, price, instrument, strategyName, chunkCount, executionWindow);
    }

    /**
     * Internal TWAP execution logic
     */
    private CompletableFuture<TWAPExecutionResult> executeWithTWAPInternal(
            String action, int totalQuantity, double price, String instrument,
            String strategyName, int chunkCount, Duration executionWindow) {
        
        CompletableFuture<TWAPExecutionResult> future = new CompletableFuture<>();
        
        // Calculate chunk sizes
        int baseChunkSize = totalQuantity / chunkCount;
        int remainder = totalQuantity % chunkCount;
        
        // Calculate delay between chunks
        long delayMillis = executionWindow.toMillis() / chunkCount;
        
        LocalDateTime startTime = LocalDateTime.now(TAIPEI_ZONE);
        log.info("📊 Starting TWAP execution: {} {} shares of {} over {} minutes ({} chunks)",
            action, totalQuantity, instrument, executionWindow.toMinutes(), chunkCount);
        
        TWAPExecutionResult.TWAPExecutionResultBuilder resultBuilder = TWAPExecutionResult.builder()
            .instrument(instrument)
            .totalQuantity(totalQuantity)
            .chunkCount(chunkCount)
            .executedQuantity(0)
            .startTime(startTime)
            .status("IN_PROGRESS");
        
        // Schedule chunk executions
        for (int i = 0; i < chunkCount; i++) {
            int chunkSize = baseChunkSize + (i < remainder ? 1 : 0);
            int chunkIndex = i + 1;
            long delay = i * delayMillis;
            
            scheduler.schedule(() -> {
                try {
                    log.info("📈 TWAP chunk {}/{}: {} {} shares of {} @ {}",
                        chunkIndex, chunkCount, action, chunkSize, instrument, price);
                    
                    orderExecutionService.executeOrderWithRetry(
                        action, chunkSize, price, instrument, false, false, strategyName);
                    
                    // Update executed quantity
                    synchronized (resultBuilder) {
                        int newExecuted = resultBuilder.build().getExecutedQuantity() + chunkSize;
                        resultBuilder.executedQuantity(newExecuted);
                        
                        // Check if all chunks executed
                        if (newExecuted >= totalQuantity) {
                            resultBuilder
                                .status("COMPLETED")
                                .endTime(LocalDateTime.now(TAIPEI_ZONE));
                            future.complete(resultBuilder.build());
                            log.info("✅ TWAP execution completed: {} {} shares of {}",
                                action, totalQuantity, instrument);
                        }
                    }
                    
                } catch (Exception e) {
                    log.error("❌ TWAP chunk {}/{} failed: {}", chunkIndex, chunkCount, e.getMessage());
                    resultBuilder
                        .status("FAILED")
                        .endTime(LocalDateTime.now(TAIPEI_ZONE));
                    future.completeExceptionally(e);
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
        
        // Set timeout for overall execution
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                log.warn("⏰ TWAP execution timeout for {} {}", action, instrument);
                resultBuilder
                    .status("TIMEOUT")
                    .endTime(LocalDateTime.now(TAIPEI_ZONE));
                future.complete(resultBuilder.build());
            }
        }, executionWindow.toMillis() + 60000, TimeUnit.MILLISECONDS); // 1 minute buffer
        
        return future;
    }

    /**
     * Calculate optimal chunk count based on order size
     */
    public int calculateOptimalChunkCount(int quantity) {
        if (quantity < MIN_QUANTITY_FOR_TWAP) {
            return 1;
        } else if (quantity < 200) {
            return 3;
        } else if (quantity < 500) {
            return 5;
        } else {
            return 7;
        }
    }

    /**
     * Calculate optimal execution window based on order size and volatility
     */
    public Duration calculateOptimalExecutionWindow(int quantity, double volatility) {
        // Base window: 10 minutes
        // Add 2 minutes per 100 shares
        // Add 5 minutes if high volatility (> 0.03)
        
        long baseMinutes = 10;
        long sizeAdjustment = (quantity / 100) * 2;
        long volatilityAdjustment = volatility > 0.03 ? 5 : 0;
        
        long totalMinutes = Math.min(baseMinutes + sizeAdjustment + volatilityAdjustment, 30); // Cap at 30 minutes
        return Duration.ofMinutes(totalMinutes);
    }

    /**
     * TWAP execution result
     */
    @lombok.Builder
    @lombok.Value
    public static class TWAPExecutionResult {
        String instrument;
        int totalQuantity;
        int executedQuantity;
        int chunkCount;
        String status; // IN_PROGRESS, COMPLETED, FAILED, TIMEOUT
        LocalDateTime startTime;
        LocalDateTime endTime;
        
        public double getExecutionProgress() {
            return totalQuantity > 0 ? (double) executedQuantity / totalQuantity : 0.0;
        }
        
        public Duration getExecutionDuration() {
            if (endTime != null && startTime != null) {
                return Duration.between(startTime, endTime);
            }
            return Duration.ZERO;
        }
    }
    
    /**
     * Shutdown scheduler on application shutdown
     */
    public void shutdown() {
        log.info("🛑 Shutting down TWAP execution scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
