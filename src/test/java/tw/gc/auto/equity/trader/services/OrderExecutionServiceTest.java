package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.Trade;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderExecutionServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private TradingProperties tradingProperties;
    @Mock
    private TradingProperties.Bridge bridge;
    @Mock
    private TelegramService telegramService;
    @Mock
    private DataLoggingService dataLoggingService;
    @Mock
    private RiskManagementService riskManagementService;
    @Mock
    private StockRiskSettingsService stockRiskSettingsService;
    @Mock
    private EarningsBlackoutService earningsBlackoutService;
    @Mock
    private tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService taiwanComplianceService;
    @Mock
    private LlmService llmService;

    private PositionManager positionManager;
    private OrderExecutionService orderExecutionService;

    @BeforeEach
    void setUp() {
        when(tradingProperties.getBridge()).thenReturn(bridge);
        when(bridge.getUrl()).thenReturn("http://localhost:8888");
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(false);
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(false);

        positionManager = new PositionManager();
        orderExecutionService = new OrderExecutionService(
                restTemplate, objectMapper, tradingProperties, telegramService,
                dataLoggingService, positionManager, riskManagementService, stockRiskSettingsService,
                earningsBlackoutService, taiwanComplianceService, llmService
        );
    }

    @Test
    void executeOrderWithRetry_whenBuyOrder_shouldIncrementPosition() {
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        assertEquals(1, positionManager.getPosition("2454.TW"));
        verify(telegramService).sendMessage(contains("ORDER FILLED"));
        verify(dataLoggingService).logTrade(any(Trade.class));
    }

    @Test
    void executeOrderWithRetry_whenSellOrder_shouldDecrementPosition() {
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.executeOrderWithRetry("SELL", 1, 20000.0, "2454.TW", false, false);

        assertEquals(-1, positionManager.getPosition("2454.TW"));
    }

    @Test
    void executeOrderWithRetry_whenApiFails_shouldRetryAndNotify() {
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenThrow(new RuntimeException("Order rejected"));

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        verify(restTemplate, atLeast(1)).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("Order failed"));
    }

    @Test
    void executeOrderWithRetry_whenEarningsBlackout_shouldBlockNewEntry() {
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(true);

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("EARNINGS BLACKOUT"));
    }

    @Test
    void flattenPosition_whenLongPosition_shouldSell() throws Exception {
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20000.0, java.time.LocalDateTime.now());

        String signalJson = "{\"current_price\":20050}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.flattenPosition("Test reason", "2454.TW", "stock", false);

        verify(restTemplate).postForObject(contains("/order"), argThat(obj -> {
            java.util.Map map = (java.util.Map) obj;
            return map.get("action").equals("SELL") && map.get("quantity").equals("1");
        }), eq(String.class));
        assertEquals(0, positionManager.getPosition("2454.TW"));
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenBuyWithSufficientBalance_shouldReturnRequestedQuantity() throws Exception {
        String accountJson = "{\"status\":\"ok\",\"available_margin\":100000}";
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(objectMapper.readTree(accountJson)).thenReturn(new ObjectMapper().readTree(accountJson));

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", 2, 20000.0, "2454.TW", "stock");

        assertEquals(2, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenBuyWithInsufficientBalance_shouldReduceQuantity() throws Exception {
        String accountJson = "{\"status\":\"ok\",\"available_margin\":30000}";
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(objectMapper.readTree(accountJson)).thenReturn(new ObjectMapper().readTree(accountJson));

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", 5, 20000.0, "2454.TW", "stock");

        assertEquals(1, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenBuyWithZeroBalance_shouldReturnZero() throws Exception {
        String accountJson = "{\"status\":\"ok\",\"available_margin\":0}";
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(objectMapper.readTree(accountJson)).thenReturn(new ObjectMapper().readTree(accountJson));

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", 5, 20000.0, "2454.TW", "stock");

        assertEquals(0, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenAccountStatusNotOk_shouldReturnRequestedQuantity() throws Exception {
        String accountJson = "{\"status\":\"error\",\"available_margin\":0}";
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(objectMapper.readTree(accountJson)).thenReturn(new ObjectMapper().readTree(accountJson));

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", 5, 20000.0, "2454.TW", "stock");

        assertEquals(5, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenSellWithSufficientPosition_shouldReturnRequestedQuantity() {
        positionManager.setPosition("2454.TW", 5);

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("SELL", 3, 20000.0, "2454.TW", "stock");

        assertEquals(3, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenSellWithInsufficientPosition_shouldReduceToAvailable() {
        positionManager.setPosition("2454.TW", 2);

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("SELL", 5, 20000.0, "2454.TW", "stock");

        assertEquals(2, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenSellWithNoPosition_shouldReturnZero() {
        positionManager.setPosition("2454.TW", 0);

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("SELL", 5, 20000.0, "2454.TW", "stock");

        assertEquals(0, adjusted);
    }

    @Test
    void checkBalanceAndAdjustQuantity_whenExceptionOccurs_shouldReturnRequestedQuantity() {
        when(restTemplate.getForObject(contains("/account"), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        int adjusted = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", 5, 20000.0, "2454.TW", "stock");

        assertEquals(5, adjusted);
    }

    @Test
    void executeOrderWithRetry_whenTaiwanComplianceVeto_shouldBlockOrder() {
        TaiwanStockComplianceService.ComplianceResult vetoResult = 
                TaiwanStockComplianceService.ComplianceResult.blocked("Odd-lot day trading requires 500k capital");
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(true);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(300000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean(), anyDouble())).thenReturn(vetoResult);

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "MA_3_8");

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("TAIWAN COMPLIANCE"));
    }

    @Test
    void executeOrderWithRetry_whenAiVetoEnabled_shouldCheckAndBlock() {
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(true);
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(false);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(1000000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean(), anyDouble()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        Map<String, Object> vetoResult = new HashMap<>();
        vetoResult.put("veto", true);
        vetoResult.put("reason", "High volatility detected");
        when(llmService.executeTradeVeto(any())).thenReturn(vetoResult);

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "TestStrategy");

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("AI RISK VETO"));
    }

    @Test
    void executeOrderWithRetry_whenAiVetoApproves_shouldExecute() {
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(true);
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(false);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(1000000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean(), anyDouble()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        Map<String, Object> vetoResult = new HashMap<>();
        vetoResult.put("veto", false);
        vetoResult.put("reason", "Trade approved");
        when(llmService.executeTradeVeto(any())).thenReturn(vetoResult);
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "TestStrategy");

        verify(restTemplate).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("ORDER FILLED"));
    }

    @Test
    void executeOrderWithRetry_whenAiVetoFails_shouldBlockTrade() {
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(true);
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(false);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(1000000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean(), anyDouble()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        when(llmService.executeTradeVeto(any())).thenThrow(new RuntimeException("LLM service unavailable"));

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "TestStrategy");

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("AI VETO CHECK FAILED"));
    }

    @Test
    void executeOrderWithRetry_withLegacySignature_shouldWork() {
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        verify(restTemplate).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
    }

    @Test
    void executeOrderWithRetry_whenIsExitOrder_shouldNotLogTrade() {
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.executeOrderWithRetry("SELL", 1, 20000.0, "2454.TW", true, false);

        verify(dataLoggingService, never()).logTrade(any(Trade.class));
    }

    @Test
    void flattenPosition_whenNoPosition_shouldReturnEarly() {
        positionManager.setPosition("2454.TW", 0);

        orderExecutionService.flattenPosition("Test reason", "2454.TW", "stock", false);

        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
    }

    @Test
    void flattenPosition_whenShortPosition_shouldBuy() throws Exception {
        positionManager.setPosition("TXFR1", -1);
        positionManager.updateEntry("TXFR1", 18000.0, java.time.LocalDateTime.now());

        String signalJson = "{\"current_price\":17950}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.flattenPosition("Stop loss", "TXFR1", "futures", false);

        verify(restTemplate).postForObject(contains("/order"), argThat(obj -> {
            java.util.Map map = (java.util.Map) obj;
            return map.get("action").equals("BUY") && map.get("quantity").equals("1");
        }), eq(String.class));
    }

    @Test
    void flattenPosition_whenFuturesMode_shouldCalculateWithMultiplier() throws Exception {
        positionManager.setPosition("TXFR1", 1);
        positionManager.updateEntry("TXFR1", 18000.0, java.time.LocalDateTime.now());

        String signalJson = "{\"current_price\":18100}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.flattenPosition("Profit target", "TXFR1", "futures", false);

        verify(riskManagementService).recordPnL(eq("TXFR1"), eq(5000.0), anyInt());
    }

    @Test
    void flattenPosition_whenWeeklyLimitHit_shouldNotifyTelegram() throws Exception {
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20000.0, java.time.LocalDateTime.now());

        String signalJson = "{\"current_price\":19500}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(true);
        when(riskManagementService.getWeeklyPnL()).thenReturn(-5000.0);

        orderExecutionService.flattenPosition("Weekly limit", "2454.TW", "stock", false);

        verify(telegramService).sendMessage(contains("WEEKLY LOSS LIMIT HIT"));
    }

    @Test
    void flattenPosition_whenExceptionOccurs_shouldLogError() throws Exception {
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20000.0, java.time.LocalDateTime.now());

        when(restTemplate.getForObject(contains("/signal"), eq(String.class)))
                .thenThrow(new RuntimeException("Signal service down"));

        orderExecutionService.flattenPosition("Test", "2454.TW", "stock", false);

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
    }

    @Test
    void isInEarningsBlackout_whenBlackoutActive_shouldReturnTrue() {
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(true);

        boolean result = orderExecutionService.isInEarningsBlackout();

        assertTrue(result);
    }

    @Test
    void isInEarningsBlackout_whenExceptionOccurs_shouldReturnFalse() {
        when(earningsBlackoutService.isDateBlackout(any())).thenThrow(new RuntimeException("Service unavailable"));

        boolean result = orderExecutionService.isInEarningsBlackout();

        assertFalse(result);
    }

    @Test
    void getEarningsBlackoutStatus_shouldDelegateToIsInEarningsBlackout() {
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(true);

        boolean result = orderExecutionService.getEarningsBlackoutStatus();

        assertTrue(result);
    }
}
