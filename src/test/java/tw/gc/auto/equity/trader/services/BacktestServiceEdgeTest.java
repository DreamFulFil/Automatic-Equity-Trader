package tw.gc.auto.equity.trader.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;

class BacktestServiceEdgeTest {

    private static BacktestService newService() {
        BacktestResultRepository backtestResultRepository = Mockito.mock(BacktestResultRepository.class);
        MarketDataRepository marketDataRepository = Mockito.mock(MarketDataRepository.class);
        HistoryDataService historyDataService = Mockito.mock(HistoryDataService.class);
        SystemStatusService systemStatusService = Mockito.mock(SystemStatusService.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        StrategyStockMappingService strategyStockMappingService = Mockito.mock(StrategyStockMappingService.class);

        return new BacktestService(
                backtestResultRepository,
                marketDataRepository,
                historyDataService,
                systemStatusService,
                dataSource,
                jdbcTemplate,
                strategyStockMappingService);
    }

    @Test
    void meetsCriteria_shouldRejectInvalidSymbolFormats() {
        BacktestService svc = newService();

        assertEquals(false, svc.meetsCriteria(new BacktestService.StockCandidate("2330", "TSMC", 1.0, 1.0, "src")));
        assertEquals(false, svc.meetsCriteria(new BacktestService.StockCandidate("ABCD.TW", "Bad", 1.0, 1.0, "src")));
        assertEquals(false, svc.meetsCriteria(new BacktestService.StockCandidate("2330.TW", "NoLiquidity", 0.0, 0.0, "src")));
        assertEquals(true, svc.meetsCriteria(new BacktestService.StockCandidate("2330.TW", "TSMC", 1.0, 0.0, "src")));
    }

    @Test
    void fetchTop50Stocks_shouldReturnDynamicWhenEnoughCandidates() {
        BacktestService svc = Mockito.spy(newService());

        Mockito.doReturn(Set.of()).when(svc).fetchFromTWSE();
        Mockito.doReturn(Set.of()).when(svc).fetchFromYahooFinanceTW();

        Set<BacktestService.StockCandidate> taiex = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String symbol = String.format("%04d.TW", 1000 + i);
            taiex.add(new BacktestService.StockCandidate(symbol, "N" + i, 1.0 + i, 0.0, "TAIEX"));
        }
        Mockito.doReturn(taiex).when(svc).fetchTAIEXComponents();

        List<String> symbols = svc.fetchTop50Stocks();
        assertNotNull(symbols);
        assertEquals(50, symbols.size());
        assertTrue(symbols.contains("1000.TW"));
    }

    @Test
    void runBacktestResultWriter_shouldExitWhenCompleteAndEmpty() {
        BacktestService svc = newService();

        int written = svc.runBacktestResultWriter(new LinkedBlockingQueue<>(), new AtomicBoolean(true));
        assertEquals(0, written);
    }

    @Test
    void runBacktestForStock_shouldReturnEmptyWhenNoHistory() {
        BacktestService svc = newService();

        MarketDataRepository marketDataRepository = Mockito.mock(MarketDataRepository.class);
        Mockito.when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                Mockito.anyString(),
                Mockito.any(MarketData.Timeframe.class),
                Mockito.any(LocalDateTime.class),
                Mockito.any(LocalDateTime.class)))
            .thenReturn(List.of());

        // inject the mocked repository by reflection (field is final)
        try {
            var f = BacktestService.class.getDeclaredField("marketDataRepository");
            f.setAccessible(true);
            f.set(svc, marketDataRepository);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var result = svc.runBacktestForStock(
                "2330.TW",
                List.<IStrategy>of(),
                1000.0,
                "run",
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                new LinkedBlockingQueue<BacktestResult>());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
