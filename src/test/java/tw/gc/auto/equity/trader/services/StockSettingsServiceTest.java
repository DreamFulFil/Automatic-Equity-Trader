package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.StockSettingsRepository;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockSettingsServiceTest {

    @Mock
    private StockSettingsRepository stockSettingsRepo;

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private StockSettingsService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new StockSettingsService(stockSettingsRepo, restTemplate, objectMapper);
    }

    @Test
    void testEnsureDefaultSettingsCreatesWhenNotExists() {
        when(stockSettingsRepo.findFirst()).thenReturn(null);
        
        service.ensureDefaultSettings();
        
        verify(stockSettingsRepo).save(any(StockSettings.class));
    }

    @Test
    void testEnsureDefaultSettingsDoesNotCreateWhenExists() {
        StockSettings existing = StockSettings.builder()
                .shares(100)
                .shareIncrement(20)
                .updatedAt(LocalDateTime.now())
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(existing);
        
        service.ensureDefaultSettings();
        
        verify(stockSettingsRepo, never()).save(any(StockSettings.class));
    }

    @Test
    void testGetSettingsReturnsExistingSettings() {
        StockSettings settings = StockSettings.builder()
                .shares(70)
                .shareIncrement(27)
                .updatedAt(LocalDateTime.now())
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        StockSettings result = service.getSettings();
        
        assertNotNull(result);
        assertEquals(70, result.getShares());
        assertEquals(27, result.getShareIncrement());
    }

    @Test
    void testGetSettingsThrowsWhenNotFound() {
        when(stockSettingsRepo.findFirst()).thenReturn(null);
        
        assertThrows(IllegalStateException.class, () -> service.getSettings());
    }

    @Test
    void testUpdateSettings() {
        StockSettings settings = StockSettings.builder()
                .shares(70)
                .shareIncrement(27)
                .updatedAt(LocalDateTime.now())
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        when(stockSettingsRepo.save(any(StockSettings.class))).thenReturn(settings);
        
        StockSettings updated = service.updateSettings(100, 30);
        
        assertEquals(100, settings.getShares());
        assertEquals(30, settings.getShareIncrement());
        verify(stockSettingsRepo).save(settings);
    }

    @Test
    void testFetchAccountEquityFromApi() throws Exception {
        String jsonResponse = "{\"equity\": 150000.0}";
        ResponseEntity<String> response = new ResponseEntity<>(jsonResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(response);
        
        double equity = service.fetchAccountEquity();
        
        assertEquals(150000.0, equity);
    }

    @Test
    void testFetchAccountEquityFallbackOnError() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection error"));
        
        double equity = service.fetchAccountEquity();
        
        assertEquals(80000.0, equity); // Fallback value
    }

    @Test
    void testFetchAccountEquityFallbackOnInvalidJson() {
        ResponseEntity<String> response = new ResponseEntity<>("{invalid}", HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(response);
        
        double equity = service.fetchAccountEquity();
        
        assertEquals(80000.0, equity); // Fallback value
    }

    @Test
    void testGetBaseStockQuantityWithNoScaling() {
        StockSettings settings = StockSettings.builder()
                .shares(70)
                .shareIncrement(27)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        int quantity = service.getBaseStockQuantity(80000.0);
        
        assertEquals(70, quantity); // Base shares only
    }

    @Test
    void testGetBaseStockQuantityWithScaling() {
        StockSettings settings = StockSettings.builder()
                .shares(70)
                .shareIncrement(27)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        // 120000 - 80000 = 40000 / 20000 = 2 increments * 27 = 54 additional shares
        int quantity = service.getBaseStockQuantity(120000.0);
        
        assertEquals(124, quantity); // 70 + 54
    }

    @Test
    void testGetBaseStockQuantityWithMultipleIncrements() {
        StockSettings settings = StockSettings.builder()
                .shares(100)
                .shareIncrement(25)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        // 180000 - 80000 = 100000 / 20000 = 5 increments * 25 = 125 additional shares
        int quantity = service.getBaseStockQuantity(180000.0);
        
        assertEquals(225, quantity); // 100 + 125
    }

    @Test
    void testCalculateSafePositionSize() {
        // 100000 equity * 10% / 100 price = 100 shares
        int safeShares = service.calculateSafePositionSize(100000.0, 100.0, 10.0);
        
        assertEquals(100, safeShares);
    }

    @Test
    void testCalculateSafePositionSizeWithHigherRisk() {
        // 100000 equity * 20% / 50 price = 400 shares
        int safeShares = service.calculateSafePositionSize(100000.0, 50.0, 20.0);
        
        assertEquals(400, safeShares);
    }

    @Test
    void testShouldScalePositionTrue() {
        boolean shouldScale = service.shouldScalePosition(120000.0, 80000.0);
        
        assertTrue(shouldScale); // Growth >= 20000
    }

    @Test
    void testShouldScalePositionFalse() {
        boolean shouldScale = service.shouldScalePosition(95000.0, 80000.0);
        
        assertFalse(shouldScale); // Growth < 20000
    }

    @Test
    void testShouldScalePositionExactThreshold() {
        boolean shouldScale = service.shouldScalePosition(100000.0, 80000.0);
        
        assertTrue(shouldScale); // Growth == 20000
    }

    @Test
    void testGetScalingRecommendation() {
        StockSettings settings = StockSettings.builder()
                .shares(100)
                .shareIncrement(25)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        Map<String, Object> recommendation = service.getScalingRecommendation(100000.0, 100.0);
        
        assertNotNull(recommendation);
        assertEquals(100000.0, recommendation.get("currentEquity"));
        assertNotNull(recommendation.get("recommendedShares"));
        assertNotNull(recommendation.get("maxSafeShares"));
        assertEquals(100, recommendation.get("baseShares"));
        assertEquals(25, recommendation.get("incrementPerLevel"));
        assertNotNull(recommendation.get("positionRiskPct"));
    }

    @Test
    void testGetScalingRecommendationLowRisk() {
        StockSettings settings = StockSettings.builder()
                .shares(50)
                .shareIncrement(10)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        Map<String, Object> recommendation = service.getScalingRecommendation(200000.0, 100.0);
        
        double riskPct = (Double) recommendation.get("positionRiskPct");
        assertTrue(riskPct <= 10);
        assertEquals("Position size within safe limits", recommendation.get("status"));
    }

    @Test
    void testGetScalingRecommendationModerateRisk() {
        StockSettings settings = StockSettings.builder()
                .shares(200)
                .shareIncrement(50)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        Map<String, Object> recommendation = service.getScalingRecommendation(100000.0, 50.0);
        
        double riskPct = (Double) recommendation.get("positionRiskPct");
        assertTrue(riskPct > 10);
        assertEquals("Position size above 10% - moderate risk", recommendation.get("warning"));
    }

    @Test
    void testGetScalingRecommendationHighRisk() {
        StockSettings settings = StockSettings.builder()
                .shares(400)
                .shareIncrement(50)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        Map<String, Object> recommendation = service.getScalingRecommendation(100000.0, 50.0);
        
        double riskPct = (Double) recommendation.get("positionRiskPct");
        assertTrue(riskPct > 15);
        assertEquals("Position size exceeds 15% - high risk!", recommendation.get("warning"));
    }

    @Test
    void testGetScalingRecommendationUsesMinOfCurrentAndSafe() {
        StockSettings settings = StockSettings.builder()
                .shares(500)
                .shareIncrement(100)
                .build();
        when(stockSettingsRepo.findFirst()).thenReturn(settings);
        
        // Safe shares should limit the recommendation
        Map<String, Object> recommendation = service.getScalingRecommendation(100000.0, 200.0);
        
        int recommended = (Integer) recommendation.get("recommendedShares");
        int maxSafe = (Integer) recommendation.get("maxSafeShares");
        assertTrue(recommended <= maxSafe);
    }

    @Test
    void testInitialize() {
        when(stockSettingsRepo.findFirst()).thenReturn(null);
        
        service.initialize();
        
        verify(stockSettingsRepo).save(any(StockSettings.class));
    }

    @Test
    void testFetchAccountEquityMissingEquityField() throws Exception {
        String jsonResponse = "{\"balance\": 150000.0}";
        ResponseEntity<String> response = new ResponseEntity<>(jsonResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(response);
        
        double equity = service.fetchAccountEquity();
        
        assertEquals(80000.0, equity); // Falls back because no "equity" field
    }

    @Test
    void testFetchAccountEquityNon2xxResponse() {
        ResponseEntity<String> response = new ResponseEntity<>("{\"equity\": 150000.0}", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.getForEntity(anyString(), eq(String.class))).thenReturn(response);
        
        double equity = service.fetchAccountEquity();
        
        assertEquals(80000.0, equity); // Falls back due to non-2xx response
    }
}
