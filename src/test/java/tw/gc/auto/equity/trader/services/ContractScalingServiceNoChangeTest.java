package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContractScalingServiceNoChangeTest {

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

    private ContractScalingService service;

    @BeforeEach
    void setUp() {
        when(tradingProperties.getBridge()).thenReturn(bridge);
        when(bridge.getUrl()).thenReturn("http://localhost:8888");

        service = new ContractScalingService(restTemplate, new ObjectMapper(), telegramService, tradingProperties, tradingStateService);

        // Force previousContracts to match computed contracts.
        ReflectionTestUtils.setField(service, "maxContracts", new AtomicInteger(2));

        lenient().when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn("{\"status\":\"ok\",\"equity\":300000}");
        lenient().when(restTemplate.getForObject(contains("/account/profit-history"), eq(String.class))).thenReturn("{\"status\":\"ok\",\"total_pnl\":100000}");
    }

    @Test
    void updateContractSizing_whenNoContractChange_shouldNotSendTelegram() {
        service.updateContractSizing();

        verify(telegramService, never()).sendMessage(contains("Contract Scaling Changed"));
    }

    // ==================== Coverage test for line 125 ====================
    
    @Test
    void updateContractSizing_whenContractsChanged_shouldLogChangeMessage() {
        // Line 125: String.format(" | %s Changed from %d", newContracts > previousContracts ? "UP" : "DOWN", previousContracts)
        // Start with 1 contract (default), then update to 2
        ContractScalingService freshService = new ContractScalingService(restTemplate, new ObjectMapper(), telegramService, tradingProperties, tradingStateService);
        
        // Account returns 300k equity and 100k profit = 2 contracts
        when(restTemplate.getForObject(contains("/account"), eq(String.class))).thenReturn("{\"status\":\"ok\",\"equity\":300000}");
        when(restTemplate.getForObject(contains("/account/profit-history"), eq(String.class))).thenReturn("{\"status\":\"ok\",\"total_pnl\":100000}");
        
        freshService.updateContractSizing();
        
        // Should send message about contract change from 1 to 2 (UP)
        verify(telegramService).sendMessage(contains("Contract Scaling Changed: 1 → 2"));
    }
}
