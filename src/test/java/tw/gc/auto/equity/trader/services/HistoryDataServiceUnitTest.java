package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;

import javax.sql.DataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryDataServiceUnitTest {

    private BarRepository barRepository;
    private MarketDataRepository marketDataRepository;
    private StrategyStockMappingRepository strategyStockMappingRepository;
    private RestTemplate restTemplate;
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private DataSource dataSource;
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private org.springframework.transaction.PlatformTransactionManager txManager;
    private SystemStatusService systemStatusService;
    private BacktestService backtestService;
    private TelegramService telegramService;
    private TaiwanStockNameService taiwanStockNameService;

    private HistoryDataService svc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        barRepository = mock(BarRepository.class);
        marketDataRepository = mock(MarketDataRepository.class);
        strategyStockMappingRepository = mock(StrategyStockMappingRepository.class);
        restTemplate = mock(RestTemplate.class);
        objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        dataSource = mock(DataSource.class);
        jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        // Provide a simple TransactionStatus for TransactionTemplate to use
        org.springframework.transaction.TransactionStatus ts = mock(org.springframework.transaction.TransactionStatus.class);
        when(txManager.getTransaction(any())).thenReturn(ts);
        systemStatusService = mock(SystemStatusService.class);
        backtestService = null; // not needed for these unit tests
        telegramService = mock(TelegramService.class);
        taiwanStockNameService = mock(TaiwanStockNameService.class);

        svc = new HistoryDataService(
                barRepository,
                marketDataRepository,
                strategyStockMappingRepository,
                restTemplate,
                objectMapper,
                dataSource,
                jdbcTemplate,
                txManager,
                systemStatusService,
                backtestService,
                telegramService,
                taiwanStockNameService
        );
    }

    @Test
    void formatters_and_notify_sendTelegram() {
        HistoryDataService.DownloadResult r1 = new HistoryDataService.DownloadResult("A", 10, 8, 2);
        HistoryDataService.DownloadResult r2 = new HistoryDataService.DownloadResult("B", 5, 5, 0);
        Map<String, HistoryDataService.DownloadResult> map = Map.of("A", r1, "B", r2);

        String ind = svc.formatIndividualSummary(r1);
        assertThat(ind).contains("A").contains("records: 10");

        String summary = svc.formatSummaryForAll(map);
        assertThat(summary).contains("symbols=2").contains("A - inserted 8");

        // notifyDownloadSummary should call telegramService.sendMessage
        svc.notifyDownloadSummary(map);
        verify(telegramService, times(1)).sendMessage(anyString());

        // empty map should not call
        reset(telegramService);
        svc.notifyDownloadSummary(Map.of());
        verifyNoInteractions(telegramService);
    }

    @Test
    void fillMissingNamesIfMissing_usesFallback_whenAvailable() {
        HistoryDataService.HistoricalDataPoint p1 = new HistoryDataService.HistoricalDataPoint();
        p1.setTimestamp(LocalDateTime.now());
        p1.setName(null);
        p1.setOpen(1.0);
        p1.setClose(2.0);
        p1.setHigh(2.0);
        p1.setLow(1.0);
        p1.setVolume(100L);
        p1.setSymbol("2330.TW");

        // convert to Bar/MarketData via toBar/toMarketData indirectly by building Bar objects
        Bar b1 = Bar.builder().symbol("2330.TW").timestamp(p1.getTimestamp()).name(null).open(1.0).close(2.0).high(2.0).low(1.0).volume(100L).market("TSE").timeframe("1day").isComplete(true).build();
        MarketData md1 = MarketData.builder().symbol("2330.TW").timestamp(p1.getTimestamp()).name(null).open(1.0).close(2.0).high(2.0).low(1.0).volume(100L).timeframe(MarketData.Timeframe.DAY_1).build();

        List<Bar> bars = List.of(b1);
        List<MarketData> mds = List.of(md1);

        when(taiwanStockNameService.hasStockName("2330.TW")).thenReturn(true);
        when(taiwanStockNameService.getStockName("2330.TW")).thenReturn("TSMC");

        svc.fillMissingNamesIfMissing(bars, mds, "2330.TW");

        assertEquals("TSMC", bars.get(0).getName());
        assertEquals("TSMC", mds.get(0).getName());
    }

    @Test
    void truncateTablesIfNeeded_success_and_failure_resetsFlag() {
        // First call should execute truncation methods
        svc.resetTruncationFlag();
        svc.truncateTablesIfNeeded();
        verify(barRepository, times(1)).truncateTable();
        verify(marketDataRepository, times(1)).truncateTable();
        verify(strategyStockMappingRepository, times(1)).truncateTable();

        // Subsequent call should be no-op
        svc.truncateTablesIfNeeded();
        verify(barRepository, times(1)).truncateTable();

        // Reset flag and simulate failure on truncate
        svc.resetTruncationFlag();
        doThrow(new RuntimeException("boom")).when(barRepository).truncateTable();
        assertThrows(RuntimeException.class, () -> svc.truncateTablesIfNeeded());

        // After failure, flag should have been reset allowing success on next call
        doNothing().when(barRepository).truncateTable();
        // other repos no-op
        svc.truncateTablesIfNeeded();
        verify(barRepository, times(3)).truncateTable();
    }

    @Test
    void getStockNameFromHistory_prefersBar_thenMarketData_thenNull() {
        Bar b = Bar.builder().symbol("X").name("FromBar").timestamp(LocalDateTime.now()).build();
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc("X", "1day"))
            .thenReturn(Optional.of(b));

        String name = svc.getStockNameFromHistory("X");
        assertEquals("FromBar", name);

        // bar missing, fallback to market data
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc("Y", "1day")).thenReturn(Optional.empty());
        MarketData md = MarketData.builder().symbol("Y").name("FromMD").timestamp(LocalDateTime.now()).timeframe(MarketData.Timeframe.DAY_1).build();
        when(marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc("Y", MarketData.Timeframe.DAY_1))
            .thenReturn(Optional.of(md));

        assertEquals("FromMD", svc.getStockNameFromHistory("Y"));

        // neither present
        when(barRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc("Z", "1day")).thenReturn(Optional.empty());
        when(marketDataRepository.findFirstBySymbolAndTimeframeOrderByTimestampDesc("Z", MarketData.Timeframe.DAY_1)).thenReturn(Optional.empty());
        assertNull(svc.getStockNameFromHistory("Z"));
    }
}
