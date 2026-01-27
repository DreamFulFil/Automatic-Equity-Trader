package tw.gc.auto.equity.trader.services;

import de.bytefish.pgbulkinsert.PgBulkInsert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.BacktestResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class BacktestServicePersistenceTest {

    private BacktestService service;
    private tw.gc.auto.equity.trader.repositories.BacktestResultRepository mockRepo;
    private tw.gc.auto.equity.trader.repositories.MarketDataRepository mockMarketRepo;
    private HistoryDataService mockHistoryService;
    private SystemStatusService mockSystemStatusService;
    private DataSource mockDataSource;
    private JdbcTemplate mockJdbc;
    private StrategyStockMappingService mockMapping;
    private FundamentalDataService mockFundamentalDataService;

    @BeforeEach
    void setUp() {
        mockRepo = mock(tw.gc.auto.equity.trader.repositories.BacktestResultRepository.class);
        mockMarketRepo = mock(tw.gc.auto.equity.trader.repositories.MarketDataRepository.class);
        mockHistoryService = mock(HistoryDataService.class);
        when(mockHistoryService.getStockNameFromHistory(anyString())).thenReturn("FallbackName");
        mockSystemStatusService = mock(SystemStatusService.class);
        mockDataSource = mock(DataSource.class);
        mockJdbc = mock(JdbcTemplate.class);
        mockMapping = mock(StrategyStockMappingService.class);
        mockFundamentalDataService = mock(FundamentalDataService.class);

        service = new BacktestService(mockRepo, mockMarketRepo, mockHistoryService, mockSystemStatusService, mockDataSource, mockJdbc, mockMapping, mockFundamentalDataService);
    }

    @Test
    void initBacktestResultBulkInsert_initializesMapping() throws Exception {
        // Invoke private init method
        var m = BacktestService.class.getDeclaredMethod("initBacktestResultBulkInsert");
        m.setAccessible(true);

        java.lang.reflect.Field field = BacktestService.class.getDeclaredField("backtestResultBulkInsert");
        field.setAccessible(true);

        assertNull(field.get(service));
        m.invoke(service);
        assertNotNull(field.get(service), "backtestResultBulkInsert should be initialized");
    }

    @Test
    void flushBacktestResults_pgBulkInsert_success() throws Exception {
        BacktestResult r = BacktestResult.builder()
                .backtestRunId("BT-1")
                .symbol("TST.TW")
                .stockName("TestCo")
                .strategyName("S")
                .initialCapital(1000.0)
                .finalEquity(1100.0)
                .createdAt(LocalDateTime.now())
                .build();

        // Inject a mocked PgBulkInsert into the private field
        @SuppressWarnings("unchecked")
        PgBulkInsert<BacktestResult> mockBulk = mock(PgBulkInsert.class);
        java.lang.reflect.Field bulkField = BacktestService.class.getDeclaredField("backtestResultBulkInsert");
        bulkField.setAccessible(true);
        bulkField.set(service, mockBulk);

        // Mock DataSource -> Connection -> PGConnection
        Connection mockConn = mock(Connection.class);
        PGConnection mockPgConn = mock(PGConnection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.unwrap(PGConnection.class)).thenReturn(mockPgConn);

        // Call the private flush method
        java.lang.reflect.Method m = BacktestService.class.getDeclaredMethod("flushBacktestResults", List.class);
        m.setAccessible(true);

        int inserted = (int) m.invoke(service, List.of(r));

        assertEquals(1, inserted);
        // Verify that saveAll was invoked on the bulk insert with the PGConnection (disambiguate overloaded method)
        verify(mockBulk).saveAll(eq(mockPgConn), any(java.util.stream.Stream.class));
    }

    @Test
    void flushBacktestResults_pgBulkInsertThrows_fallsBackToJdbc() throws Exception {
        BacktestResult r = BacktestResult.builder()
                .backtestRunId("BT-2")
                .symbol("TST2.TW")
                .stockName("TestCo2")
                .strategyName("S2")
                .initialCapital(2000.0)
                .finalEquity(2100.0)
                .createdAt(LocalDateTime.now())
                .build();

        @SuppressWarnings("unchecked")
        PgBulkInsert<BacktestResult> mockBulk = mock(PgBulkInsert.class);
        java.lang.reflect.Field bulkField = BacktestService.class.getDeclaredField("backtestResultBulkInsert");
        bulkField.setAccessible(true);
        bulkField.set(service, mockBulk);

        // Make saveAll throw an exception to force fallback (disambiguate overloaded method)
        doThrow(new RuntimeException("boom")).when(mockBulk).saveAll(any(), any(java.util.stream.Stream.class));

        // DataSource -> Connection -> PGConnection
        Connection mockConn = mock(Connection.class);
        PGConnection mockPgConn = mock(PGConnection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.unwrap(PGConnection.class)).thenReturn(mockPgConn);

        // Stub jdbcTemplate.batchUpdate to simulate successful fallback
        when(mockJdbc.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{new int[]{1}});

        java.lang.reflect.Field jdbcField = BacktestService.class.getDeclaredField("jdbcTemplate");
        jdbcField.setAccessible(true);
        jdbcField.set(service, mockJdbc);

        java.lang.reflect.Method m = BacktestService.class.getDeclaredMethod("flushBacktestResults", List.class);
        m.setAccessible(true);

        int inserted = (int) m.invoke(service, List.of(r));

        assertEquals(1, inserted);
        verify(mockJdbc).batchUpdate(anyString(), anyList(), anyInt(), any());
    }
}
