package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Batch17ExtraTests {

    @Mock
    BarRepository barRepository;
    @Mock
    MarketDataRepository marketDataRepository;
    @Mock
    StrategyStockMappingRepository strategyStockMappingRepository;
    @Mock
    org.springframework.web.client.RestTemplate restTemplate;
    @Mock
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    DataSource dataSource;
    @Mock
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Mock
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock
    SystemStatusService systemStatusService;
    @Mock
    BacktestService backtestService;
    @Mock
    TelegramService telegramService;
    @Mock
    TaiwanStockNameService taiwanStockNameService;

    private HistoryDataService historyDataService;
    private BacktestService backtestServiceInstance;
    private TelegramCommandHandler telegramCommandHandler;

    @BeforeEach
    void setUp() {
        // lenient transaction manager stub
        org.springframework.transaction.TransactionStatus mockStatus = mock(org.springframework.transaction.TransactionStatus.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mockStatus);
        historyDataService = new HistoryDataService(
            barRepository,
            marketDataRepository,
            strategyStockMapping_repository_safe(),
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

        // BacktestService instance for parseNumber testing
        backtestServiceInstance = new BacktestService(
            mock(tw.gc.auto.equity.trader.repositories.BacktestResultRepository.class),
            marketDataRepository,
            historyDataService,
            systemStatusService,
            dataSource,
            jdbcTemplate,
            mock(StrategyStockMappingService.class)
        );

        // Minimal TelegramCommandHandler for private method testing
        telegramCommandHandler = new TelegramCommandHandler(
            telegramService,
            mock(TradingStateService.class),
            mock(PositionManager.class),
            mock(RiskManagementService.class),
            mock(ContractScalingService.class),
            mock(StockSettingsService.class),
            mock(ShioajiSettingsService.class),
            mock(LlmService.class),
            mock(OrderExecutionService.class),
            mock(org.springframework.context.ApplicationContext.class),
            mock(StockRiskSettingsService.class),
            mock(ActiveStrategyService.class),
            mock(StrategyPerformanceService.class),
            mock(ActiveStockService.class),
            mock(BacktestService.class),
            mock(HistoryDataService.class),
            mock(tw.gc.auto.equity.trader.repositories.MarketDataRepository.class),
            mock(AutoStrategySelector.class),
            mock(SystemConfigService.class)
        );
    }

    // helper to work around constructor ordering in this test file for the strategy mapping repo
    private StrategyStockMappingRepository strategyStockMapping_repository_safe() {
        return strategyStockMappingRepository;
    }

    @Test
    void initInnerBulkMappingClasses_shouldInstantiateWithoutError() throws Exception {
        // Ensure inner mapping classes' constructors execute mapping registration logic
        Class<?> barMapping = Class.forName("tw.gc.auto.equity.trader.services.HistoryDataService$BarBulkInsertMapping");
        Class<?> mdMapping = Class.forName("tw.gc.auto.equity.trader.services.HistoryDataService$MarketDataBulkInsertMapping");

        // instantiate via no-arg constructor
        Object b = barMapping.getDeclaredConstructor().newInstance();
        Object m = mdMapping.getDeclaredConstructor().newInstance();

        assertThat(b).isNotNull();
        assertThat(m).isNotNull();

        // Also invoke initBulkInsertMappings to ensure idempotence
        java.lang.reflect.Method init = HistoryDataService.class.getDeclaredMethod("initBulkInsertMappings");
        init.setAccessible(true);
        init.invoke(historyDataService);
        init.invoke(historyDataService);
    }

    @Test
    void backtest_parseNumber_handlesUnitsAndMalformed() throws Exception {
        java.lang.reflect.Method m = BacktestService.class.getDeclaredMethod("parseNumber", String.class);
        m.setAccessible(true);

        double k = (double) m.invoke(backtestServiceInstance, "1.5K");
        double mVal = (double) m.invoke(backtestServiceInstance, "2M");
        double b = (double) m.invoke(backtestServiceInstance, "3B");
        double plain = (double) m.invoke(backtestServiceInstance, "12345");
        double bad = (double) m.invoke(backtestServiceInstance, "--");

        assertThat(k).isEqualTo(1500.0);
        assertThat(mVal).isEqualTo(2_000_000.0);
        assertThat(b).isEqualTo(3_000_000_000.0);
        assertThat(plain).isEqualTo(12345.0);
        assertThat(bad).isEqualTo(0.0);
    }

    @Test
    void telegram_handleStrategyRecommendation_nullAndNonNull() throws Exception {
        // Access private method
        java.lang.reflect.Method m = TelegramCommandHandler.class.getDeclaredMethod("handleStrategyRecommendation");
        m.setAccessible(true);

        // Case 1: strategyPerformanceService returns null
        java.lang.reflect.Field perfField = TelegramCommandHandler.class.getDeclaredField("strategyPerformanceService");
        perfField.setAccessible(true);
        StrategyPerformanceService perfServiceMock = mock(StrategyPerformanceService.class);
        when(perfServiceMock.getBestPerformer(30)).thenReturn(null);
        perfField.set(telegramCommandHandler, perfServiceMock);

        java.lang.reflect.Field tradingStateField = TelegramCommandHandler.class.getDeclaredField("tradingStateService");
        tradingStateField.setAccessible(true);
        TradingStateService tradingStateMock = mock(TradingStateService.class);
        when(tradingStateMock.getActiveStrategyName()).thenReturn("NONE");
        tradingStateField.set(telegramCommandHandler, tradingStateMock);

        m.invoke(telegramCommandHandler);
        verify(telegramService).sendMessage(contains("No performance data available"));

        // Case 2: non-null best performer
        reset(telegramService);
        tw.gc.auto.equity.trader.entities.StrategyPerformance perf = tw.gc.auto.equity.trader.entities.StrategyPerformance.builder()
            .strategyName("BEST").sharpeRatio(1.2).maxDrawdownPct(2.0).totalReturnPct(10.0).totalTrades(5).build();
        when(perfServiceMock.getBestPerformer(30)).thenReturn(perf);
        when(tradingStateMock.getActiveStrategyName()).thenReturn("OTHER");

        m.invoke(telegramCommandHandler);
        verify(telegramService).sendMessage(contains("Best Performer"));
    }
}
