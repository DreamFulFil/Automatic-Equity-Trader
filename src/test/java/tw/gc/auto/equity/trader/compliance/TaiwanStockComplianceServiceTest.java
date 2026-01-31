package tw.gc.auto.equity.trader.compliance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService.ComplianceResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaiwanStockComplianceServiceTest {

    @Mock
    private RestTemplate restTemplate;
    
    private TaiwanStockComplianceService service;

    @BeforeEach
    void setUp() {
        service = new TaiwanStockComplianceService(restTemplate);
    }

    @Test
    void testRoundLotTradeAlwaysApproved() {
        ComplianceResult result = service.checkTradeCompliance(1000, false);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testRoundLotDayTradeAlwaysApproved() {
        ComplianceResult result = service.checkTradeCompliance(2000, true);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testOddLotNonDayTradeApproved() {
        ComplianceResult result = service.checkTradeCompliance(500, false);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testOddLotDayTradeAlwaysBlocked() {
        ComplianceResult result = service.checkTradeCompliance(500, true);
        
        assertFalse(result.isApproved());
        assertNotNull(result.getVetoReason());
        assertTrue(result.getVetoReason().contains("Odd-lot day trading is forbidden"));
    }

    @Test
    void testComplianceResultApproved() {
        ComplianceResult result = ComplianceResult.approved();
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testComplianceResultBlocked() {
        ComplianceResult result = ComplianceResult.blocked("Test reason");
        
        assertFalse(result.isApproved());
        assertEquals("Test reason", result.getVetoReason());
    }

    @Test
    void testIsIntradayStrategyWithIntradayName() {
        assertTrue(service.isIntradayStrategy("intraday_momentum"));
        assertTrue(service.isIntradayStrategy("Intraday Strategy"));
    }

    @Test
    void testIsIntradayStrategyWithDayTradingName() {
        assertTrue(service.isIntradayStrategy("day trading strategy"));
        assertTrue(service.isIntradayStrategy("Day Trading"));
    }

    @Test
    void testIsIntradayStrategyWithScalpingName() {
        assertTrue(service.isIntradayStrategy("scalping strategy"));
        assertTrue(service.isIntradayStrategy("Scalping"));
    }

    @Test
    void testIsIntradayStrategyWithHighFrequencyName() {
        assertTrue(service.isIntradayStrategy("high frequency trading"));
    }

    @Test
    void testIsIntradayStrategyWithPivotPointsName() {
        assertTrue(service.isIntradayStrategy("pivot points strategy"));
    }

    @Test
    void testIsIntradayStrategyWithVWAPName() {
        assertTrue(service.isIntradayStrategy("vwap strategy"));
    }

    @Test
    void testIsIntradayStrategyWithTWAPName() {
        assertTrue(service.isIntradayStrategy("twap execution"));
    }

    @Test
    void testIsIntradayStrategyWithTickName() {
        assertTrue(service.isIntradayStrategy("tick strategy"));
    }

    @Test
    void testIsIntradayStrategyWithMinuteName() {
        assertTrue(service.isIntradayStrategy("5-minute strategy"));
    }

    @Test
    void testIsIntradayStrategyWithSwingTradingName() {
        assertFalse(service.isIntradayStrategy("swing trading"));
    }

    @Test
    void testIsIntradayStrategyWithPositionTradingName() {
        assertFalse(service.isIntradayStrategy("position trading"));
    }

    @Test
    void testIsIntradayStrategyWithNullName() {
        assertFalse(service.isIntradayStrategy(null));
    }

    @Test
    void testIsIntradayStrategyWithEmptyName() {
        assertFalse(service.isIntradayStrategy(""));
    }

    @Test
    void testFetchCurrentCapitalWithException() {
        when(restTemplate.getForObject(anyString(), any())).thenThrow(new RuntimeException("Connection error"));
        
        double capital = service.fetchCurrentCapital();
        
        assertEquals(80_000.0, capital);
    }

    @Test
    void testFetchCurrentCapitalWithNullResponse() {
        // Covers lines 66, 71-72: response is null, defaults to 80,000
        when(restTemplate.getForObject(eq("http://localhost:8888/account"), any())).thenReturn(null);
        
        double capital = service.fetchCurrentCapital();
        
        assertEquals(80_000.0, capital);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFetchCurrentCapitalWithValidResponse() throws Exception {
        // Covers lines 64, 66-68: successful response with valid equity
        // Use doAnswer to create the AccountResponse with reflection and setAccessible
        doAnswer(invocation -> {
            Class<?> accountResponseClass = Class.forName("tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService$AccountResponse");
            java.lang.reflect.Constructor<?> constructor = accountResponseClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object accountResponse = constructor.newInstance();
            
            java.lang.reflect.Field equityField = accountResponseClass.getDeclaredField("equity");
            equityField.setAccessible(true);
            equityField.set(accountResponse, 150_000.0);
            
            return accountResponse;
        }).when(restTemplate).getForObject(eq("http://localhost:8888/account"), any(Class.class));
        
        double capital = service.fetchCurrentCapital();
        
        assertEquals(150_000.0, capital);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFetchCurrentCapitalWithZeroEquity() throws Exception {
        // Covers lines 71-72: response.equity == 0, defaults to 80,000
        doAnswer(invocation -> {
            Class<?> accountResponseClass = Class.forName("tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService$AccountResponse");
            java.lang.reflect.Constructor<?> constructor = accountResponseClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object accountResponse = constructor.newInstance();
            
            java.lang.reflect.Field equityField = accountResponseClass.getDeclaredField("equity");
            equityField.setAccessible(true);
            equityField.set(accountResponse, 0.0);
            
            return accountResponse;
        }).when(restTemplate).getForObject(eq("http://localhost:8888/account"), any(Class.class));
        
        double capital = service.fetchCurrentCapital();
        
        assertEquals(80_000.0, capital);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFetchCurrentCapitalWithNegativeEquity() throws Exception {
        // Covers lines 71-72: response.equity < 0 (negative), defaults to 80,000
        doAnswer(invocation -> {
            Class<?> accountResponseClass = Class.forName("tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService$AccountResponse");
            java.lang.reflect.Constructor<?> constructor = accountResponseClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object accountResponse = constructor.newInstance();
            
            java.lang.reflect.Field equityField = accountResponseClass.getDeclaredField("equity");
            equityField.setAccessible(true);
            equityField.set(accountResponse, -100.0);
            
            return accountResponse;
        }).when(restTemplate).getForObject(eq("http://localhost:8888/account"), any(Class.class));
        
        double capital = service.fetchCurrentCapital();
        
        assertEquals(80_000.0, capital);
    }
}
