package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.config.TradingProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ContractScalingIntegrationTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramService telegramService;
    
    private ContractScalingService contractScalingService;
    private TradingProperties tradingProperties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tradingProperties = new TradingProperties();
        tradingProperties.getBridge().setUrl("http://localhost:8888");
        
        contractScalingService = new ContractScalingService(
            restTemplate, objectMapper, telegramService, tradingProperties
        );
    }

    @Test
    void testCalculateContractSize_ScalingTable() {
        // Test all scaling thresholds
        assertEquals(1, contractScalingService.calculateContractSize(100_000, 50_000));
        assertEquals(1, contractScalingService.calculateContractSize(250_000, 70_000)); // Equity met, profit not
        assertEquals(1, contractScalingService.calculateContractSize(200_000, 90_000)); // Profit met, equity not
        assertEquals(2, contractScalingService.calculateContractSize(250_000, 80_000)); // Both met
        assertEquals(2, contractScalingService.calculateContractSize(400_000, 120_000)); // Higher equity, same tier
        assertEquals(3, contractScalingService.calculateContractSize(500_000, 180_000)); // Tier 3
        assertEquals(4, contractScalingService.calculateContractSize(1_000_000, 400_000)); // Tier 4 (max)
        assertEquals(4, contractScalingService.calculateContractSize(10_000_000, 5_000_000)); // Above max (capped at 4)
    }

    @Test
    void testUpdateContractSizing_Success() {
        // Given: Account API returns valid data
        String accountJson = """
            {
                "status": "ok",
                "equity": 750000.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account"), eq(String.class)))
            .thenReturn(accountJson);
        
        String profitJson = """
            {
                "status": "ok",
                "total_pnl": 200000.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account/profit-history?days=30"), eq(String.class)))
            .thenReturn(profitJson);
        
        // When: updateContractSizing is called
        contractScalingService.updateContractSizing();
        
        // Then: Should calculate 3 contracts (750k equity + 200k profit)
        assertEquals(3, contractScalingService.getMaxContracts());
        assertEquals(750000.0, contractScalingService.getLastEquity());
        assertEquals(200000.0, contractScalingService.getLast30DayProfit());
        
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("Contract sizing updated: 3 contracts") &&
            message.contains("Equity: 750000 TWD") &&
            message.contains("30d profit: 200000 TWD")
        ));
    }

    @Test
    void testUpdateContractSizing_AccountError() {
        // Given: Account API returns error
        String accountJson = """
            {
                "status": "error",
                "error": "Account not found"
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account"), eq(String.class)))
            .thenReturn(accountJson);
        
        // When: updateContractSizing is called
        contractScalingService.updateContractSizing();
        
        // Then: Should default to 1 contract and send warning message
        assertEquals(1, contractScalingService.getMaxContracts());
        // Note: The actual implementation logs a warning but doesn't send Telegram message for account errors
    }

    @Test
    void testUpdateContractSizing_ProfitHistoryError() {
        // Given: Account API succeeds, profit API fails
        String accountJson = """
            {
                "status": "ok",
                "equity": 300000.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account"), eq(String.class)))
            .thenReturn(accountJson);
        
        String profitJson = """
            {
                "status": "error",
                "error": "No trade history"
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account/profit-history?days=30"), eq(String.class)))
            .thenReturn(profitJson);
        
        // When: updateContractSizing is called
        contractScalingService.updateContractSizing();
        
        // Then: Should use 0 for 30d profit, resulting in 1 contract
        assertEquals(1, contractScalingService.getMaxContracts());
        assertEquals(300000.0, contractScalingService.getLastEquity());
        assertEquals(0.0, contractScalingService.getLast30DayProfit());
    }

    @Test
    void testUpdateContractSizing_NetworkError() {
        // Given: Network error
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("Connection timeout"));
        
        // When: updateContractSizing is called
        contractScalingService.updateContractSizing();
        
        // Then: Should default to 1 contract and send error message
        assertEquals(1, contractScalingService.getMaxContracts());
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("Contract sizing failed") &&
            message.contains("Connection timeout") &&
            message.contains("Defaulting to 1 contract")
        ));
    }

    @Test
    void testUpdateContractSizing_ContractChange() {
        // Given: Initial state with 1 contract
        contractScalingService.getMaxContracts(); // Initialize to 1
        
        // Account data that should result in 2 contracts
        String accountJson = """
            {
                "status": "ok",
                "equity": 300000.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account"), eq(String.class)))
            .thenReturn(accountJson);
        
        String profitJson = """
            {
                "status": "ok",
                "total_pnl": 90000.0
            }
            """;
        when(restTemplate.getForObject(eq("http://localhost:8888/account/profit-history?days=30"), eq(String.class)))
            .thenReturn(profitJson);
        
        // When: updateContractSizing is called
        contractScalingService.updateContractSizing();
        
        // Then: Should show contract change in message
        assertEquals(2, contractScalingService.getMaxContracts());
        verify(telegramService).sendMessage(argThat(message -> 
            message.contains("Contract sizing updated: 2 contracts") &&
            message.contains("UP Changed from 1")
        ));
    }

    @Test
    void testUpdateContractSizing_EdgeCases() {
        // Test exact threshold boundaries
        
        // Exactly at 250k equity and 80k profit threshold
        assertEquals(2, contractScalingService.calculateContractSize(250_000, 80_000));
        
        // Just below threshold
        assertEquals(1, contractScalingService.calculateContractSize(249_999, 80_000));
        assertEquals(1, contractScalingService.calculateContractSize(250_000, 79_999));
        
        // Negative profit (should still work)
        assertEquals(1, contractScalingService.calculateContractSize(1_000_000, -50_000));
        
        // Zero values
        assertEquals(1, contractScalingService.calculateContractSize(0, 0));
    }

    @Test
    void testUpdateContractSizing_MalformedJson() {
        // Given: Malformed JSON response
        when(restTemplate.getForObject(eq("http://localhost:8888/account"), eq(String.class)))
            .thenReturn("invalid json {");
        
        // When: updateContractSizing is called
        contractScalingService.updateContractSizing();
        
        // Then: Should handle gracefully and default to 1 contract
        assertEquals(1, contractScalingService.getMaxContracts());
        verify(telegramService).sendMessage(contains("Contract sizing failed"));
    }
}