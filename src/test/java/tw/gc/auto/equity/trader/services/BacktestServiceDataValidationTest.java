package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;
import tw.gc.auto.equity.trader.testutil.MarketDataTestFactory;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class BacktestServiceDataValidationTest {

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

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        lenient().when(historyDataService.getStockNameFromHistory(anyString())).thenReturn("TestName");
        backtestService = new BacktestService(
            backtestResultRepository,
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            strategyStockMappingService
        );
    }

    @Test
    void runBacktest_shouldHandleEmptyHistory() {
        List<IStrategy> strategies = List.of(new NeutralStrategy("N"));

        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
            strategies,
            List.of(),
            10_000.0,
            "BT-EMPTY"
        );

        assertThat(results).containsKey("N");
        assertThat(results.get("N").getTotalTrades()).isZero();
        assertThat(results.get("N").getInitialCapital()).isEqualTo(10_000.0);
    }

    @Test
    void runBacktest_shouldHandleSingleDataPoint_withoutIndexErrors() {
        IStrategy alwaysLong = new IStrategy() {
            @Override
            public TradeSignal execute(Portfolio portfolio, MarketData data) {
                return TradeSignal.longSignal(1.0, "single");
            }

            @Override
            public String getName() {
                return "ONE";
            }

            @Override
            public StrategyType getType() {
                return StrategyType.SHORT_TERM;
            }
        };

        List<MarketData> history = List.of(
            MarketDataTestFactory.createMarketData("2330.TW", LocalDateTime.now().minusDays(1), 100.0)
        );

        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
            List.of(alwaysLong),
            history,
            10_000.0,
            "BT-ONE"
        );

        assertThat(results).containsKey("ONE");
        assertThat(results.get("ONE").getFinalEquity()).isEqualTo(10_000.0);
        // Position is opened and then closed at end-of-history, which counts as a trade even with pnl=0.
        assertThat(results.get("ONE").getTotalTrades()).isEqualTo(1);
    }

    @Test
    void runBacktest_shouldHandleGappedHistory_withoutBreakingEquityCalculation() {
        List<MarketData> history = MarketDataTestFactory.createHistoryWithGaps("2330.TW", List.of(5, 10, 15));
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
            List.of(new NeutralStrategy("GAPS")),
            history,
            50_000.0,
            "BT-GAPS"
        );

        BacktestService.InMemoryBacktestResult r = results.get("GAPS");
        assertThat(r.getFinalEquity()).isEqualTo(50_000.0);
        assertThat(Double.isFinite(r.getSharpeRatio())).isTrue();
        assertThat(Double.isFinite(r.getMaxDrawdownPercentage())).isTrue();
    }

    @Test
    void runBacktest_shouldHandleExtremeDailyMoves_10Percent_withoutNaNMetrics() {
        List<MarketData> history = MarketDataTestFactory.createVolatileHistory("2330.TW", 20, 10);

        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
            List.of(new FlipStrategy("FLIP")),
            history,
            100_000.0,
            "BT-VOL-10"
        );

        BacktestService.InMemoryBacktestResult r = results.get("FLIP");
        assertThat(r.getTotalTrades()).isGreaterThanOrEqualTo(1);
        assertThat(Double.isFinite(r.getTotalReturnPercentage())).isTrue();
        assertThat(Double.isFinite(r.getSharpeRatio())).isTrue();
        assertThat(Double.isFinite(r.getMaxDrawdownPercentage())).isTrue();
    }

    @Test
    void processSignal_shouldOpenShort_withDeterministicQuantity() throws Exception {
        Portfolio p = Portfolio.builder()
            .positions(new HashMap<>())
            .entryPrices(new HashMap<>())
            .equity(10_000.0)
            .availableMargin(10_000.0)
            .tradingMode("backtest")
            .tradingQuantity(1)
            .build();

        MarketData data = MarketData.builder()
            .symbol("2330.TW")
            .close(100.0)
            .timestamp(LocalDateTime.now())
            .build();

        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S", 10_000.0);

        Method process = BacktestService.class.getDeclaredMethod(
            "processSignal",
            IStrategy.class,
            Portfolio.class,
            MarketData.class,
            TradeSignal.class,
            BacktestService.InMemoryBacktestResult.class
        );
        process.setAccessible(true);

        IStrategy strategy = mock(IStrategy.class);
        process.invoke(backtestService, strategy, p, data, TradeSignal.shortSignal(1.0, "short"), result);

        // qty = (int) (10000 * 0.95 / 100) = 95
        assertThat(p.getPosition("2330.TW")).isEqualTo(-95);
        assertThat(p.getEntryPrice("2330.TW")).isEqualTo(100.0);
        assertThat(p.getAvailableMargin()).isEqualTo(500.0);
    }

    @Test
    void processSignal_shouldCloseShort_correctPnLArithmetic_withoutOpeningLongWhenQtyZero() throws Exception {
        // Pick values that ensure:
        // - short opens with qty=1
        // - closing at a much higher price yields loss
        // - subsequent LONG does NOT open a new long because qty becomes 0
        Portfolio p = Portfolio.builder()
            .positions(new HashMap<>())
            .entryPrices(new HashMap<>())
            .equity(99.0)
            .availableMargin(99.0)
            .tradingMode("backtest")
            .tradingQuantity(1)
            .build();

        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S", 99.0);

        Method process = BacktestService.class.getDeclaredMethod(
            "processSignal",
            IStrategy.class,
            Portfolio.class,
            MarketData.class,
            TradeSignal.class,
            BacktestService.InMemoryBacktestResult.class
        );
        process.setAccessible(true);

        IStrategy strategy = mock(IStrategy.class);

        // Open short at 50: qty = (int)(99 * 0.95 / 50) = 1
        MarketData open = MarketData.builder().symbol("2330.TW").close(50.0).timestamp(LocalDateTime.now()).build();
        process.invoke(backtestService, strategy, p, open, TradeSignal.shortSignal(1.0, "short"), result);
        assertThat(p.getPosition("2330.TW")).isEqualTo(-1);
        assertThat(p.getAvailableMargin()).isEqualTo(49.0);

        // Close short at 100 via LONG; qty after close = (int)(99 * 0.95 / 100) = 0 -> no long opened
        MarketData close = MarketData.builder().symbol("2330.TW").close(100.0).timestamp(LocalDateTime.now()).build();
        process.invoke(backtestService, strategy, p, close, TradeSignal.longSignal(1.0, "close-short"), result);

        // pnl = (50 - 100) * 1 = -50
        assertThat(p.getPosition("2330.TW")).isZero();
        assertThat(p.getEquity()).isEqualTo(49.0);
        assertThat(p.getAvailableMargin()).isEqualTo(99.0);
        assertThat(result.getTotalTrades()).isEqualTo(1);
        assertThat(result.getTotalPnL()).isEqualTo(-50.0);
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

    private static class FlipStrategy implements IStrategy {
        private final String name;
        private int calls;

        private FlipStrategy(String name) {
            this.name = name;
        }

        @Override
        public TradeSignal execute(Portfolio portfolio, MarketData data) {
            calls++;
            return calls % 2 == 1
                ? TradeSignal.longSignal(1.0, "flip-long")
                : TradeSignal.shortSignal(1.0, "flip-short");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public StrategyType getType() {
            return StrategyType.SHORT_TERM;
        }

        @Override
        public void reset() {
            calls = 0;
        }
    }
}
