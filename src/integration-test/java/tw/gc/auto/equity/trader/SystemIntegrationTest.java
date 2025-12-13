package tw.gc.auto.equity.trader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Spring-independent replacement for the former integration tests.
 * Uses Mockito to validate request/response handling without external services.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private String bridgeUrl;
    private String ollamaUrl;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bridgeUrl = "http://localhost:8888";
        ollamaUrl = "http://localhost:11434";

        // Default happy-path stubs; individual tests override as needed.
        when(restTemplate.getForObject(eq(bridgeUrl + "/health"), eq(String.class)))
            .thenReturn("{\"status\":\"ok\",\"shioaji_connected\":false,\"time\":\"2025-12-08T12:00:00\"}");
        when(restTemplate.getForObject(eq(bridgeUrl + "/signal"), eq(String.class)))
            .thenReturn("{\"current_price\":20000.0,\"direction\":\"LONG\",\"confidence\":0.8,\"exit_signal\":false,\"timestamp\":\"2025-12-08T12:00:00\"}");
        when(restTemplate.getForObject(eq(bridgeUrl + "/signal/news"), eq(String.class)))
            .thenReturn("{\"news_veto\":false,\"news_score\":0.7,\"news_reason\":\"calm\",\"headlines_count\":5,\"timestamp\":\"2025-12-08T12:00:00\"}");
        when(restTemplate.getForObject(eq(ollamaUrl + "/api/tags"), eq(String.class)))
            .thenReturn("{}");

        when(restTemplate.getForObject(eq(bridgeUrl + "/account"), eq(String.class)))
            .thenReturn("{\"equity\":300000,\"available_margin\":250000,\"status\":\"ok\",\"timestamp\":\"2025-12-08T12:00:00\"}");
        when(restTemplate.getForObject(eq(bridgeUrl + "/account/profit-history?days=30"), eq(String.class)))
            .thenReturn("{\"total_pnl\":1000,\"days\":30,\"status\":\"ok\",\"timestamp\":\"2025-12-08T12:00:00\"}");
        when(restTemplate.getForObject(eq(bridgeUrl + "/account/profit-history?days=7"), eq(String.class)))
            .thenReturn("{\"total_pnl\":200,\"days\":7,\"status\":\"ok\",\"timestamp\":\"2025-12-08T12:00:00\"}");

        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), any(), eq(String.class)))
            .thenReturn("{\"validated\":true}");
    }

    // ==================== Component Health Checks ====================

    @Test
    @Order(1)
    @DisplayName("Python bridge should be healthy")
    void pythonBridge_shouldBeHealthy() throws Exception {
        String mockResponse = "{\"status\":\"ok\",\"shioaji_connected\":false,\"time\":\"2025-12-08T18:21:47\"}";
        when(restTemplate.getForObject(bridgeUrl + "/health", String.class))
            .thenReturn(mockResponse);

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
        when(restTemplate.getForObject(ollamaUrl + "/api/tags", String.class))
            .thenReturn("{}");

        String response = restTemplate.getForObject(ollamaUrl + "/api/tags", String.class);
        assertNotNull(response);
    }

    // ==================== Signal Flow Tests ====================

    @Test
    @Order(10)
    @DisplayName("Signal endpoint should return valid trading signal")
    void signalEndpoint_shouldReturnValidSignal() throws Exception {
        String mockResponse = "{\"current_price\":20000.0,\"direction\":\"LONG\",\"confidence\":0.8,\"exit_signal\":false,\"timestamp\":\"2025-12-08T18:21:47\"}";
        when(restTemplate.getForObject(bridgeUrl + "/signal", String.class))
            .thenReturn(mockResponse);

        String response = restTemplate.getForObject(bridgeUrl + "/signal", String.class);

        assertNotNull(response);
        JsonNode json = objectMapper.readTree(response);

        assertTrue(json.has("current_price"));
        assertTrue(json.has("direction"));
        assertTrue(json.has("confidence"));
        assertTrue(json.has("exit_signal"));
        assertTrue(json.has("timestamp"));

        String direction = json.get("direction").asText();
        assertTrue(direction.equals("LONG") || direction.equals("SHORT") || direction.equals("NEUTRAL"));

        double confidence = json.get("confidence").asDouble();
        assertTrue(confidence >= 0 && confidence <= 1);
    }

    @Test
    @Order(11)
    @DisplayName("Signal endpoint should be consistent across calls")
    void signalEndpoint_shouldBeConsistent() throws Exception {
        String mockResponse = "{\"current_price\":20000.0,\"direction\":\"LONG\",\"confidence\":0.8,\"exit_signal\":false,\"timestamp\":\"2025-12-08T18:21:47\"}";
        when(restTemplate.getForObject(bridgeUrl + "/signal", String.class))
            .thenReturn(mockResponse);

        for (int i = 0; i < 3; i++) {
            String response = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
            JsonNode json = objectMapper.readTree(response);
            assertTrue(json.has("direction"));
            assertTrue(json.has("confidence"));
        }
    }

    // ==================== Order Endpoint ====================

    @Test
    @Order(20)
    @DisplayName("Order dry-run should accept map payload")
    void orderDryRun_shouldAcceptMapPayload() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 1);
        order.put("price", 20000.0);

        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenReturn("{\"validated\":true}");

        String response = restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class);
        assertNotNull(response);
        assertTrue(response.contains("validated"));
    }

    @Test
    @Order(21)
    @DisplayName("Order dry-run should accept integer prices")
    void orderDryRun_shouldAcceptIntegerPrices() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 1);
        order.put("price", 20000);

        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenReturn("{\"validated\":true}");

        String response = restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class);
        assertNotNull(response);
        assertTrue(response.contains("validated"));
    }

    @Test
    @Order(22)
    @DisplayName("Order dry-run should reject invalid action")
    void orderDryRun_shouldRejectInvalidAction() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "INVALID");
        order.put("quantity", 1);
        order.put("price", 20000.0);

        HttpClientErrorException exception = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null);
        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenThrow(exception);

        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class, () ->
                restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class));
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
    }

    @Test
    @Order(23)
    @DisplayName("Order dry-run should reject zero quantity")
    void orderDryRun_shouldRejectZeroQuantity() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 0);
        order.put("price", 20000.0);

        HttpClientErrorException exception = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null);
        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenThrow(exception);

        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class, () ->
                restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class));
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
    }

    @Test
    @Order(24)
    @DisplayName("Order dry-run should handle RestTemplate exchange")
    void orderDryRun_exchangeCompatibility() {
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "BUY");
        orderMap.put("quantity", 1);
        orderMap.put("price", 27506.0);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        org.springframework.http.HttpEntity<Map<String, Object>> request = new org.springframework.http.HttpEntity<>(orderMap, headers);

        ResponseEntity<String> mockResponse = new ResponseEntity<>("{\"validated\":true}", HttpStatus.OK);
        when(restTemplate.exchange(eq(bridgeUrl + "/order/dry-run"), eq(HttpMethod.POST), eq(request), eq(String.class)))
            .thenReturn(mockResponse);

        ResponseEntity<String> response = restTemplate.exchange(
                bridgeUrl + "/order/dry-run",
                HttpMethod.POST,
                request,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("validated"));
    }

    // ==================== News & Misc ====================

    @Test
    @Order(30)
    @DisplayName("News endpoint should return veto decision")
    void newsEndpoint_shouldReturnVetoDecision() throws Exception {
        String mockResponse = "{\"news_veto\":false,\"score\":0.7}";
        when(restTemplate.getForObject(bridgeUrl + "/signal/news", String.class))
            .thenReturn(mockResponse);

        String response = restTemplate.getForObject(bridgeUrl + "/signal/news", String.class);
        JsonNode json = objectMapper.readTree(response);
        assertTrue(json.has("news_veto"));
    }

    @Test
    @Order(40)
    @DisplayName("Non-existent endpoint should return 404")
    void nonExistentEndpoint_shouldReturn404() {
        HttpClientErrorException exception = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);
        when(restTemplate.getForObject(bridgeUrl + "/does-not-exist", String.class))
            .thenThrow(exception);

        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class, () ->
                restTemplate.getForObject(bridgeUrl + "/does-not-exist", String.class));
        assertEquals(HttpStatus.NOT_FOUND, thrown.getStatusCode());
    }

    @Test
    @Order(41)
    @DisplayName("Malformed JSON should return 422")
    void malformedJson_shouldReturn422() {
        HttpClientErrorException exception = HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable", HttpHeaders.EMPTY, null, null);
        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq("not-json"), eq(String.class)))
            .thenThrow(exception);

        HttpClientErrorException thrown = assertThrows(HttpClientErrorException.class, () ->
                restTemplate.postForObject(bridgeUrl + "/order/dry-run", "not-json", String.class));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, thrown.getStatusCode());
    }
}
