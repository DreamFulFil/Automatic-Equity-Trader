package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BacktestServiceUnitTest {

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
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Mock
    private StrategyStockMappingService strategyStockMappingService;

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
            strategyStockMappingService
        );
    }

    @Test
    void processSignal_shouldOpenLong_whenNeutral() throws Exception {
        // Arrange
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
            .close(50.0)
            .timestamp(LocalDateTime.now())
            .build();

        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S1", 10000.0);

        TradeSignal signal = TradeSignal.longSignal(0.9, "test-long");

        Method process = null;
        for (Method mm : BacktestService.class.getDeclaredMethods()) {
            if (mm.getName().equals("processSignal") && mm.getParameterCount() == 5) {
                process = mm;
                break;
            }
        }
        process.setAccessible(true);

        // Act
        tw.gc.auto.equity.trader.strategy.IStrategy strategyMock = mock(tw.gc.auto.equity.trader.strategy.IStrategy.class);
        org.mockito.Mockito.lenient().when(strategyMock.getName()).thenReturn("S1");
        process.invoke(backtestService, strategyMock, p, data, signal, result);

        // Assert - position should be opened long and entry price set
        assertThat(p.getPosition("2330.TW")).isGreaterThan(0);
        assertThat(p.getEntryPrice("2330.TW")).isEqualTo(50.0);
        assertThat(p.getAvailableMargin()).isLessThan(10_000.0);
    }

    @Test
    void processSignal_shouldOpenShort_whenNeutral() throws Exception {
        Portfolio p = Portfolio.builder()
            .positions(new HashMap<>())
            .entryPrices(new HashMap<>())
            .equity(5_000.0)
            .availableMargin(5_000.0)
            .tradingMode("backtest")
            .tradingQuantity(1)
            .build();

        MarketData data = MarketData.builder()
            .symbol("2330.TW")
            .close(100.0)
            .timestamp(LocalDateTime.now())
            .build();

        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S2", 5000.0);

        TradeSignal signal = TradeSignal.shortSignal(0.8, "test-short");

        Method process = BacktestService.class.getDeclaredMethod("processSignal", tw.gc.auto.equity.trader.strategy.IStrategy.class, Portfolio.class, MarketData.class, TradeSignal.class, BacktestService.InMemoryBacktestResult.class);
        process.setAccessible(true);

        tw.gc.auto.equity.trader.strategy.IStrategy strategyMock = mock(tw.gc.auto.equity.trader.strategy.IStrategy.class);
        org.mockito.Mockito.lenient().when(strategyMock.getName()).thenReturn("S2");

        process.invoke(backtestService, strategyMock, p, data, signal, result);

        // Assert - position should be opened short (negative) and entry price set
        assertThat(p.getPosition("2330.TW")).isLessThan(0);
        assertThat(p.getEntryPrice("2330.TW")).isEqualTo(100.0);
        assertThat(p.getAvailableMargin()).isLessThan(5_000.0);
    }

    @Test
    void processSignal_shouldNotOpen_whenAlreadyLong() throws Exception {
        Portfolio p = Portfolio.builder()
            .positions(new HashMap<>())
            .entryPrices(new HashMap<>())
            .equity(2_000.0)
            .availableMargin(2_000.0)
            .tradingMode("backtest")
            .tradingQuantity(1)
            .build();

        // simulate already long
        p.setPosition("2330.TW", 10);
        p.setEntryPrice("2330.TW", 50.0);

        MarketData data = MarketData.builder()
            .symbol("2330.TW")
            .close(60.0)
            .timestamp(LocalDateTime.now())
            .build();

        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S3", 2000.0);

        TradeSignal signal = TradeSignal.longSignal(0.7, "test-long");

        Method process = BacktestService.class.getDeclaredMethod("processSignal", tw.gc.auto.equity.trader.strategy.IStrategy.class, Portfolio.class, MarketData.class, TradeSignal.class, BacktestService.InMemoryBacktestResult.class);
        process.setAccessible(true);

        tw.gc.auto.equity.trader.strategy.IStrategy strategyMock = mock(tw.gc.auto.equity.trader.strategy.IStrategy.class);
        org.mockito.Mockito.lenient().when(strategyMock.getName()).thenReturn("S3");

        process.invoke(backtestService, strategyMock, p, data, signal, result);

        // Assert - existing long should remain unchanged
        assertThat(p.getPosition("2330.TW")).isEqualTo(10);
        assertThat(p.getEntryPrice("2330.TW")).isEqualTo(50.0);
    }

    @Test
    void closeAllPositions_shouldClose_andRecordTrade() throws Exception {
        Portfolio p = Portfolio.builder()
            .positions(new HashMap<>())
            .entryPrices(new HashMap<>())
            .equity(1000.0)
            .availableMargin(1000.0)
            .tradingMode("backtest")
            .tradingQuantity(1)
            .build();

        p.setPosition("2330.TW", 5);
        p.setEntryPrice("2330.TW", 10.0);

        MarketData data = MarketData.builder()
            .symbol("2330.TW")
            .close(12.0)
            .timestamp(LocalDateTime.now())
            .build();

        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S4", 1000.0);

        Method closeAll = BacktestService.class.getDeclaredMethod("closeAllPositions", Portfolio.class, MarketData.class, BacktestService.InMemoryBacktestResult.class);
        closeAll.setAccessible(true);

        closeAll.invoke(backtestService, p, data, result);

        assertThat(p.getPosition("2330.TW")).isEqualTo(0);
        assertThat(result.getTotalTrades()).isGreaterThan(0);
    }

    @Test
    void inMemoryResult_metrics_shouldCalculate_return_drawdown_and_sharpe() {
        BacktestService.InMemoryBacktestResult r = new BacktestService.InMemoryBacktestResult("S1", 1000.0);

        // build equity curve
        r.trackEquity(1000.0);
        r.trackEquity(900.0);
        r.trackEquity(1100.0);
        r.trackEquity(1050.0);

        // add trades
        r.addTrade(100.0);
        r.addTrade(-50.0);
        r.addTrade(200.0);

        r.setFinalEquity(1050.0);
        r.calculateMetrics();

        assertThat(r.getTotalReturnPercentage()).isCloseTo(5.0, org.assertj.core.data.Percentage.withPercentage(0.1));
        assertThat(r.getMaxDrawdownPercentage()).isGreaterThanOrEqualTo(0.0);
        assertThat(r.getSharpeRatio()).isGreaterThanOrEqualTo(0.0);
    }
}
