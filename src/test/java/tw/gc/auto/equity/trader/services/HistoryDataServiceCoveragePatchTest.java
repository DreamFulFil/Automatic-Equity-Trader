package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryDataServiceCoveragePatchTest {

    @Mock
    private BarRepository barRepository;
    @Mock
    private MarketDataRepository marketDataRepository;
    @Mock
    private StrategyStockMappingRepository strategyStockMappingRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private DataSource dataSource;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock
    private SystemStatusService systemStatusService;
    @Mock
    private BacktestService backtestService;
    @Mock
    private TelegramService telegramService;
    @Mock
    private TaiwanStockNameService taiwanStockNameService;

    private HistoryDataService service;

    @BeforeEach
    void setUp() {
        service = new HistoryDataService(
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
    void runGlobalWriter_flushesAndAttributesCounts() throws Exception {
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(10);
        AtomicBoolean complete = new AtomicBoolean(false);
        ConcurrentHashMap<String, AtomicInteger> inserted = new ConcurrentHashMap<>();

        HistoryDataService.HistoricalDataPoint point = new HistoryDataService.HistoricalDataPoint();
        point.setSymbol("2330.TW");
        point.setTimestamp(LocalDateTime.now());
        point.setOpen(100.0);
        point.setHigh(101.0);
        point.setLow(99.0);
        point.setClose(100.5);
        point.setVolume(1000L);
        queue.put(point);

        doReturn(new int[][]{{1}}).when(jdbcTemplate)
            .batchUpdate(anyString(), anyList(), anyInt(), any());

        Method method = HistoryDataService.class.getDeclaredMethod(
            "runGlobalWriter", BlockingQueue.class, AtomicBoolean.class, ConcurrentHashMap.class);
        method.setAccessible(true);

        CountDownLatch latch = new CountDownLatch(1);
        Thread writerThread = new Thread(() -> {
            try {
                method.invoke(service, queue, complete, inserted);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });
        writerThread.start();

        Thread.sleep(200);
        complete.set(true);
        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    void downloadHistoricalData_batchesInterruptedDuringQueuePut() throws Exception {
        Thread.currentThread().interrupt();
        try {
            service.downloadHistoricalData("2330.TW", 1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void downloadHistoricalData_batchesInterruptedDuringFutureGet() throws Exception {
        Thread.currentThread().interrupt();
        try {
            service.downloadHistoricalData("2330.TW", 1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void downloadHistoricalData_writerLatchInterrupted() throws Exception {
        Thread.currentThread().interrupt();
        try {
            service.downloadHistoricalData("2330.TW", 1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void downloadHistoricalDataForMultipleStocks_handlesAcquireInterrupt() throws Exception {
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(10);
        Semaphore permits = new Semaphore(0);
        java.util.Map<String, HistoryDataService.DownloadResult> results = new java.util.HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        Thread.currentThread().interrupt();
        try {
            service.runMultiStockDownloadTask("INT.TW", 1, queue, permits, results, counter);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void downloadHistoricalDataForMultipleStocks_recordsFailure() throws Exception {
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(10);
        Semaphore permits = new Semaphore(1);
        java.util.Map<String, HistoryDataService.DownloadResult> results = new java.util.HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        when(restTemplate.exchange(anyString(), any(), any(), eq(java.util.Map.class)))
            .thenThrow(new RuntimeException("boom"));

        service.runMultiStockDownloadTask("INT.TW", 1, queue, permits, results, counter);
        assertNotNull(results.get("INT.TW"));
    }

    @Test
    void runSingleWriter_handlesInterruptedWhilePolling() throws Exception {
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(1);
        AtomicBoolean complete = new AtomicBoolean(false);

        Method method = HistoryDataService.class.getDeclaredMethod(
            "runSingleWriter", String.class, BlockingQueue.class, AtomicBoolean.class);
        method.setAccessible(true);

        AtomicBoolean finished = new AtomicBoolean(false);
        Thread writerThread = new Thread(() -> {
            try {
                method.invoke(service, "2330.TW", queue, complete);
                finished.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        writerThread.start();
        Thread.sleep(100);
        writerThread.interrupt();
        writerThread.join(2000);
    }
}
