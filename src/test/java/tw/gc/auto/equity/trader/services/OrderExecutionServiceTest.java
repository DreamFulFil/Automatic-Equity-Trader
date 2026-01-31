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
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.entities.Trade;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("null")
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
    @Mock
    private TradeRiskScorer tradeRiskScorer;
    @Mock
    private FundamentalFilter fundamentalFilter;

    private PositionManager positionManager;
    private OrderExecutionService orderExecutionService;

    @BeforeEach
    void setUp() {
        when(tradingProperties.getBridge()).thenReturn(bridge);
        when(bridge.getUrl()).thenReturn("http://localhost:8888");
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(false);
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(false);
        when(stockRiskSettingsService.getSettings()).thenReturn(StockRiskSettings.builder().build());
        when(riskManagementService.evaluatePreTradeRisk(anyString(), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyLong(), anyDouble()))
            .thenAnswer(invocation -> {
                int qty = invocation.getArgument(1, Integer.class);
                return new RiskManagementService.PreTradeRiskResult(true, qty, "OK", "OK");
            });
        // By default allow fundamentals to pass so AI veto and risk scoring paths are exercised
        when(fundamentalFilter.evaluateStock(anyString())).thenReturn(FundamentalFilter.FilterResult.builder().passed(true).build());
        // By default, ensure trade risk scorer does not veto so tests can exercise AI veto paths
        when(tradeRiskScorer.quickRiskCheck(any())).thenReturn(Map.of("veto", false, "risk_score", 0.0, "reason", "OK"));

        positionManager = new PositionManager();
        orderExecutionService = new OrderExecutionService(
                restTemplate, objectMapper, tradingProperties, telegramService,
                dataLoggingService, positionManager, riskManagementService, stockRiskSettingsService,
                earningsBlackoutService, taiwanComplianceService, llmService, tradeRiskScorer, fundamentalFilter
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
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
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
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean())).thenReturn(vetoResult);

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "MA_3_8");

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("TAIWAN COMPLIANCE"));
    }

    @Test
    void executeOrderWithRetry_whenAiVetoEnabled_shouldCheckAndBlock() {
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(true);
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(false);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(1000000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        // Simulate risk scorer veto (AI veto now implemented via TradeRiskScorer)
        when(tradeRiskScorer.quickRiskCheck(any())).thenReturn(Map.of("veto", true, "risk_score", 100.0, "reason", "High volatility detected"));

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "TestStrategy");

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("RISK SCORE VETO"));
    }

    @Test
    void executeOrderWithRetry_whenAiVetoApproves_shouldExecute() {
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(true);
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(false);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(1000000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        Map<String, Object> vetoResult = new HashMap<>();
        vetoResult.put("veto", false);
        vetoResult.put("reason", "Trade approved");
        // Ensure risk scorer allows the trade to proceed to execution for this test
        when(tradeRiskScorer.quickRiskCheck(any())).thenReturn(Map.of("veto", false, "risk_score", 0.0, "reason", "OK"));
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
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        when(tradeRiskScorer.quickRiskCheck(any())).thenThrow(new RuntimeException("Risk scoring service unavailable"));

        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, "TestStrategy");

        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("RISK SCORING FAILED"));
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
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
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

    // ==================== Coverage tests for lines 150, 233, 253-255, 259, 281, 290 ====================

    @Test
    void executeOrderWithRetry_shouldIncludeStrategyNameInTradeProposal() {
        // Line 150: tradeProposal.put("strategy_name", strategyName != null ? strategyName : "Unknown")
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(true);
        when(taiwanComplianceService.isIntradayStrategy(anyString())).thenReturn(false);
        when(taiwanComplianceService.fetchCurrentCapital()).thenReturn(1000000.0);
        when(taiwanComplianceService.checkTradeCompliance(anyInt(), anyBoolean()))
                .thenReturn(TaiwanStockComplianceService.ComplianceResult.approved());
        Map<String, Object> vetoResult = new HashMap<>();
        vetoResult.put("veto", false);
        vetoResult.put("reason", "approved");
        when(llmService.executeTradeVeto(any())).thenReturn(vetoResult);
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // Call with null strategyName to trigger line 150 fallback
        when(tradeRiskScorer.quickRiskCheck(any())).thenReturn(Map.of("veto", false, "risk_score", 0.0, "reason", "OK"));
        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false, null);

        verify(tradeRiskScorer).quickRiskCheck(argThat(proposal -> {
            Map<String, Object> map = (Map<String, Object>) proposal;
            return "Unknown".equals(map.get("strategy_name"));
        }));
    }

    @Test
    void executeOrderWithRetry_emergencyShutdown_shouldSetSimulationMode() {
        // Line 233: mode(emergencyShutdown ? Trade.TradingMode.SIMULATION : Trade.TradingMode.LIVE)
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // Execute with emergencyShutdown=true
        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, true);

        verify(dataLoggingService).logTrade(argThat(trade -> 
            trade.getMode() == Trade.TradingMode.SIMULATION
        ));
    }

    @Test
    void executeOrderWithRetry_whenInterruptedDuringBackoff_shouldReturn() throws InterruptedException {
        // Lines 253-255, 259: InterruptedException during backoff
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenThrow(new RuntimeException("First fail"))
                .thenThrow(new RuntimeException("Second fail"))
                .thenReturn("{\"status\":\"filled\"}");

        // Start execution in a separate thread and interrupt it
        Thread testThread = new Thread(() -> {
            orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);
        });
        testThread.start();
        
        // Wait for first retry attempt then interrupt
        Thread.sleep(500);
        testThread.interrupt();
        testThread.join(5000);
        
        // The thread should have stopped due to interrupt
        assertFalse(testThread.isAlive());
    }

    @Test
    void flattenPosition_shouldCalculateHoldDuration() throws Exception {
        // Line 281: Calculate hold duration from entry time
        positionManager.setPosition("2454.TW", 1);
        LocalDateTime entryTime = java.time.LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(30);
        positionManager.updateEntry("2454.TW", 20000.0, entryTime);

        String signalJson = "{\"current_price\":20100}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.flattenPosition("Test reason", "2454.TW", "stock", false);

        // Verify closeLatestTrade was called with calculated holdDuration
        verify(dataLoggingService).closeLatestTrade(
            eq("2454.TW"), 
            any(), 
            anyDouble(), 
            anyDouble(), 
            intThat(duration -> duration >= 29 && duration <= 31) // Should be ~30 minutes
        );
    }

    @Test
    void flattenPosition_emergencyShutdown_shouldUseSimulationMode() throws Exception {
        // Line 290: mode(emergencyShutdown ? Trade.TradingMode.SIMULATION : Trade.TradingMode.LIVE)
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20000.0, java.time.LocalDateTime.now());

        String signalJson = "{\"current_price\":20100}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.flattenPosition("Test reason", "2454.TW", "stock", true); // emergencyShutdown=true

        verify(dataLoggingService).closeLatestTrade(
            eq("2454.TW"), 
            eq(Trade.TradingMode.SIMULATION), // Should be SIMULATION when emergency
            anyDouble(), 
            anyDouble(), 
            anyInt()
        );
    }

    @Test
    void flattenPosition_withNullEntryTime_shouldUseZeroHoldDuration() throws Exception {
        // Line 281: entryTime == null case
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20000.0, null); // null entry time

        String signalJson = "{\"current_price\":20100}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        orderExecutionService.flattenPosition("Test reason", "2454.TW", "stock", false);

        verify(dataLoggingService).closeLatestTrade(
            eq("2454.TW"), 
            any(), 
            anyDouble(), 
            anyDouble(), 
            eq(0) // Should be 0 when entry time is null
        );
    }

    // ==================== Coverage test for line 259 ====================
    
    @Test
    void executeOrderWithRetry_lastRetryFails_exitsAfterMaxAttempts() {
        // Line 259: After all retries fail, the method returns (implicit)
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenThrow(new RuntimeException("Always fails"));

        // This should not hang - it should exit after 3 retries
        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        // Verify that it tried 3 times (max retries)
        verify(restTemplate, times(3)).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("Order failed"));
    }
}

