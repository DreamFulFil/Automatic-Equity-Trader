package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for dual-mode (stock/futures) functionality
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DualModeTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramService telegramService;
    @Mock private ContractScalingService contractScalingService;
    @Mock private RiskManagementService riskManagementService;
    @Mock private ApplicationContext applicationContext;
    
    private TradingEngine tradingEngine;
    private TradingProperties tradingProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tradingProperties = new TradingProperties();
        tradingProperties.getWindow().setStart("11:30");
        tradingProperties.getWindow().setEnd("13:00");
        tradingProperties.getRisk().setDailyLossLimit(4500);
        tradingProperties.getRisk().setWeeklyLossLimit(15000);
        tradingProperties.getRisk().setMaxHoldMinutes(45);
        tradingProperties.getBridge().setUrl("http://localhost:8888");
        
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        when(riskManagementService.getWeeklyPnL()).thenReturn(0.0);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up system property
        System.clearProperty("trading.mode");
    }

    @Test
    void testDefaultMode_IsStock() {
        // Given: No trading.mode system property set
        System.clearProperty("trading.mode");
        
        // When: Create engine
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        // Initialize to read the property
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        tradingEngine.initialize();
        
        // Then: Should be stock mode
        assertEquals("stock", tradingEngine.getTradingMode());
        
        // And: Startup message should show stock mode
        verify(telegramService).sendMessage(contains("STOCK (2330.TW odd lots)"));
    }

    @Test
    void testFuturesMode_WhenExplicitlySet() {
        // Given: trading.mode set to futures
        System.setProperty("trading.mode", "futures");
        
        // When: Create engine
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        when(contractScalingService.getMaxContracts()).thenReturn(2);
        tradingEngine.initialize();
        
        // Then: Should be futures mode
        assertEquals("futures", tradingEngine.getTradingMode());
        
        // And: Startup message should show futures mode
        verify(telegramService).sendMessage(contains("FUTURES (MTXF)"));
    }

    @Test
    void testStockMode_UsesBaseStockQuantity() {
        // Given: Stock mode with 100k equity
        System.setProperty("trading.mode", "stock");
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        tradingEngine.initialize();
        
        // Then: Base quantity should be 67 shares
        assertEquals(67, tradingEngine.getBaseStockQuantity());
        assertEquals(67, tradingEngine.getTradingQuantity());
    }

    @Test
    void testStockMode_ScalesWithEquity() {
        // Given: Stock mode with 300k equity
        System.setProperty("trading.mode", "stock");
        when(contractScalingService.getLastEquity()).thenReturn(300000.0);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        tradingEngine.initialize();
        
        // Then: Quantity should be 67 + (3-1)*33 = 67 + 66 = 133 shares
        assertEquals(133, tradingEngine.getBaseStockQuantity());
    }

    @Test
    void testFuturesMode_UsesContractScaling() {
        // Given: Futures mode with 3 contracts
        System.setProperty("trading.mode", "futures");
        when(contractScalingService.getMaxContracts()).thenReturn(3);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        tradingEngine.initialize();
        
        // Then: Quantity should come from contract scaling
        assertEquals(3, tradingEngine.getTradingQuantity());
    }

    @Test
    void testStockMode_OrderMessageShowsCorrectInstrument() throws Exception {
        // Given: Stock mode
        System.setProperty("trading.mode", "stock");
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(eq("http://localhost:8888/health"), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        when(restTemplate.postForObject(eq("http://localhost:8888/order/dry-run"), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"validated\"}");
        tradingEngine.initialize();
        
        // Reset mocks to count only new interactions
        reset(telegramService);
        
        // Mock successful order
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"filled\"}");
        
        String signalJson = "{\"direction\":\"LONG\",\"confidence\":0.75,\"current_price\":700}";
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        // When: Execute entry
        tradingEngine.evaluateEntry();
        
        // Then: Order message should show 2330.TW
        verify(telegramService).sendMessage(contains("2330.TW"));
    }

    @Test
    void testFuturesMode_OrderMessageShowsMTXF() throws Exception {
        // Given: Futures mode
        System.setProperty("trading.mode", "futures");
        when(contractScalingService.getMaxContracts()).thenReturn(1);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(eq("http://localhost:8888/health"), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        when(restTemplate.postForObject(eq("http://localhost:8888/order/dry-run"), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"validated\"}");
        tradingEngine.initialize();
        
        // Reset mocks to count only new interactions
        reset(telegramService);
        
        // Mock successful order
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"filled\"}");
        
        String signalJson = "{\"direction\":\"LONG\",\"confidence\":0.75,\"current_price\":22500}";
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        // When: Execute entry
        tradingEngine.evaluateEntry();
        
        // Then: Order message should show MTXF
        verify(telegramService).sendMessage(contains("MTXF"));
    }

    @Test
    void testStockMode_PnLMultiplierIsOne() throws Exception {
        // Given: Stock mode with position
        System.setProperty("trading.mode", "stock");
        when(contractScalingService.getLastEquity()).thenReturn(100000.0);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(eq("http://localhost:8888/health"), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"validated\"}");
        tradingEngine.initialize();
        
        // Reset mocks to verify only new interactions
        reset(riskManagementService);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        
        // Position: 100 shares at 700, now at 710 (P&L should be 100 * 10 * 1 = 1000)
        tradingEngine.currentPosition.set(100);
        tradingEngine.entryPrice.set(700.0);
        
        // Signal showing exit_signal true - return same signal for all calls
        String signalJson = "{\"current_price\":710,\"exit_signal\":true}";
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"filled\"}");
        
        // When: Evaluate exit - this will trigger flattenPosition
        tradingEngine.evaluateExit();
        
        // Then: P&L should be calculated with multiplier 1 (stock)
        // The flattenPosition will use the same signal price (710), entry (700), pos (100)
        // P&L = (710 - 700) * 100 * 1 = 1000
        verify(riskManagementService).recordPnL(eq(1000.0), anyInt());
    }

    @Test
    void testFuturesMode_PnLMultiplierIsFifty() throws Exception {
        // Given: Futures mode with position
        System.setProperty("trading.mode", "futures");
        when(contractScalingService.getMaxContracts()).thenReturn(1);
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService
        );
        
        when(restTemplate.getForObject(eq("http://localhost:8888/health"), eq(String.class)))
            .thenReturn("{\"status\":\"ok\"}");
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"validated\"}");
        tradingEngine.initialize();
        
        // Reset mocks to verify only new interactions
        reset(riskManagementService);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        
        // Position: 2 contracts at 22500, now at 22510 (P&L should be 2 * 10 * 50 = 1000)
        tradingEngine.currentPosition.set(2);
        tradingEngine.entryPrice.set(22500.0);
        
        // Signal showing exit_signal true - return same signal for all calls
        String signalJson = "{\"current_price\":22510,\"exit_signal\":true}";
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("{\"status\":\"filled\"}");
        
        // When: Evaluate exit - this will trigger flattenPosition
        tradingEngine.evaluateExit();
        
        // Then: P&L should be calculated with multiplier 50 (futures)
        // The flattenPosition will use the same signal price (22510), entry (22500), pos (2)
        // P&L = (22510 - 22500) * 2 * 50 = 1000
        verify(riskManagementService).recordPnL(eq(1000.0), anyInt());
    }
}
