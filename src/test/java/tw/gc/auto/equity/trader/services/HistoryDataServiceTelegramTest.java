package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryDataServiceTelegramTest {

    private HistoryDataService historyDataService;
    private TelegramService telegramService;

    @BeforeEach
    void setUp() {
        // Mock all constructor dependencies (we only care about TelegramService interactions)
        BarRepository barRepo = mock(BarRepository.class);
        MarketDataRepository mdRepo = mock(MarketDataRepository.class);
        StrategyStockMappingRepository mapRepo = mock(StrategyStockMappingRepository.class);
        org.springframework.web.client.RestTemplate restTemplate = mock(org.springframework.web.client.RestTemplate.class);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        org.springframework.transaction.PlatformTransactionManager txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        SystemStatusService systemStatusService = mock(SystemStatusService.class);
        BacktestService backtestService = mock(BacktestService.class);
        telegramService = mock(TelegramService.class);
        TaiwanStockNameService nameService = mock(TaiwanStockNameService.class);

        historyDataService = new HistoryDataService(
                barRepo,
                mdRepo,
                mapRepo,
                restTemplate,
                objectMapper,
                dataSource,
                jdbcTemplate,
                txManager,
                systemStatusService,
                backtestService,
                telegramService,
                nameService
        );
    }

    @Test
    void notifyDownloadComplete_doesNotSendTelegramMessage() {
        HistoryDataService.DownloadResult r = new HistoryDataService.DownloadResult("2330.TW", 1234, 1200, 34);

        historyDataService.notifyDownloadComplete(r);

        verify(telegramService, never()).sendMessage(any());
    }

    @Test
    void notifyDownloadStartedForSymbol_doesNotSendTelegramMessage() {
        historyDataService.notifyDownloadStartedForSymbol("2330.TW", 5);
        verify(telegramService, never()).sendMessage(any());
    }

    @Test
    void notifyDownloadStartedForAll_sendsFullListMessage() {
        java.util.List<String> syms = java.util.List.of("A","B","C","D","E","F","G","H","I","J","K");
        historyDataService.notifyDownloadStartedForAll(syms, 5);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramService, times(1)).sendMessage(captor.capture());
        String msg = captor.getValue();
        assertTrue(msg.contains("symbols=11") || msg.contains("11 symbols"));
        assertTrue(msg.contains("A, B, C, D, E, F, G, H, I, J, K") || msg.contains("A,B,C,D,E,F,G,H,I,J,K"));
    }

    @Test
    void notifyDownloadSummary_sendsDetailedTelegramMessage() {
        Map<String, HistoryDataService.DownloadResult> results = new HashMap<>();
        results.put("A", new HistoryDataService.DownloadResult("A", 100, 90, 10));
        results.put("B", new HistoryDataService.DownloadResult("B", 200, 180, 20));

        historyDataService.notifyDownloadSummary(results);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramService, times(1)).sendMessage(captor.capture());
        String msg = captor.getValue();
        assertTrue(msg.contains("symbols=2"));
        assertTrue(msg.contains("records=300"));
        assertTrue(msg.contains("inserted=270"));
        assertTrue(msg.contains("A - inserted 90"));
        assertTrue(msg.contains("B - inserted 180"));
    }

    @Test
    void formatSummaryForAll_returnsExpectedSummary() {
        Map<String, HistoryDataService.DownloadResult> results = new HashMap<>();
        results.put("A", new HistoryDataService.DownloadResult("A", 100, 90, 10));
        results.put("B", new HistoryDataService.DownloadResult("B", 200, 180, 20));

        String summary = historyDataService.formatSummaryForAll(results);
        assertTrue(summary.contains("symbols=2"));
        assertTrue(summary.contains("records=300"));
        assertTrue(summary.contains("inserted=270"));
        assertTrue(summary.contains("skipped=30"));
    }
}