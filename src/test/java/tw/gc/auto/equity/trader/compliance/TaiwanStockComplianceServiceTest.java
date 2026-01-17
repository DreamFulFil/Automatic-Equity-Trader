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
        ComplianceResult result = service.checkTradeCompliance(1000, false, 50_000);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testRoundLotDayTradeAlwaysApproved() {
        ComplianceResult result = service.checkTradeCompliance(2000, true, 100_000);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testOddLotNonDayTradeApproved() {
        ComplianceResult result = service.checkTradeCompliance(500, false, 50_000);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testOddLotDayTradeWithInsufficientCapitalBlocked() {
        ComplianceResult result = service.checkTradeCompliance(500, true, 1_500_000);
        
        assertFalse(result.isApproved());
        assertNotNull(result.getVetoReason());
        assertTrue(result.getVetoReason().contains("Odd-lot day trading"));
        assertTrue(result.getVetoReason().contains("2,000,000"));
    }

    @Test
    void testOddLotDayTradeWithSufficientCapitalApproved() {
        ComplianceResult result = service.checkTradeCompliance(500, true, 2_000_000);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
    }

    @Test
    void testOddLotDayTradeWithExcessCapitalApproved() {
        ComplianceResult result = service.checkTradeCompliance(750, true, 5_000_000);
        
        assertTrue(result.isApproved());
        assertNull(result.getVetoReason());
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
}
