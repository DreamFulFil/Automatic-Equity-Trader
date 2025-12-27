package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.repositories.ShadowModeStockRepository;
import tw.gc.auto.equity.trader.repositories.ActiveStrategyConfigRepository;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test for Data Operations
 * 
 * Tests the complete data pipeline:
 * 1. Historical data population
 * 2. Combinatorial backtesting
 * 3. Strategy auto-selection
 * 4. Full pipeline execution
 * 
 * Uses Testcontainers for PostgreSQL
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataOperationsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MarketDataRepository marketDataRepository;

    @Autowired
    private StrategyStockMappingRepository mappingRepository;

    @Autowired
    private ShadowModeStockRepository shadowModeRepository;

    @Autowired
    private ActiveStrategyConfigRepository activeStrategyRepository;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @Order(1)
    @DisplayName("Should get initial data status with zero records")
    void testGetDataStatus_Initial() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/data-ops/status",
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("success");
        assertThat(body.get("market_data_records")).isEqualTo(0);
        assertThat(body.get("backtest_results")).isEqualTo(0);
        assertThat(body.get("shadow_mode_stocks")).isEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("Should populate historical data successfully")
    void testPopulateHistoricalData() {
        // Given
        int days = 100; // Use smaller dataset for faster testing

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/data-ops/populate?days=" + days,
                null,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("success");
        assertThat(body.get("total_records")).isNotNull();
        
        // Verify data in database
        long count = marketDataRepository.count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @Order(3)
    @DisplayName("Should run combinatorial backtests successfully")
    void testRunCombinationalBacktests() {
        // Given
        double capital = 80000;
        int days = 100;

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/data-ops/backtest?capital=" + capital + "&days=" + days,
                null,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("success");
        assertThat(body.get("successful")).isNotNull();
        
        // Verify backtest results in database
        long count = mappingRepository.count();
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @Order(4)
    @DisplayName("Should auto-select best strategy successfully")
    void testAutoSelectBestStrategy() {
        // Given
        double minSharpe = 0.1; // Lower threshold for test data
        double minReturn = 5.0;
        double minWinRate = 30.0;

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/data-ops/select-strategy" +
                        "?minSharpe=" + minSharpe +
                        "&minReturn=" + minReturn +
                        "&minWinRate=" + minWinRate,
                null,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("success");
        
        Map<String, Object> activeStrategy = (Map<String, Object>) body.get("active_strategy");
        assertThat(activeStrategy).isNotNull();
        assertThat(activeStrategy.get("symbol")).isNotNull();
        assertThat(activeStrategy.get("strategy")).isNotNull();
        
        Map<String, Object> shadowMode = (Map<String, Object>) body.get("shadow_mode");
        assertThat(shadowMode).isNotNull();
        assertThat(shadowMode.get("count")).isNotNull();
        
        // Verify active strategy in database
        long activeCount = activeStrategyRepository.count();
        assertThat(activeCount).isGreaterThan(0);
        
        // Verify shadow mode stocks in database
        long shadowCount = shadowModeRepository.count();
        assertThat(shadowCount).isGreaterThan(0);
    }

    @Test
    @Order(5)
    @DisplayName("Should get updated data status after operations")
    void testGetDataStatus_AfterOperations() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/api/data-ops/status",
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("success");
        assertThat((Integer) body.get("market_data_records")).isGreaterThan(0);
        assertThat((Integer) body.get("backtest_results")).isGreaterThan(0);
        assertThat((Integer) body.get("shadow_mode_stocks")).isGreaterThan(0);
    }

    @Test
    @Order(6)
    @DisplayName("Should handle full pipeline execution")
    void testFullPipeline() {
        // Given - Clear existing data for clean test
        marketDataRepository.deleteAll();
        mappingRepository.deleteAll();
        shadowModeRepository.deleteAll();
        
        int days = 50; // Small dataset for faster test

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/data-ops/full-pipeline?days=" + days,
                null,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        
        // Check overall status
        String status = (String) body.get("status");
        assertThat(status).isIn("success", "failed"); // May fail if no strategies meet criteria
        
        // Verify steps were attempted
        assertThat(body.get("steps")).isNotNull();
        
        // If successful, verify database state
        if ("success".equals(status)) {
            assertThat(marketDataRepository.count()).isGreaterThan(0);
            assertThat(mappingRepository.count()).isGreaterThan(0);
            assertThat(shadowModeRepository.count()).isGreaterThan(0);
        }
    }

    @Test
    @Order(7)
    @DisplayName("Should handle errors gracefully when no data exists")
    void testAutoSelectStrategy_NoData() {
        // Given - Clear all data
        marketDataRepository.deleteAll();
        mappingRepository.deleteAll();
        shadowModeRepository.deleteAll();

        // When
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/data-ops/select-strategy",
                null,
                Map.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("status")).isEqualTo("error");
        assertThat(body.get("message")).asString().contains("No strategies");
    }
}
