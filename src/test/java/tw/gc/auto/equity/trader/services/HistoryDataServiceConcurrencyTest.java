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
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.testutil.AsyncTestHelper;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for HistoryDataService concurrency scenarios.
 *
 * Closes remaining coverage gaps for:
 * - Task A3: InterruptedException handling in multi-stock download permit acquisition
 * - Task A4: Writer latch timeout path while still sending summary and completing status
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryDataService - Concurrency Tests")
class HistoryDataServiceConcurrencyTest {

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

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    private HistoryDataService historyDataService;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        objectMapper = mock(ObjectMapper.class);

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

        lenient().when(backtestService.fetchTop50Stocks()).thenReturn(List.of());
        lenient().when(dataSource.getConnection()).thenThrow(new SQLException("boom"));
        lenient().when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenReturn(new int[0][0]);

        stubRestTemplateExchange(10);
    }

    @Test
    @DisplayName("Task A3: Interrupted during semaphore acquire -> thread re-interrupted + zero DownloadResult")
    void taskA3_interruptedDuringAcquire_resultsContainsZeroAndThreadReInterrupted() throws Exception {
        BlockingQueue<HistoryDataService.HistoricalDataPoint> queue = new ArrayBlockingQueue<>(10);
        Semaphore permits = new Semaphore(1);
        Map<String, HistoryDataService.DownloadResult> results = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        AtomicBoolean threadStillInterrupted = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            Thread.currentThread().interrupt();
            historyDataService.runMultiStockDownloadTask("2330.TW", 1, queue, permits, results, counter);
            threadStillInterrupted.set(Thread.currentThread().isInterrupted());
        });

        t.start();
        t.join(2000);

        assertThat(threadStillInterrupted.get()).isTrue();
        assertThat(results).containsKey("2330.TW");
        assertThat(results.get("2330.TW").getTotalRecords()).isZero();

        assertThat(logAppender.list)
            .anyMatch(e -> e.getFormattedMessage().contains("Download interrupted for 2330.TW"));
    }

    @Test
    @DisplayName("Task A4: writerLatch timeout still sends summary + completes history download")
    void taskA4_writerLatchTimeoutStillSendsSummaryAndCompletes() throws Exception {
        // Slow down jdbcBatchInsert so the writer thread won't finish before the (tiny) await timeout.
        lenient().doAnswer(inv -> {
            Thread.sleep(50);
            return new int[0][0];
        }).when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class));

        List<String> symbols = List.of("2330.TW", "2454.TW", "2317.TW");

        Map<String, HistoryDataService.DownloadResult> results = historyDataService
            .downloadHistoricalDataForMultipleStocks(symbols, 1, 1, TimeUnit.MILLISECONDS);

        assertThat(results).hasSize(3);
        assertThat(results.get("2330.TW").getTotalRecords()).isEqualTo(10);
        assertThat(results.get("2454.TW").getTotalRecords()).isEqualTo(10);
        assertThat(results.get("2317.TW").getTotalRecords()).isEqualTo(10);

        // The whole point of this test is that we return before the writer finishes.
        assertThat(results.get("2330.TW").getInserted()).isZero();
        assertThat(results.get("2454.TW").getInserted()).isZero();
        assertThat(results.get("2317.TW").getInserted()).isZero();

        verify(systemStatusService).startHistoryDownload();
        verify(systemStatusService).completeHistoryDownload();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramService, atLeastOnce()).sendMessage(msgCaptor.capture());
        assertThat(String.join("\n", msgCaptor.getAllValues())).contains("Historical download complete");

        assertThat(logAppender.list)
            .anyMatch(e -> e.getLevel().toString().equals("WARN") && e.getFormattedMessage().contains("Global writer timed out"));
    }

    private void stubRestTemplateExchange(int pointsPerCall) {
        lenient().when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                HttpEntity<Map<String, Object>> entity = (HttpEntity<Map<String, Object>>) inv.getArgument(2);
                Map<String, Object> request = entity.getBody();
                String symbol = request == null ? "UNKNOWN" : (String) request.get("symbol");

                Map<String, Object> body = Map.of("data", buildData(symbol, pointsPerCall));
                return new ResponseEntity<>(body, HttpStatus.OK);
            });
    }

    private static List<Map<String, Object>> buildData(String symbol, int points) {
        return java.util.stream.IntStream.range(0, points)
            .mapToObj(i -> Map.<String, Object>of(
                "timestamp", LocalDateTime.now().minusDays(i).toString(),
                "name", "TestStock-" + symbol,
                "open", 100.0,
                "high", 101.0,
                "low", 99.0,
                "close", 100.5,
                "volume", 1000
            ))
            .toList();
    }
}
