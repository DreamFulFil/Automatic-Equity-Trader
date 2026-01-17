package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TelegramProperties;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.telegram.GoLiveStateManager;
import tw.gc.auto.equity.trader.services.telegram.TelegramCommandRegistry;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.testutil.AsyncTestHelper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Section C (TelegramCommandHandler): Integration-style tests that verify TelegramService command dispatching
 * end-to-end (pollUpdates -> dispatchCommand -> legacy/custom handler -> outgoing message).
 *
 * Note: This repo uses Telegram Bot HTTP API JSON, so we model "Update" and "Message" as JSON payloads.
 */
@ExtendWith(MockitoExtension.class)
class TelegramCommandHandlerIntegrationTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramProperties telegramProperties;
    @Mock private StockSettingsService stockSettingsService;
    @Mock private GoLiveStateManager goLiveStateManager;

    // TelegramCommandHandler deps
    @Mock private TradingStateService tradingStateService;
    @Mock private PositionManager positionManager;
    @Mock private RiskManagementService riskManagementService;
    @Mock private ContractScalingService contractScalingService;
    @Mock private ShioajiSettingsService shioajiSettingsService;
    @Mock private LlmService llmService;
    @Mock private OrderExecutionService orderExecutionService;
    @Mock private ConfigurableApplicationContext applicationContext;
    @Mock private StockRiskSettingsService stockRiskSettingsService;
    @Mock private ActiveStrategyService activeStrategyService;
    @Mock private StrategyPerformanceService strategyPerformanceService;
    @Mock private ActiveStockService activeStockService;
    @Mock private BacktestService backtestService;
    @Mock private HistoryDataService historyDataService;
    @Mock private MarketDataRepository marketDataRepository;
    @Mock private AutoStrategySelector autoStrategySelector;
    @Mock private SystemConfigService systemConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TelegramCommandRegistry commandRegistry;
    private TelegramService telegramService;
    private TelegramCommandHandler handler;

    private final List<String> sentTexts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        commandRegistry = new TelegramCommandRegistry();
        commandRegistry.registerCommands();

        telegramService = new TelegramService(
            restTemplate,
            telegramProperties,
            objectMapper,
            stockSettingsService,
            commandRegistry,
            goLiveStateManager
        );

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

        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        lenient().when(telegramProperties.getChatId()).thenReturn("123456789");

        // Capture outgoing messages by intercepting HTTP body
        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            HttpEntity<Map<String, Object>> entity = (HttpEntity<Map<String, Object>>) invocation.getArgument(1);
            sentTexts.add((String) entity.getBody().get("text"));
            return "{}";
        }).when(restTemplate).postForObject(contains("sendMessage"), any(HttpEntity.class), eq(String.class));

        // SpringApplication.exit() queries ExitCodeGenerator beans
        lenient().when(applicationContext.getBeansOfType(org.springframework.boot.ExitCodeGenerator.class)).thenReturn(Map.of());

        handler.registerCommands(List.of());
    }

    @Test
    void pollUpdates_backtestCustomCommand_shouldDispatchAndSendCompletion() {
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenReturn(List.of(mock(MarketData.class)));

        when(applicationContext.getBeansOfType(IStrategy.class))
            .thenReturn(Map.of("s1", mock(IStrategy.class)));

        BacktestService.InMemoryBacktestResult best = new BacktestService.InMemoryBacktestResult("BEST", 1000.0);
        best.setFinalEquity(1200.0);
        best.trackEquity(1000.0);
        best.trackEquity(1200.0);
        best.addTrade(10.0);
        best.calculateMetrics();

        when(backtestService.runBacktest(anyList(), anyList(), anyDouble()))
            .thenReturn(Map.of("BEST", best));

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/backtest 2330.TW 1"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(2500, () ->
            sentTexts.stream().anyMatch(t -> t.contains("BACKTEST COMPLETE"))
        )).isTrue();
    }

    @Test
    void pollUpdates_downloadHistoryCustomCommand_shouldCallServiceAndSendCompletion() throws Exception {
        when(historyDataService.downloadHistoricalData(eq("2330.TW"), eq(5)))
            .thenReturn(new HistoryDataService.DownloadResult("2330.TW", 10, 7, 3));

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/download-history 2330.TW 5"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(2500, () ->
            sentTexts.stream().anyMatch(t -> t.contains("DOWNLOAD COMPLETE"))
        )).isTrue();

        verify(historyDataService).downloadHistoricalData("2330.TW", 5);
    }

    @Test
    void pollUpdates_statusCommand_shouldInvokeLegacyHandler_andIncludeHoldTime() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");

        when(positionManager.positionFor("2330.TW"))
            .thenReturn(new java.util.concurrent.atomic.AtomicInteger(100));
        when(positionManager.entryPriceFor("2330.TW"))
            .thenReturn(new java.util.concurrent.atomic.AtomicReference<>(500.0));

        LocalDateTime entry = LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(15);
        when(positionManager.entryTimeFor("2330.TW"))
            .thenReturn(new java.util.concurrent.atomic.AtomicReference<>(entry));

        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(100).shareIncrement(10).build());
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(100);
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(contractScalingService.getLast30DayProfit()).thenReturn(0.0);
        when(riskManagementService.getDailyPnL()).thenReturn(0.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(0.0);

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/status"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("BOT STATUS") && t.contains("held "))
        )).isTrue();
    }

    @Test
    void pollUpdates_shutdownCommand_shouldFlattenAndExitGracefully() {
        handler.setExitEnabled(false);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");
        when(tradingStateService.isEmergencyShutdown()).thenReturn(false);

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/shutdown"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1500, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Shutting down application"))
        )).isTrue();

        assertThat(AsyncTestHelper.waitForAsync(3500, () -> {
            try {
                verify(orderExecutionService).flattenPosition(anyString(), any(), any(), anyBoolean());
                return true;
            } catch (AssertionError e) {
                return false;
            }
        })).isTrue();
    }

    @Test
    void pollUpdates_shutdownCommand_whenFlattenThrows_shouldStillExitGracefully() {
        handler.setExitEnabled(false);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");

        doThrow(new RuntimeException("flatten failed"))
            .when(orderExecutionService).flattenPosition(anyString(), any(), any(), anyBoolean());

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/shutdown"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(3500, () -> {
            try {
                verify(orderExecutionService).flattenPosition(anyString(), any(), any(), anyBoolean());
                return true;
            } catch (AssertionError e) {
                return false;
            }
        })).isTrue();
    }

    @Test
    void pollUpdates_shutdownCommand_whenInterruptedDuringSleep_shouldHitOuterCatch() {
        handler.setExitEnabled(false);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/shutdown"));

        try (MockedConstruction<Thread> mocked = mockConstruction(Thread.class, (mock, context) -> {
            Runnable r = (Runnable) context.arguments().getFirst();
            doAnswer(invocation -> {
                Thread.currentThread().interrupt();
                try {
                    r.run();
                } finally {
                    Thread.interrupted();
                }
                return null;
            }).when(mock).start();
        })) {
            telegramService.pollUpdates();
        }

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Shutting down application"))
        )).isTrue();
    }

    @Test
    void pollUpdates_unauthorizedChat_shouldIgnore() {
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "999999999", "/status"));

        telegramService.pollUpdates();

        assertThat(sentTexts).isEmpty();
    }

    @Test
    void pollUpdates_whenApiReturnsMalformedJson_shouldBeCaught() {
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn("{not-json");

        telegramService.pollUpdates();

        assertThat(sentTexts).isEmpty();
    }

    @Test
    void pollUpdates_askCommand_withBlankArgs_shouldTriggerRecommendation_andWhenAlreadyActive_shouldHitBranch() {
        when(tradingStateService.getActiveStrategyName()).thenReturn("BEST");

        tw.gc.auto.equity.trader.entities.StrategyPerformance best = mock(tw.gc.auto.equity.trader.entities.StrategyPerformance.class);
        when(best.getStrategyName()).thenReturn("BEST");
        when(best.getTotalTrades()).thenReturn(1);
        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(best);

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/ask"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("STRATEGY RECOMMENDATION") && t.contains("already active"))
        )).isTrue();
    }

    @Test
    void pollUpdates_closeCommand_whenHasPosition_shouldFlattenAndConfirm() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(1);

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/close"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Position closed"))
        )).isTrue();

        verify(orderExecutionService).flattenPosition(anyString(), any(), any(), anyBoolean());
    }

    @Test
    void pollUpdates_backtestCommand_withNoArgs_shouldSendUsage() {
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/backtest"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Usage: /backtest"))
        )).isTrue();
    }

    @Test
    void pollUpdates_changeStockCommand_withBlankArgs_shouldShowCurrentStock() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/change-stock"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("CHANGE ACTIVE STOCK") && t.contains("2330.TW"))
        )).isTrue();
    }

    @Test
    void pollUpdates_askCommand_whenRecommendationThrows_shouldSendError() {
        when(strategyPerformanceService.getBestPerformer(30)).thenThrow(new RuntimeException("boom"));

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/ask"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Failed to generate recommendation"))
        )).isTrue();
    }

    @Test
    void pollUpdates_unknownCommand_shouldSendHelpMessage() {
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/not-a-command"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Unknown command"))
        )).isTrue();
    }

    @Test
    void pollUpdates_configCommand_whenValidateReturnsError_shouldSendError() {
        when(systemConfigService.validateAndSetConfig(eq("daily_loss_limit"), eq("abc")))
            .thenReturn("bad value");

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/config daily_loss_limit abc"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("bad value"))
        )).isTrue();
    }

    @Test
    void pollUpdates_riskCommand_whenMissingValue_shouldSendUsage() {
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/risk daily_loss_limit"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("Usage: /risk"))
        )).isTrue();
    }

    @Test
    void pollUpdates_riskCommand_whenUpdateReturnsError_shouldSendError() {
        when(stockRiskSettingsService.updateRiskSetting(eq("daily_loss_limit"), eq("1")))
            .thenReturn("invalid");

        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(1, "123456789", "/risk daily_loss_limit 1"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(1000, () ->
            sentTexts.stream().anyMatch(t -> t.contains("invalid"))
        )).isTrue();
    }

    @Test
    void pollUpdates_shutdownCommand_exitEnabledTrue_shouldInvokeExitHandler() {
        handler.setExitEnabled(true);

        java.util.concurrent.CountDownLatch exitLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger exitCodeRef = new java.util.concurrent.atomic.AtomicInteger(-1);
        handler.setExitHandler(code -> {
            exitCodeRef.set(code);
            exitLatch.countDown();
        });

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");

        // Ensure our /shutdown update isn't discarded by TelegramService's lastUpdateId (which may have been set by clearOldMessages()).
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class)))
            .thenReturn(buildUpdatesResponse(999, "123456789", "/shutdown"));

        telegramService.pollUpdates();

        assertThat(AsyncTestHelper.waitForAsync(6000, () -> exitLatch.getCount() == 0)).isTrue();
        assertThat(exitCodeRef.get()).isEqualTo(0);
    }

    private static String buildUpdatesResponse(int updateId, String chatId, String text) {
        return String.format(
            "{\"ok\":true,\"result\":[{\"update_id\":%d,\"message\":{\"chat\":{\"id\":\"%s\"},\"text\":\"%s\"}}]}",
            updateId, chatId, text.replace("\\", "\\\\").replace("\"", "\\\"")
        );
    }
}
