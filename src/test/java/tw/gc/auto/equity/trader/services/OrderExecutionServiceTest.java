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
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.Trade;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        when(stockRiskSettingsService.isAiVetoEnabled()).thenReturn(false); // Disable AI veto in tests by default
        
        positionManager = new PositionManager();
        orderExecutionService = new OrderExecutionService(
            restTemplate, objectMapper, tradingProperties, telegramService, 
            dataLoggingService, positionManager, riskManagementService, stockRiskSettingsService,
            earningsBlackoutService, taiwanComplianceService, llmService
        );
    }

    @Test
    void executeOrderWithRetry_whenBuyOrder_shouldIncrementPosition() {
        // Given
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // When
        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        // Then
        assertEquals(1, positionManager.getPosition("2454.TW"));
        verify(telegramService).sendMessage(contains("ORDER FILLED"));
        verify(dataLoggingService).logTrade(any(Trade.class));
    }

    @Test
    void executeOrderWithRetry_whenSellOrder_shouldDecrementPosition() {
        // Given
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // When
        orderExecutionService.executeOrderWithRetry("SELL", 1, 20000.0, "2454.TW", false, false);

        // Then
        assertEquals(-1, positionManager.getPosition("2454.TW"));
    }

    @Test
    void executeOrderWithRetry_whenApiFails_shouldRetryAndNotify() {
        // Given
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenThrow(new RuntimeException("Order rejected"));

        // When
        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        // Then
        verify(restTemplate, times(3)).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("Order failed after 3 attempts"));
    }

    @Test
    void executeOrderWithRetry_whenEarningsBlackout_shouldBlockNewEntry() {
        // Given
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(true);

        // When - try to place a new entry order (not exit)
        orderExecutionService.executeOrderWithRetry("BUY", 1, 20000.0, "2454.TW", false, false);

        // Then - order should be blocked
        verify(restTemplate, never()).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
        verify(telegramService).sendMessage(contains("EARNINGS BLACKOUT"));
    }

    @Test
    void executeOrderWithRetry_whenEarningsBlackout_shouldAllowExitOrder() {
        // Given
        when(earningsBlackoutService.isDateBlackout(any())).thenReturn(true);
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class)))
                .thenReturn("{\"status\":\"filled\"}");

        // When - exit order should be allowed during blackout
        orderExecutionService.executeOrderWithRetry("SELL", 1, 20000.0, "2454.TW", true, false);

        // Then - exit should proceed
        verify(restTemplate).postForObject(anyString(), any(java.util.Map.class), eq(String.class));
    }

    @Test
    void flattenPosition_whenLongPosition_shouldSell() throws Exception {
        // Given
        positionManager.setPosition("2454.TW", 1);
        positionManager.updateEntry("2454.TW", 20000.0, java.time.LocalDateTime.now());

        String signalJson = "{\"current_price\":20050}";
        when(restTemplate.getForObject(contains("/signal"), eq(String.class))).thenReturn(signalJson);
        when(objectMapper.readTree(signalJson)).thenReturn(new ObjectMapper().readTree(signalJson));
        when(restTemplate.postForObject(anyString(), any(java.util.Map.class), eq(String.class))).thenReturn("{\"status\":\"filled\"}");

        // When
        orderExecutionService.flattenPosition("Test reason", "2454.TW", "stock", false);

        // Then
        verify(restTemplate).postForObject(contains("/order"), argThat(obj -> {
            java.util.Map map = (java.util.Map) obj;
            return map.get("action").equals("SELL") && map.get("quantity").equals("1");
        }), eq(String.class));
        assertEquals(0, positionManager.getPosition("2454.TW"));
    }
}
