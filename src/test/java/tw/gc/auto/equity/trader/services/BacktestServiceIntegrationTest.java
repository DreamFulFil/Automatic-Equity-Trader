package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;
import tw.gc.auto.equity.trader.testutil.AsyncTestHelper;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Integration-ish tests for BacktestService parallel execution.
 * Uses AsyncTestHelper for deterministic waiting to avoid flaky multi-thread timing.
 */
@Tag("integration")
@ExtendWith(MockitoExtension.class)
class BacktestServiceIntegrationTest {

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
        when(historyDataService.getStockNameFromHistory(anyString())).thenReturn("TestName");

        // Force PgBulkInsert path to fail so writer uses the JdbcTemplate fallback.
        try {
            lenient().when(dataSource.getConnection()).thenThrow(new SQLException("no pg"));
        } catch (SQLException ignored) {
        }
        lenient().when(jdbcTemplate.batchUpdate(anyString(), anyList(), anyInt(), any())).thenReturn(new int[][]{new int[]{1}});

        backtestService = spy(new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService,
            fundamentalDataService
        ));
    }

    @Test
    void runParallelizedBacktest_shouldCompleteAllSymbols_withoutFlakyWaits() {
        List<String> symbols = List.of(
            "0001.TW", "0002.TW", "0003.TW", "0004.TW", "0005.TW",
            "0006.TW", "0007.TW", "0008.TW", "0009.TW", "0010.TW"
        );
        doReturn(symbols).when(backtestService).fetchTop50Stocks();

        when(systemStatusService.startBacktest()).thenReturn(true);
        when(marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(anyString(), any(), any(), any()))
            .thenReturn(10L);

        List<MarketData> history = List.of(
            MarketData.builder().symbol("X.TW").timeframe(MarketData.Timeframe.DAY_1).timestamp(LocalDateTime.now().minusDays(2)).close(100.0).build(),
            MarketData.builder().symbol("X.TW").timeframe(MarketData.Timeframe.DAY_1).timestamp(LocalDateTime.now().minusDays(1)).close(101.0).build()
        );
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(anyString(), any(), any(), any()))
            .thenAnswer(inv -> {
                String sym = inv.getArgument(0);
                return history.stream().map(md -> MarketData.builder()
                    .symbol(sym)
                    .timeframe(md.getTimeframe())
                    .timestamp(md.getTimestamp())
                    .open(md.getOpen())
                    .high(md.getHigh())
                    .low(md.getLow())
                    .close(md.getClose())
                    .volume(md.getVolume())
                    .build()).toList();
            });

        List<IStrategy> strategies = List.of(new NeutralStrategy("N"));

        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results =
            backtestService.runParallelizedBacktest(strategies, 10_000.0);

        AsyncTestHelper.waitForAsync(2_000, () -> results.size() == symbols.size());

        assertThat(results).hasSize(symbols.size());
        for (String sym : symbols) {
            assertThat(results).containsKey(sym);
            assertThat(results.get(sym)).containsKey("N");
        }
    }

    @Test
    void runBacktestForStock_shouldHandleResultQueueFull_withoutThrowing() throws Exception {
        BlockingQueue<BacktestResult> smallQueue = new ArrayBlockingQueue<>(1);
        smallQueue.put(BacktestResult.builder().backtestRunId("BT").symbol("X").strategyName("PRE").build());

        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(anyString(), any(), any(), any()))
            .thenReturn(List.of(
                MarketData.builder().symbol("2330.TW").timeframe(MarketData.Timeframe.DAY_1).timestamp(LocalDateTime.now().minusDays(1)).close(100.0).build(),
                MarketData.builder().symbol("2330.TW").timeframe(MarketData.Timeframe.DAY_1).timestamp(LocalDateTime.now()).close(101.0).build()
            ));

        List<IStrategy> strategies = List.of(
            new NeutralStrategy("S1"),
            new NeutralStrategy("S2"),
            new NeutralStrategy("S3")
        );

        Method runForStock = BacktestService.class.getDeclaredMethod(
            "runBacktestForStock",
            String.class,
            List.class,
            double.class,
            String.class,
            LocalDateTime.class,
            LocalDateTime.class,
            BlockingQueue.class
        );
        runForStock.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, BacktestService.InMemoryBacktestResult> result =
            (Map<String, BacktestService.InMemoryBacktestResult>) runForStock.invoke(
                backtestService,
                "2330.TW",
                strategies,
                10_000.0,
                "BT-QFULL",
                LocalDateTime.now().minusYears(1),
                LocalDateTime.now(),
                smallQueue
            );

        AsyncTestHelper.waitForAsync(1_000, () -> result.size() == 3);

        assertThat(result).containsKeys("S1", "S2", "S3");
        assertThat(smallQueue).hasSize(1);
    }

    private static class NeutralStrategy implements IStrategy {
        private final String name;

        private NeutralStrategy(String name) {
            this.name = name;
        }

        @Override
        public TradeSignal execute(Portfolio portfolio, MarketData data) {
            return TradeSignal.neutral("hold");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public StrategyType getType() {
            return StrategyType.SHORT_TERM;
        }
    }
}
