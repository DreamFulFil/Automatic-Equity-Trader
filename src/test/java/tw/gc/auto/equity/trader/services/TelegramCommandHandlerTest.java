package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import tw.gc.auto.equity.trader.services.ContractScalingService;
import tw.gc.auto.equity.trader.services.RiskManagementService;
import tw.gc.auto.equity.trader.services.StockRiskSettingsService;
import tw.gc.auto.equity.trader.services.ShioajiSettingsService;
import tw.gc.auto.equity.trader.services.StockSettingsService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.strategy.IStrategy;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramCommandHandlerTest {

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

    private TelegramCommandHandler telegramCommandHandler;

    @BeforeEach
    void setUp() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(positionManager.positionFor(anyString())).thenReturn(new AtomicInteger(0));
        when(positionManager.entryPriceFor(anyString())).thenReturn(new AtomicReference<>(0.0));
        when(positionManager.entryTimeFor(anyString())).thenReturn(new AtomicReference<>(null));
        
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(100);
        
        StockSettings stockSettings = StockSettings.builder().shares(100).shareIncrement(10).build();
        when(stockSettingsService.getSettings()).thenReturn(stockSettings);
        
        StockRiskSettings stockRiskSettings = StockRiskSettings.builder().dailyLossLimitTwd(5000).weeklyLossLimitTwd(15000).build();
        when(stockRiskSettingsService.getSettings()).thenReturn(stockRiskSettings);
        
        ShioajiSettings shioajiSettings = ShioajiSettings.builder().simulation(true).build();
        when(shioajiSettingsService.getSettings()).thenReturn(shioajiSettings);

        ActiveStrategyService mockActiveStrategyService = mock(ActiveStrategyService.class);
        StrategyPerformanceService mockStrategyPerformanceService = mock(StrategyPerformanceService.class);
        ActiveStockService mockActiveStockService = mock(ActiveStockService.class);
        when(mockActiveStockService.getActiveStock()).thenReturn("2454.TW");
        when(mockActiveStockService.getActiveSymbol(anyString())).thenReturn("2454.TW");
        
        telegramCommandHandler = new TelegramCommandHandler(
            telegramService, tradingStateService, positionManager, riskManagementService,
            contractScalingService, stockSettingsService, shioajiSettingsService, llmService,
            orderExecutionService, applicationContext, stockRiskSettingsService,
            mockActiveStrategyService, mockStrategyPerformanceService, mockActiveStockService,
            mock(tw.gc.auto.equity.trader.services.BacktestService.class),
            mock(tw.gc.auto.equity.trader.services.HistoryDataService.class),
            mock(tw.gc.auto.equity.trader.repositories.MarketDataRepository.class),
            mock(tw.gc.auto.equity.trader.services.AutoStrategySelector.class),
            mock(tw.gc.auto.equity.trader.services.SystemConfigService.class)
        );
        
        // Register commands to trigger internal registration logic (though we test handlers directly via reflection or by invoking registered callbacks if we could access them, but here we might need to expose handlers or test via side effects)
        // Since registerCommands registers callbacks, we can't easily invoke them unless we capture the callbacks.
        // For simplicity, I'll assume we can test the logic by extracting methods or just verifying registration.
        // But wait, the handlers are private methods in TelegramCommandHandler.
        // I should probably make them package-private or test via reflection.
        // Or better, I can capture the Consumer passed to registerCommandHandlers.
    }

    @Test
    void registerCommands_shouldRegisterAllHandlers() {
        telegramCommandHandler.registerCommands(Collections.emptyList());
        verify(telegramService).registerCommandHandlers(any(), any(), any(), any(), any());
        verify(telegramService, atLeastOnce()).registerCustomCommand(anyString(), any());
    }
    
    // To test the actual logic of handlers, I would need to invoke the private methods.
    // I'll use reflection for now as I did in TradingEngineTest.
    
    @Test
    void handleStatusCommand_shouldSendStatusMessage() throws Exception {
        invokePrivateMethod(telegramCommandHandler, "handleStatusCommand");
        verify(telegramService).sendMessage(contains("BOT STATUS"));
    }

    @Test
    void handlePauseCommand_shouldPauseTrading() throws Exception {
        invokePrivateMethod(telegramCommandHandler, "handlePauseCommand");
        verify(tradingStateService).setTradingPaused(true);
        verify(telegramService).sendMessage(contains("PAUSED"));
    }

    @Test
    void handleResumeCommand_shouldResumeTrading() throws Exception {
        invokePrivateMethod(telegramCommandHandler, "handleResumeCommand");
        verify(tradingStateService).setTradingPaused(false);
        verify(telegramService).sendMessage(contains("RESUMED"));
    }

    private void invokePrivateMethod(Object obj, String methodName, Object... args) throws Exception {
        java.lang.reflect.Method method = obj.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(obj, args);
    }
}
