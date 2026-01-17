package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style test for the full multi-stock download pipeline (real virtual threads + real BlockingQueue).
 *
 * Covers Task A5 by exercising:
 * - Virtual-thread fan-out
 * - Global writer thread drain/flush
 * - Aggregated insertedBySymbol result mapping
 */
@ExtendWith(MockitoExtension.class)
@Tag("integration")
@DisplayName("HistoryDataService - Integration Tests")
class HistoryDataServiceIntegrationTest {

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

        when(backtestService.fetchTop50Stocks()).thenReturn(List.of());
        when(dataSource.getConnection()).thenThrow(new SQLException("boom"));
        when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
            .thenReturn(new int[0][0]);

        stubRestTemplateExchange(30);
    }

    @Test
    @DisplayName("Task A5: Multi-stock concurrent download -> all results populated")
    void taskA5_multiStockConcurrentDownload() {
        List<String> symbols = List.of("2330.TW", "2454.TW", "2317.TW");

        Map<String, HistoryDataService.DownloadResult> results = historyDataService
            .downloadHistoricalDataForMultipleStocks(symbols, 1, 30, TimeUnit.SECONDS);

        assertThat(results).hasSize(3);
        symbols.forEach(s -> {
            assertThat(results.get(s).getTotalRecords()).isEqualTo(30);
            assertThat(results.get(s).getInserted()).isEqualTo(30);
        });

        verify(systemStatusService).startHistoryDownload();
        verify(systemStatusService).completeHistoryDownload();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramService, atLeastOnce()).sendMessage(msgCaptor.capture());
        assertThat(String.join("\n", msgCaptor.getAllValues())).contains("Starting historical download");
        assertThat(String.join("\n", msgCaptor.getAllValues())).contains("Historical download complete");
    }

    private void stubRestTemplateExchange(int pointsPerCall) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
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
