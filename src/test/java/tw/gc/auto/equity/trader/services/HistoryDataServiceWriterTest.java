package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.testutil.AsyncTestHelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for HistoryDataService writer threads (runGlobalWriter and runSingleWriter).
 * 
 * Coverage targets:
 * - Task A1: runGlobalWriter with controlled BlockingQueue
 * - Task A2: runSingleWriter with missing stock names
 * 
 * These tests cover lines 140-198, 540-600, and writer logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryDataService - Writer Thread Tests")
class HistoryDataServiceWriterTest {

    @Mock
    private BarRepository barRepository;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private StrategyStockMappingRepository strategyStockMappingRepository;

    @Mock
    private DataSource dataSource;

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
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private HistoryDataService historyDataService;

    @BeforeEach
    void setUp() {
        // Manual constructor injection to avoid RestTemplate and ObjectMapper complexity
        historyDataService = new HistoryDataService(
            barRepository,
            marketDataRepository,
            strategyStockMappingRepository,
            mock(org.springframework.web.client.RestTemplate.class),
            mock(com.fasterxml.jackson.databind.ObjectMapper.class),
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
    @DisplayName("Task A1: runGlobalWriter with 2500 items - verify 2 flushes (2000 + 500)")
    void testRunGlobalWriterWithBatchFlushing() throws Exception {
        // Arrange: Create queue with 2500 historical data points
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(3000);
        AtomicBoolean complete = new AtomicBoolean(false);
        ConcurrentHashMap<String, AtomicInteger> insertedBySymbol = new ConcurrentHashMap<>();
        
        // Add 2500 data points (1500 for symbol A, 1000 for symbol B)
        for (int i = 0; i < 1500; i++) {
            queue.put(createHistoricalDataPoint("2330.TW", LocalDateTime.now().minusDays(i), 100.0 + i));
        }
        for (int i = 0; i < 1000; i++) {
            queue.put(createHistoricalDataPoint("2454.TW", LocalDateTime.now().minusDays(i), 200.0 + i));
        }
        
        // Mock DataSource to return null connection (force JdbcTemplate fallback)
        when(dataSource.getConnection()).thenReturn(null);

        // Mock JdbcTemplate batch operations (overload used by HistoryDataService)
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenReturn(new int[0][0]);

        ArgumentCaptor<Integer> batchSizeCaptor = ArgumentCaptor.forClass(Integer.class);
        
        // Act: Run writer in separate thread
        Thread writerThread = new Thread(() -> {
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
                throw new RuntimeException(e);
            }
        });
        writerThread.start();
        
        // Wait for queue to drain fully
        AsyncTestHelper.waitForAsync(10000, queue::isEmpty);

        // Signal completion
        complete.set(true);
        writerThread.join(10000);

        // Assert: Verify insertedBySymbol map is populated and attributed by symbol
        assertThat(insertedBySymbol).isNotEmpty();
        assertThat(insertedBySymbol.keySet()).containsExactlyInAnyOrder("2330.TW", "2454.TW");
        assertThat(insertedBySymbol.get("2330.TW").get()).isEqualTo(1500);
        assertThat(insertedBySymbol.get("2454.TW").get()).isEqualTo(1000);

        // Verify batch operations were called multiple times and include batch sizes 2000 + 500
        verify(jdbcTemplate, atLeast(4))
            .batchUpdate(anyString(), anyList(), batchSizeCaptor.capture(), any(ParameterizedPreparedStatementSetter.class));
        assertThat(batchSizeCaptor.getAllValues()).contains(2000, 500);
    }

    @Test
    @DisplayName("Task A2: runSingleWriter with missing stock names - verify fillMissingNamesIfMissing")
    void testRunSingleWriterWithMissingNames() throws Exception {
        // Arrange: Create queue with data points missing names
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(3000);
        AtomicBoolean complete = new AtomicBoolean(false);
        
        // Add 2100 points to trigger batch flush (BULK_INSERT_BATCH_SIZE = 2000)
        for (int i = 0; i < 2100; i++) {
            HistoryDataService.HistoricalDataPoint point = createHistoricalDataPoint(
                "2330.TW", 
                LocalDateTime.now().minusDays(i), 
                100.0 + i
            );
            point.setName(null); // Missing name
            queue.put(point);
        }
        
        // Mock TaiwanStockNameService
        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("Taiwan Semiconductor");
        
        // Mock JdbcTemplate (overload used by HistoryDataService)
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenReturn(new int[0][0]);
        
        // Act: Run writer using reflection
        Thread writerThread = new Thread(() -> {
            try {
                java.lang.reflect.Method method = HistoryDataService.class.getDeclaredMethod(
                    "runSingleWriter",
                    String.class,
                    BlockingQueue.class,
                    AtomicBoolean.class
                );
                method.setAccessible(true);
                method.invoke(historyDataService, "2330.TW", queue, complete);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writerThread.start();
        
        // Wait for processing
        AsyncTestHelper.waitForAsync(10000, queue::isEmpty);
        complete.set(true);
        writerThread.join(10000);
        
        // Assert: Verify fillMissingNamesIfMissing was called (indirectly via service call)
        verify(taiwanStockNameService, atLeast(1)).hasStockName("2330.TW");
        verify(taiwanStockNameService, atLeast(1)).getStockName("2330.TW");
        
        // Verify batch insert was called
        verify(jdbcTemplate, atLeast(2))
            .batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("Task A2b: Test fillMissingNamesIfMissing directly with package-private access")
    void testFillMissingNamesDirectly() {
        // Arrange: Create bars and market data with missing names
        var bars = new java.util.ArrayList<tw.gc.auto.equity.trader.entities.Bar>();
        var marketDataList = new java.util.ArrayList<tw.gc.auto.equity.trader.entities.MarketData>();
        
        for (int i = 0; i < 5; i++) {
            bars.add(tw.gc.auto.equity.trader.entities.Bar.builder()
                .symbol("2330.TW")
                .name(null)
                .timestamp(LocalDateTime.now().minusDays(i))
                .open(100.0)
                .high(102.0)
                .low(98.0)
                .close(101.0)
                .volume(1000L)
                .build());
                
            marketDataList.add(tw.gc.auto.equity.trader.entities.MarketData.builder()
                .symbol("2330.TW")
                .name(null)
                .timestamp(LocalDateTime.now().minusDays(i))
                .open(100.0)
                .high(102.0)
                .low(98.0)
                .close(101.0)
                .volume(1000L)
                .build());
        }
        
        // Mock service
        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("Taiwan Semiconductor");
        
        // Act: Call package-private method
        historyDataService.fillMissingNamesIfMissing(bars, marketDataList, "2330.TW");
        
        // Assert: All names should be filled
        assertThat(bars).allMatch(bar -> "Taiwan Semiconductor".equals(bar.getName()));
        assertThat(marketDataList).allMatch(md -> "Taiwan Semiconductor".equals(md.getName()));
        
        verify(taiwanStockNameService).hasStockName("2330.TW");
        verify(taiwanStockNameService).getStockName("2330.TW");
    }

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
}
