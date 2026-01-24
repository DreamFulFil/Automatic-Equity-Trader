package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.testutil.AsyncTestHelper;
import tw.gc.auto.equity.trader.testutil.TelegramTestHelper;

import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Section C (TelegramCommandHandler): Focused tests for /backtest behavior.
 * Uses TelegramTestHelper to capture the registered handler and verify outgoing messages.
 */
@ExtendWith(MockitoExtension.class)
class TelegramCommandHandlerBacktestTest {

    @Mock private TelegramService telegramService;
    @Mock private TradingStateService tradingStateService;
    @Mock private PositionManager positionManager;
    @Mock private RiskManagementService riskManagementService;
    @Mock private ContractScalingService contractScalingService;
    @Mock private StockSettingsService stockSettingsService;
    @Mock private ShioajiSettingsService shioajiSettingsService;
    @Mock private LlmService llmService;
    @Mock private OrderExecutionService orderExecutionService;
    @Mock private ApplicationContext applicationContext;
    @Mock private StockRiskSettingsService stockRiskSettingsService;
    @Mock private ActiveStrategyService activeStrategyService;
    @Mock private StrategyPerformanceService strategyPerformanceService;
    @Mock private ActiveStockService activeStockService;
    @Mock private BacktestService backtestService;
    @Mock private HistoryDataService historyDataService;
    @Mock private MarketDataRepository marketDataRepository;
    @Mock private AutoStrategySelector autoStrategySelector;
    @Mock private SystemConfigService systemConfigService;

