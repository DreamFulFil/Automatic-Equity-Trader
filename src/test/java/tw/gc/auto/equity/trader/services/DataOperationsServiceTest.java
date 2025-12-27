package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.repositories.ShadowModeStockRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit Test for DataOperationsService
 * 
 * Tests the service layer logic for data operations without requiring
 * full Spring Boot context or Testcontainers.
 */
class DataOperationsServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MarketDataRepository marketDataRepository;

    @Mock
    private StrategyStockMappingRepository mappingRepository;

    @Mock
    private ShadowModeStockRepository shadowModeRepository;

    private ObjectMapper objectMapper;
    private DataOperationsService dataOperationsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        dataOperationsService = new DataOperationsService(
                restTemplate,
                objectMapper,
                marketDataRepository,
                mappingRepository,
                shadowModeRepository
        );
    }

    @Test
    @DisplayName("Should successfully populate historical data via Python bridge")
    void testPopulateHistoricalData_Success() {
        // Given
        int days = 730;
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("total_records", 5220);
        mockResponse.put("stocks", 10);
        mockResponse.put("days", days);

        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        Map<String, Object> result = dataOperationsService.populateHistoricalData(days);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result.get("total_records")).isEqualTo(5220);
        assertThat(result.get("stocks")).isEqualTo(10);
    }

    @Test
    @DisplayName("Should handle error when Python bridge is unreachable")
    void testPopulateHistoricalData_Error() {
        // Given
        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new RuntimeException("Connection refused"));

        // When
        Map<String, Object> result = dataOperationsService.populateHistoricalData(730);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result.get("message")).asString().contains("Connection refused");
    }

    @Test
    @DisplayName("Should successfully run combinatorial backtests")
    void testRunCombinationalBacktests_Success() {
        // Given
        double capital = 80000;
        int days = 730;
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("total_combinations", 500);
        mockResponse.put("successful", 500);
        mockResponse.put("failed", 0);

        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        Map<String, Object> result = dataOperationsService.runCombinationalBacktests(capital, days);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result.get("total_combinations")).isEqualTo(500);
        assertThat(result.get("successful")).isEqualTo(500);
    }

    @Test
    @DisplayName("Should successfully auto-select best strategy")
    void testAutoSelectBestStrategy_Success() {
        // Given
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        
        Map<String, Object> activeStrategy = new HashMap<>();
        activeStrategy.put("symbol", "1303.TW");
        activeStrategy.put("strategy", "Balance of Power");
        activeStrategy.put("sharpe", 5.16);
        mockResponse.put("active_strategy", activeStrategy);
        
        Map<String, Object> shadowMode = new HashMap<>();
        shadowMode.put("count", 10);
        mockResponse.put("shadow_mode", shadowMode);

        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        Map<String, Object> result = dataOperationsService.autoSelectBestStrategy(0.5, 10.0, 50.0);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
        
        Map<String, Object> active = (Map<String, Object>) result.get("active_strategy");
        assertThat(active.get("symbol")).isEqualTo("1303.TW");
        assertThat(active.get("strategy")).isEqualTo("Balance of Power");
        
        Map<String, Object> shadow = (Map<String, Object>) result.get("shadow_mode");
        assertThat(shadow.get("count")).isEqualTo(10);
    }

    @Test
    @DisplayName("Should get data status successfully")
    void testGetDataStatus_Success() {
        // Given
        when(marketDataRepository.count()).thenReturn(5220L);
        when(mappingRepository.count()).thenReturn(500L);
        when(shadowModeRepository.count()).thenReturn(10L);

        // When
        Map<String, Object> result = dataOperationsService.getDataStatus();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
        assertThat(result.get("market_data_records")).isEqualTo(5220L);
        assertThat(result.get("backtest_results")).isEqualTo(500L);
        assertThat(result.get("shadow_mode_stocks")).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should handle database error gracefully")
    void testGetDataStatus_DatabaseError() {
        // Given
        when(marketDataRepository.count()).thenThrow(new RuntimeException("Database error"));

        // When
        Map<String, Object> result = dataOperationsService.getDataStatus();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("error");
        assertThat(result.get("message")).asString().contains("Database error");
    }

    @Test
    @DisplayName("Should successfully run full pipeline")
    void testRunFullPipeline_Success() {
        // Given
        int days = 730;
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("status", "success");
        mockResponse.put("started_at", "2025-12-19T09:00:00");
        mockResponse.put("completed_at", "2025-12-19T09:25:00");

        when(restTemplate.postForEntity(
                anyString(),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // When
        Map<String, Object> result = dataOperationsService.runFullPipeline(days);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("success");
    }
}
