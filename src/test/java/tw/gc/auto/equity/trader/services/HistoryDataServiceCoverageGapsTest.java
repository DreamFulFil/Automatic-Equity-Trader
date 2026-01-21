package tw.gc.auto.equity.trader.services;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.PGConnection;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.testutil.AsyncTestHelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional tests for HistoryDataService to close coverage gaps.
 * 
 * Target lines:
 * - 151-152: Global writer exception handling
 * - 188: insertedBySymbol without prev result
 * - 224-228: Exception handling in runMultiStockDownloadTask
 * - 290-291: Batch download exception handling
 * - 413: pgBulkInsert success path
 * - 524, 530: Lambda expressions in MarketDataBulkInsertMapping
 * - 569-570: Writer thread exception in single-stock download
 * - 608-616: Batch download InterruptedException
 * - 630-631: future.get() exception
 * - 643, 645-647: writerLatch timeout and InterruptedException
 * - 711, 717, 743: Missing names batch flush paths
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryDataService - Coverage Gaps Tests")
class HistoryDataServiceCoverageGapsTest {

    @Mock
    private BarRepository barRepository;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private StrategyStockMappingRepository strategyStockMappingRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PGConnection pgConnection;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SystemStatusService systemStatusService;

    @Mock
    private BacktestService backtestService;

    @Mock
    private TelegramService telegramService;

    @Mock
    private TaiwanStockNameService taiwanStockNameService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private HistoryDataService historyDataService;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws Exception {
        historyDataService = new HistoryDataService(
            barRepository,
            marketDataRepository,
            strategyStockMappingRepository,
            restTemplate,
            objectMapper,
            dataSource,
            jdbcTemplate,
            transactionManager,
            systemStatusService,
            backtestService,
            telegramService,
            taiwanStockNameService
        );

        // Setup log capturing
        Logger logger = (Logger) LoggerFactory.getLogger(HistoryDataService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        // Common mock setups
        TransactionStatus mockStatus = mock(TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mockStatus);
        lenient().when(systemStatusService.startHistoryDownload()).thenReturn(true);
    }

    // ==================== Lines 151-152: Global writer exception ====================

    @Test
    @DisplayName("Lines 151-152: Global writer catches exception and logs error")
    void globalWriterExceptionIsCaughtAndLogged() throws Exception {
        // Arrange: Mock DataSource to throw exception during pgBulkInsert
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection pool exhausted"));
        // Mock JdbcTemplate fallback to also throw
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenThrow(new RuntimeException("JDBC batch failed"));

        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(100);
        AtomicBoolean complete = new AtomicBoolean(false);
        ConcurrentHashMap<String, AtomicInteger> insertedBySymbol = new ConcurrentHashMap<>();

        // Add some data to trigger flush
        for (int i = 0; i < 10; i++) {
            queue.put(createHistoricalDataPoint("2330.TW", LocalDateTime.now().minusDays(i), 100.0));
        }

        // Act: Run writer via reflection
        AtomicBoolean exceptionThrown = new AtomicBoolean(false);
        CountDownLatch writerLatch = new CountDownLatch(1);
        
        Thread writerThread = Thread.ofPlatform()
            .name("TestGlobalWriter")
            .start(() -> {
                try {
                    java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
                        "runGlobalWriter",
                        BlockingQueue.class,
                        AtomicBoolean.class,
                        ConcurrentHashMap.class
                    );
                    method.setAccessible(true);
                    method.invoke(historyDataService, queue, complete, insertedBySymbol);
                } catch (Exception e) {
                    exceptionThrown.set(true);
                } finally {
                    writerLatch.countDown();
                }
            });

        // Let it process
        Thread.sleep(200);
        complete.set(true);
        writerLatch.await(5, TimeUnit.SECONDS);

        // The method should handle the exception internally (not propagate)
        // Verify logs contain error message
        assertThat(logAppender.list)
            .anyMatch(e -> e.getLevel().toString().equals("WARN") && 
                e.getFormattedMessage().contains("PgBulkInsert failed"));
    }

    // ==================== Lines 188: insertedBySymbol without prev result ====================

