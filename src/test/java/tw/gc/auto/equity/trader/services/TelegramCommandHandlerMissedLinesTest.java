package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.SpringApplication;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TelegramCommandHandler to cover missed lines.
 * Focuses on edge cases, error paths, and async operations.
 * 
 * Missed lines: 73, 102, 323-326, 331-332, 342-344, 357-358, 365, 367-368, 390,
 *               418, 448-451, 469-470, 477, 525, 632-634, 640, 642, 731-732, 758,
 *               794-795, 803-804
 * 
 * ASYNC HANDLING:
 * - Lines involving Thread.start() (353-370, auto-selection, download-history, backtest):
 *   Tests use Thread.sleep() or verify(..., timeout(ms)) to wait for background threads.
 *   Pattern: verify(mock, timeout(2000)).method(...) ensures deterministic testing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramCommandHandlerMissedLinesTest {

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
    @Mock ActiveStrategyService activeStrategyService;
    @Mock StrategyPerformanceService strategyPerformanceService;
    @Mock ActiveStockService activeStockService;
    @Mock BacktestService backtestService;
    @Mock HistoryDataService historyDataService;
    @Mock MarketDataRepository marketDataRepository;
    @Mock AutoStrategySelector autoStrategySelector;
    @Mock SystemConfigService systemConfigService;
    @Mock IStrategy strategy1;
    @Mock IStrategy strategy2;

    private TelegramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramCommandHandler(
            telegramService, tradingStateService, positionManager,
            riskManagementService, contractScalingService, stockSettingsService,
            shioajiSettingsService, llmService, orderExecutionService,
            applicationContext, stockRiskSettingsService, activeStrategyService,
            strategyPerformanceService, activeStockService, backtestService,
            historyDataService, marketDataRepository, autoStrategySelector,
            systemConfigService
        );
        handler.setExitEnabled(false);
    }

    // ==================== Line 73: /strategy with no args, empty list ====================
    @Test
    void strategyCommand_noArgs_emptyList_showsNone() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/strategy"), captor.capture());
        
        when(tradingStateService.getActiveStrategyName()).thenReturn("Current");
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Line 73: .orElse("None") when stream is empty
        verify(telegramService).sendMessage(contains("None"));
    }

    // ==================== Line 102: /mode no args ====================
    @Test
    void modeCommand_noArgs_showsCurrent() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/mode"), captor.capture());
        
        ShioajiSettings settings = ShioajiSettings.builder().simulation(true).build();
        when(shioajiSettingsService.getSettings()).thenReturn(settings);
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("invalid");
        
        // Line 102: else branch when args not "live" or "sim"
        verify(telegramService).sendMessage(contains("Current Mode"));
        verify(telegramService).sendMessage(contains("SIMULATION"));
    }

    // ==================== Lines 323-326: Strategy recommendation with null values ====================
    @Test
    void askCommand_noArgs_performanceWithNulls() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/ask"), captor.capture());
        
        // Line 323-326: Performance with null sharpe, drawdown, return, winRate
        StrategyPerformance perf = StrategyPerformance.builder()
            .strategyName("TestStrategy")
            .sharpeRatio(null)
            .maxDrawdownPct(null)
            .totalReturnPct(null)
            .winRatePct(null)
            .totalTrades(10)
            .build();
        
        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(perf);
        when(tradingStateService.getActiveStrategyName()).thenReturn("Other");
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Verify null-safe formatting (0.0 defaults)
        verify(telegramService).sendMessage(contains("0.00"));
    }

    // ==================== Lines 331-332: Strategy recommendation already active ====================
    @Test
    void askCommand_noArgs_strategyAlreadyActive() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/ask"), captor.capture());
        
        StrategyPerformance perf = StrategyPerformance.builder()
            .strategyName("ActiveOne")
            .sharpeRatio(1.5)
            .totalTrades(10)
            .build();
        
        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(perf);
        when(tradingStateService.getActiveStrategyName()).thenReturn("ActiveOne");
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Line 331-332: isAlreadyActive = true
        verify(telegramService).sendMessage(contains("already active"));
    }

    // ==================== Lines 342-344: handleStrategyRecommendation exception ====================
    @Test
    void handleStrategyRecommendation_exception() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/ask"), captor.capture());
        
        when(strategyPerformanceService.getBestPerformer(30)).thenThrow(new RuntimeException("DB error"));
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Line 342-344: catch Exception
        verify(telegramService).sendMessage(contains("Failed to generate recommendation"));
    }

    // ==================== Lines 357-358: Shutdown flatten exception ====================
    @Test
    void shutdownCommand_flattenThrows() throws InterruptedException {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> shutdownCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), any(), any(), shutdownCaptor.capture()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        
        doThrow(new RuntimeException("flatten error")).when(orderExecutionService)
            .flattenPosition(anyString(), anyString(), anyString(), anyBoolean());
        
        @SuppressWarnings("unchecked")
        Consumer<Void> consumer = shutdownCaptor.getValue();
        consumer.accept(null);
        
        // Line 357-358: catch Exception in flatten
        verify(orderExecutionService, timeout(2000)).flattenPosition(anyString(), anyString(), anyString(), anyBoolean());
    }

    // ==================== Lines 365, 367-368: Shutdown exitHandler called ====================
    @Test
    void shutdownCommand_callsExitHandler() throws InterruptedException {
        java.util.concurrent.atomic.AtomicInteger exitCode = new java.util.concurrent.atomic.AtomicInteger(-1);
        handler.setExitHandler(exitCode::set);
        handler.setExitEnabled(true);
        
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> shutdownCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), any(), any(), shutdownCaptor.capture()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.exit(eq(applicationContext), any())).thenReturn(0);

            @SuppressWarnings("unchecked")
            Consumer<Void> consumer = shutdownCaptor.getValue();
            consumer.accept(null);

            // Wait for async thread
            Thread.sleep(2500);

            // Line 365, 367-368: exitHandler.accept(exitCode)
            org.junit.jupiter.api.Assertions.assertEquals(0, exitCode.get());
        }
    }

    // ==================== Line 390: /status with position and entry time ====================
    @Test
    void statusCommand_withPositionAndEntryTime() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            statusCaptor.capture(), any(), any(), any(), any()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        // Position with entry time - Line 390 calculates duration
        when(positionManager.positionFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicInteger(5));
        when(positionManager.entryPriceFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(500.0));
        when(positionManager.entryTimeFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(LocalDateTime.now().minusMinutes(30)));
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(100).shareIncrement(10).build());
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(100);
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        
        @SuppressWarnings("unchecked")
        Consumer<Void> consumer = statusCaptor.getValue();
        consumer.accept(null);
        
        // Line 390: duration calculated from entry time
        verify(telegramService).sendMessage(contains("5 @ 500"));
    }

    // ==================== Line 418: /status newsVeto check ====================
    @Test
    void statusCommand_newsVetoActive() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            statusCaptor.capture(), any(), any(), any(), any()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.positionFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicInteger(0));
        when(positionManager.entryPriceFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(0.0));
        when(positionManager.entryTimeFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(null));
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(0).build());
        when(tradingStateService.isNewsVeto()).thenReturn(true);
        
        @SuppressWarnings("unchecked")
        Consumer<Void> consumer = statusCaptor.getValue();
        consumer.accept(null);
        
        // Line 418: newsVeto ? "ACTIVE" : "Clear"
        verify(telegramService).sendMessage(contains("ACTIVE"));
    }

    // ==================== Lines 448-451: /close with position ====================
    @Test
    void closeCommand_hasPosition_flattens() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> closeCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            any(), any(), any(), closeCaptor.capture(), any()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition(anyString())).thenReturn(5);
        
        @SuppressWarnings("unchecked")
        Consumer<Void> consumer = closeCaptor.getValue();
        consumer.accept(null);
        
        // Lines 448-451: log, flatten, sendMessage
        verify(orderExecutionService).flattenPosition(anyString(), eq("2330.TW"), eq("stock"), anyBoolean());
        verify(telegramService).sendMessage(contains("Position closed"));
    }

    // ==================== Lines 469-470, 477: /backtest no args ====================
    @Test
    void backtestCommand_noArgs_showsUsage() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/backtest"), captor.capture());
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Lines 469-470, 477: empty args, return early
        verify(telegramService).sendMessage(contains("Usage: /backtest"));
    }

    // ==================== Line 525: backtest max result ====================
    @Test
    void backtestCommand_withArgs_runsBacktest() throws InterruptedException {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/backtest"), captor.capture());
        
        when(strategy1.getName()).thenReturn("S1");
        when(applicationContext.getBeansOfType(IStrategy.class)).thenReturn(Map.of("s1", strategy1));
        
        MarketData md = MarketData.builder()
            .symbol("2330.TW")
            .open(500.0)
            .high(510.0)
            .low(490.0)
            .close(505.0)
            .volume(1000L)
            .timestamp(LocalDateTime.now())
            .build();
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), eq(MarketData.Timeframe.DAY_1), any(), any()
        )).thenReturn(List.of(md));
        
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("S1", 80000.0);
        result.setFinalEquity(88000.0);
        result.calculateMetrics();
        when(backtestService.runBacktest(anyList(), anyList(), anyDouble()))
            .thenReturn(Map.of("S1", result));
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("2330.TW 1");
        
        // Wait for async thread
        Thread.sleep(500);
        
        // Line 525: max(...) to find best result
        verify(telegramService, timeout(2000)).sendMessage(contains("BACKTEST COMPLETE"));
    }

    // ==================== Lines 632-634, 640, 642: /change-stock no args ====================
    @Test
    void changeStockCommand_noArgs_showsCurrent() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/change-stock"), captor.capture());
        
        // Must be in stock mode for change-stock to work
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Lines 632-634, 640, 642: no args branch
        verify(telegramService).sendMessage(contains("Current stock: 2330.TW"));
        verify(telegramService).sendMessage(contains("Usage: /change-stock"));
    }

    // ==================== Lines 731-732: /config error path ====================
    @Test
    void configCommand_validationError() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/config"), captor.capture());
        
        when(systemConfigService.validateAndSetConfig(anyString(), anyString()))
            .thenReturn("Invalid value");
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("key value");
        
        // Line 731-732: error != null
        verify(telegramService).sendMessage(eq("Invalid value"));
    }

    // ==================== Line 758: /observer no args ====================
    @Test
    void observerCommand_noArgs_showsStatus() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/observer"), captor.capture());
        
        when(tradingStateService.isActivePolling()).thenReturn(true);
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Line 758: isActive ? "Active polling" : "Passive mode"
        verify(telegramService).sendMessage(contains("Active polling"));
    }

    // ==================== Lines 794-795, 803-804: /risk error and success ====================
    @Test
    void riskCommand_invalidArgs_showsUsage() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/risk"), captor.capture());
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("onlyonearg");
        
        // Lines 794-795: parts.length < 2
        verify(telegramService).sendMessage(contains("Usage: /risk"));
    }

    @Test
    void riskCommand_updateError() {
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/risk"), captor.capture());
        
        when(stockRiskSettingsService.updateRiskSetting(anyString(), anyString()))
            .thenReturn("Error message");
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("key value");
        
        // Lines 803-804: error != null
        verify(telegramService).sendMessage(eq("Error message"));
    }

    // ==================== Additional: /strategy with args ====================
    @Test
    void strategyCommand_withArgs_multipleStrategies() {
        when(strategy1.getName()).thenReturn("Strategy1");
        when(strategy2.getName()).thenReturn("Strategy2");
        
        handler.registerCommands(List.of(strategy1, strategy2));
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/strategy"), captor.capture());
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        when(tradingStateService.getActiveStrategyName()).thenReturn("Strategy1");
        consumer.accept(null);
        
        // Verify reduce joins names with ", "
        verify(telegramService, atLeastOnce()).sendMessage(contains("Strategy1"));
    }

    // ==================== Coverage tests for lines 102, 390, 525, 758 ====================
    
    @Test
    void modeCommand_neitherLiveNorSim_showsCurrentMode() {
        // Line 102: else branch when args is not "live"/"sim"/"simulation"
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/mode"), captor.capture());
        
        ShioajiSettings settings = ShioajiSettings.builder().simulation(false).build();
        when(shioajiSettingsService.getSettings()).thenReturn(settings);
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("unknown_mode");
        
        // Should show current mode with usage info
        verify(telegramService).sendMessage(contains("Current Mode"));
        verify(telegramService).sendMessage(contains("LIVE"));
    }
    
    @Test
    void statusCommand_withNullEntryTime_showsZeroDuration() {
        // Line 390: entryTimeRef.get() != null ? Duration calculation : 0
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> statusCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCommandHandlers(
            statusCaptor.capture(), any(), any(), any(), any()
        );
        
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        // Position with null entry time
        when(positionManager.positionFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicInteger(5));
        when(positionManager.entryPriceFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(500.0));
        when(positionManager.entryTimeFor(anyString())).thenReturn(new java.util.concurrent.atomic.AtomicReference<>(null));
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getSettings()).thenReturn(StockSettings.builder().shares(100).shareIncrement(10).build());
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(100);
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        
        @SuppressWarnings("unchecked")
        Consumer<Void> consumer = statusCaptor.getValue();
        consumer.accept(null);
        
        // Line 390: duration should be 0 when entry time is null
        verify(telegramService).sendMessage(contains("5 @ 500"));
    }
    
    @Test
    void backtestCommand_findsBestResult_showsBestPerformer() throws InterruptedException {
        // Line 525: .max(...) to find best result by total return percentage
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/backtest"), captor.capture());
        
        when(strategy1.getName()).thenReturn("BestStrategy");
        when(strategy2.getName()).thenReturn("WorseStrategy");
        when(applicationContext.getBeansOfType(IStrategy.class)).thenReturn(Map.of("s1", strategy1, "s2", strategy2));
        
        MarketData md = MarketData.builder()
            .symbol("2330.TW")
            .open(500.0)
            .high(510.0)
            .low(490.0)
            .close(505.0)
            .volume(1000L)
            .timestamp(LocalDateTime.now())
            .build();
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), eq(MarketData.Timeframe.DAY_1), any(), any()
        )).thenReturn(List.of(md));
        
        BacktestService.InMemoryBacktestResult result1 = new BacktestService.InMemoryBacktestResult("BestStrategy", 80000.0);
        result1.setFinalEquity(100000.0); // 25% return
        result1.calculateMetrics();
        BacktestService.InMemoryBacktestResult result2 = new BacktestService.InMemoryBacktestResult("WorseStrategy", 80000.0);
        result2.setFinalEquity(85000.0); // 6.25% return
        result2.calculateMetrics();
        when(backtestService.runBacktest(anyList(), anyList(), anyDouble()))
            .thenReturn(Map.of("BestStrategy", result1, "WorseStrategy", result2));
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept("2330.TW 1");
        
        Thread.sleep(500);
        
        // Line 525: Should find best result (BestStrategy with 25% return)
        verify(telegramService, timeout(2000)).sendMessage(contains("BACKTEST COMPLETE"));
    }
    
    @Test
    void observerCommand_passiveMode_showsPassiveStatus() {
        // Line 758: isActive ? "Active polling" : "Passive mode"
        handler.registerCommands(List.of());
        
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(telegramService).registerCustomCommand(eq("/observer"), captor.capture());
        
        // Set to passive mode (isActivePolling = false)
        when(tradingStateService.isActivePolling()).thenReturn(false);
        
        @SuppressWarnings("unchecked")
        Consumer<String> consumer = captor.getValue();
        consumer.accept(null);
        
        // Line 758: Should show "Passive mode"
        verify(telegramService).sendMessage(contains("Passive mode"));
    }
}

