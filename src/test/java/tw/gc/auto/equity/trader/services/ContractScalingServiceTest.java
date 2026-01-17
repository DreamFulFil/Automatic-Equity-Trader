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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractScalingServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TelegramService telegramService;

    @Mock
    private TradingProperties tradingProperties;

    @Mock
    private TradingProperties.Bridge bridge;

    @Mock
    private TradingStateService tradingStateService;

    private ObjectMapper objectMapper;
    private ContractScalingService contractScalingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(tradingProperties.getBridge()).thenReturn(bridge);
        when(bridge.getUrl()).thenReturn("http://localhost:8888");

        contractScalingService = new ContractScalingService(
                restTemplate,
                objectMapper,
                telegramService,
                tradingProperties,
                tradingStateService
        );
    }

    // ==================== calculateContractSize() tests ====================

    @Test
    void calculateContractSize_whenEquityBelow250k_shouldReturn1() {
        assertEquals(1, contractScalingService.calculateContractSize(100_000, 50_000));
    }

    @Test
    void calculateContractSize_whenEquity250kAndProfit80k_shouldReturn2() {
        assertEquals(2, contractScalingService.calculateContractSize(250_000, 80_000));
    }

    @Test
    void calculateContractSize_whenEquity500kAndProfit180k_shouldReturn3() {
        assertEquals(3, contractScalingService.calculateContractSize(500_000, 180_000));
    }

    @Test
    void calculateContractSize_whenEquity1mAndProfit400k_shouldReturn4() {
        assertEquals(4, contractScalingService.calculateContractSize(1_000_000, 400_000));
    }

    @Test
    void calculateContractSize_whenEquity2mAndProfit800k_shouldReturn4() {
        assertEquals(4, contractScalingService.calculateContractSize(2_000_000, 800_000));
    }

    @Test
    void calculateContractSize_whenEquity5mAndProfit2m_shouldReturn4() {
        assertEquals(4, contractScalingService.calculateContractSize(5_000_000, 2_000_000));
    }

    @Test
    void calculateContractSize_whenEquityHighButProfitLow_shouldReturn1() {
        assertEquals(1, contractScalingService.calculateContractSize(500_000, 50_000));
    }

    @Test
    void calculateContractSize_whenProfitHighButEquityLow_shouldReturn1() {
        assertEquals(1, contractScalingService.calculateContractSize(200_000, 200_000));
    }

    @Test
    void calculateContractSize_edgeCase_exactThreshold() {
        // Exactly at threshold should qualify
        assertEquals(2, contractScalingService.calculateContractSize(250_000, 80_000));
    }

    @Test
    void calculateContractSize_edgeCase_justBelowThreshold() {
        // Just below threshold should not qualify
        assertEquals(1, contractScalingService.calculateContractSize(249_999, 80_000));
        assertEquals(1, contractScalingService.calculateContractSize(250_000, 79_999));
    }

    // ==================== updateContractSizing() tests ====================

    @Test
    void updateContractSizing_whenBridgeFails_shouldDefaultTo1() {
        when(restTemplate.getForObject(contains("/account"), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        contractScalingService.updateContractSizing();

        assertEquals(1, contractScalingService.getMaxContracts());
        verify(telegramService).sendMessage(contains("Contract sizing failed"));
    }

    @Test
    void updateContractSizing_whenAccountStatusError_shouldDefaultTo1() throws Exception {
        String accountJson = "{\"status\":\"error\",\"error\":\"Not connected\"}";
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);

        contractScalingService.updateContractSizing();

        assertEquals(1, contractScalingService.getMaxContracts());
    }

    @Test
    void updateContractSizing_whenSuccessful_shouldUpdateContracts() throws Exception {
        String accountJson = "{\"status\":\"ok\",\"equity\":300000}";
        String profitJson = "{\"status\":\"ok\",\"total_pnl\":100000}";
        
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(restTemplate.getForObject(contains("/account/profit-history"), eq(String.class))).thenReturn(profitJson);

        contractScalingService.updateContractSizing();

        assertEquals(2, contractScalingService.getMaxContracts());
        assertEquals(300000, contractScalingService.getLastEquity());
        assertEquals(100000, contractScalingService.getLast30DayProfit());
        // Now only sends message if contracts changed (from 1 to 2)
        verify(telegramService).sendMessage(contains("Contract Scaling Changed"));
    }

    @Test
    void updateContractSizing_whenProfitHistoryFails_shouldUseZeroProfit() throws Exception {
        String accountJson = "{\"status\":\"ok\",\"equity\":300000}";
        String profitJson = "{\"status\":\"error\",\"error\":\"Failed\"}";
        
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(restTemplate.getForObject(contains("/account/profit-history"), eq(String.class))).thenReturn(profitJson);

        contractScalingService.updateContractSizing();

        // With 300k equity but 0 profit, should be 1 contract
        assertEquals(1, contractScalingService.getMaxContracts());
    }

    // ==================== getters tests ====================

    @Test
    void getMaxContracts_shouldReturnDefault1() {
        assertEquals(1, contractScalingService.getMaxContracts());
    }

    @Test
    void getLastEquity_shouldReturnDefault0() {
        assertEquals(0.0, contractScalingService.getLastEquity());
    }

    @Test
    void getLast30DayProfit_shouldReturnDefault0() {
        assertEquals(0.0, contractScalingService.getLast30DayProfit());
    }

    // ==================== dailyContractSizingUpdate() tests ====================

    @Test
    void dailyContractSizingUpdate_whenInStockMode_shouldSkipUpdate() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");

        contractScalingService.dailyContractSizingUpdate();

        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
    }

    @Test
    void dailyContractSizingUpdate_whenInFuturesMode_shouldPerformUpdate() throws Exception {
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        String accountJson = "{\"status\":\"ok\",\"equity\":300000}";
        String profitJson = "{\"status\":\"ok\",\"total_pnl\":100000}";
        
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn(accountJson);
        when(restTemplate.getForObject(contains("/account/profit-history"), eq(String.class))).thenReturn(profitJson);

        contractScalingService.dailyContractSizingUpdate();

        verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(String.class));
        assertEquals(2, contractScalingService.getMaxContracts());
    }
    
    @Test
    void initialize_shouldNotPerformAnyAction() {
        // initialize() is a no-op by design
        contractScalingService.initialize();
        
        // Verify no external calls were made
        verify(restTemplate, never()).getForObject(anyString(), any());
        verify(telegramService, never()).sendMessage(anyString());
    }
}
