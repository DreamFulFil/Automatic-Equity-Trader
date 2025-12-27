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
import tw.gc.mtxfbot.entities.StockSettings;
import tw.gc.mtxfbot.entities.RiskSettings;
import tw.gc.mtxfbot.repositories.DailyStatisticsRepository;
import tw.gc.mtxfbot.services.DataLoggingService;
import tw.gc.mtxfbot.services.EndOfDayStatisticsService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingEngineProductionTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramService telegramService;
    @Mock private ContractScalingService contractScalingService;
    @Mock private RiskManagementService riskManagementService;
    @Mock private StockSettingsService stockSettingsService;
    @Mock private RiskSettingsService riskSettingsService;
    @Mock private ApplicationContext applicationContext;
    @Mock private DataLoggingService dataLoggingService;
    @Mock private EndOfDayStatisticsService endOfDayStatisticsService;
    @Mock private DailyStatisticsRepository dailyStatisticsRepository;
    
    private TradingEngine tradingEngine;
    private TradingProperties tradingProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tradingProperties = new TradingProperties();
        tradingProperties.getWindow().setStart("11:30");
        tradingProperties.getWindow().setEnd("13:00");
        tradingProperties.getBridge().setUrl("http://localhost:8888");
        
        // Mock stock settings
        StockSettings stockSettings = StockSettings.builder()
                .shares(55)
                .shareIncrement(27)
                .build();
        when(stockSettingsService.getSettings()).thenReturn(stockSettings);
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(55);

        // Mock risk settings
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
        
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService,
            stockSettingsService, riskSettingsService, dataLoggingService,
            endOfDayStatisticsService, dailyStatisticsRepository
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void testQuantityBugFix_AlwaysSendsStringQuantity() throws Exception {
        // Given: Contract scaling returns 2 contracts
        when(contractScalingService.getMaxContracts()).thenReturn(2);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        
        // Mock signal response
        String signalJson = """
            {
                "direction": "LONG",
                "confidence": 0.75,
                "current_price": 22500.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        // Mock successful order response
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("order_filled");
        
        // When: evaluateEntry is called
        tradingEngine.evaluateEntry();
        
        // Then: Verify quantity is sent as string "2", not integer 2
        verify(restTemplate).postForObject(
            eq("http://localhost:8888/order"),
            argThat(orderMap -> {
                Map<String, Object> map = (Map<String, Object>) orderMap;
                return "2".equals(map.get("quantity")); // Must be string, not integer
            }),
            eq(String.class)
        );
    }

    @Test
    void testOrderRetryLogic_RetriesOnFailure() throws Exception {
        // Given: Contract scaling returns 1 contract
        when(contractScalingService.getMaxContracts()).thenReturn(1);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        
        // Mock signal response
        String signalJson = """
            {
                "direction": "LONG",
                "confidence": 0.75,
                "current_price": 22500.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        // Mock order failures then success
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenThrow(new RuntimeException("Connection failed"))
            .thenThrow(new RuntimeException("Timeout"))
            .thenReturn("order_filled");
        
        // When: evaluateEntry is called
        tradingEngine.evaluateEntry();
        
        // Then: Should retry 3 times total
        verify(restTemplate, times(3)).postForObject(
            eq("http://localhost:8888/order"), any(Map.class), eq(String.class)
        );
        
        // Should send success message after final attempt
        verify(telegramService).sendMessage(contains("ORDER FILLED"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void test45MinuteHardExit_ForcesFlattening() throws Exception {
        // Given: Position exists for 46 minutes
        tradingEngine.setPositionForTest(tradingEngine.getActiveSymbol(), 2);
        tradingEngine.entryPriceFor(tradingEngine.getActiveSymbol()).set(22500.0);
        tradingEngine.entryTimeFor(tradingEngine.getActiveSymbol()).set(LocalDateTime.now(AppConstants.TAIPEI_ZONE).minusMinutes(46));
        
        // Mock signal for current price
        String signalJson = """
            {
                "current_price": 22600.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("order_filled");
        
        // When: check45MinuteHardExit is called
        tradingEngine.check45MinuteHardExit();
        
        // Then: Should send 45-minute warning and flatten
        verify(telegramService).sendMessage(contains("45-MIN HARD EXIT"));
        verify(restTemplate).postForObject(
            eq("http://localhost:8888/order"),
            argThat(orderMap -> {
                Map<String, Object> map = (Map<String, Object>) orderMap;
                return "SELL".equals(map.get("action")) && "2".equals(map.get("quantity"));
            }),
            eq(String.class)
        );
    }

    @Test
    void testWeeklyLossBreaker_PausesTrading() {
        // Given: Weekly limit is hit
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(true);
        when(riskManagementService.getWeeklyPnL()).thenReturn(-15500.0);
        
        // When: handleResumeCommand is called
        tradingEngine.handleResumeCommand();
        
        // Then: Should reject resume request
        verify(telegramService).sendMessage("❌ Cannot resume - Weekly loss limit hit\nWait until next Monday");
    }

    @Test
    void testTelegramStatusCommand_ShowsCompleteStatus() {
        // Given: Bot state with futures mode explicitly set
        System.setProperty("trading.mode", "futures");
        
        // Re-create engine to pick up system property
        tradingEngine = new TradingEngine(
            restTemplate, objectMapper, telegramService, tradingProperties,
            applicationContext, contractScalingService, riskManagementService,
            stockSettingsService, riskSettingsService, dataLoggingService,
            endOfDayStatisticsService, dailyStatisticsRepository
        );
        
        when(contractScalingService.getMaxContracts()).thenReturn(3);
        when(contractScalingService.getLastEquity()).thenReturn(750000.0);
        when(contractScalingService.getLast30DayProfit()).thenReturn(200000.0);
        when(riskManagementService.getDailyPnL()).thenReturn(1500.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(8500.0);
        
        // Position exists
        tradingEngine.setPositionForTest(tradingEngine.getActiveSymbol(), 2);
        tradingEngine.entryPriceFor(tradingEngine.getActiveSymbol()).set(22500.0);
        tradingEngine.entryTimeFor(tradingEngine.getActiveSymbol()).set(LocalDateTime.now(AppConstants.TAIPEI_ZONE).minusMinutes(15));
        
        // When: handleStatusCommand is called
        tradingEngine.handleStatusCommand();
        
        // Then: Should send comprehensive status with mode info
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("📊 BOT STATUS") &&
            message.contains("Mode:") &&
            message.contains("Equity: 750000 TWD") &&
            message.contains("30d Profit: 200000 TWD") &&
            message.contains("Today P&L: 1500 TWD") &&
            message.contains("Week P&L: 8500 TWD") &&
            message.contains("2 @ 22500")
        ));
        
        // Cleanup
        System.clearProperty("trading.mode");
    }

    @Test
    void testEarningsBlackout_PreventsTrading() {
        // Given: Earnings blackout is active
        when(riskManagementService.isEarningsBlackout()).thenReturn(true);
        when(riskManagementService.getEarningsBlackoutStock()).thenReturn("MediaTek earnings");
        
        // When: tradingLoop runs
        tradingEngine.tradingLoop();
        
        // Then: Should not make any API calls for signals
        verify(restTemplate, never()).getForObject(contains("/signal"), eq(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testContractScaling_UsesCorrectQuantity() throws Exception {
        // Given: Contract scaling returns 4 contracts (max)
        when(contractScalingService.getMaxContracts()).thenReturn(4);
        when(riskManagementService.isEarningsBlackout()).thenReturn(false);
        when(riskManagementService.isWeeklyLimitHit()).thenReturn(false);
        
        // Mock signal response
        String signalJson = """
            {
                "direction": "SHORT",
                "confidence": 0.80,
                "current_price": 22400.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("order_filled");
        
        // When: evaluateEntry is called
        tradingEngine.evaluateEntry();
        
        // Then: Should use 4 contracts
        verify(restTemplate).postForObject(
            eq("http://localhost:8888/order"),
            argThat(orderMap -> {
                Map<String, Object> map = (Map<String, Object>) orderMap;
                return "SELL".equals(map.get("action")) && "4".equals(map.get("quantity"));
            }),
            eq(String.class)
        );
    }

    @Test
    void testDailyLossLimit_TriggersEmergencyShutdown() {
        // Given: Daily loss limit exceeded
        when(riskManagementService.isDailyLimitExceeded(4500)).thenReturn(true);
        when(riskManagementService.getDailyPnL()).thenReturn(-5000.0);
        
        // Mock signal for flattening
        String signalJson = """
            {
                "current_price": 22300.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        // Position exists
        tradingEngine.setPositionForTest(tradingEngine.getActiveSymbol(), 1);
        tradingEngine.entryPriceFor(tradingEngine.getActiveSymbol()).set(22500.0);
        
        // When: checkRiskLimits is called
        tradingEngine.checkRiskLimits();
        
        // Then: Should trigger emergency shutdown
        assertTrue(tradingEngine.emergencyShutdown);
        verify(telegramService).sendMessage(contains("🚨 EMERGENCY SHUTDOWN"));
    }

    @Test
    void testNewsVeto_PreventsEntry() throws Exception {
        // Given: News veto is active
        tradingEngine.cachedNewsVeto.set(true);
        tradingEngine.cachedNewsReason.set("Major market volatility detected");
        
        // When: evaluateEntry is called
        tradingEngine.evaluateEntry();
        
        // Then: Should not make signal API call
        verify(restTemplate, never()).getForObject(contains("/signal"), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStopLoss_TriggersAtCorrectThreshold() throws Exception {
        // Given: Position with 2 contracts at loss
        tradingEngine.setPositionForTest(tradingEngine.getActiveSymbol(), 2);
        tradingEngine.entryPriceFor(tradingEngine.getActiveSymbol()).set(22500.0);
        
        // Mock signal showing loss position
        String signalJson = """
            {
                "current_price": 22250.0,
                "exit_signal": false
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/signal"), eq(String.class)))
            .thenReturn(signalJson);
        
        when(restTemplate.postForObject(eq("http://localhost:8888/order"), any(Map.class), eq(String.class)))
            .thenReturn("order_filled");
        
        // When: evaluateExit is called
        // Unrealized P&L = (22250 - 22500) * 2 * 50 = -25000 TWD
        // Stop loss threshold = -500 * 2 = -1000 TWD
        // Should trigger stop loss
        tradingEngine.evaluateExit();
        
        // Then: Should flatten position due to stop loss
        verify(restTemplate).postForObject(
            eq("http://localhost:8888/order"),
            argThat(orderMap -> {
                Map<String, Object> map = (Map<String, Object>) orderMap;
                return "SELL".equals(map.get("action"));
            }),
            eq(String.class)
        );
    }
}