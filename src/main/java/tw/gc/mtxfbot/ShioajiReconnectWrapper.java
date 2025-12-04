package tw.gc.mtxfbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Shioaji Auto-Reconnect Wrapper
 * 
 * Provides automatic reconnection logic for Shioaji API failures.
 * Thread-safe with exponential backoff retry strategy.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShioajiReconnectWrapper {
    
    private final RestTemplate restTemplate;
    private final TelegramService telegramService;
    
    private final ReentrantLock reconnectLock = new ReentrantLock();
    private volatile boolean isConnected = true;
    
    /**
     * Execute API call with auto-reconnect on failure
     * 
     * @param bridgeUrl Base URL of Python bridge
     * @param endpoint API endpoint to call
     * @param requestBody Request body (null for GET)
     * @param responseType Expected response type
     * @return API response
     * @throws Exception if all retry attempts fail
     */
    public <T> T executeWithReconnect(String bridgeUrl, String endpoint, Object requestBody, Class<T> responseType) throws Exception {
        int maxRetries = 5;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                
                T response;
                if (requestBody == null) {
                    response = restTemplate.getForObject(bridgeUrl + endpoint, responseType);
                } else {
                    response = restTemplate.postForObject(bridgeUrl + endpoint, requestBody, responseType);
                }
                
                // Success - mark as connected
                if (!isConnected) {
                    isConnected = true;
                    log.info("✅ Shioaji reconnection successful");
                    telegramService.sendMessage("✅ Shioaji reconnected successfully");
                }
                
                return response;
                
            } catch (Exception e) {
                log.error("❌ Shioaji API call failed (attempt {}): {}", attempt, e.getMessage());
                
                // Mark as disconnected on first failure
                if (attempt == 1 && isConnected) {
                    isConnected = false;
                }
                
                if (attempt >= maxRetries) {
                    telegramService.sendMessage("🚨 Shioaji connection failed after " + maxRetries + " attempts: " + e.getMessage());
                    throw e;
                }
                
                // Try to reconnect before next attempt
                if (attempt == 1) {
                    attemptReconnect(bridgeUrl);
                }
                
                // Exponential backoff: 2s, 4s, 8s, 16s, 32s
                try {
                    long delay = 2000L * (1L << (attempt - 1));
                    log.info("⏳ Retrying in {}ms...", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Interrupted during retry", ie);
                }
            }
        }
        
        throw new Exception("Should not reach here");
    }
    
    /**
     * Attempt to reconnect Shioaji session
     */
    private void attemptReconnect(String bridgeUrl) {
        if (!reconnectLock.tryLock()) {
            log.debug("🔄 Reconnect already in progress, skipping");
            return;
        }
        
        try {
            log.info("🔄 Attempting Shioaji reconnection...");
            
            // Call bridge reconnect endpoint
            String result = restTemplate.postForObject(bridgeUrl + "/reconnect", null, String.class);
            
            if (result != null && result.contains("success")) {
                log.info("✅ Shioaji reconnect initiated");
            } else {
                log.warn("⚠️ Shioaji reconnect returned: {}", result);
            }
            
        } catch (Exception e) {
            log.error("❌ Shioaji reconnect failed: {}", e.getMessage());
        } finally {
            reconnectLock.unlock();
        }
    }
    
    /**
     * Check if Shioaji connection is healthy
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Force a connection health check
     */
    public boolean healthCheck(String bridgeUrl) {
        try {
            String response = restTemplate.getForObject(bridgeUrl + "/health", String.class);
            boolean healthy = response != null && response.contains("ok");
            isConnected = healthy;
            return healthy;
        } catch (Exception e) {
            log.error("❌ Health check failed: {}", e.getMessage());
            isConnected = false;
            return false;
        }
    }
}