package tw.gc.auto.equity.trader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.entities.RiskSettings;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.services.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingEngineTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private TelegramService telegramService;
    @Mock private TradingProperties tradingProperties;
    @Mock private TradingProperties.Bridge bridge;
    @Mock private ApplicationContext applicationContext;
    @Mock private ContractScalingService contractScalingService;
    @Mock private RiskManagementService riskManagementService;
    @Mock private StockSettingsService stockSettingsService;
    @Mock private RiskSettingsService riskSettingsService;
    @Mock private DataLoggingService dataLoggingService;
    @Mock private EndOfDayStatisticsService endOfDayStatisticsService;
    @Mock private DailyStatisticsRepository dailyStatisticsRepository;
    @Mock private ShioajiSettingsService shioajiSettingsService;
    
    // New Services Mocks
    @Mock private TelegramCommandHandler telegramCommandHandler;
    @Mock private OrderExecutionService orderExecutionService;
    @Mock private StrategyManager strategyManager;
    @Mock private ReportingService reportingService;

    // Real instances for state
    private TradingStateService tradingStateService;
    private PositionManager positionManager;

    private TradingEngineService tradingEngine;

    @BeforeEach
    void setUp() {
        System.setProperty("trading.mode", "futures");
        
        when(tradingProperties.getBridge()).thenReturn(bridge);
        when(bridge.getUrl()).thenReturn("http://localhost:8888");
        
        when(contractScalingService.getMaxContracts()).thenReturn(1);
        when(contractScalingService.getLastEquity()).thenReturn(80000.0);
        
        when(riskManagementService.getWeeklyPnL()).thenReturn(0.0);
        when(riskManagementService.getDailyPnL()).thenReturn(0.0);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);

        StockSettings stockSettings = StockSettings.builder().shares(55).shareIncrement(27).build();
        when(stockSettingsService.getSettings()).thenReturn(stockSettings);
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(55);

        RiskSettings riskSettings = RiskSettings.builder()
                .maxPosition(1)
                .dailyLossLimit(4500)
                .weeklyLossLimit(15000)
                .maxHoldMinutes(45)
                .build();
        when(riskSettingsService.getSettings()).thenReturn(riskSettings);
        when(riskSettingsService.getDailyLossLimit()).thenReturn(4500);
        when(riskSettingsService.getWeeklyLossLimit()).thenReturn(15000);
        when(riskSettingsService.getMaxHoldMinutes()).thenReturn(45);

        ShioajiSettings shioajiSettings = ShioajiSettings.builder().simulation(true).build();
        when(shioajiSettingsService.getSettings()).thenReturn(shioajiSettings);

        tradingStateService = new TradingStateService();
        positionManager = new PositionManager();

        tradingEngine = new TradingEngineService(
            restTemplate, objectMapper, telegramService, tradingProperties, applicationContext,
            contractScalingService, riskManagementService, stockSettingsService, riskSettingsService,
            dataLoggingService, endOfDayStatisticsService, dailyStatisticsRepository, shioajiSettingsService,
            tradingStateService, telegramCommandHandler, orderExecutionService, positionManager,
            strategyManager, reportingService
        );
    }

    @Test
    void initialize_whenBridgeConnected_shouldSetMarketDataConnected() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"status\":\"ok\"}");

        tradingEngine.initialize();

        verify(telegramService).sendMessage(contains("TRADING SYSTEM STARTED"));
        assertTrue(tradingStateService.isMarketDataConnected());
    }

    @Test
    void tradingLoop_whenEmergencyShutdown_shouldReturnImmediately() {
        tradingStateService.setEmergencyShutdown(true);
        tradingStateService.setMarketDataConnected(true);

        tradingEngine.tradingLoop();

        verify(restTemplate, never()).getForObject(contains("/signal"), any());
    }

    @Test
    void checkRiskLimits_whenDailyLossLimitHit_shouldTriggerEmergencyShutdown() throws Exception {
        tradingStateService.setMarketDataConnected(true);
        when(riskManagementService.isDailyLimitExceeded(4500)).thenReturn(true);
        when(riskManagementService.getDailyPnL()).thenReturn(-5000.0);

        invokePrivateMethod(tradingEngine, "checkRiskLimits");

        verify(telegramService).sendMessage(contains("EMERGENCY SHUTDOWN"));
        assertTrue(tradingStateService.isEmergencyShutdown());
        verify(orderExecutionService).flattenPosition(eq("Daily loss limit"), anyString(), anyString(), eq(true));
    }

    @Test
    void evaluateEntry_whenNewsVetoActive_shouldNotEnterPosition() throws Exception {
        tradingStateService.setNewsVeto(true, "Bad news");

        invokePrivateMethod(tradingEngine, "evaluateEntry");

        verify(restTemplate, never()).getForObject(contains("/signal"), eq(String.class));
    }

    @Test
    void evaluateEntry_whenHighConfidenceLong_shouldExecuteBuyOrder() throws Exception {
        tradingStateService.setNewsVeto(false, "");
        tradingStateService.setTradingMode("stock");

        String signalJson = "{\"direction\":\"LONG\",\"confidence\":0.75,\"current_price\":20000}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        
        when(orderExecutionService.checkBalanceAndAdjustQuantity(anyString(), anyInt(), anyDouble(), anyString(), anyString()))
            .thenReturn(1);

        invokePrivateMethod(tradingEngine, "evaluateEntry");

        verify(orderExecutionService).executeOrderWithRetry(eq("BUY"), eq(1), eq(20000.0), eq("2454.TW"), eq(false), eq(false));
    }

    @Test
    void evaluateExit_whenStopLossHit_shouldFlattenPosition() throws Exception {
        tradingStateService.setTradingMode("stock");
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20100.0, java.time.LocalDateTime.now().minusMinutes(10));
        
        // Current price 20000, position 1, loss = -100 > -500? Wait, 20000-20100 = -100.
        // Stop loss threshold is -500 * 1 = -500.
        // -100 is > -500. So NOT stop loss.
        // Let's make loss bigger. Entry 21000. Loss -1000.
        positionManager.updateEntry("2454.TW", 21000.0, java.time.LocalDateTime.now().minusMinutes(10));

        String signalJson = "{\"current_price\":20000,\"exit_signal\":false}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));

        invokePrivateMethod(tradingEngine, "evaluateExit");

        verify(orderExecutionService).flattenPosition(eq("Stop-loss"), eq("2454.TW"), eq("stock"), eq(false));
    }

    @Test
    void updateNewsVetoCache_whenVetoActive_shouldCacheAndNotify() throws Exception {
        String newsJson = "{\"news_veto\":true,\"news_reason\":\"Market crash\",\"news_score\":0.1}";
        when(restTemplate.getForObject(contains("/signal/news"), eq(String.class))).thenReturn(newsJson);
        when(objectMapper.readTree(newsJson)).thenReturn(new ObjectMapper().readTree(newsJson));

        invokePrivateMethod(tradingEngine, "updateNewsVetoCache");

        assertTrue(tradingStateService.isNewsVeto());
        verify(telegramService).sendMessage(contains("NEWS VETO ACTIVE"));
    }

    @Test
    void shutdown_shouldFlattenAndNotifyBridge() {
        tradingEngine.shutdown();

        verify(orderExecutionService).flattenPosition(eq("System shutdown"), anyString(), anyString(), anyBoolean());
        verify(telegramService).sendMessage(contains("Bot stopped"));
    }

    private void invokePrivateMethod(Object obj, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Integer) {
                paramTypes[i] = int.class;
            } else if (args[i] instanceof Double) {
                paramTypes[i] = double.class;
            } else {
                paramTypes[i] = args[i].getClass();
            }
        }
        
        Method method;
        try {
            method = obj.getClass().getDeclaredMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            method = obj.getClass().getDeclaredMethod(methodName);
            args = new Object[0];
        }
        method.setAccessible(true);
        method.invoke(obj, args);
    }
}
