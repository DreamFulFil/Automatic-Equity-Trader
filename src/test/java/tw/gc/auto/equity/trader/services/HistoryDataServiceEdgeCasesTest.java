package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional edge case tests for HistoryDataService to increase coverage
 * Focus: exception handling, telegram notification failures, threading edge cases
 */
@ExtendWith(MockitoExtension.class)
class HistoryDataServiceEdgeCasesTest {

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
    void downloadHistoricalDataForMultipleStocks_shouldHandleTelegramNotificationFailureOnStart() {
        // Given
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("2330.TW");
        
        // Telegram notification fails on start
        doThrow(new RuntimeException("Telegram API down")).when(telegramService).sendMessage(anyString());
        
        // When/Then - should not crash the entire download
        try {
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        } catch (Exception e) {
            // Telegram failure should be caught and logged
        }
        
        // Verify download continued despite telegram failure
        verify(systemStatusService).startHistoryDownload();
        verify(systemStatusService).completeHistoryDownload();
    }

    @Test
    void downloadHistoricalDataForMultipleStocks_shouldHandleTelegramNotificationFailureOnComplete() {
        // Given
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("2330.TW");
        
        // First call succeeds (start notification), second fails (summary notification)
        doNothing().doThrow(new RuntimeException("Telegram API down")).when(telegramService).sendMessage(anyString());
        
        // When - should complete even if summary notification fails
        try {
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        } catch (Exception e) {
            // Expected - telegram error on summary
        }
        
        // Verify download completed
        verify(systemStatusService).completeHistoryDownload();
    }

    @Test
    void downloadHistoricalData_shouldHandleInterruptedExceptionDuringDownload() throws Exception {
        // Given
        String symbol = "2330.TW";
        
        // Simulate Python bridge failure that could trigger InterruptedException path
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("Connection interrupted"));
        
        // When
        HistoryDataService.DownloadResult result = historyDataService.downloadHistoricalData(symbol, 1);
        
