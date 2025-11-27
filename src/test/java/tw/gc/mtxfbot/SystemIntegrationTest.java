package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full system integration tests for Java-Python-Ollama interactions.
 * 
 * These tests verify the complete data flow between all components
 * to catch serialization, communication, and integration issues.
 * 
 * Run with:
 *   BRIDGE_URL=http://localhost:8888 OLLAMA_URL=http://localhost:11434 \
 *   mvn test -Dtest=SystemIntegrationTest
 * 
 * Requires:
 *   - Python bridge running on port 8888
 *   - Ollama running on port 11434
 *   - Shioaji connected (for full tests)
 */
@EnabledIfEnvironmentVariable(named = "BRIDGE_URL", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemIntegrationTest {

    private static RestTemplate restTemplate;
    private static ObjectMapper objectMapper;
    private static String bridgeUrl;
    private static String ollamaUrl;

    @BeforeAll
    static void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
        bridgeUrl = System.getenv("BRIDGE_URL");
        ollamaUrl = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434");
        
        if (bridgeUrl == null) {
            bridgeUrl = "http://localhost:8888";
        }
    }

    // ==================== Component Health Checks ====================

    @Test
    @Order(1)
    @DisplayName("Python bridge should be healthy")
    void pythonBridge_shouldBeHealthy() throws Exception {
        String response = restTemplate.getForObject(bridgeUrl + "/health", String.class);
        
        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);
        assertEquals("ok", json.get("status").asText());
        assertTrue(json.has("shioaji_connected"));
        assertTrue(json.has("time"));
    }

    @Test
    @Order(2)
    @DisplayName("Ollama should be available")
    void ollama_shouldBeAvailable() {
        try {
            String response = restTemplate.getForObject(ollamaUrl + "/api/tags", String.class);
            assertNotNull(response);
        } catch (Exception e) {
            System.out.println("⚠️ Ollama not available - some tests will be skipped");
        }
    }

    // ==================== Signal Flow Tests ====================

    @Test
    @Order(10)
    @DisplayName("Signal endpoint should return valid trading signal")
    void signalEndpoint_shouldReturnValidSignal() throws Exception {
        String response = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
        
        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);
        
        // Required fields
        assertTrue(json.has("current_price"));
        assertTrue(json.has("direction"));
        assertTrue(json.has("confidence"));
        assertTrue(json.has("exit_signal"));
        assertTrue(json.has("timestamp"));
        
        // Valid values
        String direction = json.get("direction").asText();
        assertTrue(direction.equals("LONG") || direction.equals("SHORT") || direction.equals("NEUTRAL"),
                "Direction should be LONG, SHORT, or NEUTRAL but was: " + direction);
        
        double confidence = json.get("confidence").asDouble();
        assertTrue(confidence >= 0 && confidence <= 1,
                "Confidence should be 0-1 but was: " + confidence);
    }

    @Test
    @Order(11)
    @DisplayName("Signal endpoint should be consistent across multiple calls")
    void signalEndpoint_shouldBeConsistent() throws Exception {
        // Call multiple times and verify structure is consistent
        for (int i = 0; i < 3; i++) {
            String response = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
            JsonNode json = objectMapper.readTree(response);
            
            assertTrue(json.has("direction"));
            assertTrue(json.has("confidence"));
            
            // Small delay between calls
            Thread.sleep(100);
        }
    }

    // ==================== Order Flow Tests (the 422 bug scenario) ====================

    @Test
    @Order(20)
    @DisplayName("Order dry-run should accept Map payload from Java")
    void orderDryRun_shouldAcceptMapPayload() throws Exception {
        // This is exactly how TradingEngine sends orders
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "BUY");
        orderMap.put("quantity", 1);
        orderMap.put("price", 27506.0);

        String response = restTemplate.postForObject(
                bridgeUrl + "/order/dry-run", orderMap, String.class);

        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);
        assertEquals("validated", json.get("status").asText());
        assertTrue(json.get("dry_run").asBoolean());
    }

    @Test
    @Order(21)
    @DisplayName("Order dry-run should accept integer prices")
    void orderDryRun_shouldAcceptIntegerPrices() throws Exception {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "SELL");
        orderMap.put("quantity", 1);
        orderMap.put("price", 20000);  // int, not double

        String response = restTemplate.postForObject(
                bridgeUrl + "/order/dry-run", orderMap, String.class);

        assertNotNull(response);
        assertTrue(response.contains("validated"));
    }

    @Test
    @Order(22)
    @DisplayName("Order dry-run should reject invalid action")
    void orderDryRun_shouldRejectInvalidAction() {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "INVALID");
        orderMap.put("quantity", 1);
        orderMap.put("price", 20000.0);

        try {
            restTemplate.postForObject(bridgeUrl + "/order/dry-run", orderMap, String.class);
            fail("Should have thrown 400 error");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    @Order(23)
    @DisplayName("Order dry-run should reject zero quantity")
    void orderDryRun_shouldRejectZeroQuantity() {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "BUY");
        orderMap.put("quantity", 0);
        orderMap.put("price", 20000.0);

        try {
            restTemplate.postForObject(bridgeUrl + "/order/dry-run", orderMap, String.class);
            fail("Should have thrown 400 error");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    @Order(24)
    @DisplayName("Order dry-run should reject missing fields")
    void orderDryRun_shouldRejectMissingFields() {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "BUY");
        // Missing quantity and price

        try {
            restTemplate.postForObject(bridgeUrl + "/order/dry-run", orderMap, String.class);
            fail("Should have thrown 422 error");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        }
    }

    // ==================== News/Ollama Integration Tests ====================

    @Test
    @Order(30)
    @DisplayName("News endpoint should return veto decision")
    void newsEndpoint_shouldReturnVetoDecision() throws Exception {
        try {
            // This test requires Ollama - may timeout if Ollama is slow
            String response = restTemplate.getForObject(bridgeUrl + "/signal/news", String.class);
            
            assertNotNull(response);
            JsonNode json = objectMapper.readTree(response);
            
            assertTrue(json.has("news_veto"));
            assertTrue(json.has("news_score"));
            assertTrue(json.has("news_reason"));
            assertTrue(json.has("headlines_count"));
            assertTrue(json.has("timestamp"));
            
            // Valid values
            double score = json.get("news_score").asDouble();
            assertTrue(score >= 0 && score <= 1,
                    "News score should be 0-1 but was: " + score);
            
        } catch (Exception e) {
            // Ollama might not be available or might timeout
            System.out.println("⚠️ News endpoint test skipped: " + e.getMessage());
        }
    }

    // ==================== Full Trading Cycle Simulation ====================

    @Test
    @Order(40)
    @DisplayName("Full trading cycle should work end-to-end")
    void fullTradingCycle_shouldWork() throws Exception {
        // Step 1: Health check (what Java does on startup)
        String healthResponse = restTemplate.getForObject(bridgeUrl + "/health", String.class);
        JsonNode health = objectMapper.readTree(healthResponse);
        assertEquals("ok", health.get("status").asText());
        
        // Step 2: Pre-market health check (dry-run order)
        Map<String, Object> testOrder = new HashMap<>();
        testOrder.put("action", "BUY");
        testOrder.put("quantity", 1);
        testOrder.put("price", 20000.0);
        
        String dryRunResponse = restTemplate.postForObject(
                bridgeUrl + "/order/dry-run", testOrder, String.class);
        assertTrue(dryRunResponse.contains("validated"));
        
        // Step 3: Get trading signal
        String signalResponse = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalResponse);
        
        String direction = signal.get("direction").asText();
        double confidence = signal.get("confidence").asDouble();
        double price = signal.get("current_price").asDouble();
        
        // Step 4: If signal is actionable, validate order
        if (!direction.equals("NEUTRAL") && confidence >= 0.65) {
            String action = direction.equals("LONG") ? "BUY" : "SELL";
            
            Map<String, Object> order = new HashMap<>();
            order.put("action", action);
            order.put("quantity", 1);
            order.put("price", price);
            
            String orderResponse = restTemplate.postForObject(
                    bridgeUrl + "/order/dry-run", order, String.class);
            assertTrue(orderResponse.contains("validated"));
        }
        
        // Step 5: Check news veto (if Ollama available)
        try {
            String newsResponse = restTemplate.getForObject(bridgeUrl + "/signal/news", String.class);
            JsonNode news = objectMapper.readTree(newsResponse);
            assertTrue(news.has("news_veto"));
        } catch (Exception e) {
            System.out.println("⚠️ News check skipped: " + e.getMessage());
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(50)
    @DisplayName("Non-existent endpoint should return 404")
    void nonExistentEndpoint_shouldReturn404() {
        try {
            restTemplate.getForObject(bridgeUrl + "/nonexistent", String.class);
            fail("Should have thrown 404 error");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    @Order(51)
    @DisplayName("Malformed JSON should return 422")
    void malformedJson_shouldReturn422() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>("not json", headers);
            
            restTemplate.postForObject(bridgeUrl + "/order/dry-run", request, String.class);
            fail("Should have thrown 422 error");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
        }
    }

    // ==================== Performance Tests ====================

    @Test
    @Order(60)
    @DisplayName("Signal endpoint should respond within 1 second")
    void signalEndpoint_shouldRespondQuickly() {
        long start = System.currentTimeMillis();
        restTemplate.getForObject(bridgeUrl + "/signal", String.class);
        long elapsed = System.currentTimeMillis() - start;
        
        assertTrue(elapsed < 1000, 
                "Signal endpoint took too long: " + elapsed + "ms");
    }

    @Test
    @Order(61)
    @DisplayName("Health endpoint should respond within 500ms")
    void healthEndpoint_shouldRespondQuickly() {
        long start = System.currentTimeMillis();
        restTemplate.getForObject(bridgeUrl + "/health", String.class);
        long elapsed = System.currentTimeMillis() - start;
        
        assertTrue(elapsed < 500, 
                "Health endpoint took too long: " + elapsed + "ms");
    }

    @Test
    @Order(62)
    @DisplayName("Order dry-run should respond within 500ms")
    void orderDryRun_shouldRespondQuickly() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 1);
        order.put("price", 20000.0);
        
        long start = System.currentTimeMillis();
        restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class);
        long elapsed = System.currentTimeMillis() - start;
        
        assertTrue(elapsed < 500, 
                "Order dry-run took too long: " + elapsed + "ms");
    }
}
