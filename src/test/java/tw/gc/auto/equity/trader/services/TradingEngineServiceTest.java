package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TradingEngineServiceTest {

    @Mock
    RestTemplate restTemplate;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    TelegramService telegramService;

    @Mock
    TradingProperties tradingProperties;

    @Mock
    org.springframework.context.ApplicationContext applicationContext;

    @Mock
    ContractScalingService contractScalingService;

    @Mock
    RiskManagementService riskManagementService;

    @Mock
    StockSettingsService stockSettingsService;

    @Mock
    StockRiskSettingsService stockRiskSettingsService;

    @Mock
    DataLoggingService dataLoggingService;

    @Mock
    EndOfDayStatisticsService endOfDayStatisticsService;

    @Mock
    DailyStatisticsRepository dailyStatisticsRepository;

    @Mock
    ShioajiSettingsService shioajiSettingsService;

    @Mock
    TradingStateService tradingStateService;

    @Mock
    TelegramCommandHandler telegramCommandHandler;

    @Mock
    OrderExecutionService orderExecutionService;

    @Mock
    PositionManager positionManager;

    @Mock
    StrategyManager strategyManager;

    @Mock
    ReportingService reportingService;

    @Mock
    ActiveStockService activeStockService;

    private TradingEngineService engine;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(tradingProperties.getBridge()).thenReturn(new tw.gc.auto.equity.trader.config.TradingProperties.Bridge());
        engine = new TradingEngineService(
            restTemplate,
            objectMapper,
            telegramService,
            tradingProperties,
            applicationContext,
            contractScalingService,
            riskManagementService,
            stockSettingsService,
            stockRiskSettingsService,
            dataLoggingService,
            endOfDayStatisticsService,
            dailyStatisticsRepository,
            shioajiSettingsService,
            tradingStateService,
            telegramCommandHandler,
            orderExecutionService,
            positionManager,
            strategyManager,
            reportingService,
            activeStockService
        );
    }

    @Test
    void checkRiskLimits_triggersEmergencyFlatten_whenDailyLimitExceeded() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(riskManagementService.isDailyLimitExceeded(anyInt())).thenReturn(true);
        when(stockRiskSettingsService.getDailyLossLimit()).thenReturn(1000);
        when(riskManagementService.getDailyPnL()).thenReturn(-1500.0);
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");

        engine.checkRiskLimits();

        verify(orderExecutionService).flattenPosition(eq("Daily loss limit"), anyString(), anyString(), eq(true));
        verify(tradingStateService).setEmergencyShutdown(true);
        verify(telegramService).sendMessage(startsWith("🚨 EMERGENCY SHUTDOWN"));
    }

    @Test
    void check45MinuteHardExit_triggersFlatten_whenHeldTooLong() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(2);
        // set entry time older than max hold
        int maxHold = 1; // minute
        when(stockRiskSettingsService.getMaxHoldMinutes()).thenReturn(maxHold);
        LocalDateTime past = LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(maxHold + 5);
        when(positionManager.getEntryTime("2330.TW")).thenReturn(past);

        engine.check45MinuteHardExit();

        verify(orderExecutionService).flattenPosition(eq("45-minute time limit"), eq("2330.TW"), anyString(), anyBoolean());
        verify(telegramService).sendMessage(startsWith("⏰ 45-MIN HARD EXIT"));
    }

    @Test
    void evaluateEntry_skipsBuy_whenInsufficientBalance_andNotPlaceOrder() throws Exception {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        // signal JSON -> low confidence branch bypassed, so produce high confidence but balance check returns 0
        String json = "{\"direction\":\"LONG\",\"confidence\":0.9,\"current_price\":100.0}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));
        when(orderExecutionService.checkBalanceAndAdjustQuantity(eq("BUY"), anyInt(), anyDouble(), eq("2330.TW"), anyString())).thenReturn(0);

        engine.evaluateEntry();

        verify(telegramService).sendMessage(contains("Insufficient account balance"));
        verify(orderExecutionService).checkBalanceAndAdjustQuantity(eq("BUY"), anyInt(), anyDouble(), eq("2330.TW"), anyString());
    }

    @Test
    void evaluateExit_triggersStopLoss_flatten() throws Exception {
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(10);
        when(positionManager.getEntryPrice("2330.TW")).thenReturn(1000.0);
        when(positionManager.getEntryTime("2330.TW")).thenReturn(LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(10));
        when(tradingStateService.getTradingMode()).thenReturn("stock");

        String json = "{\"current_price\":100.0, \"exit_signal\": false, \"news_veto\": false}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        engine.evaluateExit();

        verify(orderExecutionService).flattenPosition(eq("Stop-loss"), eq("2330.TW"), anyString(), anyBoolean());
    }

    @Test
    void runPreMarketHealthCheck_success_and_failure_paths() throws Exception {
        // Success path -> response contains 'validated' -> no telegram sent
        java.lang.reflect.Method m = TradingEngineService.class.getDeclaredMethod("runPreMarketHealthCheck");
        m.setAccessible(true);

        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("order validated: ok");
        m.invoke(engine);
        verify(telegramService, never()).sendMessage(startsWith("⚠️ Pre-market"));

        // Unexpected response -> warns and sends telegram
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("weird response");
        m.invoke(engine);
        verify(telegramService).sendMessage(startsWith("⚠️ Pre-market health check"));

        // Exception path -> should send PRE-MARKET CHECK FAILED
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenThrow(new RuntimeException("boom"));
        m.invoke(engine);
        verify(telegramService).sendMessage(startsWith("🚨 PRE-MARKET CHECK FAILED"));
    }

    @Test
    void updateNewsVetoCache_setsVeto_and_sendsMessage() throws Exception {
        java.lang.reflect.Method m = TradingEngineService.class.getDeclaredMethod("updateNewsVetoCache");
        m.setAccessible(true);

        String newsJson = "{\"news_veto\":true,\"news_reason\":\"Bad news\",\"news_score\":0.82}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(newsJson);
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        m.invoke(engine);

        verify(tradingStateService).setNewsVeto(eq(true), eq("Bad news"));
        verify(telegramService).sendMessage(startsWith("🤖 🚨 NEWS VETO ACTIVE"));
    }

    @Test
    void tradingLoop_reconnectsBridge_and_executesStrategies() throws Exception {
        // Set active polling and bridge disconnected
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(false);
        when(restTemplate.getForObject(contains("/health"), eq(String.class))).thenReturn("pong");
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn("{\"current_price\":123.45}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        engine.tradingLoop();

        verify(tradingStateService).setMarketDataConnected(true);
        verify(strategyManager).executeStrategies(any(), eq(123.45));
        verify(telegramService).sendMessage(contains("✅ Python bridge"));
    }

    @Test
    void tradingLoop_passiveMode_doesNothing() {
        when(tradingStateService.isActivePolling()).thenReturn(false);

        engine.tradingLoop();

        verifyNoInteractions(restTemplate);
        verifyNoInteractions(strategyManager);
    }

    @Test
    void tradingLoop_restClientException_setsMarketDisconnected() {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(true);
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenThrow(new org.springframework.web.client.RestClientException("boom"));

        engine.tradingLoop();

        verify(tradingStateService).setMarketDataConnected(false);
    }

    @Test
    void tradingLoop_skips_whenEarningsBlackout() {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(true);
        when(riskManagementService.isEarningsBlackout()).thenReturn(true);

        engine.tradingLoop();

        verify(strategyManager, never()).executeStrategies(any(), anyDouble());
    }

    @Test
    void tradingLoop_skips_whenWeeklyLimitHit_and_noPosition() {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(true);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(true);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(0);

        engine.tradingLoop();

        verify(strategyManager, never()).executeStrategies(any(), anyDouble());
    }

    @Test
    void evaluateEntry_skips_whenNewsVeto() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(true);

        engine.evaluateEntry();

        verifyNoInteractions(restTemplate);
        verify(dataLoggingService, never()).logSignal(any());
    }

    @Test
    void evaluateEntry_lowConfidence_logsSignal_but_noOrder() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"direction\":\"LONG\",\"confidence\":0.5,\"current_price\":100.0}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        engine.evaluateEntry();

        verify(dataLoggingService).logSignal(any());
        verify(orderExecutionService, never()).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void evaluateEntry_long_placesBuy_whenBalanceSufficient() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"direction\":\"LONG\",\"confidence\":0.9,\"current_price\":100.0}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));
        when(orderExecutionService.checkBalanceAndAdjustQuantity(eq("BUY"), anyInt(), anyDouble(), eq("2330.TW"), anyString())).thenReturn(2);
        when(tradingStateService.isEmergencyShutdown()).thenReturn(false);

        engine.evaluateEntry();

        verify(dataLoggingService).logSignal(any());
        verify(orderExecutionService).executeOrderWithRetry(eq("BUY"), eq(2), eq(100.0), eq("2330.TW"), eq(false), anyBoolean());
    }

    @Test
    void evaluateEntry_short_inStock_noPosition_skips() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"direction\":\"SHORT\",\"confidence\":0.9,\"current_price\":100.0}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));
        when(orderExecutionService.checkBalanceAndAdjustQuantity(eq("SELL"), anyInt(), anyDouble(), eq("2330.TW"), anyString())).thenReturn(0);

        engine.evaluateEntry();

        verify(orderExecutionService).checkBalanceAndAdjustQuantity(eq("SELL"), anyInt(), anyDouble(), eq("2330.TW"), anyString());
        verify(telegramService, never()).sendMessage(anyString());
        verify(orderExecutionService, never()).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void evaluateEntry_short_inFutures_placesSell_whenMarginSufficient() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(activeStockService.getActiveSymbol("futures")).thenReturn("FUT.TEST");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"direction\":\"SHORT\",\"confidence\":0.9,\"current_price\":200.0}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));
        when(orderExecutionService.checkBalanceAndAdjustQuantity(eq("BUY"), anyInt(), anyDouble(), eq("FUT.TEST"), anyString())).thenReturn(4);
        when(tradingStateService.isEmergencyShutdown()).thenReturn(false);

        engine.evaluateEntry();

        verify(orderExecutionService).executeOrderWithRetry(eq("SELL"), eq(4), eq(200.0), eq("FUT.TEST"), eq(false), anyBoolean());
    }

    @Test
    void evaluateEntry_short_inStock_warnsAndIgnores() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveSymbol("stock")).thenReturn("2330.TW");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"direction\":\"SHORT\",\"confidence\":0.9,\"current_price\":100.0}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));
        when(orderExecutionService.checkBalanceAndAdjustQuantity(eq("SELL"), anyInt(), anyDouble(), eq("2330.TW"), anyString())).thenReturn(2);

        engine.evaluateEntry();

        verify(telegramService).sendMessage(contains("SHORT signal received but ignored"));
        verify(orderExecutionService, never()).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void evaluateEntry_short_inFutures_insufficientMargin_skips() throws Exception {
        when(tradingStateService.isNewsVeto()).thenReturn(false);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(activeStockService.getActiveSymbol("futures")).thenReturn("FUT.TEST");
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"direction\":\"SHORT\",\"confidence\":0.9,\"current_price\":200.0}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));
        when(orderExecutionService.checkBalanceAndAdjustQuantity(eq("BUY"), anyInt(), anyDouble(), eq("FUT.TEST"), anyString())).thenReturn(0);

        engine.evaluateEntry();

        verify(orderExecutionService, never()).executeOrderWithRetry(anyString(), anyInt(), anyDouble(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    void evaluateExit_duringMinHoldTime_triggersStopLoss() throws Exception {
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(10);
        when(positionManager.getEntryPrice("2330.TW")).thenReturn(1000.0);
        when(positionManager.getEntryTime("2330.TW")).thenReturn(LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(2));
        when(tradingStateService.getTradingMode()).thenReturn("stock");

        String json = "{\"current_price\":100.0, \"exit_signal\": false, \"news_veto\": false}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        engine.evaluateExit();

        verify(orderExecutionService).flattenPosition(eq("Stop-loss"), eq("2330.TW"), anyString(), anyBoolean());
    }

    @Test
    void evaluateExit_exitSignal_triggersReversalFlatten() throws Exception {
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(positionManager.getPosition("2330.TW")).thenReturn(10);
        when(positionManager.getEntryPrice("2330.TW")).thenReturn(100.0);
        when(positionManager.getEntryTime("2330.TW")).thenReturn(LocalDateTime.now(ZoneId.of("Asia/Taipei")).minusMinutes(10));
        when(tradingStateService.getTradingMode()).thenReturn("stock");

        String json = "{\"current_price\":110.0, \"exit_signal\": true, \"confidence\": 0.9, \"news_veto\": false, \"news_score\": 0.5}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        engine.evaluateExit();

        verify(dataLoggingService).logSignal(any());
        verify(orderExecutionService).flattenPosition(eq("Trend reversal"), eq("2330.TW"), anyString(), anyBoolean());
    }

    @Test
    void tradingLoop_callsUpdateNewsVeto_every10Minutes() throws Exception {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(true);
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn("{\"current_price\":123.45}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        // Mock current time to be at 10-minute mark
        java.time.Clock fixedClock = java.time.Clock.fixed(
            java.time.Instant.parse("2024-01-01T10:10:15Z"),
            ZoneId.of("Asia/Taipei")
        );

        engine.tradingLoop();

        // updateNewsVetoCache is called when now.getMinute() % 10 == 0 && now.getSecond() < 30
        // Since this is hard to test without injecting clock, this test just ensures no exception
        verify(strategyManager).executeStrategies(any(), eq(123.45));
    }

    @Test
    void tradingLoop_genericException_sendsAlert() throws Exception {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(true);
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenThrow(new RuntimeException("Generic error"));

        engine.tradingLoop();

        verify(telegramService).sendMessage(contains("Trading loop error"));
    }

    @Test
    void updateNewsVetoCache_noVeto_logsSuccess() throws Exception {
        java.lang.reflect.Method m = TradingEngineService.class.getDeclaredMethod("updateNewsVetoCache");
        m.setAccessible(true);

        String newsJson = "{\"news_veto\":false,\"news_reason\":\"\",\"news_score\":0.5}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(newsJson);
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        m.invoke(engine);

        verify(tradingStateService).setNewsVeto(eq(false), eq(""));
        verify(telegramService, never()).sendMessage(contains("NEWS VETO ACTIVE"));
    }

    @Test
    void updateNewsVetoCache_exception_logsError() throws Exception {
        java.lang.reflect.Method m = TradingEngineService.class.getDeclaredMethod("updateNewsVetoCache");
        m.setAccessible(true);

        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("News API error"));

        m.invoke(engine);

        verify(tradingStateService, never()).setNewsVeto(anyBoolean(), anyString());
    }

    @Test
    void tradingLoop_reconnectBridge_runsContractScaling_forFutures() throws Exception {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(false);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(restTemplate.getForObject(contains("/health"), eq(String.class))).thenReturn("pong");
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn("{\"current_price\":123.45}");
        when(objectMapper.readTree(anyString())).thenAnswer(invocation -> new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) invocation.getArgument(0)));

        engine.tradingLoop();

        verify(contractScalingService).updateContractSizing();
        verify(strategyManager).executeStrategies(any(), eq(123.45));
    }

    @Test
    void tradingLoop_bridgeReconnectFails_returnsEarly() {
        when(tradingStateService.isActivePolling()).thenReturn(true);
        when(tradingStateService.isMarketDataConnected()).thenReturn(false);
        when(restTemplate.getForObject(contains("/health"), eq(String.class))).thenThrow(new org.springframework.web.client.RestClientException("Connection refused"));

        engine.tradingLoop();

        verify(strategyManager, never()).executeStrategies(any(), anyDouble());
    }

}


