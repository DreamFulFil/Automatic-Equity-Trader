package tw.gc.mtxfbot;

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
import tw.gc.mtxfbot.config.TradingProperties;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingEngineTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TelegramService telegramService;

    @Mock
    private TradingProperties tradingProperties;

    @Mock
    private TradingProperties.Window window;

    @Mock
    private TradingProperties.Stock stock;

    @Mock
    private TradingProperties.Risk risk;

    @Mock
    private TradingProperties.Bridge bridge;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ContractScalingService contractScalingService;

    @Mock
    private RiskManagementService riskManagementService;

    private TradingEngine tradingEngine;

    @BeforeEach
    void setUp() {
        when(tradingProperties.getWindow()).thenReturn(window);
        when(tradingProperties.getStock()).thenReturn(stock);
        when(tradingProperties.getRisk()).thenReturn(risk);
        when(tradingProperties.getBridge()).thenReturn(bridge);
        when(window.getStart()).thenReturn("11:30");
        when(window.getEnd()).thenReturn("13:00");
        when(stock.getInitialShares()).thenReturn(55);
        when(stock.getShareIncrement()).thenReturn(27);
        when(risk.getMaxPosition()).thenReturn(1);
        when(risk.getDailyLossLimit()).thenReturn(4500);
        when(risk.getWeeklyLossLimit()).thenReturn(15000);
        when(risk.getMaxHoldMinutes()).thenReturn(45);
        when(bridge.getUrl()).thenReturn("http://localhost:8888");
        
        when(contractScalingService.getMaxContracts()).thenReturn(1);
        when(contractScalingService.getLastEquity()).thenReturn(80000.0);
        when(contractScalingService.getLast30DayProfit()).thenReturn(0.0);
        
        when(riskManagementService.getWeeklyPnL()).thenReturn(0.0);
        when(riskManagementService.getDailyPnL()).thenReturn(0.0);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);

        tradingEngine = new TradingEngine(
                restTemplate,
                objectMapper,
                telegramService,
                tradingProperties,
                applicationContext,
                contractScalingService,
                riskManagementService
        );
    }

    // ==================== initialize() tests ====================

    @Test
    void initialize_whenBridgeConnected_shouldSetMarketDataConnected() throws Exception {
        // Given
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"status\":\"ok\"}");

        // When
        tradingEngine.initialize();

        // Then - startup message now says "Bot started" instead of "MTXF Lunch Bot started"
        verify(telegramService).sendMessage(contains("Bot started"));
        assertTrue(getFieldValue(tradingEngine, "marketDataConnected", Boolean.class));
    }

    @Test
    void initialize_whenBridgeConnectionFails_shouldSendErrorMessage() throws Exception {
        // Given
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // When
        tradingEngine.initialize();

        // Then
        verify(telegramService).sendMessage(contains("Python bridge connection failed"));
        assertFalse(getFieldValue(tradingEngine, "marketDataConnected", Boolean.class));
    }

    // ==================== tradingLoop() tests ====================

    @Test
    void tradingLoop_whenEmergencyShutdown_shouldReturnImmediately() throws Exception {
        // Given
        setFieldValue(tradingEngine, "emergencyShutdown", true);
        setFieldValue(tradingEngine, "marketDataConnected", true);

        // When
        tradingEngine.tradingLoop();

        // Then
        verifyNoInteractions(restTemplate);
    }

    @Test
    void tradingLoop_whenMarketDataNotConnected_shouldReturnImmediately() throws Exception {
        // Given
        setFieldValue(tradingEngine, "emergencyShutdown", false);
        setFieldValue(tradingEngine, "marketDataConnected", false);

        // When
        tradingEngine.tradingLoop();

        // Then
        verifyNoInteractions(restTemplate);
    }

    // ==================== checkRiskLimits() tests ====================

    @Test
    void checkRiskLimits_whenDailyLossLimitHit_shouldTriggerEmergencyShutdown() throws Exception {
        // Given
        setFieldValue(tradingEngine, "marketDataConnected", true);
        when(riskManagementService.isDailyLimitExceeded(4500)).thenReturn(true);
        when(riskManagementService.getDailyPnL()).thenReturn(-5000.0);

        // When
        invokePrivateMethod(tradingEngine, "checkRiskLimits");

        // Then
        verify(telegramService).sendMessage(contains("EMERGENCY SHUTDOWN"));
        assertTrue(getFieldValue(tradingEngine, "emergencyShutdown", Boolean.class));
    }

    @Test
    void checkRiskLimits_whenWithinLimit_shouldNotTriggerShutdown() throws Exception {
        // Given
        setFieldValue(tradingEngine, "marketDataConnected", true);
        when(riskManagementService.isDailyLimitExceeded(4500)).thenReturn(false);

        // When
        invokePrivateMethod(tradingEngine, "checkRiskLimits");

        // Then
        verify(telegramService, never()).sendMessage(contains("EMERGENCY SHUTDOWN"));
        assertFalse(getFieldValue(tradingEngine, "emergencyShutdown", Boolean.class));
    }

    // ==================== evaluateEntry() tests ====================

    @Test
    void evaluateEntry_whenNewsVetoActive_shouldNotEnterPosition() throws Exception {
        // Given
        AtomicBoolean cachedNewsVeto = getAtomicBooleanField(tradingEngine, "cachedNewsVeto");
        cachedNewsVeto.set(true);

        // When
        invokePrivateMethod(tradingEngine, "evaluateEntry");

        // Then
        verify(restTemplate, never()).getForObject(contains("/signal"), eq(String.class));
    }

    @Test
    void evaluateEntry_whenLowConfidence_shouldNotEnterPosition() throws Exception {
        // Given
        AtomicBoolean cachedNewsVeto = getAtomicBooleanField(tradingEngine, "cachedNewsVeto");
        cachedNewsVeto.set(false);

        String signalJson = "{\"direction\":\"LONG\",\"confidence\":0.5,\"current_price\":20000}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));

        // When
        invokePrivateMethod(tradingEngine, "evaluateEntry");

        // Then
        verify(restTemplate, never()).postForObject(anyString(), anyString(), eq(String.class));
    }

    @Test
    void evaluateEntry_whenHighConfidenceLong_shouldExecuteBuyOrder() throws Exception {
        // Given
        AtomicBoolean cachedNewsVeto = getAtomicBooleanField(tradingEngine, "cachedNewsVeto");
        cachedNewsVeto.set(false);

        String signalJson = "{\"direction\":\"LONG\",\"confidence\":0.75,\"current_price\":20000}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "evaluateEntry");

        // Then
        verify(restTemplate).postForObject(contains("/order"), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("ORDER FILLED"));
    }

    @Test
    void evaluateEntry_whenHighConfidenceShort_shouldExecuteSellOrder() throws Exception {
        // Given
        AtomicBoolean cachedNewsVeto = getAtomicBooleanField(tradingEngine, "cachedNewsVeto");
        cachedNewsVeto.set(false);

        String signalJson = "{\"direction\":\"SHORT\",\"confidence\":0.75,\"current_price\":20000}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "evaluateEntry");

        // Then
        verify(restTemplate).postForObject(contains("/order"), any(java.util.Map.class), eq(String.class));
    }

    // ==================== evaluateExit() tests ====================

    @Test
    void evaluateExit_whenStopLossHit_shouldFlattenPosition() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        AtomicReference<Double> entryPrice = getAtomicField(tradingEngine, "entryPrice");
        currentPosition.set(1);
        entryPrice.set(20100.0); // Entry price
        
        // Current price 20000, position 1, loss = (20000-20100)*1*50 = -5000 TWD > -500 stop
        String signalJson = "{\"current_price\":20000,\"exit_signal\":false}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "evaluateExit");

        // Then
        verify(telegramService).sendMessage(contains("POSITION CLOSED"));
    }

    @Test
    void evaluateExit_whenExitSignalTrue_shouldFlattenPosition() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        AtomicReference<Double> entryPrice = getAtomicField(tradingEngine, "entryPrice");
        currentPosition.set(1);
        entryPrice.set(20000.0);
        
        String signalJson = "{\"current_price\":20050,\"exit_signal\":true}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "evaluateExit");

        // Then
        verify(telegramService).sendMessage(contains("POSITION CLOSED"));
    }

    @Test
    void evaluateExit_whenProfitRunning_shouldNotExit() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        AtomicReference<Double> entryPrice = getAtomicField(tradingEngine, "entryPrice");
        currentPosition.set(1);
        entryPrice.set(20000.0);
        
        // Profit but no exit signal - should let it run
        String signalJson = "{\"current_price\":20100,\"exit_signal\":false}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));

        // When
        invokePrivateMethod(tradingEngine, "evaluateExit");

        // Then
        verify(restTemplate, never()).postForObject(anyString(), anyString(), eq(String.class));
    }

    // ==================== executeOrder() tests ====================

    @Test
    void executeOrder_whenBuyOrder_shouldIncrementPosition() throws Exception {
        // Given
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "executeOrderWithRetry", "BUY", 1, 20000.0);

        // Then
        AtomicInteger position = getAtomicIntField(tradingEngine, "currentPosition");
        assertEquals(1, position.get());
        verify(telegramService).sendMessage(contains("ORDER FILLED"));
    }

    @Test
    void executeOrder_whenSellOrder_shouldDecrementPosition() throws Exception {
        // Given
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "executeOrderWithRetry", "SELL", 1, 20000.0);

        // Then
        AtomicInteger position = getAtomicIntField(tradingEngine, "currentPosition");
        assertEquals(-1, position.get());
    }

    @Test
    void executeOrder_whenApiFails_shouldSendErrorMessage() throws Exception {
        // Given
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenThrow(new RuntimeException("Order rejected"));

        // When
        invokePrivateMethod(tradingEngine, "executeOrderWithRetry", "BUY", 1, 20000.0);

        // Then
        verify(telegramService).sendMessage(contains("Order failed after 3 attempts"));
    }

    // ==================== flattenPosition() tests ====================

    @Test
    void flattenPosition_whenNoPosition_shouldDoNothing() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        currentPosition.set(0);

        // When
        invokePrivateMethod(tradingEngine, "flattenPosition", "Test reason");

        // Then
        verifyNoInteractions(restTemplate);
    }

    @Test
    void flattenPosition_whenLongPosition_shouldSell() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        AtomicReference<Double> entryPrice = getAtomicField(tradingEngine, "entryPrice");
        currentPosition.set(1);
        entryPrice.set(20000.0);

        String signalJson = "{\"current_price\":20050}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "flattenPosition", "Test reason");

        // Then
        verify(restTemplate).postForObject(contains("/order"), any(java.util.Map.class), eq(String.class));
    }

    @Test
    void flattenPosition_whenShortPosition_shouldBuy() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        AtomicReference<Double> entryPrice = getAtomicField(tradingEngine, "entryPrice");
        currentPosition.set(-1);
        entryPrice.set(20000.0);

        String signalJson = "{\"current_price\":19950}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        invokePrivateMethod(tradingEngine, "flattenPosition", "Test reason");

        // Then
        verify(restTemplate).postForObject(contains("/order"), any(java.util.Map.class), eq(String.class));
    }

    // ==================== updateNewsVetoCache() tests ====================

    @Test
    void updateNewsVetoCache_whenVetoActive_shouldCacheAndNotify() throws Exception {
        // Given
        String newsJson = "{\"news_veto\":true,\"news_reason\":\"Market crash\",\"news_score\":0.1}";
        when(restTemplate.getForObject(contains("/signal/news"), eq(String.class))).thenReturn(newsJson);
        when(objectMapper.readTree(newsJson)).thenReturn(new ObjectMapper().readTree(newsJson));

        // When
        invokePrivateMethod(tradingEngine, "updateNewsVetoCache");

        // Then
        AtomicBoolean cachedVeto = getAtomicBooleanField(tradingEngine, "cachedNewsVeto");
        assertTrue(cachedVeto.get());
        verify(telegramService).sendMessage(contains("NEWS VETO ACTIVE"));
    }

    @Test
    void updateNewsVetoCache_whenNoVeto_shouldCacheWithoutNotification() throws Exception {
        // Given
        String newsJson = "{\"news_veto\":false,\"news_reason\":\"\",\"news_score\":0.6}";
        when(restTemplate.getForObject(contains("/signal/news"), eq(String.class))).thenReturn(newsJson);
        when(objectMapper.readTree(newsJson)).thenReturn(new ObjectMapper().readTree(newsJson));

        // When
        invokePrivateMethod(tradingEngine, "updateNewsVetoCache");

        // Then
        AtomicBoolean cachedVeto = getAtomicBooleanField(tradingEngine, "cachedNewsVeto");
        assertFalse(cachedVeto.get());
        verify(telegramService, never()).sendMessage(contains("NEWS VETO"));
    }

    // ==================== sendDailySummary() tests ====================

    @Test
    void sendDailySummary_whenProfitable_shouldShowProfitableStatus() throws Exception {
        // Given
        when(riskManagementService.getDailyPnL()).thenReturn(1500.0);

        // When
        invokePrivateMethod(tradingEngine, "sendDailySummary");

        // Then
        verify(telegramService).sendMessage(contains("Profitable"));
        verify(telegramService).sendMessage(contains("Solid day"));
    }

    @Test
    void sendDailySummary_whenLoss_shouldShowLossStatus() throws Exception {
        // Given
        when(riskManagementService.getDailyPnL()).thenReturn(-1000.0);

        // When
        invokePrivateMethod(tradingEngine, "sendDailySummary");

        // Then
        verify(telegramService).sendMessage(contains("Loss"));
    }

    @Test
    void sendDailySummary_whenExceptionalDay_shouldCelebrate() throws Exception {
        // Given
        when(riskManagementService.getDailyPnL()).thenReturn(5000.0);

        // When
        invokePrivateMethod(tradingEngine, "sendDailySummary");

        // Then
        verify(telegramService).sendMessage(contains("EXCEPTIONAL DAY"));
    }

    // ==================== shutdownPythonBridge() tests ====================

    // OBSOLETE TEST - shutdownPythonBridge method no longer exists
    /*
    @Test
    void shutdownPythonBridge_shouldCallShutdownEndpoint() throws Exception {
        // Given
        when(restTemplate.postForObject(anyString(), anyString(), eq(String.class)))
                .thenReturn("{\"status\":\"shutting_down\"}");

        // When
        invokePrivateMethod(tradingEngine, "shutdownPythonBridge");

        // Then
        verify(restTemplate).postForObject(contains("/shutdown"), eq(""), eq(String.class));
    }

    @Test
    void shutdownPythonBridge_whenFails_shouldNotThrow() throws Exception {
        // Given
        when(restTemplate.postForObject(anyString(), anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // When & Then - should not throw
        assertDoesNotThrow(() -> invokePrivateMethod(tradingEngine, "shutdownPythonBridge"));
    }
    */

    // ==================== autoFlatten() tests ====================

    @Test
    void autoFlatten_shouldFlattenAndSendSummary() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        currentPosition.set(0); // No position to flatten

        // When
        // Note: We can't fully test autoFlatten because it calls System.exit()
        // But we can verify the methods it calls
        invokePrivateMethod(tradingEngine, "sendDailySummary");

        // Then
        verify(telegramService).sendMessage(contains("DAILY SUMMARY"));
    }

    // ==================== shutdown() tests ====================

    @Test
    void shutdown_shouldFlattenAndNotifyBridge() throws Exception {
        // Given
        AtomicInteger currentPosition = getAtomicIntField(tradingEngine, "currentPosition");
        currentPosition.set(0);

        // When
        tradingEngine.shutdown();

        // Then
        verify(telegramService).sendMessage(contains("Bot stopped"));
    }

    // ==================== Helper methods ====================

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName, Class<T> type) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(obj);
    }

    private void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<Double> getAtomicField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicReference<Double>) field.get(obj);
    }

    private AtomicBoolean getAtomicBooleanField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicBoolean) field.get(obj);
    }

    private AtomicInteger getAtomicIntField(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicInteger) field.get(obj);
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
            // Try without parameters
            method = obj.getClass().getDeclaredMethod(methodName);
            args = new Object[0];
        }
        method.setAccessible(true);
        method.invoke(obj, args);
    }
}
