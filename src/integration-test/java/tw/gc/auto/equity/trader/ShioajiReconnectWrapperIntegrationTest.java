package tw.gc.auto.equity.trader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShioajiReconnectWrapperIntegrationTest {

    @Mock private RestTemplate restTemplate;
    @Mock private TelegramService telegramService;
    
    private ShioajiReconnectWrapperService wrapper;
    private final String bridgeUrl = "http://localhost:8888";

    @BeforeEach
    void setUp() {
        wrapper = new ShioajiReconnectWrapperService(restTemplate, telegramService);
    }

    @Test
    void testExecuteWithReconnect_SuccessOnFirstAttempt() throws Exception {
        // Given: API call succeeds immediately
        when(restTemplate.getForObject(eq(bridgeUrl + "/test"), eq(String.class)))
            .thenReturn("success");
        
        // When: executeWithReconnect is called
        String result = wrapper.executeWithReconnect(bridgeUrl, "/test", null, String.class);
        
        // Then: Should return success without retries
        assertEquals("success", result);
        verify(restTemplate, times(1)).getForObject(eq(bridgeUrl + "/test"), eq(String.class));
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void testExecuteWithReconnect_RetriesWithExponentialBackoff() throws Exception {
        // Given: API fails 3 times then succeeds
        when(restTemplate.getForObject(eq(bridgeUrl + "/test"), eq(String.class)))
            .thenThrow(new RuntimeException("Connection failed"))
            .thenThrow(new RuntimeException("Timeout"))
            .thenThrow(new RuntimeException("Server error"))
            .thenReturn("success");
        
        // Mock reconnect endpoint
        when(restTemplate.postForObject(eq(bridgeUrl + "/reconnect"), isNull(), eq(String.class)))
            .thenReturn("reconnect_success");
        
        // When: executeWithReconnect is called
        String result = wrapper.executeWithReconnect(bridgeUrl, "/test", null, String.class);
        
        // Then: Should succeed after retries
        assertEquals("success", result);
        verify(restTemplate, times(4)).getForObject(eq(bridgeUrl + "/test"), eq(String.class));
        verify(restTemplate, times(1)).postForObject(eq(bridgeUrl + "/reconnect"), isNull(), eq(String.class));
        verify(telegramService).sendMessage(contains("reconnected successfully"));
    }

    @Test
    void testExecuteWithReconnect_FailsAfterMaxRetries() {
        // Given: API always fails
        when(restTemplate.getForObject(eq(bridgeUrl + "/test"), eq(String.class)))
            .thenThrow(new RuntimeException("Persistent failure"));
        
        when(restTemplate.postForObject(eq(bridgeUrl + "/reconnect"), isNull(), eq(String.class)))
            .thenReturn("reconnect_attempted");
        
        // When/Then: Should throw exception after max retries
        Exception exception = assertThrows(Exception.class, () -> 
            wrapper.executeWithReconnect(bridgeUrl, "/test", null, String.class)
        );
        
        assertEquals("Persistent failure", exception.getMessage());
        verify(restTemplate, times(5)).getForObject(eq(bridgeUrl + "/test"), eq(String.class));
        verify(telegramService).sendMessage(contains("connection failed after 5 attempts"));
        assertFalse(wrapper.isConnected());
    }

    @Test
    void testExecuteWithReconnect_PostRequest() throws Exception {
        // Given: POST request with body
        Object requestBody = java.util.Map.of("action", "BUY", "quantity", "1");
        when(restTemplate.postForObject(eq(bridgeUrl + "/order"), eq(requestBody), eq(String.class)))
            .thenReturn("order_success");
        
        // When: executeWithReconnect is called with POST
        String result = wrapper.executeWithReconnect(bridgeUrl, "/order", requestBody, String.class);
        
        // Then: Should use POST method
        assertEquals("order_success", result);
        verify(restTemplate).postForObject(eq(bridgeUrl + "/order"), eq(requestBody), eq(String.class));
    }

    @Test
    void testHealthCheck_Success() {
        // Given: Health endpoint returns ok
        when(restTemplate.getForObject(eq(bridgeUrl + "/health"), eq(String.class)))
            .thenReturn("status: ok");
        
        // When: healthCheck is called
        boolean healthy = wrapper.healthCheck(bridgeUrl);
        
        // Then: Should return true and mark as connected
        assertTrue(healthy);
        assertTrue(wrapper.isConnected());
    }

    @Test
    void testHealthCheck_Failure() {
        // Given: Health endpoint fails
        when(restTemplate.getForObject(eq(bridgeUrl + "/health"), eq(String.class)))
            .thenThrow(new RuntimeException("Health check failed"));
        
        // When: healthCheck is called
        boolean healthy = wrapper.healthCheck(bridgeUrl);
        
        // Then: Should return false and mark as disconnected
        assertFalse(healthy);
        assertFalse(wrapper.isConnected());
    }

    @Test
    void testReconnectLogic_ThreadSafe() throws Exception {
        // Given: Multiple threads trying to reconnect
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("Connection failed"));
        
        when(restTemplate.postForObject(eq(bridgeUrl + "/reconnect"), isNull(), eq(String.class)))
            .thenReturn("reconnect_success");
        
        // When: Multiple threads call executeWithReconnect simultaneously
        Thread thread1 = new Thread(() -> {
            try {
                wrapper.executeWithReconnect(bridgeUrl, "/test1", null, String.class);
            } catch (Exception e) {
                // Expected to fail
            }
        });
        
        Thread thread2 = new Thread(() -> {
            try {
                wrapper.executeWithReconnect(bridgeUrl, "/test2", null, String.class);
            } catch (Exception e) {
                // Expected to fail
            }
        });
        
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        
        // Then: Reconnect should be called at most once per thread (2 total), protected by lock to prevent concurrent calls
        verify(restTemplate, atMost(2)).postForObject(eq(bridgeUrl + "/reconnect"), isNull(), eq(String.class));
    }

    @Test
    void testConnectionStateTracking() throws Exception {
        // Given: Initially connected
        assertTrue(wrapper.isConnected());
        
        // When: API call fails completely
        when(restTemplate.getForObject(anyString(), eq(String.class)))
            .thenThrow(new RuntimeException("Persistent failure"));
        
        try {
            wrapper.executeWithReconnect(bridgeUrl, "/test", null, String.class);
        } catch (Exception e) {
            // Expected
        }
        
        // Then: Should be marked as disconnected
        assertFalse(wrapper.isConnected());
        
        // When: API call succeeds again
        when(restTemplate.getForObject(eq(bridgeUrl + "/test"), eq(String.class)))
            .thenReturn("success");
        
        wrapper.executeWithReconnect(bridgeUrl, "/test", null, String.class);
        
        // Then: Should be marked as connected again
        assertTrue(wrapper.isConnected());
        verify(telegramService).sendMessage(contains("reconnected successfully"));
    }
}