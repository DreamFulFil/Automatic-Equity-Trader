package tw.gc.auto.equity.trader.services.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.OrderExecutionService;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TWAPExecutionService - Phase 8
 */
@ExtendWith(MockitoExtension.class)
class TWAPExecutionServiceTest {

    @Mock
    private OrderExecutionService orderExecutionService;

    @InjectMocks
    private TWAPExecutionService twapService;

    @BeforeEach
    void setUp() {
        reset(orderExecutionService);
    }

    @Test
    void shouldExecuteSmallOrderImmediately() throws Exception {
        // Given: Order below TWAP threshold (< 100 shares)
        int quantity = 50;

        // When
        CompletableFuture<TWAPExecutionService.TWAPExecutionResult> future = 
            twapService.executeWithTWAP("BUY", quantity, 100.0, "2330", false, false, "TestStrategy");
        
        TWAPExecutionService.TWAPExecutionResult result = future.get();

        // Then: Should execute immediately without chunking
        assertThat(result.getChunkCount()).isEqualTo(1);
        assertThat(result.getExecutedQuantity()).isEqualTo(quantity);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        
        verify(orderExecutionService, times(1)).executeOrderWithRetry(
            eq("BUY"), eq(quantity), eq(100.0), eq("2330"), eq(false), eq(false), eq("TestStrategy"));
    }

    @Test
    void shouldExecuteExitOrderImmediately() throws Exception {
        // Given: Exit order (always immediate regardless of size)
        int quantity = 200;

        // When
        CompletableFuture<TWAPExecutionService.TWAPExecutionResult> future = 
            twapService.executeWithTWAP("SELL", quantity, 100.0, "2330", true, false, "TestStrategy");
        
        TWAPExecutionService.TWAPExecutionResult result = future.get();

        // Then: Should execute immediately
        assertThat(result.getChunkCount()).isEqualTo(1);
        assertThat(result.getExecutedQuantity()).isEqualTo(quantity);
        
        verify(orderExecutionService, times(1)).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), eq(true), eq(false), anyString());
    }

    @Test
    void shouldExecuteEmergencyOrderImmediately() throws Exception {
        // Given: Emergency shutdown order
        int quantity = 500;

        // When
        CompletableFuture<TWAPExecutionService.TWAPExecutionResult> future = 
            twapService.executeWithTWAP("SELL", quantity, 100.0, "2330", false, true, "TestStrategy");
        
        TWAPExecutionService.TWAPExecutionResult result = future.get();

        // Then: Should execute immediately
        assertThat(result.getChunkCount()).isEqualTo(1);
        verify(orderExecutionService, times(1)).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), eq(true), anyString());
    }

    @Test
    void shouldSplitLargeOrderIntoChunks() {
        // Given: Large order (200 shares) should be split
        int quantity = 200;

        // When
        CompletableFuture<TWAPExecutionService.TWAPExecutionResult> future = 
            twapService.executeWithTWAP("BUY", quantity, 100.0, "2330", "TestStrategy", 5, Duration.ofMinutes(10));

        // Wait a bit for chunks to start executing
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Then: Should call orderExecutionService multiple times (chunks)
        verify(orderExecutionService, atLeast(1)).executeOrderWithRetry(
            eq("BUY"), anyInt(), eq(100.0), eq("2330"), eq(false), eq(false), eq("TestStrategy"));
    }

    @Test
    void shouldCalculateCorrectChunkCount() {
        // Given/When/Then: Different order sizes should get appropriate chunk counts
        assertThat(twapService.calculateOptimalChunkCount(50)).isEqualTo(1);  // < 100: no TWAP
        assertThat(twapService.calculateOptimalChunkCount(150)).isEqualTo(3); // 100-200: 3 chunks
        assertThat(twapService.calculateOptimalChunkCount(300)).isEqualTo(5); // 200-500: 5 chunks
        assertThat(twapService.calculateOptimalChunkCount(600)).isEqualTo(7); // > 500: 7 chunks
    }

    @Test
    void shouldCalculateExecutionWindowBasedOnSize() {
        // Given: Different order sizes and volatility

        // When/Then: Larger orders and higher volatility should get longer windows
        Duration smallOrder = twapService.calculateOptimalExecutionWindow(100, 0.01);
        Duration largeOrder = twapService.calculateOptimalExecutionWindow(500, 0.01);
        Duration highVolatility = twapService.calculateOptimalExecutionWindow(200, 0.05);
        
        assertThat(smallOrder.toMinutes()).isLessThan(largeOrder.toMinutes());
        assertThat(highVolatility.toMinutes()).isGreaterThan(twapService.calculateOptimalExecutionWindow(200, 0.01).toMinutes());
        
        // All windows should be capped at 30 minutes
        Duration hugeOrder = twapService.calculateOptimalExecutionWindow(10000, 0.1);
        assertThat(hugeOrder.toMinutes()).isLessThanOrEqualTo(30);
    }

    @Test
    void shouldCalculateExecutionProgress() {
        // Given: TWAP result with partial execution
        TWAPExecutionService.TWAPExecutionResult result = TWAPExecutionService.TWAPExecutionResult.builder()
            .instrument("2330")
            .totalQuantity(200)
            .executedQuantity(100)
            .chunkCount(5)
            .status("IN_PROGRESS")
            .startTime(java.time.LocalDateTime.now())
            .build();

        // When/Then
        assertThat(result.getExecutionProgress()).isEqualTo(0.5); // 50% executed
    }

    @Test
    void shouldHandleZeroQuantityGracefully() {
        // Given: Zero total quantity edge case
        TWAPExecutionService.TWAPExecutionResult result = TWAPExecutionService.TWAPExecutionResult.builder()
            .instrument("2330")
            .totalQuantity(0)
            .executedQuantity(0)
            .chunkCount(1)
            .status("COMPLETED")
            .startTime(java.time.LocalDateTime.now())
            .endTime(java.time.LocalDateTime.now())
            .build();

        // When/Then: Should not throw, return 0.0 progress
        assertThat(result.getExecutionProgress()).isEqualTo(0.0);
    }

    @Test
    void shouldCalculateExecutionDuration() {
        // Given: Result with start and end times
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMinutes(10);
        java.time.LocalDateTime end = java.time.LocalDateTime.now();
        
        TWAPExecutionService.TWAPExecutionResult result = TWAPExecutionService.TWAPExecutionResult.builder()
            .instrument("2330")
            .totalQuantity(200)
            .executedQuantity(200)
            .chunkCount(5)
            .status("COMPLETED")
            .startTime(start)
            .endTime(end)
            .build();

        // When/Then
        Duration duration = result.getExecutionDuration();
        assertThat(duration.toMinutes()).isGreaterThanOrEqualTo(9);
        assertThat(duration.toMinutes()).isLessThanOrEqualTo(11);
    }

    @Test
    void shouldReturnZeroDurationWhenIncomplete() {
        // Given: Result without end time
        TWAPExecutionService.TWAPExecutionResult result = TWAPExecutionService.TWAPExecutionResult.builder()
            .instrument("2330")
            .totalQuantity(200)
            .executedQuantity(100)
            .chunkCount(5)
            .status("IN_PROGRESS")
            .startTime(java.time.LocalDateTime.now())
            .build();

        // When/Then
        assertThat(result.getExecutionDuration()).isEqualTo(Duration.ZERO);
    }
}
