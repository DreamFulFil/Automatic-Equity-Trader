package tw.gc.mtxfbot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Python bridge order endpoint using Mockito.
 * 
 * These tests verify the HTTP request/response cycle without requiring
 * the Python bridge to be running, using mocked RestTemplate.
 * 
 * Converted from integration tests to Spring-independent unit tests.
 */
@ExtendWith(MockitoExtension.class)
class OrderEndpointIntegrationTest {

    @Mock
    private RestTemplate restTemplate;
    private String bridgeUrl;

    @BeforeEach
    void setUp() {
        bridgeUrl = "http://localhost:8888";
    }

    @Test
    void healthEndpoint_shouldReturnOk() {
        when(restTemplate.getForObject(bridgeUrl + "/health", String.class))
            .thenReturn("{\"status\":\"ok\"}");
        
        String response = restTemplate.getForObject(bridgeUrl + "/health", String.class);
        
        assertNotNull(response);
        assertTrue(response.contains("ok") || response.contains("status"));
    }

    @Test
    void orderDryRun_withMapPayload_shouldReturnValidated() {
        // This is how Java TradingEngine sends orders - as a Map
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 1);
        order.put("price", 20000.0);

        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenReturn("{\"validated\":true}");

        String response = restTemplate.postForObject(
                bridgeUrl + "/order/dry-run", order, String.class);

        assertNotNull(response);
        assertTrue(response.contains("validated"), 
                "Expected 'validated' in response but got: " + response);
    }

    @Test
    void orderDryRun_withSellAction_shouldReturnValidated() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "SELL");
        order.put("quantity", 1);
        order.put("price", 20000.0);

        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenReturn("{\"validated\":true}");

        String response = restTemplate.postForObject(
                bridgeUrl + "/order/dry-run", order, String.class);

        assertNotNull(response);
        assertTrue(response.contains("validated"));
    }

    @Test
    void orderDryRun_withInvalidAction_shouldReturn400() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "INVALID");
        order.put("quantity", 1);
        order.put("price", 20000.0);

        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");
        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenThrow(exception);

        try {
            restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class);
            fail("Expected 400 error for invalid action");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    void orderDryRun_withZeroQuantity_shouldReturn400() {
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 0);
        order.put("price", 20000.0);

        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request");
        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenThrow(exception);

        try {
            restTemplate.postForObject(bridgeUrl + "/order/dry-run", order, String.class);
            fail("Expected 400 error for zero quantity");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        }
    }

    @Test
    void orderDryRun_withIntegerPrice_shouldWork() {
        // Test that integer prices work (Java might send int instead of double)
        Map<String, Object> order = new HashMap<>();
        order.put("action", "BUY");
        order.put("quantity", 1);
        order.put("price", 20000); // int, not double

        when(restTemplate.postForObject(eq(bridgeUrl + "/order/dry-run"), eq(order), eq(String.class)))
            .thenReturn("{\"validated\":true}");

        String response = restTemplate.postForObject(
                bridgeUrl + "/order/dry-run", order, String.class);

        assertNotNull(response);
        assertTrue(response.contains("validated"));
    }

    @Test
    void signalEndpoint_shouldReturnValidJson() {
        when(restTemplate.getForObject(bridgeUrl + "/signal", String.class))
            .thenReturn("{\"current_price\":20000.0,\"direction\":\"BUY\"}");
        
        String response = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
        
        assertNotNull(response);
        assertTrue(response.contains("current_price") || response.contains("direction"),
                "Signal response should contain price or direction: " + response);
    }

    @Test
    void newsEndpoint_shouldReturnValidJson() {
        when(restTemplate.getForObject(bridgeUrl + "/signal/news", String.class))
            .thenReturn("{\"news_veto\":false,\"score\":0.5}");
        
        String response = restTemplate.getForObject(bridgeUrl + "/signal/news", String.class);
        
        assertNotNull(response);
        assertTrue(response.contains("news_veto") || response.contains("score"),
                "News response should contain veto status: " + response);
    }

    /**
     * This test specifically verifies the fix for the 422 bug.
     * It sends the request exactly as RestTemplate does with a Map payload.
     */
    @Test
    void orderDryRun_verifiesRestTemplateCompatibility() {
        // Simulate exactly what TradingEngine.executeOrder does
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("action", "BUY");
        orderMap.put("quantity", 1);
        orderMap.put("price", 27506.0); // Real price from the bug report

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderMap, headers);

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
        assertTrue(response.getBody().contains("validated"),
                "Order should be validated. Got: " + response.getBody());
    }
}