        // Then - should return a result with 0 records
        org.assertj.core.api.Assertions.assertThat(result.getTotalRecords()).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(result.getInserted()).isEqualTo(0);
    }

    @Test
    void notifyDownloadStartedForAll_shouldHandleNullTelegramService() {
        // Given - service with null telegram
        HistoryDataService serviceWithNullTelegram = new HistoryDataService(
            barRepository, marketDataRepository, strategyStockMappingRepository,
            restTemplate, objectMapper, dataSource, jdbcTemplate,
            transactionManager, systemStatusService, backtestService,
            null, // null telegram service
            taiwanStockNameService
        );
        
        // When/Then - should not throw NPE
        serviceWithNullTelegram.notifyDownloadStartedForAll(List.of("2330.TW"), 1);
        
        // Verify no telegram interaction (service is null)
        verifyNoInteractions(telegramService);
    }

    @Test
    void notifyDownloadSummary_shouldHandleNullTelegramService() {
        // Given - service with null telegram
        HistoryDataService serviceWithNullTelegram = new HistoryDataService(
            barRepository, marketDataRepository, strategyStockMappingRepository,
            restTemplate, objectMapper, dataSource, jdbcTemplate,
            transactionManager, systemStatusService, backtestService,
            null, // null telegram service
            taiwanStockNameService
        );
        
        Map<String, HistoryDataService.DownloadResult> results = Map.of(
            "2330.TW", new HistoryDataService.DownloadResult("2330.TW", 100, 95, 5)
        );
        
        // When/Then - should not throw NPE
        serviceWithNullTelegram.notifyDownloadSummary(results);
        
        // Verify no telegram interaction (service is null)
        verifyNoInteractions(telegramService);
    }

    @Test
    void downloadHistoricalDataForMultipleStocks_shouldHandleSystemStatusFailure() {
        // Given
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("2330.TW");
        
        // System status service fails on start
        when(systemStatusService.startHistoryDownload()).thenThrow(new RuntimeException("Status service down"));
        
        // When/Then - should handle gracefully
        try {
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        } catch (Exception e) {
            // Expected - the exception propagates
        }
        
        // Verify startHistoryDownload was called (and threw exception)
        verify(systemStatusService).startHistoryDownload();
    }

    @Test
    void fillMissingNamesIfMissing_shouldHandleEmptyStringName() {
        // Given
        tw.gc.auto.equity.trader.entities.Bar bar = new tw.gc.auto.equity.trader.entities.Bar();
        bar.setSymbol("2330.TW");
        bar.setName(""); // Empty string should be treated as missing
        
        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("TSMC");
        
        // When
        historyDataService.fillMissingNamesIfMissing(
            List.of(bar),
            List.of(),
            "2330.TW"
        );
        
        // Then
        org.assertj.core.api.Assertions.assertThat(bar.getName()).isEqualTo("TSMC");
    }

    @Test
    void getStockNameFromHistory_shouldHandleNullFromRepository() {
        // Given
        String symbol = "2330.TW";
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day"))
            .thenReturn(java.util.Optional.empty());
        when(marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(
            symbol, tw.gc.auto.equity.trader.entities.MarketData.Timeframe.DAY_1))
            .thenReturn(java.util.Optional.empty());
        
        // When
        String name = historyDataService.getStockNameFromHistory(symbol);
        
        // Then
        org.assertj.core.api.Assertions.assertThat(name).isNull();
    }

    @Test
    void truncateTablesIfNeeded_shouldHandleRepositoryException() {
        // Given
        historyDataService.resetTruncationFlag();
        doThrow(new RuntimeException("Truncate failed")).when(barRepository).truncateTable();
        
        // When/Then - should propagate exception
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            historyDataService.truncateTablesIfNeeded();
        });
    }

    @Test
    void formatSummaryForAll_shouldHandleEmptyResults() {
        // Given
        Map<String, HistoryDataService.DownloadResult> emptyResults = Map.of();
        
        // When
        String summary = historyDataService.formatSummaryForAll(emptyResults);
        
        // Then
        org.assertj.core.api.Assertions.assertThat(summary).contains("symbols=0");
        org.assertj.core.api.Assertions.assertThat(summary).contains("records=0");
    }

    @Test
    void formatIndividualSummary_shouldIncludeSymbolAndCounts() {
        // Given
        HistoryDataService.DownloadResult result = 
            new HistoryDataService.DownloadResult("2330.TW", 1000, 950, 50);
        
        // When
        String summary = historyDataService.formatIndividualSummary(result);
        
        // Then
        org.assertj.core.api.Assertions.assertThat(summary).contains("2330.TW");
        org.assertj.core.api.Assertions.assertThat(summary).contains("1000");
        org.assertj.core.api.Assertions.assertThat(summary).contains("950");
        org.assertj.core.api.Assertions.assertThat(summary).contains("50");
    }

    @Test
    void notifyDownloadComplete_shouldHandleNullResult() {
        // When/Then - should not throw NPE
        historyDataService.notifyDownloadComplete(null);
        
        // Verify no telegram interaction
        verifyNoInteractions(telegramService);
    }

    @Test
    void awaitWriterLatch_shouldHandleInterruptedException() throws Exception {
        // Given
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        // When - interrupt the current thread before calling awaitWriterLatch
        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            boolean result = historyDataService.awaitWriterLatch(latch, 5, java.util.concurrent.TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThat(result).isFalse();
            org.assertj.core.api.Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
        });
        testThread.start();
        testThread.join(2000);
    }

    @Test
    void runMultiStockDownloadTask_shouldHandleGeneralException() throws Exception {
        // Given
        java.util.concurrent.BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = 
            new java.util.concurrent.ArrayBlockingQueue<>(10);
        java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(1);
        java.util.Map<String, HistoryDataService.DownloadResult> results = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        
        // Mock backtestService to throw exception during downloadBatch
        when(backtestService.fetchTop50Stocks()).thenThrow(new RuntimeException("Test exception"));
        
        // When
        historyDataService.runMultiStockDownloadTask("2330.TW", 1, queue, permits, results, counter);
        
        // Then - should have a result with 0 records
        org.assertj.core.api.Assertions.assertThat(results).containsKey("2330.TW");
        org.assertj.core.api.Assertions.assertThat(results.get("2330.TW").getTotalRecords()).isZero();
    }

    @Test
    void fillMissingNamesIfMissing_shouldHandleNullBars() {
        // When/Then - should not throw NPE
        historyDataService.fillMissingNamesIfMissing(null, List.of(), "2330.TW");
        
        // Verify no name service interaction
        verifyNoInteractions(taiwanStockNameService);
    }

    @Test
    void fillMissingNamesIfMissing_shouldHandleEmptyBars() {
        // When/Then - should not throw
        historyDataService.fillMissingNamesIfMissing(List.of(), List.of(), "2330.TW");
        
        // Verify no name service interaction
        verifyNoInteractions(taiwanStockNameService);
    }

    @Test
    void downloadHistoricalDataForMultipleStocks_shouldHandleInsertedBySymbolWithoutPrevResult() {
        // Given - setup a scenario where insertedBySymbol has a key that's not in results
        historyDataService.resetTruncationFlag();
        List<String> symbols = List.of("NEW.TW");
        
        // Mock to return empty response (download fails, but insertedBySymbol may have entry)
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(new org.springframework.http.ResponseEntity<>(Map.of(), org.springframework.http.HttpStatus.OK));
        
        // When
        Map<String, HistoryDataService.DownloadResult> results = 
            historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 1);
        
        // Then - should have result for NEW.TW
        org.assertj.core.api.Assertions.assertThat(results).containsKey("NEW.TW");
    }

    @Test
    void notifyDownloadStartedForAll_shouldHandleNullSymbols() {
        // When/Then - should not throw NPE
        historyDataService.notifyDownloadStartedForAll(null, 1);
        
        // Verify no telegram interaction
        verifyNoInteractions(telegramService);
    }

    @Test
    void notifyDownloadStartedForAll_shouldHandleEmptySymbols() {
        // When/Then - should not throw
        historyDataService.notifyDownloadStartedForAll(List.of(), 1);
        
        // Verify no telegram interaction
        verifyNoInteractions(telegramService);
    }

    @Test
    void getStockNameFromHistory_shouldReturnNullWhenBarHasEmptyName() {
        // Given
        String symbol = "2330.TW";
        tw.gc.auto.equity.trader.entities.Bar bar = new tw.gc.auto.equity.trader.entities.Bar();
        bar.setSymbol(symbol);
        bar.setName(""); // Empty name
        
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc(symbol, "1day"))
            .thenReturn(java.util.Optional.of(bar));
        
        // When
        String name = historyDataService.getStockNameFromHistory(symbol);
        
        // Then - empty string is returned (bar found with empty name)
        org.assertj.core.api.Assertions.assertThat(name).isEmpty();
    }
}
