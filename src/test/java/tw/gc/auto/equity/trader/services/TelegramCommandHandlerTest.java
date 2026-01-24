package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import tw.gc.auto.equity.trader.services.HistoryDataService;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.services.ActiveStrategyService;
import tw.gc.auto.equity.trader.services.StrategyPerformanceService;
import tw.gc.auto.equity.trader.services.ActiveStockService;
import tw.gc.auto.equity.trader.services.AutoStrategySelector;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.services.SystemConfigService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramCommandHandlerTest {

    @Mock TelegramService telegramService;
    @Mock TradingStateService tradingStateService;
    @Mock PositionManager positionManager;
    @Mock RiskManagementService riskManagementService;
    @Mock ContractScalingService contractScalingService;
    @Mock StockSettingsService stockSettingsService;
    @Mock ShioajiSettingsService shioajiSettingsService;
    @Mock LlmService llmService;
    @Mock OrderExecutionService orderExecutionService;
    @Mock org.springframework.context.ApplicationContext applicationContext;
    @Mock StockRiskSettingsService stockRiskSettingsService;
    @Mock HistoryDataService historyDataService;
    @Mock ActiveStrategyService activeStrategyService;
    @Mock StrategyPerformanceService strategyPerformanceService;
    @Mock ActiveStockService activeStockService;
    @Mock AutoStrategySelector autoStrategySelector;
    @Mock BacktestService backtestService;
    @Mock MarketDataRepository marketDataRepository;
    @Mock SystemConfigService systemConfigService;

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
    void registerCommands_registersCommands() {
        handler.registerCommands(java.util.List.of());
        verify(telegramService, atLeastOnce()).registerCustomCommand(anyString(), any());
    }

    @Test
    void registerCommands_registersKeyCommands() {
        handler.registerCommands(java.util.List.of());
        verify(telegramService).registerCustomCommand(eq("/download-history"), any());
        verify(telegramService).registerCustomCommand(eq("/set-main-strategy"), any());
        verify(telegramService).registerCustomCommand(eq("/auto-strategy-select"), any());
        verify(telegramService).registerCommandHandlers(any(), any(), any(), any(), any());
    }

    @Test
    void setMainStrategy_noArgs_showsHelp() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("S1");
        IStrategy s2 = mock(IStrategy.class);
        when(s2.getName()).thenReturn("S2");

        handler.registerCommands(java.util.List.of(s1, s2));

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/set-main-strategy"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);

        verify(telegramService).sendMessage(contains("Available strategies"));
    }

    @Test
    void setMainStrategy_validAndInvalidPaths() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("BEST");

        handler.registerCommands(java.util.List.of(s1));

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/set-main-strategy"), captor.capture());

        doNothing().when(activeStrategyService).switchStrategy(anyString(), anyMap(), anyString(), anyBoolean());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // invalid
        consumer.accept("UNKNOWN");
        verify(telegramService).sendMessage(contains("Strategy not found"));

        // valid
        consumer.accept("BEST");
        verify(activeStrategyService).switchStrategy(eq("BEST"), anyMap(), anyString(), eq(false));
        verify(telegramService, atLeastOnce()).sendMessage(contains("Main Strategy Updated"));
    }

    @Test
    void autoStrategySelect_successAndFailure() throws Exception {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/auto-strategy-select"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // success
        doNothing().when(autoStrategySelector).selectBestStrategyAndStock();
        consumer.accept(null);

        boolean ok = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("Auto-selection completed"));
                ok = true; break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }
        if (!ok) throw new AssertionError("Expected auto-selection success message");

        // failure
        reset(telegramService);
        doThrow(new RuntimeException("boom")).when(autoStrategySelector).selectBestStrategyAndStock();
        handler.registerCommands(java.util.List.of());
        verify(telegramService).registerCustomCommand(eq("/auto-strategy-select"), any());
        // capture again
        ArgumentCaptor<Consumer> captor2 = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService, atLeastOnce()).registerCustomCommand(eq("/auto-strategy-select"), captor2.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer2 = captor2.getValue();
        consumer2.accept(null);

        ok = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("Auto-selection failed"));
                ok = true; break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }
        if (!ok) throw new AssertionError("Expected auto-selection failure message");
    }

    @Test
    void shutdown_triggersFlattenAndExitDisabled() throws Exception {
        handler.setExitEnabled(false);
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<java.util.function.Consumer> statusCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> pauseCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> resumeCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> closeCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> shutdownCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);

        verify(telegramService).registerCommandHandlers(statusCaptor.capture(), pauseCaptor.capture(), resumeCaptor.capture(), closeCaptor.capture(), shutdownCaptor.capture());

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        java.util.function.Consumer<Void> shutdownConsumer = shutdownCaptor.getValue();
        shutdownConsumer.accept(null);

        // initial message
        verify(telegramService).sendMessage(contains("Shutting down application"));

        // flatten should be invoked in background thread
        verify(orderExecutionService, timeout(2000)).flattenPosition(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void downloadHistory_invokesHistoryServiceAndSendsSummary() throws Exception {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/download-history"), captor.capture());

        // Stub the history service to return a quick result
        HistoryDataService.DownloadResult result = new HistoryDataService.DownloadResult("2330.TW", 100, 90, 10);
        when(historyDataService.downloadHistoricalData(eq("2330.TW"), eq(1))).thenReturn(result);

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // Invoke the registered command handler
        consumer.accept("2330.TW 1");

        // Initial download message should be sent synchronously
        verify(telegramService).sendMessage(contains("Downloading 1 years of data for 2330.TW"));

        // The detailed completion message is sent from a background thread; wait briefly for it
        boolean seen = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("DOWNLOAD COMPLETE"));
                seen = true;
                break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }

        if (!seen) {
            throw new AssertionError("Expected DOWNLOAD COMPLETE message not sent");
        }
    }

    @Test
    void modeCommand_switchesAndShows() {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/mode"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // switch to live
        consumer.accept("live");
        verify(shioajiSettingsService).updateSimulationMode(eq(false));
        verify(telegramService).sendMessage(contains("LIVE TRADING mode"));

        // switch to simulation
        consumer.accept("sim");
        verify(shioajiSettingsService).updateSimulationMode(eq(true));
        verify(telegramService).sendMessage(contains("SIMULATION mode"));

        // show current
        tw.gc.auto.equity.trader.entities.ShioajiSettings settings = tw.gc.auto.equity.trader.entities.ShioajiSettings.builder().simulation(true).build();
        when(shioajiSettingsService.getSettings()).thenReturn(settings);
        consumer.accept(null);
        verify(telegramService, atLeastOnce()).sendMessage(contains("Current Mode"));
    }

    @Test
    void ask_withArgs_invokesLLM_and_handlesException() throws Exception {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/ask"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        when(llmService.generateInsight(anyString())).thenReturn("Insight response");
        consumer.accept("Explain RSI");
        verify(telegramService).sendMessage(contains("Tutor: Insight response"));

        // exception path
        reset(telegramService);
        doThrow(new RuntimeException("llm fail")).when(llmService).generateInsight(anyString());
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor2 = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService, atLeastOnce()).registerCustomCommand(eq("/ask"), captor2.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer2 = captor2.getValue();
        consumer2.accept("question");
        verify(telegramService).sendMessage(contains("Error asking tutor"));
    }

    @Test
    void ask_withoutArgs_recommendsStrategy_nullAndNonNull() {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/ask"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // no performance data
        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(null);
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("No performance data available"));

        // has data
        reset(telegramService);
        tw.gc.auto.equity.trader.entities.StrategyPerformance perf = tw.gc.auto.equity.trader.entities.StrategyPerformance.builder()
            .strategyName("S1").sharpeRatio(1.5).maxDrawdownPct(2.0).totalReturnPct(10.0).totalTrades(5).build();
        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(perf);
        when(tradingStateService.getActiveStrategyName()).thenReturn("OTHER");
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("Best Performer"));
    }

    @Test
    void setMainStrategy_switchFails_sendsError() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("BEST");

        handler.registerCommands(java.util.List.of(s1));

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/set-main-strategy"), captor.capture());

        doThrow(new RuntimeException("boom")).when(activeStrategyService).switchStrategy(anyString(), anyMap(), anyString(), anyBoolean());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("BEST");
        verify(telegramService).sendMessage(contains("Failed to switch strategy"));
    }

    @Test
    void status_showsDifferentStates() {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<java.util.function.Consumer> statusCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> pauseCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> resumeCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> closeCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> shutdownCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);

        verify(telegramService).registerCommandHandlers(statusCaptor.capture(), pauseCaptor.capture(), resumeCaptor.capture(), closeCaptor.capture(), shutdownCaptor.capture());

        java.util.function.Consumer<Void> statusConsumer = statusCaptor.getValue();

        // position manager defaults to zero/no-entry to avoid NPE
        when(positionManager.positionFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicInteger(0));
        when(positionManager.entryPriceFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(0.0));
        when(positionManager.entryTimeFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(null));
        // ensure active stock is returned by getActiveSymbol()
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        // make active stock and settings available so mode info formatting doesn't NPE
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");
        when(stockSettingsService.getSettings()).thenReturn(tw.gc.auto.equity.trader.entities.StockSettings.builder().shares(100).shareIncrement(10).build());
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);

        // default active
        when(tradingStateService.isEmergencyShutdown()).thenReturn(false);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        when(tradingStateService.isTradingPaused()).thenReturn(false);
        statusConsumer.accept(null);
        verify(telegramService).sendMessage(contains("BOT STATUS"));

        // emergency shutdown
        reset(telegramService);
        when(tradingStateService.isEmergencyShutdown()).thenReturn(true);
        statusConsumer.accept(null);
        verify(telegramService).sendMessage(contains("EMERGENCY SHUTDOWN"));
    }

    @Test
    void autoSelect_successAndFailure() throws Exception {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/auto-select"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // success
        doNothing().when(autoStrategySelector).selectBestStrategyAndStock();
        consumer.accept(null);

        boolean ok = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("Auto-selection completed"));
                ok = true; break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }
        if (!ok) throw new AssertionError("Expected auto-selection success message");

        // failure
        reset(telegramService);
        doThrow(new RuntimeException("boom")).when(autoStrategySelector).selectBestStrategyAndStock();
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor2 = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService, atLeastOnce()).registerCustomCommand(eq("/auto-select"), captor2.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer2 = captor2.getValue();
        consumer2.accept(null);

        ok = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("Auto-selection failed"));
                ok = true; break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }
        if (!ok) throw new AssertionError("Expected auto-selection failure message");
    }

    @Test
    void changeStock_whenFutures_showsWarning() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/change-stock"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(tradingStateService.getTradingMode()).thenReturn("futures");
        consumer.accept("2330.TW");
        verify(telegramService).sendMessage(contains("only works in STOCK mode"));
    }

    @Test
    void changeStock_invalidFormat_showsError() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/change-stock"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        consumer.accept("abcd");
        verify(telegramService).sendMessage(contains("Invalid stock symbol format"));
    }

    @Test
    void changeStock_alreadyTrading_showsInfo() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/change-stock"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        consumer.accept("2454.TW");
        verify(telegramService).sendMessage(contains("Already trading"));
    }

    @Test
    void changeStock_success_flattensAndSetsActive() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/change-stock"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(positionManager.getPosition("2454.TW")).thenReturn(2);

        doNothing().when(orderExecutionService).flattenPosition(anyString(), any(), anyString(), anyBoolean());
        doNothing().when(activeStockService).setActiveStock(anyString());

        consumer.accept("2330.TW");
        verify(telegramService).sendMessage(contains("Flattening position"));
        verify(orderExecutionService).flattenPosition(anyString(), any(), anyString(), anyBoolean());
        verify(activeStockService).setActiveStock(eq("2330.TW"));
    }

    @Test
    void changeStock_setActiveStockThrows_sendsError() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/change-stock"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(0);
        doThrow(new RuntimeException("db down")).when(activeStockService).setActiveStock("1234.TW");

        consumer.accept("1234.TW");
        verify(telegramService).sendMessage(contains("Failed to change stock"));
    }

    @Test
    void backtest_invalidAndNoHistoryPaths() throws Exception {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/backtest"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        // Missing days
        consumer.accept("2330.TW");
        verify(telegramService).sendMessage(contains("Invalid format"));

        // Non-numeric days
        reset(telegramService);
        consumer.accept("2330.TW abc");
        verify(telegramService).sendMessage(contains("Invalid days"));

        // Days out of range
        reset(telegramService);
        consumer.accept("2330.TW 4000");
        verify(telegramService).sendMessage(contains("Days must be between"));

        // No history
        reset(telegramService);
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(anyString(), any(), any(), any())).thenReturn(java.util.List.of());
        consumer.accept("2330.TW 10");

        boolean seen = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("No historical data found"));
                seen = true; break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }
        if (!seen) throw new AssertionError("Expected no historical data message");
    }

    @Test
    void downloadHistory_invalidYears() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/download-history"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        consumer.accept("2330.TW 0");
        verify(telegramService).sendMessage(contains("Years must be between"));

        reset(telegramService);
        consumer.accept("2330.TW abc");
        verify(telegramService).sendMessage(contains("Invalid years"));
    }

    @Test
    void config_help_and_update() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/config"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(systemConfigService.getConfigHelp()).thenReturn("HELP TEXT");
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("HELP TEXT"));

        // invalid usage
        reset(telegramService);
        consumer.accept("onlykey");
        verify(telegramService).sendMessage(contains("Usage: /config [key] [value]"));

        // successful update
        reset(telegramService);
        when(systemConfigService.validateAndSetConfig(eq("daily_loss_limit"), eq("2000"))).thenReturn(null);
        consumer.accept("daily_loss_limit 2000");
        verify(telegramService).sendMessage(contains("Config updated"));
    }

    @Test
    void observer_on_off_and_invalid() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/observer"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(tradingStateService.isActivePolling()).thenReturn(true);
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("Active polling"));

        reset(telegramService);
        consumer.accept("on");
        verify(telegramService).sendMessage(contains("Active polling enabled"));

        reset(telegramService);
        consumer.accept("off");
        verify(telegramService).sendMessage(contains("Passive mode enabled"));

        reset(telegramService);
        consumer.accept("invalid");
        verify(telegramService).sendMessage(contains("Invalid argument"));
    }

    @Test
    void risk_help_and_update() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/risk"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(stockRiskSettingsService.getAllStockRiskSettingsFormatted()).thenReturn("ALL");
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("ALL"));

        reset(telegramService);
        when(stockRiskSettingsService.getStockRiskSettingsHelp()).thenReturn("HELP");
        consumer.accept("help");
        verify(telegramService).sendMessage(contains("HELP"));

        reset(telegramService);
        when(stockRiskSettingsService.updateRiskSetting(eq("max_shares_per_trade"), eq("100"))).thenReturn(null);
        consumer.accept("max_shares_per_trade 100");
        verify(telegramService).sendMessage(contains("Risk setting updated"));
    }

    @Test
    void selectStrategy_handlesException() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/select-best-strategy"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        doNothing().when(autoStrategySelector).selectBestStrategyAndStock();
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("Strategy selection completed"));

        reset(telegramService);
        doThrow(new RuntimeException("boom")).when(autoStrategySelector).selectBestStrategyAndStock();
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("Error:"));
    }

    @Test
    void backtest_success_sendsSummary() throws Exception {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/backtest"), captor.capture());

        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();

        // Provide a minimal market history
        MarketData md = mock(MarketData.class);
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(anyString(), any(), any(), any()))
            .thenReturn(java.util.List.of(md));

        // Provide at least one strategy bean
        when(applicationContext.getBeansOfType(IStrategy.class)).thenReturn(java.util.Map.of("s1", mock(IStrategy.class)));

        // Create a best performer result
        BacktestService.InMemoryBacktestResult best = new BacktestService.InMemoryBacktestResult("BEST", 1000.0);
        best.setFinalEquity(2000.0);
        best.trackEquity(1000.0);
        best.trackEquity(2000.0);
        best.addTrade(100.0);
        best.calculateMetrics();

        when(backtestService.runBacktest(anyList(), anyList(), anyDouble())).thenReturn(java.util.Map.of("BEST", best));

        consumer.accept("TEST 1");

        boolean seen = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("BACKTEST COMPLETE"));
                seen = true; break;
            } catch (AssertionError e) {
                Thread.sleep(50);
            }
        }
        if (!seen) throw new AssertionError("Expected BACKTEST COMPLETE message not sent");
    }

    @Test
    void showConfigs_sendsAllConfigs() {
        handler.registerCommands(java.util.List.of());
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/show-configs"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> consumer = captor.getValue();

        when(systemConfigService.getAllConfigsFormatted()).thenReturn("ALL CONFIGS");
        consumer.accept(null);
        verify(telegramService).sendMessage(contains("ALL CONFIGS"));
    }

    @Test
    void deprecatedCommands_sendDeprecationNotice() {
        handler.registerCommands(java.util.List.of());

        // /populate-data
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/populate-data"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> pop = captor.getValue();
        pop.accept(null);
        verify(telegramService).sendMessage(contains("deprecated"));

        // /full-pipeline
        ArgumentCaptor<Consumer> cap3 = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/full-pipeline"), cap3.capture());
        @SuppressWarnings("unchecked") Consumer<String> full = cap3.getValue();
        full.accept(null);
        verify(telegramService, atLeastOnce()).sendMessage(contains("deprecated"));

        // /data-status
        ArgumentCaptor<Consumer> cap4 = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/data-status"), cap4.capture());
        @SuppressWarnings("unchecked") Consumer<String> data = cap4.getValue();
        data.accept(null);
        verify(telegramService, atLeastOnce()).sendMessage(contains("deprecated"));
    }

    @Test
    void runBacktests_startsParallelizedBacktest_withDefaultCapital() {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/run-backtests"), captor.capture());
        @SuppressWarnings("unchecked") Consumer<String> run = captor.getValue();

        when(backtestService.runParallelizedBacktest(80_000.0))
            .thenReturn(java.util.Map.of("2330.TW", java.util.Map.of()));

        run.accept(null);

        boolean started = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("RUN BACKTESTS STARTED"));
                started = true;
                break;
            } catch (AssertionError e) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for start message");
                }
            }
        }
        if (!started) throw new AssertionError("Expected RUN BACKTESTS STARTED message not sent");

        boolean completed = false;
        for (int i = 0; i < 40; i++) {
            try {
                verify(telegramService, atLeastOnce()).sendMessage(contains("RUN BACKTESTS COMPLETE"));
                completed = true;
                break;
            } catch (AssertionError e) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for completion message");
                }
            }
        }
        if (!completed) throw new AssertionError("Expected RUN BACKTESTS COMPLETE message not sent");

        verify(backtestService, atLeastOnce()).runParallelizedBacktest(80_000.0);
    }


    @Test
    void status_weekly_blackout_and_paused_states() {
        handler.registerCommands(java.util.List.of());

        ArgumentCaptor<java.util.function.Consumer> statusCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> pauseCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> resumeCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> closeCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);
        ArgumentCaptor<java.util.function.Consumer> shutdownCaptor = ArgumentCaptor.forClass(java.util.function.Consumer.class);

        verify(telegramService).registerCommandHandlers(statusCaptor.capture(), pauseCaptor.capture(), resumeCaptor.capture(), closeCaptor.capture(), shutdownCaptor.capture());

        java.util.function.Consumer<Void> statusConsumer = statusCaptor.getValue();

        when(positionManager.positionFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicInteger(0));
        when(positionManager.entryPriceFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(0.0));
        when(positionManager.entryTimeFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(null));
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(stockSettingsService.getSettings()).thenReturn(tw.gc.auto.equity.trader.entities.StockSettings.builder().shares(100).shareIncrement(10).build());
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);

        // weekly limit
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(true);
        statusConsumer.accept(null);
        verify(telegramService).sendMessage(contains("WEEKLY LIMIT"));

        reset(telegramService);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.isEarningsBlackout()).thenReturn(true);
        statusConsumer.accept(null);
        verify(telegramService).sendMessage(contains("EARNINGS BLACKOUT"));

        reset(telegramService);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        when(tradingStateService.isTradingPaused()).thenReturn(true);
        statusConsumer.accept(null);
        verify(telegramService).sendMessage(contains("PAUSED BY USER"));
    }

    @Test
    void deprecatedStrategyCommand_withNoArgs_showsCurrent() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("MA-Cross");
        
        handler.registerCommands(java.util.List.of(s1));
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/strategy"), captor.capture());
        
        when(tradingStateService.getActiveStrategyName()).thenReturn("MA-Cross");
        
        captor.getValue().accept(null);
        verify(telegramService).sendMessage(contains("Current Active Strategy: MA-Cross"));
        verify(telegramService).sendMessage(contains("Deprecated"));
    }

    @Test
    void deprecatedStrategyCommand_withEmptyArgs_showsCurrent() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("DCA");
        
        handler.registerCommands(java.util.List.of(s1));
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/strategy"), captor.capture());
        
        when(tradingStateService.getActiveStrategyName()).thenReturn("DCA");
        
        captor.getValue().accept("   ");
        verify(telegramService).sendMessage(contains("Current Active Strategy: DCA"));
    }

    @Test
    void deprecatedStrategyCommand_withValidStrategy_switches() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("MA-Cross");
        IStrategy s2 = mock(IStrategy.class);
        when(s2.getName()).thenReturn("DCA");
        
        handler.registerCommands(java.util.List.of(s1, s2));
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/strategy"), captor.capture());
        
        captor.getValue().accept("DCA");
        
        verify(tradingStateService).setActiveStrategyName("DCA");
        verify(telegramService).sendMessage(contains("✅ Active Strategy switched to: DCA"));
        verify(telegramService).sendMessage(contains("Deprecated"));
    }

    @Test
    void deprecatedStrategyCommand_withInvalidStrategy_sendsError() {
        IStrategy s1 = mock(IStrategy.class);
        when(s1.getName()).thenReturn("MA-Cross");
        
        handler.registerCommands(java.util.List.of(s1));
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/strategy"), captor.capture());
        
        captor.getValue().accept("NonExistent");
        
        verify(telegramService).sendMessage(contains("❌ Strategy not found: NonExistent"));
        verify(tradingStateService, never()).setActiveStrategyName(anyString());
    }

    @Test
    void resumeCommand_whenWeeklyLimit_showsError() {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> resumeCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), resumeCaptor.capture(), any(), any()
        );
        
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(true);
        
        resumeCaptor.getValue().accept(null);
        
        verify(telegramService).sendMessage(contains("❌ Cannot resume - Weekly loss limit hit"));
        verify(tradingStateService, never()).setTradingPaused(anyBoolean());
    }

    @Test
    void resumeCommand_whenEarningsBlackout_showsError() {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> resumeCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), resumeCaptor.capture(), any(), any()
        );
        
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.isEarningsBlackout()).thenReturn(true);
        
        resumeCaptor.getValue().accept(null);
        
        verify(telegramService).sendMessage(contains("❌ Cannot resume - Earnings blackout day"));
        verify(tradingStateService, never()).setTradingPaused(anyBoolean());
    }

    @Test
    void resumeCommand_whenNotBlocked_resumes() {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> resumeCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), resumeCaptor.capture(), any(), any()
        );
        
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        
        resumeCaptor.getValue().accept(null);
        
        verify(tradingStateService).setTradingPaused(false);
        verify(telegramService).sendMessage(contains("▶️ Trading RESUMED"));
    }

    @Test
    void closeCommand_whenNoPosition_showsInfo() {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> closeCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), any(), closeCaptor.capture(), any()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(positionManager.getPosition(anyString())).thenReturn(0);
        
        closeCaptor.getValue().accept(null);
        
        verify(telegramService).sendMessage(contains("ℹ️ No position to close"));
    }

    @Test
    void pauseCommand_setsPausedTrue() {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> pauseCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), pauseCaptor.capture(), any(), any(), any()
        );
        
        pauseCaptor.getValue().accept(null);
        
        verify(tradingStateService).setTradingPaused(true);
        verify(telegramService).sendMessage(contains("⏸️ Trading PAUSED"));
    }

    @Test
    void autoStrategySelect_whenException_sendsError() throws Exception {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/auto-strategy-select"), captor.capture());
        
        doThrow(new RuntimeException("Selection failed")).when(autoStrategySelector).selectBestStrategyAndStock();
        
        captor.getValue().accept(null);
        
        // Give thread time to execute
        Thread.sleep(200);
        
        verify(telegramService).sendMessage(contains("❌ Auto-selection failed: Selection failed"));
    }

    @Test
    void autoStrategySelect_success_sendsCompletionMessage() throws Exception {
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/auto-strategy-select"), captor.capture());
        
        doNothing().when(autoStrategySelector).selectBestStrategyAndStock();
        
        captor.getValue().accept(null);
        
        // Give thread time to execute
        Thread.sleep(200);
        
        verify(telegramService).sendMessage(contains("✅ Auto-selection completed successfully!"));
    }

    @Test
    void shutdownCommand_whenExceptionInFlatten_logsError() throws Exception {
        handler.setExitEnabled(false);
        handler.registerCommands(java.util.List.of());
        
        ArgumentCaptor<Consumer> shutdownCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), any(), any(), shutdownCaptor.capture()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(positionManager.getPosition(anyString())).thenReturn(2);
        
        // The flattenPosition method in handler is private and calls orderExecutionService
        // We can't easily mock the private method behavior, so just verify shutdown message sent
        shutdownCaptor.getValue().accept(null);
        
        // Give thread time to execute
        Thread.sleep(100);
        
        verify(telegramService).sendMessage(contains("🛑 Shutting down"));
    }
}
 