    private TelegramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramCommandHandler(
            telegramService,
            tradingStateService,
            positionManager,
            riskManagementService,
            contractScalingService,
            stockSettingsService,
            shioajiSettingsService,
            llmService,
            orderExecutionService,
            applicationContext,
            stockRiskSettingsService,
            activeStrategyService,
            strategyPerformanceService,
            activeStockService,
            backtestService,
            historyDataService,
            marketDataRepository,
            autoStrategySelector,
            systemConfigService
        );
    }

    @Test
    void backtestCommand_success_sendsBestPerformerSummary() {
        List<String> messages = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(telegramService).sendMessage(anyString());

        handler.registerCommands(List.of());
        Consumer<String> backtest = TelegramTestHelper.captureCommandHandler(telegramService, "/backtest");

        // Market history exists
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenReturn(List.of(mock(MarketData.class)));

        // At least one strategy bean
        when(applicationContext.getBeansOfType(IStrategy.class))
            .thenReturn(Map.of("s1", mock(IStrategy.class)));

        BacktestService.InMemoryBacktestResult best = new BacktestService.InMemoryBacktestResult("BEST", 1000.0);
        best.setFinalEquity(2000.0);
        best.trackEquity(1000.0);
        best.trackEquity(2000.0);
        best.addTrade(100.0);
        best.calculateMetrics();

        when(backtestService.runBacktest(anyList(), anyList(), anyDouble()))
            .thenReturn(Map.of("BEST", best));

        backtest.accept("2330.TW 10");

        assertThat(AsyncTestHelper.waitForAsync(2000, () ->
            messages.stream().anyMatch(m -> m.contains("BACKTEST COMPLETE"))
        )).isTrue();

        TelegramTestHelper.verifyMessageContains(telegramService,
            "Starting backtest for 2330.TW",
            "BACKTEST COMPLETE",
            "BEST PERFORMER",
            "Strategy: BEST"
        );
    }

    @Test
    void backtestCommand_noHistoricalData_sendsHelpfulError() {
        handler.registerCommands(List.of());
        Consumer<String> backtest = TelegramTestHelper.captureCommandHandler(telegramService, "/backtest");

        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenReturn(List.of());

        backtest.accept("2330.TW 10");

        assertThat(AsyncTestHelper.waitForAsync(2000, () -> {
            try {
                TelegramTestHelper.verifyMessageContains(telegramService, "No historical data found", "/download-history");
                return true;
            } catch (AssertionError e) {
                return false;
            }
        })).isTrue();
    }

    @Test
    void backtestCommand_emptyResults_sendsNoResultsMessage() {
        handler.registerCommands(List.of());
        Consumer<String> backtest = TelegramTestHelper.captureCommandHandler(telegramService, "/backtest");

        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenReturn(List.of(mock(MarketData.class)));

        when(applicationContext.getBeansOfType(IStrategy.class))
            .thenReturn(Map.of());

        when(backtestService.runBacktest(anyList(), anyList(), anyDouble()))
            .thenReturn(Map.of());

        backtest.accept("2330.TW 10");

        assertThat(AsyncTestHelper.waitForAsync(2000, () -> {
            try {
                TelegramTestHelper.verifyMessageSent(telegramService, "✅ Backtest complete but no results");
                return true;
            } catch (AssertionError e) {
                return false;
            }
        })).isTrue();
    }

    @Test
    void backtestCommand_exception_sendsErrorMessage() {
        handler.registerCommands(List.of());
        Consumer<String> backtest = TelegramTestHelper.captureCommandHandler(telegramService, "/backtest");

        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenReturn(List.of(mock(MarketData.class)));

        when(applicationContext.getBeansOfType(IStrategy.class))
            .thenReturn(Map.of("s1", mock(IStrategy.class)));

        when(backtestService.runBacktest(anyList(), anyList(), anyDouble()))
            .thenThrow(new RuntimeException("boom"));

        backtest.accept("2330.TW 10");

        assertThat(AsyncTestHelper.waitForAsync(2000, () -> {
            try {
                TelegramTestHelper.verifyMessageContains(telegramService, "Backtest failed: boom");
                return true;
            } catch (AssertionError e) {
                return false;
            }
        })).isTrue();
    }

    @Test
    void downloadHistory_defaultYears_shouldCallServiceWith10Years() throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(telegramService).sendMessage(anyString());

        handler.registerCommands(List.of());
        Consumer<String> download = TelegramTestHelper.captureCommandHandler(telegramService, "/download-history");

        when(historyDataService.downloadHistoricalData(eq("2330.TW"), eq(10)))
            .thenReturn(new HistoryDataService.DownloadResult("2330.TW", 10, 7, 3));

        download.accept("2330.TW");

        assertThat(AsyncTestHelper.waitForAsync(2000, () ->
            messages.stream().anyMatch(m -> m.contains("DOWNLOAD COMPLETE"))
        )).isTrue();

        verify(historyDataService).downloadHistoricalData("2330.TW", 10);
        TelegramTestHelper.verifyMessageContains(telegramService, "Downloading 10 years of data for 2330.TW", "DOWNLOAD COMPLETE");
    }

    @Test
    void downloadHistory_customYears_shouldCallServiceWithProvidedYears() throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(telegramService).sendMessage(anyString());

        handler.registerCommands(List.of());
        Consumer<String> download = TelegramTestHelper.captureCommandHandler(telegramService, "/download-history");

        when(historyDataService.downloadHistoricalData(eq("2330.TW"), eq(5)))
            .thenReturn(new HistoryDataService.DownloadResult("2330.TW", 10, 7, 3));

        download.accept("2330.TW 5");

        assertThat(AsyncTestHelper.waitForAsync(2000, () ->
            messages.stream().anyMatch(m -> m.contains("DOWNLOAD COMPLETE"))
        )).isTrue();

        verify(historyDataService).downloadHistoricalData("2330.TW", 5);
    }

    @Test
    void downloadHistory_invalidFormat_shouldSendUsageAndNotCallService() throws Exception {
        List<String> messages = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(telegramService).sendMessage(anyString());

        handler.registerCommands(List.of());
        Consumer<String> download = TelegramTestHelper.captureCommandHandler(telegramService, "/download-history");

        when(backtestService.fetchTop50Stocks()).thenReturn(List.of("2330.TW", "2454.TW"));

        download.accept(" ");

        assertThat(AsyncTestHelper.waitForAsync(2000, () ->
            messages.stream().anyMatch(m -> m.contains("DOWNLOAD HISTORY"))
        )).isTrue();

        verify(backtestService, timeout(2000)).fetchTop50Stocks();
        verify(historyDataService, timeout(2000))
            .downloadHistoricalDataForMultipleStocks(List.of("2330.TW", "2454.TW"), 10);
        verify(historyDataService, never()).downloadHistoricalData(anyString(), anyInt());
    }

    @Test
    void downloadHistory_exception_shouldSendErrorMessage() throws Exception {
        handler.registerCommands(List.of());
        Consumer<String> download = TelegramTestHelper.captureCommandHandler(telegramService, "/download-history");

        when(historyDataService.downloadHistoricalData(eq("2330.TW"), eq(1)))
            .thenThrow(new RuntimeException("boom"));

        download.accept("2330.TW 1");

        assertThat(AsyncTestHelper.waitForAsync(2000, () -> {
            try {
                TelegramTestHelper.verifyMessageContains(telegramService, "Download failed: boom");
                return true;
            } catch (AssertionError e) {
                return false;
            }
        })).isTrue();
    }

    @Test
    void newsCommand_shouldSendMockMessage() {
        handler.registerCommands(List.of());
        Consumer<String> news = TelegramTestHelper.captureCommandHandler(telegramService, "/news");

        news.accept(null);

        TelegramTestHelper.verifyMessageContains(telegramService, "News Analysis", "Fetching latest market news");
    }
}
