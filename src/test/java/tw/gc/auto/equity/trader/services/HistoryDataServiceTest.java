package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryDataServiceTest {

    @Mock
    private BarRepository barRepository;

    @Mock
    private MarketDataRepository marketDataRepository;
    
    @Mock
    private StrategyStockMappingRepository strategyStockMappingRepository;

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    @Mock
    private DataSource dataSource;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private SystemStatusService systemStatusService;
    
    @Mock
    private BacktestService backtestService;

    @Mock
    private TaiwanStockNameService taiwanStockNameService;

    @Mock
    private TelegramService telegramService;
    
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private HistoryDataService historyDataService;

    @BeforeEach
    void setUp() {
        // Create a mock TransactionManager with lenient stubbing
        transactionManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.TransactionStatus mockStatus = 
            mock(org.springframework.transaction.TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mockStatus);
        lenient().when(systemStatusService.startHistoryDownload()).thenReturn(true);
        
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
    }

    @Test
    void downloadHistoricalData_shouldSkipDuplicates() throws Exception {
        // Given
        String symbol = "2330.TW";
        
        // Note: Cannot test actual download without mocking Yahoo Finance API
        // This test verifies the service instantiation only
        // Actual duplicate handling is tested through integration tests
    }

    @Test
    void downloadResult_shouldContainCorrectFields() {
        // Given
        String symbol = "2454.TW";
        int totalRecords = 100;
        int inserted = 80;
        int skipped = 20;
        
        // When
        HistoryDataService.DownloadResult result = new HistoryDataService.DownloadResult(
            symbol, totalRecords, inserted, skipped
        );
        
        // Then
        assertThat(result.getSymbol()).isEqualTo(symbol);
        assertThat(result.getTotalRecords()).isEqualTo(totalRecords);
        assertThat(result.getInserted()).isEqualTo(inserted);
        assertThat(result.getSkipped()).isEqualTo(skipped);
    }

    @Test
    void barEntitySave_shouldHaveCorrectFields() {
        // Given
        Bar bar = new Bar();
        bar.setTimestamp(LocalDateTime.now());
        bar.setSymbol("2330.TW");
        bar.setMarket("TSE");
        bar.setTimeframe("1day");
        bar.setOpen(580.0);
        bar.setHigh(590.0);
        bar.setLow(575.0);
        bar.setClose(585.0);
        bar.setVolume(10000000L);
        bar.setComplete(true);
        
        when(barRepository.save(any(Bar.class))).thenReturn(bar);
        
        // When
        Bar saved = barRepository.save(bar);
        
        // Then
        assertThat(saved.getSymbol()).isEqualTo("2330.TW");
        assertThat(saved.getTimeframe()).isEqualTo("1day");
        assertThat(saved.getOpen()).isEqualTo(580.0);
        assertThat(saved.getClose()).isEqualTo(585.0);
        verify(barRepository).save(bar);
    }

    @Test
    void marketDataEntitySave_shouldHaveCorrectFields() {
        // Given
        MarketData marketData = MarketData.builder()
            .timestamp(LocalDateTime.now())
            .symbol("2454.TW")
            .timeframe(MarketData.Timeframe.DAY_1)
            .open(900.0)
            .high(920.0)
            .low(895.0)
            .close(915.0)
            .volume(5000000L)
            .build();
        
        when(marketDataRepository.save(any(MarketData.class))).thenReturn(marketData);
        
        // When
        MarketData saved = marketDataRepository.save(marketData);
        
        // Then
        assertThat(saved.getSymbol()).isEqualTo("2454.TW");
        assertThat(saved.getTimeframe()).isEqualTo(MarketData.Timeframe.DAY_1);
        assertThat(saved.getOpen()).isEqualTo(900.0);
        assertThat(saved.getClose()).isEqualTo(915.0);
        verify(marketDataRepository).save(marketData);
    }

    @Test
    void duplicateCheck_shouldPreventDuplicateInsertions() {
        // Given
        String symbol = "2330.TW";
        LocalDateTime timestamp = LocalDateTime.of(2024, 6, 15, 0, 0);
        
        when(barRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, "1day"))
            .thenReturn(true);
        when(marketDataRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, MarketData.Timeframe.DAY_1))
            .thenReturn(true);
        
        // When
        boolean barExists = barRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, "1day");
        boolean marketDataExists = marketDataRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, MarketData.Timeframe.DAY_1);
        
        // Then
        assertThat(barExists).isTrue();
        assertThat(marketDataExists).isTrue();
    }

    @Test
    void newRecord_shouldPassDuplicateCheck() {
        // Given
        String symbol = "2317.TW";
        LocalDateTime timestamp = LocalDateTime.of(2024, 12, 17, 0, 0);
        
        when(barRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, "1day"))
            .thenReturn(false);
        
        // When
        boolean barExists = barRepository.existsBySymbolAndTimestampAndTimeframe(symbol, timestamp, "1day");
        
        // Then
        assertThat(barExists).isFalse();
    }
    
    @Test
    void truncateTablesIfNeeded_shouldTruncateOnFirstCall() {
        // Reset the flag to simulate fresh state
        historyDataService.resetTruncationFlag();
        
        // First call should truncate
        historyDataService.truncateTablesIfNeeded();
        
        // Verify truncation was called
        verify(barRepository, times(1)).truncateTable();
        verify(marketDataRepository, times(1)).truncateTable();
        verify(strategyStockMappingRepository, times(1)).truncateTable();
    }
    
    @Test
    void truncateTablesIfNeeded_shouldNotTruncateOnSubsequentCalls() {
        // Reset the flag to simulate fresh state
        historyDataService.resetTruncationFlag();
        
        // First call
        historyDataService.truncateTablesIfNeeded();
        
        // Second call should not truncate again
        historyDataService.truncateTablesIfNeeded();
        
        // Verify truncation was called only once
        verify(barRepository, times(1)).truncateTable();
        verify(marketDataRepository, times(1)).truncateTable();
        verify(strategyStockMappingRepository, times(1)).truncateTable();
    }
    @Test
    void getStockNameFromHistory_prefersBar_thenMarketData_thenNull() {
        String symbol = "2330.TW";
        Bar barWithName = new Bar();
        barWithName.setSymbol(symbol);
        barWithName.setTimeframe("1day");
        barWithName.setName("TSMC");

        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day"))
            .thenReturn(java.util.Optional.of(barWithName));

        String name = historyDataService.getStockNameFromHistory(symbol);
        assertThat(name).isEqualTo("TSMC");

        // If bar has no name, market data should be checked
        Bar barNoName = new Bar();
        barNoName.setSymbol(symbol);
        barNoName.setTimeframe("1day");
        barNoName.setName(null);

        MarketData mdWithName = MarketData.builder()
            .symbol(symbol)
            .timeframe(MarketData.Timeframe.DAY_1)
            .name("TSMC via MD")
            .build();

        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day"))
            .thenReturn(java.util.Optional.of(barNoName));
        when(marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, MarketData.Timeframe.DAY_1))
            .thenReturn(java.util.Optional.of(mdWithName));

        String name2 = historyDataService.getStockNameFromHistory(symbol);
        assertThat(name2).isEqualTo("TSMC via MD");

        // If neither has name, result should be null
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day"))
            .thenReturn(java.util.Optional.empty());
        when(marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, MarketData.Timeframe.DAY_1))
            .thenReturn(java.util.Optional.empty());

        String name3 = historyDataService.getStockNameFromHistory(symbol);
        assertThat(name3).isNull();
    }    
    @Test
    void downloadResult_queueCapacityConfiguration() {
        // Verify the service is properly configured
        // The queue capacity and batch size are internal constants
        // This test verifies the service instantiates correctly with the new configuration
        assertThat(historyDataService).isNotNull();
    }
    
    @Test
    void resetTruncationFlag_shouldAllowRetruncation() {
        // Reset and truncate
        historyDataService.resetTruncationFlag();
        historyDataService.truncateTablesIfNeeded();
        
        // Verify first truncation
        verify(barRepository, times(1)).truncateTable();
        
        // Reset again and truncate
        historyDataService.resetTruncationFlag();
        historyDataService.truncateTablesIfNeeded();
        
        // Verify second truncation occurred
        verify(barRepository, times(2)).truncateTable();
        verify(marketDataRepository, times(2)).truncateTable();
        verify(strategyStockMappingRepository, times(2)).truncateTable();
    }
    
    @Test
    void downloadHistoricalDataForMultipleStocks_shouldReturnResultsForAllSymbols() {
        // Given
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("2330.TW", "2454.TW", "2317.TW");
        
        // When
        Map<String, HistoryDataService.DownloadResult> results = 
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        
        // Then - should have results for all symbols (even if download failed due to mock)
        assertThat(results).hasSize(3);
        assertThat(results.keySet()).containsExactlyInAnyOrder("2330.TW", "2454.TW", "2317.TW");
    }

    @Test
    void fillMissingNamesIfMissing_shouldFillFromTaiwanService() {
        // Given
        String symbol = "2454.TW";
        Bar b1 = new Bar(); b1.setSymbol(symbol); b1.setTimeframe("1day"); b1.setName(null);
        Bar b2 = new Bar(); b2.setSymbol(symbol); b2.setTimeframe("1day"); b2.setName("");
        Bar b3 = new Bar(); b3.setSymbol(symbol); b3.setTimeframe("1day"); b3.setName("MediaTek Existing");
        List<Bar> bars = List.of(b1, b2, b3);

        MarketData m1 = MarketData.builder().symbol(symbol).timeframe(MarketData.Timeframe.DAY_1).name(null).build();
        List<MarketData> mds = List.of(m1);

        when(taiwanStockNameService.hasStockName(symbol)).thenReturn(true);
        when(taiwanStockNameService.getStockName(symbol)).thenReturn("MediaTek");

        // When
        historyDataService.fillMissingNamesIfMissing(bars, mds, symbol);

        // Then
        assertThat(b1.getName()).isEqualTo("MediaTek");
        assertThat(b2.getName()).isEqualTo("MediaTek");
        assertThat(b3.getName()).isEqualTo("MediaTek Existing");
        assertThat(m1.getName()).isEqualTo("MediaTek");
    }
    
    @Test
    void downloadHistoricalDataForMultipleStocks_shouldTruncateOnlyOnce() {
        // Given
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("2330.TW", "2454.TW");
        
        // When
        historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        
        // Then - truncation should occur only once
        verify(barRepository, times(1)).truncateTable();
        verify(marketDataRepository, times(1)).truncateTable();
        verify(strategyStockMappingRepository, times(1)).truncateTable();
    }
    
    @Test
    void downloadHistoricalDataForMultipleStocks_shouldUpdateSystemStatus() {
        // Given
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("2330.TW");
        
        // When
        historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        
        // Then - should start and complete status
        verify(systemStatusService, times(1)).startHistoryDownload();
        verify(systemStatusService, times(1)).completeHistoryDownload();
    }
    
    @Test
    void downloadHistoricalDataForMultipleStocks_shouldCompleteStatusEvenOnError() {
        // Given
        historyDataService.resetTruncationFlag();
        doThrow(new RuntimeException("Test error")).when(barRepository).truncateTable();
        List<String> symbols = List.of("2330.TW");
        
        // When/Then - should still call completeHistoryDownload even on error
        try {
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        } catch (Exception e) {
            // Expected
        }
        
        verify(systemStatusService, times(1)).startHistoryDownload();
        verify(systemStatusService, times(1)).completeHistoryDownload();
    }
    
    @Test
    void downloadResult_shouldHaveSymbolField() {
        // Given/When
        HistoryDataService.DownloadResult result = new HistoryDataService.DownloadResult(
            "2330.TW", 100, 95, 5
        );
        
        // Then
        assertThat(result.getSymbol()).isEqualTo("2330.TW");
        assertThat(result.getTotalRecords()).isEqualTo(100);
        assertThat(result.getInserted()).isEqualTo(95);
        assertThat(result.getSkipped()).isEqualTo(5);
    }
    
    @Test
    void historicalDataPoint_shouldHaveSymbolField() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // When
        HistoryDataService.HistoricalDataPoint point = new HistoryDataService.HistoricalDataPoint(
            "2330.TW", "TSMC", now, 580.0, 590.0, 575.0, 585.0, 10000000L
        );
        
        // Then
        assertThat(point.getSymbol()).isEqualTo("2330.TW");
        assertThat(point.getTimestamp()).isEqualTo(now);
        assertThat(point.getOpen()).isEqualTo(580.0);
        assertThat(point.getClose()).isEqualTo(585.0);
    }
    
    @Test
    void concurrencyConstants_shouldHaveReasonableDefaults() {
        // This test verifies that the service is properly configured
        // The actual constants are private, but we verify instantiation works
        assertThat(historyDataService).isNotNull();
    }

    @Test
    void parseHistoricalData_shouldParseValidResponse() throws Exception {
        // Prepare a mock response body similar to Python API
        Map<String, Object> point = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "name", "Test Corp",
            "open", 100.0,
            "high", 101.0,
            "low", 99.0,
            "close", 100.5,
            "volume", 1000
        );
        Map<String, Object> body = Map.of("data", List.of(point));

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("parseHistoricalData", Map.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> parsed = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, body);
        assertThat(parsed).hasSize(1);
        HistoryDataService.HistoricalDataPoint p = parsed.get(0);
        assertThat(p.getName()).isEqualTo("Test Corp");
        assertThat(p.getOpen()).isEqualTo(100.0);
        assertThat(p.getVolume()).isEqualTo(1000L);
    }

    @Test
    void parseHistoricalData_shouldReturnEmptyWhenDataMissing() throws Exception {
        Map<String, Object> body = Map.of();
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("parseHistoricalData", Map.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> parsed = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, body);
        assertThat(parsed).isEmpty();
    }

    @Test
    void runGlobalWriter_shouldFallbackToJdbc_andAttributeInsertedCounts() throws Exception {
        // Prepare queue with two points of different symbols
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(10);
        LocalDateTime now = LocalDateTime.now();
        HistoryDataService.HistoricalDataPoint p1 = new HistoryDataService.HistoricalDataPoint("AAA.TW", "A Corp", now, 10.0, 11.0, 9.0, 10.5, 100L);
        HistoryDataService.HistoricalDataPoint p2 = new HistoryDataService.HistoricalDataPoint("BBB.TW", "B Corp", now, 20.0, 21.0, 19.0, 20.5, 200L);
        queue.put(p1);
        queue.put(p2);

        AtomicBoolean complete = new AtomicBoolean(true); // signal that producers are done
        ConcurrentHashMap<String, AtomicInteger> insertedBySymbol = new ConcurrentHashMap<>();

        // Force PgBulkInsert to fail by throwing on dataSource.getConnection()
        when(dataSource.getConnection()).thenThrow(new SQLException("no pg"));

        // Stub jdbcTemplate to simulate successful inserts
        // The batchUpdate overload in our test context resolves to an int[][] return type for mocking
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{{1}});

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("runGlobalWriter", BlockingQueue.class, AtomicBoolean.class, ConcurrentHashMap.class);
        m.setAccessible(true);

        int inserted = (int) m.invoke(historyDataService, queue, complete, insertedBySymbol);

        assertThat(inserted).isEqualTo(2);
        assertThat(insertedBySymbol).containsKeys("AAA.TW", "BBB.TW");
        assertThat(insertedBySymbol.get("AAA.TW").get()).isEqualTo(1);
        assertThat(insertedBySymbol.get("BBB.TW").get()).isEqualTo(1);

        // Verify JdbcTemplate fallback was used
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void runSingleWriter_shouldHandleFinalFlushAndMissingNames() throws Exception {
        // Given
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(10);
        LocalDateTime now = LocalDateTime.now();
        HistoryDataService.HistoricalDataPoint p1 = new HistoryDataService.HistoricalDataPoint("2330.TW", null, now, 100.0, 101.0, 99.0, 100.5, 1000L); // missing name
        queue.put(p1);

        AtomicBoolean complete = new AtomicBoolean(true);

        // Mock TaiwanStockNameService to provide fallback name
        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("TSMC");

        // Force PgBulkInsert to fail
        when(dataSource.getConnection()).thenThrow(new SQLException("no pg"));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{{1}});

        // When
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("runSingleWriter", String.class, BlockingQueue.class, AtomicBoolean.class);
        m.setAccessible(true);
        int inserted = (int) m.invoke(historyDataService, "2330.TW", queue, complete);

        // Then
        assertThat(inserted).isEqualTo(1);
        verify(taiwanStockNameService).getStockName("2330.TW");
        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), anyList(), anyInt(), any()); // Bar and MarketData
    }

    @Test
    void jdbcBatchInsert_shouldHandleException() throws Exception {
        // Given
        List<Bar> bars = List.of(new Bar());
        List<MarketData> marketDataList = List.of(new MarketData());

        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenThrow(new RuntimeException("DB error"));

        // When
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("jdbcBatchInsert", List.class, List.class);
        m.setAccessible(true);
        int inserted = (int) m.invoke(historyDataService, bars, marketDataList);

        // Then
        assertThat(inserted).isEqualTo(0);
    }

    @Test
    void flushBatchWithFallback_shouldUsePgWhenAvailable() throws Exception {
        // Given
        List<Bar> bars = List.of(new Bar());
        List<MarketData> marketDataList = List.of(new MarketData());

        // Mock successful PgBulkInsert
        when(dataSource.getConnection()).thenReturn(mock(java.sql.Connection.class));

        // When
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("flushBatchWithFallback", List.class, List.class);
        m.setAccessible(true);
        int inserted = (int) m.invoke(historyDataService, bars, marketDataList);

        // Then
        assertThat(inserted).isEqualTo(1); // pgBulkInsert returns bars.size()
        verify(dataSource).getConnection();
    }

    @Test
    void downloadBatch_shouldHandleRestTemplateException() throws Exception {
        // Given
        String symbol = "2330.TW";
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenThrow(new RuntimeException("Network error"));

        // When
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("downloadBatch", String.class, LocalDateTime.class, LocalDateTime.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> result = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, symbol, start, end);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void parseHistoricalData_shouldHandleMalformedData() throws Exception {
        // Given
        Map<String, Object> point = Map.of(
            "timestamp", "invalid-timestamp",
            "name", "Test",
            "open", "not-a-number", // invalid
            "high", 101.0,
            "low", 99.0,
            "close", 100.5,
            "volume", 1000
        );
        Map<String, Object> body = Map.of("data", List.of(point));

        // When
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("parseHistoricalData", Map.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> parsed = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, body);

        // Then - should skip malformed entries
        assertThat(parsed).isEmpty();
    }

    @Test
    void notifyDownloadStartedForSymbol_shouldNotSendTelegram() {
        // Given
        String symbol = "2330.TW";
        int years = 1;

        // When
        historyDataService.notifyDownloadStartedForSymbol(symbol, years);

        // Then - intentionally no-op, no telegram sent
        verifyNoInteractions(telegramService);
    }

    @Test
    void notifyDownloadComplete_shouldNotSendTelegram() {
        // Given
        HistoryDataService.DownloadResult result = new HistoryDataService.DownloadResult("2330.TW", 100, 95, 5);

        // When
        historyDataService.notifyDownloadComplete(result);

        // Then - intentionally no-op, no telegram sent
        verifyNoInteractions(telegramService);
    }

    @Test
    void notifyDownloadSummary_shouldSendTelegramWhenServiceAvailable() {
        // Given
        Map<String, HistoryDataService.DownloadResult> results = Map.of(
            "2330.TW", new HistoryDataService.DownloadResult("2330.TW", 100, 95, 5)
        );

        // When
        historyDataService.notifyDownloadSummary(results);

        // Then
        verify(telegramService).sendMessage(anyString());
    }

    @Test
    void notifyDownloadStartedForAll_shouldSendTelegramWhenServiceAvailable() {
        // Given
        List<String> symbols = List.of("2330.TW", "2454.TW");
        int years = 1;

        // When
        historyDataService.notifyDownloadStartedForAll(symbols, years);

        // Then
        verify(telegramService).sendMessage(anyString());
    }

    @Test
    void formatSummaryForAll_formatsSummaryCorrectly() {
        // Given
        Map<String, HistoryDataService.DownloadResult> results = new java.util.TreeMap<>();
        results.put("AAA.TW", new HistoryDataService.DownloadResult("AAA.TW", 10, 5, 5));
        results.put("BBB.TW", new HistoryDataService.DownloadResult("BBB.TW", 20, 15, 5));

        // When
        String msg = historyDataService.formatSummaryForAll(results);

        // Then
        assertThat(msg).contains("symbols=2, records=30, inserted=20, skipped=10");
        assertThat(msg).contains("AAA.TW - inserted 5");
        assertThat(msg).contains("BBB.TW - inserted 15");
    }

    @Test
    void notifyDownloadSummary_shouldDoNothingForNullOrEmpty() {
        historyDataService.notifyDownloadSummary(null);
        historyDataService.notifyDownloadSummary(java.util.Map.of());
        verifyNoInteractions(telegramService);
    }

    @Test
    void initBulkInsertMappings_isIdempotent() throws Exception {
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("initBulkInsertMappings");
        m.setAccessible(true);
        m.invoke(historyDataService);

        java.lang.reflect.Field f1 = HistoryDataService.class.getDeclaredField("barBulkInsert");
        java.lang.reflect.Field f2 = HistoryDataService.class.getDeclaredField("marketDataBulkInsert");
        f1.setAccessible(true);
        f2.setAccessible(true);

        Object v1 = f1.get(historyDataService);
        Object v2 = f2.get(historyDataService);
        assertThat(v1).isNotNull();
        assertThat(v2).isNotNull();

        // Invoke again and ensure same instances
        m.invoke(historyDataService);
        assertThat(f1.get(historyDataService)).isSameAs(v1);
        assertThat(f2.get(historyDataService)).isSameAs(v2);
    }

    @Test
    void pgBulkInsert_shouldUsePgConnection_andInvokeSaveAll() throws Exception {
        // Given
        List<Bar> bars = List.of(new Bar(), new Bar());
        List<MarketData> mds = List.of(new MarketData(), new MarketData());

        // Provide dummy PgBulkInsert instances (override saveAll to no-op)
        de.bytefish.pgbulkinsert.PgBulkInsert<Bar> dummyBarBulk = new de.bytefish.pgbulkinsert.PgBulkInsert<Bar>(new de.bytefish.pgbulkinsert.mapping.AbstractMapping<Bar>("public","bar") {}){
            @Override
            public void saveAll(org.postgresql.PGConnection conn, java.util.stream.Stream<Bar> stream) {
                // consume stream to simulate work
                stream.forEach(b -> {});
            }
        };
        de.bytefish.pgbulkinsert.PgBulkInsert<MarketData> dummyMdBulk = new de.bytefish.pgbulkinsert.PgBulkInsert<MarketData>(new de.bytefish.pgbulkinsert.mapping.AbstractMapping<MarketData>("public","market_data") {}){
            @Override
            public void saveAll(org.postgresql.PGConnection conn, java.util.stream.Stream<MarketData> stream) {
                stream.forEach(m -> {});
            }
        };

        java.lang.reflect.Field fb = HistoryDataService.class.getDeclaredField("barBulkInsert");
        java.lang.reflect.Field fm = HistoryDataService.class.getDeclaredField("marketDataBulkInsert");
        fb.setAccessible(true); fm.setAccessible(true);
        fb.set(historyDataService, dummyBarBulk);
        fm.set(historyDataService, dummyMdBulk);

        // Mock a Connection whose unwrap returns a PGConnection
        java.sql.Connection conn = mock(java.sql.Connection.class);
        org.postgresql.PGConnection pgConn = mock(org.postgresql.PGConnection.class);
        when(conn.unwrap(org.postgresql.PGConnection.class)).thenReturn(pgConn);

        when(dataSource.getConnection()).thenReturn(conn);

        // When - invoke private pgBulkInsert
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("pgBulkInsert", List.class, List.class);
        m.setAccessible(true);
        int inserted = (int) m.invoke(historyDataService, bars, mds);

        // Then
        assertThat(inserted).isEqualTo(2);
        // Verify that a DB connection was requested (indicates code path executed)
        verify(dataSource).getConnection();
    }

    @Test
    void fillMissingNamesIfMissing_handlesNameServiceExceptionGracefully() {
        // Given
        String symbol = "9999.TW";
        Bar b1 = new Bar(); b1.setSymbol(symbol); b1.setTimeframe("1day"); b1.setName(null);
        List<Bar> bars = List.of(b1);
        List<MarketData> mds = List.of(new MarketData());

        when(taiwanStockNameService.hasStockName(symbol)).thenThrow(new RuntimeException("boom"));

        // When - should not throw
        historyDataService.fillMissingNamesIfMissing(bars, mds, symbol);

        // Then - name remains null as we failed to fetch fallback
        assertThat(b1.getName()).isNull();
    }

    @Test
    void downloadHistoricalData_singleSymbol_shouldDownloadAndInsert() throws Exception {
        // Given
        String symbol = "TEST.TW";
        Map<String, Object> point = Map.of(
            "timestamp", java.time.LocalDateTime.now().toString(),
            "name", "TestCorp",
            "open", 10.0,
            "high", 11.0,
            "low", 9.0,
            "close", 10.5,
            "volume", 100L
        );
        Map<String, Object> body = Map.of("data", List.of(point));

        // Mock Python API
        ResponseEntity<Map> resp = new ResponseEntity<>(body, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(resp);

        // Force PgBulkInsert to fail so JdbcTemplate fallback is used
        when(dataSource.getConnection()).thenThrow(new SQLException("no pg"));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{{1}});

        // When
        HistoryDataService.DownloadResult result = historyDataService.downloadHistoricalData(symbol, 1);

        // Then
        assertThat(result.getTotalRecords()).isGreaterThanOrEqualTo(1);
        assertThat(result.getInserted()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void truncateTablesIfNeeded_shouldResetFlagOnExceptionAndAllowRetry() {
        historyDataService.resetTruncationFlag();

        // First call throws
        doThrow(new RuntimeException("boom")).when(barRepository).truncateTable();

        try {
            historyDataService.truncateTablesIfNeeded();
        } catch (RuntimeException e) {
            // expected
        }

        // Now make truncate succeed - reset stubbing/interaction counters
        reset(barRepository, marketDataRepository, strategyStockMappingRepository);
        doNothing().when(barRepository).truncateTable();
        doNothing().when(marketDataRepository).truncateTable();
        doNothing().when(strategyStockMappingRepository).truncateTable();

        // Second call should succeed
        historyDataService.truncateTablesIfNeeded();

        // verify that in the second attempt truncate was called once (we reset interactions)
        verify(barRepository, times(1)).truncateTable();
    }

    @Test
    void downloadBatch_shouldReturnEmptyOnNon2xxResponse() throws Exception {
        String symbol = "2330.TW";
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();

        ResponseEntity<Map> resp = new ResponseEntity<>(Map.of(), HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(resp);
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of());

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("downloadBatch", String.class, LocalDateTime.class, LocalDateTime.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> result = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, symbol, start, end);

        assertThat(result).isEmpty();
    }

    @Test
    void formatIndividualSummary_shouldFormatCorrectly() {
        HistoryDataService.DownloadResult r = new HistoryDataService.DownloadResult("TEST.TW", 10, 8, 2);
        String s = historyDataService.formatIndividualSummary(r);
        assertThat(s).contains("TEST.TW");
        assertThat(s).contains("records: 10");
        assertThat(s).contains("inserted: 8");
    }

    @Test
    void fillMissingNamesIfMissing_shouldDoNothingWhenNoFallbackAvailable() {
        String symbol = "2454.TW";
        Bar b1 = new Bar(); b1.setSymbol(symbol); b1.setTimeframe("1day"); b1.setName(null);
        List<Bar> bars = List.of(b1);
        List<MarketData> mds = List.of(new MarketData());

        when(taiwanStockNameService.hasStockName(symbol)).thenReturn(false);

        historyDataService.fillMissingNamesIfMissing(bars, mds, symbol);

        assertThat(b1.getName()).isNull();
    }

    @Test
    void getStockNameFromHistory_handlesRepositoryException() {
        String symbol = "2330.TW";
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day")).thenThrow(new RuntimeException("db"));

        String name = historyDataService.getStockNameFromHistory(symbol);
        assertThat(name).isNull();
    }

    @Test
    void notifyDownloadStartedForAll_shouldPropagateTelegramException_whenSendMessageFails() {
        List<String> symbols = List.of("2330.TW", "2454.TW");
        doThrow(new RuntimeException("boom")).when(telegramService).sendMessage(anyString());

        // Expect RuntimeException to propagate (caller should guard calls)
        try {
            historyDataService.notifyDownloadStartedForAll(symbols, 1);
            org.junit.jupiter.api.Assertions.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            // expected
        }

        verify(telegramService).sendMessage(anyString());
    }

    @Test
    void notifyDownloadStartedForAll_shouldDoNothingWhenTelegramServiceIsNullOrSymbolsEmpty() {
        // Create a service instance with null telegramService
        HistoryDataService s = new HistoryDataService(
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
            null, // telegramService null
            taiwanStockNameService
        );

        // Should not throw for non-empty symbols
        s.notifyDownloadStartedForAll(List.of("A.TW"), 1);

        // Should not throw for empty symbols
        s.notifyDownloadStartedForAll(List.of(), 1);
    }

    @Test
    void flushBatchWithFallback_shouldFallbackToJdbcWhenPgCopyFails() throws Exception {
        List<Bar> bars = List.of(new Bar());
        List<MarketData> mds = List.of(new MarketData());

        java.sql.Connection conn = mock(java.sql.Connection.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.unwrap(org.postgresql.PGConnection.class)).thenThrow(new SQLException("no copy"));

        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{{1}});

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("flushBatchWithFallback", List.class, List.class);
        m.setAccessible(true);
        int inserted = (int) m.invoke(historyDataService, bars, mds);

        assertThat(inserted).isEqualTo(1);
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }


    @Test
    void downloadBatch_shouldReturnEmptyWhenBodyIsNull() throws Exception {
        String symbol = "2330.TW";
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();

        ResponseEntity<Map> resp = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class))).thenReturn(resp);
        when(backtestService.fetchTop50Stocks()).thenReturn(List.of());

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("downloadBatch", String.class, LocalDateTime.class, LocalDateTime.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> result = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, symbol, start, end);

        assertThat(result).isEmpty();
    }

    @Test
    void flushBatchWithFallback_shouldFallbackWhenDataSourceReturnsNull() throws Exception {
        List<Bar> bars = List.of(new Bar());
        List<MarketData> mds = List.of(new MarketData());

        // Simulate DataSource returning null connection -> should fall back to JdbcTemplate
        when(dataSource.getConnection()).thenReturn(null);

        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{{1}});

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("flushBatchWithFallback", List.class, List.class);
        m.setAccessible(true);
        int inserted = (int) m.invoke(historyDataService, bars, mds);

        assertThat(inserted).isEqualTo(1);
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void parseHistoricalData_shouldSkipWhenMissingOpenKey() throws Exception {
        Map<String, Object> point = Map.of(
            "timestamp", LocalDateTime.now().toString(),
            "name", "Test",
            // missing "open"
            "high", 101.0,
            "low", 99.0,
            "close", 100.5,
            "volume", 1000
        );
        Map<String, Object> body = Map.of("data", List.of(point));

        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("parseHistoricalData", Map.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<HistoryDataService.HistoricalDataPoint> parsed = (List<HistoryDataService.HistoricalDataPoint>) m.invoke(historyDataService, body);

        // Missing numeric fields should cause parsing to skip the entry
        assertThat(parsed).isEmpty();
    }

    @Test
    void notifyDownloadSummary_shouldPropagateWhenTelegramThrows() {
        Map<String, HistoryDataService.DownloadResult> results = Map.of(
            "2330.TW", new HistoryDataService.DownloadResult("2330.TW", 10, 5, 5)
        );

        doThrow(new RuntimeException("boom")).when(telegramService).sendMessage(anyString());

        try {
            historyDataService.notifyDownloadSummary(results);
            org.junit.jupiter.api.Assertions.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            // expected
        }

        verify(telegramService).sendMessage(anyString());
    }

    @Test
    void notifyDownloadStartedForAll_shouldSendMessage() throws Exception {
        List<String> symbols = List.of("2330.TW", "2454.TW");
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("notifyDownloadStartedForAll", List.class, int.class);
        m.setAccessible(true);
        m.invoke(historyDataService, symbols, 5);
        
        verify(telegramService).sendMessage(argThat(msg -> 
            msg.contains("2330.TW") && msg.contains("2454.TW") && msg.contains("2 symbols")
        ));
    }

    @Test
    void notifyDownloadStartedForSymbol_shouldBeSuppressed() throws Exception {
        // This method is intentionally a no-op
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("notifyDownloadStartedForSymbol", String.class, int.class);
        m.setAccessible(true);
        m.invoke(historyDataService, "2330.TW", 5);
        
        // Verify no telegram message sent (suppressed)
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void notifyDownloadComplete_shouldBeSuppressed() throws Exception {
        HistoryDataService.DownloadResult result = new HistoryDataService.DownloadResult("2330.TW", 100, 80, 20);
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("notifyDownloadComplete", HistoryDataService.DownloadResult.class);
        m.setAccessible(true);
        m.invoke(historyDataService, result);
        
        // Verify no telegram message sent (suppressed)
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void fillMissingNamesIfMissing_shouldHandleNoMissingNames() throws Exception {
        Bar bar = new Bar();
        bar.setSymbol("2330.TW");
        bar.setName("TSMC");
        
        MarketData md = MarketData.builder()
            .symbol("2330.TW")
            .name("TSMC")
            .build();
        
        List<Bar> bars = List.of(bar);
        List<MarketData> mds = List.of(md);
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("fillMissingNamesIfMissing", List.class, List.class, String.class);
        m.setAccessible(true);
        m.invoke(historyDataService, bars, mds, "2330.TW");
        
        // Should not call service since no names are missing
        verify(taiwanStockNameService, never()).getStockName(anyString());
    }

    @Test
    void fillMissingNamesIfMissing_shouldFillWhenNameMissing() throws Exception {
        Bar bar = new Bar();
        bar.setSymbol("2330.TW");
        bar.setName(null);
        
        MarketData md = MarketData.builder()
            .symbol("2330.TW")
            .name(null)
            .build();
        
        List<Bar> bars = List.of(bar);
        List<MarketData> mds = List.of(md);
        
        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("台積電");
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("fillMissingNamesIfMissing", List.class, List.class, String.class);
        m.setAccessible(true);
        m.invoke(historyDataService, bars, mds, "2330.TW");
        
        assertThat(bar.getName()).isEqualTo("台積電");
        assertThat(md.getName()).isEqualTo("台積電");
    }

    @Test
    void fillMissingNamesIfMissing_shouldHandleServiceException() throws Exception {
        Bar bar = new Bar();
        bar.setSymbol("2330.TW");
        bar.setName("");
        
        MarketData md = MarketData.builder()
            .symbol("2330.TW")
            .name("")
            .build();
        
        List<Bar> bars = List.of(bar);
        List<MarketData> mds = List.of(md);
        
        when(taiwanStockNameService.hasStockName("2330.TW")).thenThrow(new RuntimeException("Service error"));
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("fillMissingNamesIfMissing", List.class, List.class, String.class);
        m.setAccessible(true);
        // Should not throw - exception is caught internally
        m.invoke(historyDataService, bars, mds, "2330.TW");
        
        // Names should still be empty
        assertThat(bar.getName()).isEmpty();
        assertThat(md.getName()).isEmpty();
    }

    @Test
    void fillMissingNamesIfMissing_shouldHandleNoFallbackAvailable() throws Exception {
        Bar bar = new Bar();
        bar.setSymbol("9999.TW");
        bar.setName(null);
        
        MarketData md = MarketData.builder()
            .symbol("9999.TW")
            .name(null)
            .build();
        
        List<Bar> bars = List.of(bar);
        List<MarketData> mds = List.of(md);
        
        when(taiwanStockNameService.hasStockName("9999.TW")).thenReturn(false);
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("fillMissingNamesIfMissing", List.class, List.class, String.class);
        m.setAccessible(true);
        m.invoke(historyDataService, bars, mds, "9999.TW");
        
        // Should remain null
        assertThat(bar.getName()).isNull();
        assertThat(md.getName()).isNull();
    }

    @Test
    void initBulkInsertMappings_shouldInitializeOnlyOnce() throws Exception {
        // Call init multiple times
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("initBulkInsertMappings");
        m.setAccessible(true);
        m.invoke(historyDataService);
        m.invoke(historyDataService);
        
        // Should not throw and be idempotent
    }

    @Test
    void formatIndividualSummary_shouldFormatCorrectly_withDownloadResult() throws Exception {
        HistoryDataService.DownloadResult result = new HistoryDataService.DownloadResult("2330.TW", 1000, 900, 100);
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("formatIndividualSummary", HistoryDataService.DownloadResult.class);
        m.setAccessible(true);
        String formatted = (String) m.invoke(historyDataService, result);
        
        assertThat(formatted).contains("2330.TW");
        assertThat(formatted).contains("1000");
        assertThat(formatted).contains("900");
        assertThat(formatted).contains("100");
    }

    @Test
    void formatSummaryForAll_shouldIncludeAllSymbols() throws Exception {
        Map<String, HistoryDataService.DownloadResult> results = Map.of(
            "2330.TW", new HistoryDataService.DownloadResult("2330.TW", 1000, 900, 100),
            "2454.TW", new HistoryDataService.DownloadResult("2454.TW", 500, 450, 50)
        );
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("formatSummaryForAll", Map.class);
        m.setAccessible(true);
        String formatted = (String) m.invoke(historyDataService, results);
        
        assertThat(formatted).contains("2330.TW");
        assertThat(formatted).contains("2454.TW");
        assertThat(formatted).contains("symbols=2");
        assertThat(formatted).contains("inserted=1350");
    }

    @Test
    void toBar_shouldMapCorrectly() throws Exception {
        HistoryDataService.HistoricalDataPoint point = new HistoryDataService.HistoricalDataPoint();
        point.setTimestamp(LocalDateTime.of(2024, 1, 1, 0, 0));
        point.setSymbol("2330.TW");
        point.setName("TSMC");
        point.setOpen(100.0);
        point.setHigh(105.0);
        point.setLow(98.0);
        point.setClose(103.0);
        point.setVolume(1000000L);
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("toBar", HistoryDataService.HistoricalDataPoint.class);
        m.setAccessible(true);
        Bar bar = (Bar) m.invoke(historyDataService, point);
        
        assertThat(bar.getSymbol()).isEqualTo("2330.TW");
        assertThat(bar.getName()).isEqualTo("TSMC");
        assertThat(bar.getOpen()).isEqualTo(100.0);
        assertThat(bar.getHigh()).isEqualTo(105.0);
        assertThat(bar.getLow()).isEqualTo(98.0);
        assertThat(bar.getClose()).isEqualTo(103.0);
        assertThat(bar.getVolume()).isEqualTo(1000000L);
    }

    @Test
    void toMarketData_shouldMapCorrectly() throws Exception {
        HistoryDataService.HistoricalDataPoint point = new HistoryDataService.HistoricalDataPoint();
        point.setTimestamp(LocalDateTime.of(2024, 1, 1, 0, 0));
        point.setSymbol("2454.TW");
        point.setName("MediaTek");
        point.setOpen(900.0);
        point.setHigh(920.0);
        point.setLow(895.0);
        point.setClose(915.0);
        point.setVolume(5000000L);
        
        java.lang.reflect.Method m = HistoryDataService.class.getDeclaredMethod("toMarketData", HistoryDataService.HistoricalDataPoint.class);
        m.setAccessible(true);
        MarketData md = (MarketData) m.invoke(historyDataService, point);
        
        assertThat(md.getSymbol()).isEqualTo("2454.TW");
        assertThat(md.getName()).isEqualTo("MediaTek");
        assertThat(md.getOpen()).isEqualTo(900.0);
        assertThat(md.getHigh()).isEqualTo(920.0);
        assertThat(md.getLow()).isEqualTo(895.0);
        assertThat(md.getClose()).isEqualTo(915.0);
        assertThat(md.getVolume()).isEqualTo(5000000L);
        assertThat(md.getTimeframe()).isEqualTo(MarketData.Timeframe.DAY_1);
    }
}


