package tw.gc.auto.equity.trader.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.services.HistoryDataService;
import tw.gc.auto.equity.trader.services.SystemStatusService;
import tw.gc.auto.equity.trader.strategy.impl.RSIStrategy;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class BacktestIntegrationTest {

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private HistoryDataService historyDataService;

    @Mock
    private SystemStatusService systemStatusService;

    @Mock
    private javax.sql.DataSource dataSource;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BacktestService backtestService;

    @Mock
    private BarRepository barRepository;

    @BeforeEach
    void setup() {
        // defaults
        lenient().when(systemStatusService.startHistoryDownload()).thenReturn(true);
    }

    @Test
    void backtestPersistsStockNameFromHistory() {
        String symbol = "TEST.TW";

        Bar bar = new Bar();
        bar.setSymbol(symbol);
        bar.setTimeframe("1day");
        bar.setTimestamp(LocalDateTime.now().minusDays(2));
        bar.setName("Test Co.");
        bar.setOpen(100.0);
        bar.setClose(101.0);
        bar.setHigh(105.0);
        bar.setLow(99.0);
        bar.setVolume(1000L);
        barRepository.save(bar);

        MarketData md = MarketData.builder()
            .symbol(symbol)
            .timeframe(MarketData.Timeframe.DAY_1)
            .timestamp(LocalDateTime.now().minusDays(1))
            .open(100.0)
            .high(105.0)
            .low(99.0)
            .close(102.0)
            .volume(1000L)
            .build();

        List<MarketData> history = List.of(md);

        // Stub history name lookup and repository save behavior
        when(historyDataService.getStockNameFromHistory(symbol)).thenReturn("Test Co.");
        when(backtestResultRepository.save(any(BacktestResult.class))).thenAnswer(inv -> inv.getArgument(0));

        backtestService.runBacktest(List.of(new RSIStrategy(14, 70, 30)), history, 10000.0, "BT-INT-TEST");

        // Capture saved results and assert stock name
        ArgumentCaptor<BacktestResult> captor = ArgumentCaptor.forClass(BacktestResult.class);
        verify(backtestResultRepository, atLeastOnce()).save(captor.capture());
        List<BacktestResult> saved = captor.getAllValues();
        assertThat(saved).isNotEmpty();
        for (BacktestResult r : saved) {
            assertThat(r.getStockName()).isEqualTo("Test Co.");
        }
    }
}
