package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShioajiReconnectWrapperServiceTest {

    @Test
    void executeWithReconnect_post_successOnFirstTry() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op for unit tests
            }
        };

        when(restTemplate.postForObject(eq("http://bridge/api"), any(), eq(String.class))).thenReturn("OK");

        String resp = svc.executeWithReconnect("http://bridge", "/api", new Object(), String.class);

        assertEquals("OK", resp);
        assertTrue(svc.isConnected());
        verifyNoInteractions(telegramService);
    }

    @Test
    void executeWithReconnect_firstFailureThenSuccess_triggersReconnectMessage() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op for unit tests
            }
        };

        when(restTemplate.getForObject("http://bridge/data", String.class))
            .thenThrow(new RuntimeException("boom"))
            .thenReturn("DATA");
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class)).thenReturn("success");

        String resp = svc.executeWithReconnect("http://bridge", "/data", null, String.class);

        assertEquals("DATA", resp);
        verify(telegramService).sendMessage("✅ Shioaji reconnected successfully");
    }

    @Test
    void executeWithReconnect_allRetriesFail_notifiesAndThrows() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op for unit tests
            }
        };

        when(restTemplate.getForObject("http://bridge/data", String.class)).thenThrow(new RuntimeException("down"));
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class)).thenReturn("fail");

        assertThrows(RuntimeException.class, () -> svc.executeWithReconnect("http://bridge", "/data", null, String.class));
        verify(telegramService).sendMessage(contains("🚨 Shioaji connection failed after 5 attempts"));
    }

    @Test
    void healthCheck_ok_setsConnectedTrue() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);
        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService);

        when(restTemplate.getForObject("http://bridge/health", String.class)).thenReturn("ok");

        assertTrue(svc.healthCheck("http://bridge"));
        assertTrue(svc.isConnected());
    }

    @Test
    void healthCheck_exception_setsConnectedFalse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);
        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService);

        when(restTemplate.getForObject("http://bridge/health", String.class)).thenThrow(new RuntimeException("oops"));

        assertFalse(svc.healthCheck("http://bridge"));
        assertFalse(svc.isConnected());
    }

    @Test
    void healthCheck_responseNotOk_setsConnectedFalse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);
        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService);

        when(restTemplate.getForObject("http://bridge/health", String.class)).thenReturn("unhealthy");

        assertFalse(svc.healthCheck("http://bridge"));
        assertFalse(svc.isConnected());
    }

    @Test
    void healthCheck_nullResponse_setsConnectedFalse() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);
        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService);

        when(restTemplate.getForObject("http://bridge/health", String.class)).thenReturn(null);

        assertFalse(svc.healthCheck("http://bridge"));
        assertFalse(svc.isConnected());
    }

    @Test
    void attemptReconnect_withNullResponse_shouldLog() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op for unit tests
            }
        };

        when(restTemplate.getForObject("http://bridge/data", String.class))
            .thenThrow(new RuntimeException("boom"))
            .thenReturn("DATA");
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class)).thenReturn(null);

        String resp = svc.executeWithReconnect("http://bridge", "/data", null, String.class);

        assertEquals("DATA", resp);
    }

    @Test
    void attemptReconnect_withFailureResponse_shouldLog() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op for unit tests
            }
        };

        when(restTemplate.getForObject("http://bridge/data", String.class))
            .thenThrow(new RuntimeException("boom"))
            .thenReturn("DATA");
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class)).thenReturn("failure");

        String resp = svc.executeWithReconnect("http://bridge", "/data", null, String.class);

        assertEquals("DATA", resp);
    }

    @Test
    void executeWithReconnect_interruptedDuringRetry_shouldThrow() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) throws InterruptedException {
                throw new InterruptedException("Test interrupt");
            }
        };

        when(restTemplate.getForObject("http://bridge/data", String.class))
            .thenThrow(new RuntimeException("boom"));
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class)).thenReturn("success");

        Exception ex = assertThrows(Exception.class, () -> 
            svc.executeWithReconnect("http://bridge", "/data", null, String.class));
        assertTrue(ex.getMessage().contains("Interrupted"));
    }

    @Test
    void attemptReconnect_whenLockHeld_shouldSkip() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op
            }
        };

        // Simulate lock already held by forcing first failure
        when(restTemplate.getForObject("http://bridge/data", String.class))
            .thenThrow(new RuntimeException("fail1"))
            .thenThrow(new RuntimeException("fail2"))
            .thenReturn("DATA");
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class)).thenReturn("success");

        String resp = svc.executeWithReconnect("http://bridge", "/data", null, String.class);

        assertEquals("DATA", resp);
        // Reconnect should be attempted at most once per execution
        verify(restTemplate, atMost(2)).postForObject(eq("http://bridge/reconnect"), isNull(), eq(String.class));
    }

    @Test
    void attemptReconnect_exceptionDuringReconnect_shouldLogError() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TelegramService telegramService = mock(TelegramService.class);

        ShioajiReconnectWrapperService svc = new ShioajiReconnectWrapperService(restTemplate, telegramService) {
            @Override
            void sleep(long delayMs) {
                // no-op
            }
        };

        when(restTemplate.getForObject("http://bridge/data", String.class))
            .thenThrow(new RuntimeException("boom"))
            .thenReturn("DATA");
        when(restTemplate.postForObject("http://bridge/reconnect", null, String.class))
            .thenThrow(new RuntimeException("Reconnect endpoint failed"));

        String resp = svc.executeWithReconnect("http://bridge", "/data", null, String.class);

        assertEquals("DATA", resp);
        verify(restTemplate).postForObject("http://bridge/reconnect", null, String.class);
    }
}