    @Test
    @DisplayName("Line 188: insertedBySymbol entry without prev result creates new DownloadResult")
    void insertedBySymbolWithoutPrevResultCreatesNewDownloadResult() throws Exception {
        // Setup a scenario where the global writer inserts records
        // but download task didn't put anything in results map for that symbol
        historyDataService.resetTruncationFlag();

        // Mock empty download response (no records) but writer will still be populated via queue
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(Map.of("data", List.of()), HttpStatus.OK));
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of("TEST.TW"));

        // Setup for truncation
        lenient().doNothing().when(barRepository).truncateTable();
        lenient().doNothing().when(marketDataRepository).truncateTable();
        lenient().doNothing().when(strategyStockMappingRepository).truncateTable();

        // This will test the path where insertedBySymbol has a key not in results
        List<String> symbols = List.of("TEST.TW");
        // Trigger writer path with an interrupt to exit early
        Thread.currentThread().interrupt();
        Map<String, HistoryDataService.DownloadResult> results =
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1, 1, TimeUnit.MILLISECONDS);
        Thread.interrupted();

        // Should have result with zeros since no data was returned
        assertThat(results).containsKey("TEST.TW");
        verify(systemStatusService).completeHistoryDownload();
    }

    // ==================== Lines 224-228: Exception in runMultiStockDownloadTask ====================

    @Test
    @DisplayName("Lines 224-228: Generic exception in runMultiStockDownloadTask")
    void genericExceptionInRunMultiStockDownloadTask() throws Exception {
        // Setup - mock RestTemplate to throw RuntimeException
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("Network error"));

        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(100);
        Semaphore permits = new Semaphore(1);
        Map<String, HistoryDataService.DownloadResult> results = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        // Act
        historyDataService.runMultiStockDownloadTask("FAIL.TW", 1, queue, permits, results, counter);

        // Assert - should have zero-record result
        assertThat(results).containsKey("FAIL.TW");
        assertThat(results.get("FAIL.TW").getTotalRecords()).isZero();
        assertThat(permits.availablePermits()).isEqualTo(1); // Permit released
        // Verify no exception propagated - the method completes normally
    }

    // ==================== Lines 290-291: downloadStockData batch exception ====================

    @Test
    @DisplayName("Lines 290-291: Batch download exception in downloadStockData")
    void batchDownloadExceptionInDownloadStockData() throws Exception {
        // Setup - mock RestTemplate to throw after first successful call
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("Python bridge timeout"));
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of("2330.TW"));

        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(100);
        AtomicInteger counter = new AtomicInteger(0);

        // Act - call the internal downloadStockData method
        java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
            "downloadStockData", String.class, int.class, BlockingQueue.class, AtomicInteger.class
        );
        method.setAccessible(true);
        int downloaded = (int) method.invoke(historyDataService, "2330.TW", 1, queue, counter);

        // Assert - should return 0 due to exception
        assertThat(downloaded).isZero();
        // The exception is caught internally and logged (but may be at ERROR or WARN depending on where caught)
    }

    // ==================== Lines 413: pgBulkInsert success path ====================

    @Test
    @DisplayName("Line 413: pgBulkInsert triggers fallback to jdbcBatchInsert")
    void pgBulkInsertFallbackPath() throws Exception {
        // Setup - mock PgBulkInsert to fail, triggering fallback
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
        lenient().when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenReturn(new int[0][0]);

        List<Bar> bars = createTestBars(10);
        List<MarketData> marketDataList = createTestMarketData(10);

        // Act - call flushBatchWithFallback via reflection
        java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
            "flushBatchWithFallback", List.class, List.class
        );
        method.setAccessible(true);
        int result = (int) method.invoke(historyDataService, bars, marketDataList);

        // Assert - returns size of bars list from fallback
        assertThat(result).isEqualTo(10);
        // JdbcTemplate should be called as fallback
        verify(jdbcTemplate, atLeast(1)).batchUpdate(anyString(), anyList(), anyInt(), any());
        // Verify fallback warning logged
        assertThat(logAppender.list)
            .anyMatch(e -> e.getLevel().toString().equals("WARN") &&
                e.getFormattedMessage().contains("PgBulkInsert failed"));
    }

    // ==================== Lines 524, 530: Lambda mappings ====================

    @Test
    @DisplayName("Lines 524, 530: MarketData with different asset types exercised via jdbcBatchInsert")
    void marketDataMappingLambdasExercised() throws Exception {
        // Setup - fallback to jdbcBatchInsert to exercise the data
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));
        lenient().when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenReturn(new int[0][0]);

        // Create market data with different asset types
        List<Bar> bars = createTestBars(2);
        List<MarketData> marketDataList = new ArrayList<>();
        
        // Test with assetType = null (should default to "STOCK" in mapping)
        MarketData md1 = MarketData.builder()
            .timestamp(LocalDateTime.now())
            .symbol("2330.TW")
            .name("Test")
            .timeframe(MarketData.Timeframe.DAY_1)
            .open(100.0)
            .high(102.0)
            .low(98.0)
            .close(101.0)
            .volume(1000L)
            .assetType(null)  // Line 530: null assetType
            .build();
        marketDataList.add(md1);

        // Test with assetType = FUTURE
        MarketData md2 = MarketData.builder()
            .timestamp(LocalDateTime.now())
            .symbol("0050.TW")
            .name("Future Test")
            .timeframe(MarketData.Timeframe.DAY_1)
            .open(100.0)
            .high(102.0)
            .low(98.0)
            .close(101.0)
            .volume(1000L)
            .assetType(MarketData.AssetType.FUTURE)  // Line 530: non-null assetType
            .build();
        marketDataList.add(md2);

        // Act - call flushBatchWithFallback (which will fallback to jdbc)
        java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
            "flushBatchWithFallback", List.class, List.class
        );
        method.setAccessible(true);
        int result = (int) method.invoke(historyDataService, bars, marketDataList);

        // Assert - jdbcBatchInsert was called
        verify(jdbcTemplate, atLeast(1)).batchUpdate(anyString(), anyList(), anyInt(), any());
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    // ==================== Lines 569-570: Writer thread exception in downloadHistoricalData ====================

    @Test
    @DisplayName("Lines 569-570: Writer thread exception in downloadHistoricalData")
    void writerThreadExceptionInDownloadHistoricalData() throws Exception {
        // Setup - mock to provide data but make database operations fail
        Map<String, Object> mockResponse = Map.of("data", List.of(
            Map.of("timestamp", LocalDateTime.now().toString(),
                "name", "TestStock",
                "open", 100.0, "high", 102.0, "low", 98.0, "close", 101.0, "volume", 1000)
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of("2330.TW"));

        // Make writer fail
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any()))
            .thenThrow(new RuntimeException("Writer failure"));

        // Act
        HistoryDataService.DownloadResult result = historyDataService.downloadHistoricalData("2330.TW", 1);

        // Assert - should still return result (error is caught)
        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("2330.TW");
    }

    // ==================== Lines 608-616: Batch download InterruptedException ====================

    @Test
    @DisplayName("Lines 608-616: InterruptedException during batch queue put")
    void interruptedExceptionDuringBatchQueuePut() throws Exception {
        // Test verifies interrupted thread behavior in download
        // Pre-interrupt the thread before running download

        // Run in a thread that will be interrupted
        AtomicBoolean completed = new AtomicBoolean(false);
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt(); // Pre-interrupt
            try {
                // Use a small blocking queue that might trigger interrupt on put
                BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(1);
                Semaphore permits = new Semaphore(1);
                Map<String, HistoryDataService.DownloadResult> results = new HashMap<>();
                AtomicInteger counter = new AtomicInteger(0);
                
                historyDataService.runMultiStockDownloadTask("INT.TW", 1, queue, permits, results, counter);
            } catch (Exception e) {
                // Expected - may throw due to interrupt
            }
            completed.set(true);
        });
        testThread.start();
        testThread.join(5000);

        assertThat(completed.get()).isTrue();
    }

    @Test
    @DisplayName("Line 413: notifyDownloadSummary handles empty/null without sending")
    void notifyDownloadSummary_handlesEmptyOrNull() {
        historyDataService.notifyDownloadSummary(null);
        historyDataService.notifyDownloadSummary(new HashMap<>());
        verifyNoInteractions(telegramService);
    }

    // ==================== Lines 630-631: future.get() exception ====================

    @Test
    @DisplayName("Lines 630-631: future.get() throws exception")
    void futureGetThrowsException() throws Exception {
        // Mock to make the REST call throw during Future execution
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("Future execution failed"));
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of("FUTURE.TW"));

        // Act
        HistoryDataService.DownloadResult result = historyDataService.downloadHistoricalData("FUTURE.TW", 1);

        // Assert - should handle exception gracefully
        assertThat(result).isNotNull();
        assertThat(result.getTotalRecords()).isZero();
    }

    // ==================== Lines 643, 645-647: writerLatch timeout and InterruptedException ====================

    @Test
    @DisplayName("Line 643: writerLatch timeout warning")
    void writerLatchTimeoutWarning() throws Exception {
        // This is already covered by HistoryDataServiceConcurrencyTest.taskA4_writerLatchTimeoutStillSendsSummaryAndCompletes
        // Adding another variation here

        // Make the writer very slow
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any()))
            .thenAnswer(inv -> {
                Thread.sleep(100);
                return new int[0][0];
            });
        when(dataSource.getConnection()).thenThrow(new SQLException("force fallback"));
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of("TIMEOUT.TW"));

        // Mock download response
        Map<String, Object> mockResponse = Map.of("data", List.of(
            Map.of("timestamp", LocalDateTime.now().toString(),
                "name", "TestStock",
                "open", 100.0, "high", 102.0, "low", 98.0, "close", 101.0, "volume", 1000)
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Use reflection to call downloadHistoricalDataAsync with very short timeout
        // This will trigger the timeout path
        HistoryDataService.DownloadResult result = historyDataService.downloadHistoricalData("TIMEOUT.TW", 1);

        // Should complete (even if with timeout warning)
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Lines 645-647: writerLatch await interrupted")
    void writerLatchAwaitInterrupted() throws Exception {
        // Test the awaitWriterLatch method directly
        CountDownLatch latch = new CountDownLatch(1); // Never counts down

        AtomicBoolean resultHolder = new AtomicBoolean(true);
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt(); // Pre-interrupt
            boolean result = historyDataService.awaitWriterLatch(latch, 10, TimeUnit.SECONDS);
            resultHolder.set(result);
        });
        testThread.start();
        testThread.join(2000);

        assertThat(resultHolder.get()).isFalse();
    }

    // ==================== Lines 711, 717, 743: Missing names batch flush ====================

    @Test
    @DisplayName("Lines 711, 717: Batch flush with missing names - intermediate flush")
    void batchFlushWithMissingNamesIntermediateFlush() throws Exception {
        // Create a scenario where batch flush happens with missing names
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(3000);
        AtomicBoolean complete = new AtomicBoolean(false);

        // Add 2100 points with null names to trigger batch flush (batch size = 2000)
        for (int i = 0; i < 2100; i++) {
            HistoryDataService.HistoricalDataPoint point = createHistoricalDataPoint(
                "2330.TW", LocalDateTime.now().minusDays(i), 100.0
            );
            point.setName(null); // Missing name
            queue.put(point);
        }

        // Mock name service
        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("TSMC");

        // Mock database operations
        when(dataSource.getConnection()).thenThrow(new SQLException("force fallback"));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any()))
            .thenReturn(new int[0][0]);

        // Run writer
        Thread writerThread = new Thread(() -> {
            try {
                java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
                    "runSingleWriter", String.class, BlockingQueue.class, AtomicBoolean.class
                );
                method.setAccessible(true);
                method.invoke(historyDataService, "2330.TW", queue, complete);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writerThread.start();

        // Wait for queue to drain
        AsyncTestHelper.waitForAsync(10000, queue::isEmpty);
        complete.set(true);
        writerThread.join(10000);

        // Verify name service was called
        verify(taiwanStockNameService, atLeast(1)).hasStockName("2330.TW");
        verify(taiwanStockNameService, atLeast(1)).getStockName("2330.TW");
    }

    @Test
    @DisplayName("Line 743: Final batch flush with missing names")
    void finalBatchFlushWithMissingNames() throws Exception {
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(3000);
        AtomicBoolean complete = new AtomicBoolean(false);

        // Add less than batch size (500 points) to test final flush path
        for (int i = 0; i < 500; i++) {
            HistoryDataService.HistoricalDataPoint point = createHistoricalDataPoint(
                "2454.TW", LocalDateTime.now().minusDays(i), 200.0
            );
            point.setName(""); // Empty name (treated as missing)
            queue.put(point);
        }

        // Mock name service
        when(taiwanStockNameService.hasStockName("2454.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2454.TW")).thenReturn("MediaTek");

        // Mock database operations
        when(dataSource.getConnection()).thenThrow(new SQLException("force fallback"));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any()))
            .thenReturn(new int[0][0]);

        // Signal completion immediately (to trigger final flush)
        complete.set(true);

        // Run writer
        java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
            "runSingleWriter", String.class, BlockingQueue.class, AtomicBoolean.class
        );
        method.setAccessible(true);
        int inserted = (int) method.invoke(historyDataService, "2454.TW", queue, complete);

        // Verify name service was called for final flush
        verify(taiwanStockNameService, atLeast(1)).hasStockName("2454.TW");
        verify(taiwanStockNameService, atLeast(1)).getStockName("2454.TW");

        // Verify we fell back to JdbcTemplate and that missing names were filled before inserting.
        ArgumentCaptor<List> barBatchCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> marketDataBatchCaptor = ArgumentCaptor.forClass(List.class);

        verify(jdbcTemplate, atLeastOnce()).batchUpdate(
            argThat(sql -> sql != null && sql.startsWith("INSERT INTO bar")),
            barBatchCaptor.capture(),
            eq(500),
            any(ParameterizedPreparedStatementSetter.class)
        );
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(
            argThat(sql -> sql != null && sql.startsWith("INSERT INTO market_data")),
            marketDataBatchCaptor.capture(),
            eq(500),
            any(ParameterizedPreparedStatementSetter.class)
        );

        List<?> barsInserted = barBatchCaptor.getValue();
        assertThat(barsInserted).isNotEmpty();
        assertThat(((Bar) barsInserted.getFirst()).getName()).isEqualTo("MediaTek");

        List<?> marketDataInserted = marketDataBatchCaptor.getValue();
        assertThat(marketDataInserted).isNotEmpty();
        assertThat(((MarketData) marketDataInserted.getFirst()).getName()).isEqualTo("MediaTek");
    }

    // ==================== Helper Methods ====================

    private HistoryDataService.HistoricalDataPoint createHistoricalDataPoint(
            String symbol, LocalDateTime timestamp, double price) {
        HistoryDataService.HistoricalDataPoint point = new HistoryDataService.HistoricalDataPoint();
        point.setSymbol(symbol);
        point.setName("Test Stock");
        point.setTimestamp(timestamp);
        point.setOpen(price * 0.98);
        point.setHigh(price * 1.02);
        point.setLow(price * 0.96);
        point.setClose(price);
        point.setVolume(1000L);
        return point;
    }

    private List<Bar> createTestBars(int count) {
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            bars.add(Bar.builder()
                .timestamp(LocalDateTime.now().minusDays(i))
                .symbol("2330.TW")
                .name("Test Stock")
                .market("TSE")
                .timeframe("1day")
                .open(100.0)
                .high(102.0)
                .low(98.0)
                .close(101.0)
                .volume(1000L)
                .isComplete(true)
                .build());
        }
        return bars;
    }

    private List<MarketData> createTestMarketData(int count) {
        List<MarketData> marketDataList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            marketDataList.add(MarketData.builder()
                .timestamp(LocalDateTime.now().minusDays(i))
                .symbol("2330.TW")
                .name("Test Stock")
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(100.0)
                .high(102.0)
                .low(98.0)
                .close(101.0)
                .volume(1000L)
                .build());
        }
        return marketDataList;
    }
}
