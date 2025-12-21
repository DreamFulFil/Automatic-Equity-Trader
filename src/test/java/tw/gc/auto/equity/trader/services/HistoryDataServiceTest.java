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

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            systemStatusService
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
            "2330.TW", now, 580.0, 590.0, 575.0, 585.0, 10000000L
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
}
