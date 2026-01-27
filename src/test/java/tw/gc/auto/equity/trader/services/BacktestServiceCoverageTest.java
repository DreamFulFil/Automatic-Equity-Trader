package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Additional coverage tests for BacktestService.
 * Targets lines: 154-155, 227-228, 528-530, 535-536, 638-639, 735-736, 764-766, 784, 786-787,
 * 829, 831-832, 846, 877-878, 920, 944-945, 947-949, 951-953, 956-957, 960-962,
 * 1022-1039, 1041-1043, 1057-1065, 1068-1069
 */
@ExtendWith(MockitoExtension.class)
class BacktestServiceCoverageTest {

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private HistoryDataService historyDataService;

    @Mock
    private SystemStatusService systemStatusService;

    @Mock
    private DataSource dataSource;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StrategyStockMappingService strategyStockMappingService;

    @Mock
    private FundamentalDataService fundamentalDataService;

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        backtestService = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        );
    }

    // =========================================================================
    // Lines 154-155: Exception handling in backtest simulation loop
    // =========================================================================
    @Test
    void runBacktest_shouldCatchExceptionInStrategy_andContinue() {
        // Strategy that throws exception
        IStrategy throwingStrategy = new IStrategy() {
            @Override
            public String getName() { return "ThrowingStrategy"; }
            @Override
            public StrategyType getType() { return StrategyType.SHORT_TERM; }
            @Override
            public void reset() {}
            @Override
            public TradeSignal execute(Portfolio p, MarketData d) {
                throw new RuntimeException("Strategy error");
            }
        };

        List<MarketData> history = List.of(
            MarketData.builder()
                .symbol("TEST.TW")
                .timestamp(LocalDateTime.now())
                .close(100.0)
                .build()
        );

        // Should not throw, should handle exception gracefully
        Map<String, BacktestService.InMemoryBacktestResult> results = 
            backtestService.runBacktest(List.of(throwingStrategy), history, 10000.0);

        assertNotNull(results);
        assertTrue(results.containsKey("ThrowingStrategy"));
    }

    // =========================================================================
    // Lines 227-228: Exception handling in updateStrategyStockMapping
    // =========================================================================
    @Test
    void runBacktest_shouldHandleExceptionInStrategyStockMappingUpdate() {
        doThrow(new RuntimeException("Mapping update failed"))
            .when(strategyStockMappingService).updateMapping(
                anyString(), anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any()
            );

        IStrategy strategy = mock(IStrategy.class);
        when(strategy.getName()).thenReturn("TestStrategy");
        when(strategy.execute(any(), any())).thenReturn(TradeSignal.neutral("hold"));

        List<MarketData> history = List.of(
            MarketData.builder()
                .symbol("TEST.TW")
                .timestamp(LocalDateTime.now())
                .close(100.0)
                .build()
        );

        // Should not throw, should handle exception gracefully (logs debug message)
        Map<String, BacktestService.InMemoryBacktestResult> results = 
            backtestService.runBacktest(List.of(strategy), history, 10000.0);

        assertNotNull(results);
    }

    // =========================================================================
    // Lines 528-530, 535-536: TAIEX components parsing with valid 4-digit symbols
    // =========================================================================
    @Test
    void fetchTAIEXComponents_shouldParseValidFourDigitSymbols() throws Exception {
        Document taiexDoc = Jsoup.parse("""
            <html><body>
              <table>
                <tr><td>2330</td><td>TSMC</td></tr>
                <tr><td>2317</td><td>Hon Hai</td></tr>
              </table>
            </body></html>
            """);

        Connection taiexConn = mock(Connection.class);
        when(taiexConn.userAgent(anyString())).thenReturn(taiexConn);
        when(taiexConn.timeout(anyInt())).thenReturn(taiexConn);
        when(taiexConn.get()).thenReturn(taiexDoc);

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(taiexConn);

            Method method = BacktestService.class.getDeclaredMethod("fetchTAIEXComponents");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<BacktestService.StockCandidate> candidates = (Set<BacktestService.StockCandidate>) method.invoke(backtestService);

            assertEquals(2, candidates.size());
            assertTrue(candidates.stream().anyMatch(c -> c.getSymbol().equals("2330.TW")));
            assertTrue(candidates.stream().anyMatch(c -> c.getSymbol().equals("2317.TW")));
        }
    }

    @Test
    void fetchTAIEXComponents_shouldReturnEmptyOnException() throws Exception {
        Connection taiexConn = mock(Connection.class);
        when(taiexConn.userAgent(anyString())).thenReturn(taiexConn);
        when(taiexConn.timeout(anyInt())).thenReturn(taiexConn);
        when(taiexConn.get()).thenThrow(new IOException("Connection failed"));

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(taiexConn);

            Method method = BacktestService.class.getDeclaredMethod("fetchTAIEXComponents");
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<BacktestService.StockCandidate> candidates = (Set<BacktestService.StockCandidate>) method.invoke(backtestService);

            assertTrue(candidates.isEmpty());
        }
    }

    // =========================================================================
    // Lines 638-639: StockCandidate equals method
    // =========================================================================
    @Test
    void stockCandidate_equals_shouldReturnTrueForSameSymbol() {
        BacktestService.StockCandidate c1 = new BacktestService.StockCandidate("2330.TW", "TSMC", 1000.0, 500.0, "TWSE");
        BacktestService.StockCandidate c2 = new BacktestService.StockCandidate("2330.TW", "Different Name", 0.0, 0.0, "Other");

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void stockCandidate_equals_shouldReturnFalseForDifferentSymbol() {
        BacktestService.StockCandidate c1 = new BacktestService.StockCandidate("2330.TW", "TSMC", 1000.0, 500.0, "TWSE");
        BacktestService.StockCandidate c2 = new BacktestService.StockCandidate("2317.TW", "Hon Hai", 1000.0, 500.0, "TWSE");

        assertNotEquals(c1, c2);
    }

    @Test
    void stockCandidate_equals_shouldReturnTrueForSameInstance() {
        BacktestService.StockCandidate c1 = new BacktestService.StockCandidate("2330.TW", "TSMC", 1000.0, 500.0, "TWSE");
        assertEquals(c1, c1);
    }

    @Test
    void stockCandidate_equals_shouldReturnFalseForNull() {
        BacktestService.StockCandidate c1 = new BacktestService.StockCandidate("2330.TW", "TSMC", 1000.0, 500.0, "TWSE");
        assertNotEquals(null, c1);
    }

    @Test
    void stockCandidate_equals_shouldReturnFalseForDifferentClass() {
        BacktestService.StockCandidate c1 = new BacktestService.StockCandidate("2330.TW", "TSMC", 1000.0, 500.0, "TWSE");
        assertNotEquals("2330.TW", c1);
    }

    @Test
    void stockCandidate_getScore_shouldCalculateCompositeScore() {
        BacktestService.StockCandidate c = new BacktestService.StockCandidate("2330.TW", "TSMC", 1000.0, 500.0, "TWSE");
        double expectedScore = (1000.0 * 0.7) + (500.0 * 0.3);
        assertEquals(expectedScore, c.getScore(), 0.01);
    }

    @Test
    void fetchTop50Stocks_shouldFallbackWhenNoQualifiedCandidates() throws Exception {
        BacktestService svc = spy(backtestService);
        doReturn(Set.of(new BacktestService.StockCandidate("BAD", "Bad", 0, 0, "TWSE")))
            .when(svc).fetchFromTWSE();
        doReturn(Set.of()).when(svc).fetchFromYahooFinanceTW();
        doReturn(Set.of()).when(svc).fetchTAIEXComponents();

        List<String> result = svc.fetchTop50Stocks();

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void fetchTop50Stocks_shouldReturnFallback_whenFetchThrows() {
        BacktestService svc = spy(backtestService);
        doThrow(new RuntimeException("fetch failed")).when(svc).fetchFromTWSE();

        List<String> result = svc.fetchTop50Stocks();

        assertNotNull(result);
        assertEquals(50, result.size());
    }

    // =========================================================================
    // Lines 846: Empty history symbol extraction
    // =========================================================================
    @Test
    void runBacktestCompute_shouldUseUnknownSymbol_whenHistoryIsEmpty() throws Exception {
        IStrategy strategy = mock(IStrategy.class);
        when(strategy.getName()).thenReturn("TestStrategy");
        lenient().when(strategy.execute(any(), any())).thenReturn(TradeSignal.neutral("hold"));

        Method method = BacktestService.class.getDeclaredMethod("runBacktestCompute", 
            List.class, List.class, double.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, BacktestService.InMemoryBacktestResult> results = 
            (Map<String, BacktestService.InMemoryBacktestResult>) method.invoke(
                backtestService, List.of(strategy), List.of(), 10000.0);

        assertNotNull(results);
        assertTrue(results.containsKey("TestStrategy"));
    }

    // =========================================================================
    // Lines 877-878: Exception handling in runBacktestCompute simulation loop
    // =========================================================================
    @Test
    void runBacktestCompute_shouldCatchException_andContinue() throws Exception {
        IStrategy throwingStrategy = new IStrategy() {
            @Override
            public String getName() { return "ThrowingStrategy"; }
            @Override
            public StrategyType getType() { return StrategyType.SHORT_TERM; }
            @Override
            public void reset() {}
            @Override
            public TradeSignal execute(Portfolio p, MarketData d) {
                throw new RuntimeException("Strategy error");
            }
        };

        List<MarketData> history = List.of(
            MarketData.builder()
                .symbol("TEST.TW")
                .timestamp(LocalDateTime.now())
                .close(100.0)
                .build()
        );

        Method method = BacktestService.class.getDeclaredMethod("runBacktestCompute", 
            List.class, List.class, double.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, BacktestService.InMemoryBacktestResult> results = 
            (Map<String, BacktestService.InMemoryBacktestResult>) method.invoke(
                backtestService, List.of(throwingStrategy), history, 10000.0);

        assertNotNull(results);
        assertTrue(results.containsKey("ThrowingStrategy"));
    }

    // =========================================================================
    // Lines 920: avgProfitPerTrade calculation
    // =========================================================================
    @Test
    void buildBacktestResultEntity_shouldCalculateAvgProfitPerTrade() throws Exception {
        when(historyDataService.getStockNameFromHistory(anyString())).thenReturn("Test Stock");

        BacktestService.InMemoryBacktestResult inMemoryResult = new BacktestService.InMemoryBacktestResult("TestStrategy", 10000.0);
        inMemoryResult.addTrade(100.0);
        inMemoryResult.addTrade(200.0);
        inMemoryResult.addTrade(-50.0);
        inMemoryResult.setFinalEquity(10250.0);
        inMemoryResult.calculateMetrics();

        Method method = BacktestService.class.getDeclaredMethod("buildBacktestResultEntity",
            String.class, String.class, String.class, BacktestService.InMemoryBacktestResult.class,
            LocalDateTime.class, LocalDateTime.class, int.class);
        method.setAccessible(true);

        BacktestResult entity = (BacktestResult) method.invoke(backtestService,
            "BT-123", "TEST.TW", "TestStrategy", inMemoryResult,
            LocalDateTime.now().minusDays(10), LocalDateTime.now(), 100);

        assertNotNull(entity);
        assertEquals(83.33, entity.getAvgProfitPerTrade(), 0.01); // 250 / 3 = 83.33
    }

    @Test
    void buildBacktestResultEntity_shouldReturnZeroAvgProfitPerTrade_whenNoTrades() throws Exception {
        when(historyDataService.getStockNameFromHistory(anyString())).thenReturn("Test Stock");

        BacktestService.InMemoryBacktestResult inMemoryResult = new BacktestService.InMemoryBacktestResult("TestStrategy", 10000.0);
        inMemoryResult.setFinalEquity(10000.0);
        inMemoryResult.calculateMetrics();

        Method method = BacktestService.class.getDeclaredMethod("buildBacktestResultEntity",
            String.class, String.class, String.class, BacktestService.InMemoryBacktestResult.class,
            LocalDateTime.class, LocalDateTime.class, int.class);
        method.setAccessible(true);

        BacktestResult entity = (BacktestResult) method.invoke(backtestService,
            "BT-123", "TEST.TW", "TestStrategy", inMemoryResult,
            LocalDateTime.now().minusDays(10), LocalDateTime.now(), 100);

        assertNotNull(entity);
        assertEquals(0.0, entity.getAvgProfitPerTrade(), 0.01);
    }

    // =========================================================================
    // Lines 944-962: runBacktestResultWriter batch flush and interrupt handling
    // =========================================================================
    @Test
    void runBacktestResultWriter_shouldFlushBatchWhenFull() {
        // Create a service with mocked jdbc for batch insert
        BacktestService svc = spy(backtestService);
        
        // Create a queue and add results to exceed batch size
        BlockingQueue<BacktestResult> queue = new LinkedBlockingQueue<>();
        AtomicBoolean complete = new AtomicBoolean(false);

        // Add exactly BULK_INSERT_BATCH_SIZE (500) items to trigger batch flush
        for (int i = 0; i < 500; i++) {
            queue.offer(BacktestResult.builder()
                .backtestRunId("BT-" + i)
                .symbol("TEST.TW")
                .strategyName("S")
                .build());
        }
        complete.set(true);

        // Mock the jdbc batch insert to avoid actual DB calls
        doReturn(new int[][]{new int[500]}).when(jdbcTemplate)
            .batchUpdate(anyString(), anyList(), anyInt(), any());

        int inserted = svc.runBacktestResultWriter(queue, complete);

        // Should have inserted 500 items
        assertEquals(500, inserted);
    }

    @Test
    void runBacktestResultWriter_shouldFlushOnTimeout() throws Exception {
        BacktestService svc = spy(backtestService);
        
        BlockingQueue<BacktestResult> queue = new LinkedBlockingQueue<>();
        AtomicBoolean complete = new AtomicBoolean(false);

        // Add just a few items (less than batch size)
        for (int i = 0; i < 5; i++) {
            queue.offer(BacktestResult.builder()
                .backtestRunId("BT-" + i)
                .symbol("TEST.TW")
                .strategyName("S")
                .build());
        }

        // Mock the jdbc batch insert
        doReturn(new int[][]{new int[5]}).when(jdbcTemplate)
            .batchUpdate(anyString(), anyList(), anyInt(), any());

        // Start writer in a thread
        Thread writerThread = new Thread(() -> {
            svc.runBacktestResultWriter(queue, complete);
        });
        writerThread.start();

        // Wait for timeout flush (FLUSH_TIMEOUT_MS = 1000)
        Thread.sleep(1500);
        complete.set(true);
        writerThread.join(5000);

        // Items should be flushed due to timeout
        assertTrue(queue.isEmpty());
    }

    @Test
    void runBacktestResultWriter_shouldLogProgress_whenBatchInserted() {
        BacktestService svc = spy(backtestService);
        
        BlockingQueue<BacktestResult> queue = new LinkedBlockingQueue<>();
        AtomicBoolean complete = new AtomicBoolean(false);

        // Add 1000+ items to trigger logging (totalInserted % 1_000 == 0)
        for (int i = 0; i < 1001; i++) {
            queue.offer(BacktestResult.builder()
                .backtestRunId("BT-" + i)
                .symbol("TEST.TW")
                .strategyName("S")
                .build());
        }
        complete.set(true);

        doReturn(new int[][]{new int[500]}).when(jdbcTemplate)
            .batchUpdate(anyString(), anyList(), anyInt(), any());

        int inserted = svc.runBacktestResultWriter(queue, complete);

        assertTrue(inserted >= 1000);
    }

    // =========================================================================
    // Lines 1022-1043: jdbcBatchInsertBacktestResults with null field handling
    // =========================================================================
    @Test
    void jdbcBatchInsertBacktestResults_shouldHandleNullFields() throws Exception {
        BacktestResult r = BacktestResult.builder()
            .backtestRunId("BT-1")
            .symbol("TEST.TW")
            .stockName("Test")
            .strategyName("S")
            // All numeric fields null
            .initialCapital(null)
            .finalEquity(null)
            .totalReturnPct(null)
            .sharpeRatio(null)
            .maxDrawdownPct(null)
            .totalTrades(null)
            .winningTrades(null)
            .winRatePct(null)
            .avgProfitPerTrade(null)
            .dataPoints(null)
            .createdAt(null)
            .build();

        doReturn(new int[][]{new int[]{1}}).when(jdbcTemplate)
            .batchUpdate(anyString(), anyList(), anyInt(), any());

        Method method = BacktestService.class.getDeclaredMethod("jdbcBatchInsertBacktestResults", List.class);
        method.setAccessible(true);

        int inserted = (int) method.invoke(backtestService, List.of(r));

        assertEquals(1, inserted);
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void jdbcBatchInsertBacktestResults_shouldReturnZeroOnException() throws Exception {
        BacktestResult r = BacktestResult.builder()
            .backtestRunId("BT-1")
            .symbol("TEST.TW")
            .stockName("Test")
            .strategyName("S")
            .build();

        doThrow(new RuntimeException("DB error")).when(jdbcTemplate)
            .batchUpdate(anyString(), anyList(), anyInt(), any());

        Method method = BacktestService.class.getDeclaredMethod("jdbcBatchInsertBacktestResults", List.class);
        method.setAccessible(true);

        int inserted = (int) method.invoke(backtestService, List.of(r));

        assertEquals(0, inserted);
    }

    // =========================================================================
    // Lines 1057-1069: BacktestResultBulkInsertMapping null handling
    // =========================================================================
    @Test
    void backtestResultBulkInsertMapping_shouldHandleNullValues() throws Exception {
        // Create instance of the inner mapping class
        Class<?> mappingClass = Class.forName(
            "tw.gc.auto.equity.trader.services.BacktestService$BacktestResultBulkInsertMapping");
        Object mapping = mappingClass.getDeclaredConstructor().newInstance();

        assertNotNull(mapping);
    }

    @Test
    void backtestResultBulkInsertMapping_shouldWriteNullDefaults() throws Exception {
        BacktestResult result = BacktestResult.builder()
            .backtestRunId("BT-1")
            .symbol("TEST.TW")
            .stockName("Test")
            .strategyName("S")
            .initialCapital(null)
            .finalEquity(null)
            .totalReturnPct(null)
            .sharpeRatio(null)
            .maxDrawdownPct(null)
            .totalTrades(null)
            .winningTrades(null)
            .winRatePct(null)
            .avgProfitPerTrade(null)
            .dataPoints(null)
            .createdAt(null)
            .build();

        BacktestService.BacktestResultBulkInsertMapping mapping =
            new BacktestService.BacktestResultBulkInsertMapping();

        var columns = mapping.getColumns();
        var out = new java.io.ByteArrayOutputStream();
        var writer = new de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter(out);
        writer.startRow(columns.size());
        for (var col : columns) {
            col.getWrite().accept(writer, result);
        }
        writer.close();

        assertEquals(39, columns.size());
    }

    @Test
    void backtestResultBulkInsertMapping_shouldWriteNonNullValues() throws Exception {
        BacktestResult result = BacktestResult.builder()
            .backtestRunId("BT-2")
            .symbol("TEST2.TW")
            .stockName("Test2")
            .strategyName("S2")
            .initialCapital(1000.0)
            .commissionRate(0.005)
            .slippageRate(0.001)
            .backtestPeriodStart(LocalDateTime.now().minusDays(30))
            .backtestPeriodEnd(LocalDateTime.now())
            .durationDays(30)
            .exposureTimePct(75.0)
            .finalEquity(1100.0)
            .equityPeak(1150.0)
            .totalReturnPct(10.0)
            .buyHoldReturnPct(8.0)
            .annualReturnPct(120.0)
            .annualVolatilityPct(25.0)
            .sharpeRatio(1.2)
            .sortinoRatio(1.5)
            .calmarRatio(2.0)
            .maxDrawdownPct(5.0)
            .avgDrawdownPct(2.5)
            .maxDrawdownDurationDays(5)
            .avgDrawdownDurationDays(2.5)
            .totalTrades(12)
            .winningTrades(7)
            .losingTrades(5)
            .winRatePct(58.3)
            .bestTradePct(15.0)
            .worstTradePct(-8.0)
            .avgTradePct(3.5)
            .avgProfitPerTrade(12.5)
            .maxTradeDurationDays(10)
            .avgTradeDurationDays(5.5)
            .profitFactor(2.5)
            .expectancy(50.0)
            .sqn(2.0)
            .dataPoints(200)
            .createdAt(LocalDateTime.now())
            .build();

        BacktestService.BacktestResultBulkInsertMapping mapping =
            new BacktestService.BacktestResultBulkInsertMapping();

        var columns = mapping.getColumns();
        var out = new java.io.ByteArrayOutputStream();
        var writer = new de.bytefish.pgbulkinsert.pgsql.PgBinaryWriter(out);
        writer.startRow(columns.size());
        for (var col : columns) {
            col.getWrite().accept(writer, result);
        }
        writer.close();

        assertEquals(39, columns.size());
    }

    @Test
    void runBacktestResultWriter_shouldHandleInterruptedPoll() throws Exception {
        @SuppressWarnings("unchecked")
        BlockingQueue<BacktestResult> queue = mock(BlockingQueue.class);
        when(queue.poll(anyLong(), any())).thenThrow(new InterruptedException("boom"));
        int inserted = backtestService.runBacktestResultWriter(queue, new AtomicBoolean(false));
        assertEquals(0, inserted);
        assertTrue(Thread.interrupted());
    }

    static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown = false;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    // =========================================================================
    // Lines 829, 831-832: Queue offer timeout and interruption in runBacktestForStock
    // =========================================================================
    @Test
    void runBacktestForStock_shouldHandleQueueOfferTimeout() throws Exception {
        when(historyDataService.getStockNameFromHistory(anyString())).thenReturn("Test Stock");
        
        List<MarketData> history = List.of(
            MarketData.builder()
                .symbol("TEST.TW")
                .timestamp(LocalDateTime.now())
                .close(100.0)
                .build()
        );
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any())).thenReturn(history);

        IStrategy strategy = mock(IStrategy.class);
        when(strategy.getName()).thenReturn("TestStrategy");
        when(strategy.execute(any(), any())).thenReturn(TradeSignal.neutral("hold"));

        // Create a full queue that will reject offers
        BlockingQueue<BacktestResult> fullQueue = new ArrayBlockingQueue<>(1);
        fullQueue.offer(BacktestResult.builder().backtestRunId("existing").build());

        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktestForStock(
            "TEST.TW", List.of(strategy), 10000.0, "BT-123",
            LocalDateTime.now().minusDays(10), LocalDateTime.now(), fullQueue
        );

        assertNotNull(results);
    }

    @Test
    void runBacktestForStock_shouldHandleQueueOfferInterrupted() throws Exception {
        when(historyDataService.getStockNameFromHistory(anyString())).thenReturn("Test Stock");

        List<MarketData> history = List.of(
            MarketData.builder()
                .symbol("TEST.TW")
                .timestamp(LocalDateTime.now())
                .close(100.0)
                .build()
        );
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any())).thenReturn(history);

        IStrategy strategy = mock(IStrategy.class);
        when(strategy.getName()).thenReturn("TestStrategy");
        when(strategy.execute(any(), any())).thenReturn(TradeSignal.neutral("hold"));

        @SuppressWarnings("unchecked")
        BlockingQueue<BacktestResult> queue = mock(BlockingQueue.class);
        when(queue.offer(any(), anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("interrupt"));

        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktestForStock(
            "TEST.TW", List.of(strategy), 10000.0, "BT-123",
            LocalDateTime.now().minusDays(10), LocalDateTime.now(), queue
        );

        assertNotNull(results);
        assertTrue(Thread.interrupted());
    }

    // =========================================================================
    // Lines 735-736, 764-766, 784, 786-787: Parallelized backtest exception handling
    // =========================================================================
    @Test
    void runParallelizedBacktest_shouldHandleWriterThreadException() throws Exception {
        when(systemStatusService.startBacktest()).thenReturn(true);
        
        // Return empty list to quickly complete
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(0L);

        IStrategy strategy = mock(IStrategy.class);
        lenient().when(strategy.getName()).thenReturn("TestStrategy");

        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results = 
            backtestService.runParallelizedBacktest(List.of(strategy), 10000.0);

        assertNotNull(results);
        verify(systemStatusService).completeBacktest();
    }

    @Test
    void runParallelizedBacktest_shouldCatchWriterThreadFailure() {
        BacktestService svc = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        ) {
            @Override
            public List<String> fetchTop50Stocks() {
                return List.of("TEST.TW");
            }

            @Override
            protected int runBacktestResultWriter(BlockingQueue<BacktestResult> queue, AtomicBoolean complete) {
                throw new RuntimeException("writer boom");
            }

            @Override
            protected Map<String, InMemoryBacktestResult> runBacktestForStock(
                    String symbol, List<IStrategy> strategies, double initialCapital,
                    String backtestRunId, LocalDateTime periodStart, LocalDateTime periodEnd,
                    BlockingQueue<BacktestResult> resultQueue) {
                return new HashMap<>();
            }
        };
        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(1L);

        IStrategy strategy = mock(IStrategy.class);
        lenient().when(strategy.getName()).thenReturn("TestStrategy");

        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
            svc.runParallelizedBacktest(List.of(strategy), 10000.0);

        assertNotNull(results);
        verify(systemStatusService).completeBacktest();
    }

    @Test
    void runParallelizedBacktest_shouldHandleInterruptedAcquire() {
        BacktestService svc = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        ) {
            @Override
            public List<String> fetchTop50Stocks() {
                return List.of("TEST.TW");
            }

            @Override
            protected Map<String, InMemoryBacktestResult> runBacktestForStock(
                    String symbol, List<IStrategy> strategies, double initialCapital,
                    String backtestRunId, LocalDateTime periodStart, LocalDateTime periodEnd,
                    BlockingQueue<BacktestResult> resultQueue) {
                return new HashMap<>();
            }
        };
        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(1L);

        ExecutorService direct = new DirectExecutorService();
        try (var mockedExecutors = mockStatic(Executors.class)) {
            mockedExecutors.when(Executors::newVirtualThreadPerTaskExecutor).thenReturn(direct);

            Thread.currentThread().interrupt();
            Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
                svc.runParallelizedBacktest(List.of(mock(IStrategy.class)), 10000.0);
            Thread.interrupted();
            assertNotNull(results);
        }
    }

    @Test
    void runParallelizedBacktest_shouldHandleWriterLatchTimeout() {
        BacktestService svc = spy(backtestService);
        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(1L);
        doReturn(List.of("TEST.TW")).when(svc).fetchTop50Stocks();
        doReturn(new HashMap<>()).when(svc).runBacktestForStock(
            anyString(), anyList(), anyDouble(), anyString(), any(), any(), any());

        try (var mockedLatch = mockConstruction(java.util.concurrent.CountDownLatch.class,
            (mock, context) -> when(mock.await(anyLong(), any())).thenReturn(false))) {
            Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
                svc.runParallelizedBacktest(List.of(mock(IStrategy.class)), 10000.0);
            assertNotNull(results);
        }
    }

    @Test
    void runParallelizedBacktest_shouldHandleWriterLatchInterrupted() {
        BacktestService svc = spy(backtestService);
        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(1L);
        doReturn(List.of("TEST.TW")).when(svc).fetchTop50Stocks();
        doReturn(new HashMap<>()).when(svc).runBacktestForStock(
            anyString(), anyList(), anyDouble(), anyString(), any(), any(), any());

        try (var mockedLatch = mockConstruction(java.util.concurrent.CountDownLatch.class,
            (mock, context) -> when(mock.await(anyLong(), any())).thenThrow(new InterruptedException("boom")))) {
            Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
                svc.runParallelizedBacktest(List.of(mock(IStrategy.class)), 10000.0);
            assertNotNull(results);
            assertTrue(Thread.interrupted());
        }
    }

    @Test
    void runParallelizedBacktest_shouldHandleBacktestFailure() {
        BacktestService svc = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        ) {
            @Override
            public List<String> fetchTop50Stocks() {
                return List.of("TEST.TW");
            }

            @Override
            protected Map<String, InMemoryBacktestResult> runBacktestForStock(
                    String symbol, List<IStrategy> strategies, double initialCapital,
                    String backtestRunId, LocalDateTime periodStart, LocalDateTime periodEnd,
                    BlockingQueue<BacktestResult> resultQueue) {
                throw new RuntimeException("backtest failed");
            }
        };
        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(1L);

        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
            svc.runParallelizedBacktest(List.of(mock(IStrategy.class)), 10000.0);

        assertNotNull(results);
        assertTrue(results.containsKey("TEST.TW"));
    }

    @Test
    void runParallelizedBacktest_shouldHandleInterruptedBacktest() throws Exception {
        when(systemStatusService.startBacktest()).thenReturn(true);
        
        // Simulate some stocks with data
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(100L);
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any())).thenReturn(List.of());

        IStrategy strategy = mock(IStrategy.class);
        lenient().when(strategy.getName()).thenReturn("TestStrategy");

        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results = 
            backtestService.runParallelizedBacktest(List.of(strategy), 10000.0);
        
        assertNotNull(results);
        verify(systemStatusService).completeBacktest();
    }

    @Test
    void runParallelizedBacktest_whenWriterLatchInterrupted_shouldHandle() throws Exception {
        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
            anyString(), any(), any(), any())).thenReturn(100L);
        lenient().when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any())).thenReturn(List.of(
                MarketData.builder().symbol("TEST.TW").timestamp(LocalDateTime.now()).close(100.0).build()
        ));

        BacktestService svc = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        ) {
            @Override
            public List<String> fetchTop50Stocks() {
                return List.of("TEST.TW");
            }

            @Override
            protected Map<String, InMemoryBacktestResult> runBacktestForStock(
                    String symbol, List<IStrategy> strategies, double initialCapital,
                    String backtestRunId, LocalDateTime periodStart, LocalDateTime periodEnd,
                    BlockingQueue<BacktestResult> resultQueue) {
                Thread.currentThread().interrupt();
                return new HashMap<>();
            }
        };

        IStrategy strategy = mock(IStrategy.class);
        lenient().when(strategy.getName()).thenReturn("TestStrategy");

        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
            svc.runParallelizedBacktest(List.of(strategy), 10000.0);

        assertNotNull(results);
        verify(systemStatusService).completeBacktest();
        Thread.interrupted();
    }

    // =========================================================================
    // Additional edge case tests
    // =========================================================================
    @Test
    void stockCandidate_getters_shouldReturnCorrectValues() {
        BacktestService.StockCandidate c = new BacktestService.StockCandidate(
            "2330.TW", "TSMC", 1000.0, 500.0, "TWSE");

        assertEquals("2330.TW", c.getSymbol());
        assertEquals("TSMC", c.getName());
        assertEquals(1000.0, c.getMarketCap());
        assertEquals(500.0, c.getVolume());
        assertEquals("TWSE", c.getSource());
    }

    @Test
    void inMemoryBacktestResult_trackEquity_shouldBuildEquityCurve() {
        BacktestService.InMemoryBacktestResult result = 
            new BacktestService.InMemoryBacktestResult("Test", 10000.0);
        
        result.trackEquity(10100.0);
        result.trackEquity(9900.0);
        result.trackEquity(10200.0);
        result.setFinalEquity(10200.0);
        result.calculateMetrics();

        assertTrue(result.getMaxDrawdownPercentage() > 0);
    }
}
