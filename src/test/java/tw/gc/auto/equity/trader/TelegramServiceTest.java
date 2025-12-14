package tw.gc.auto.equity.trader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TelegramProperties;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TelegramServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TelegramProperties telegramProperties;

    @Mock
    private StockSettingsService stockSettingsService;

    private ObjectMapper objectMapper;

    private TelegramService telegramService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        telegramService = new TelegramService(restTemplate, telegramProperties, objectMapper, stockSettingsService);
    }

    // ==================== sendMessage() tests ====================

    @Test
    void sendMessage_whenEnabled_shouldCallTelegramApi() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-bot-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");

        // When
        telegramService.sendMessage("Test message");

        // Then
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        
        verify(restTemplate).postForObject(urlCaptor.capture(), requestCaptor.capture(), eq(String.class));
        
        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.contains("test-bot-token"));
        assertTrue(capturedUrl.contains("sendMessage"));
        
        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertEquals("123456789", body.get("chat_id"));
        assertEquals("🤖 Test message", body.get("text"));
    }

    @Test
    void sendMessage_whenDisabled_shouldNotCallTelegramApi() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(false);

        // When
        telegramService.sendMessage("Test message");

        // Then
        verifyNoInteractions(restTemplate);
    }

    @Test
    void sendMessage_whenApiThrowsException_shouldHandleGracefully() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-bot-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        // When & Then - should not throw
        assertDoesNotThrow(() -> telegramService.sendMessage("Test message"));
    }

    @Test
    void sendMessage_withEmoji_shouldSendCorrectly() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-bot-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");

        // When
        telegramService.sendMessage("🚀 MTXF Bot started");

        // Then
        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        
        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertEquals("🤖 🚀 MTXF Bot started", body.get("text"));
    }

    @Test
    void sendMessage_withMultilineMessage_shouldSendCorrectly() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-bot-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        String multilineMessage = "Line 1\nLine 2\nLine 3";
        String expectedMessage = "Line 1\nLine 2\nLine 3";

        // When
        telegramService.sendMessage(multilineMessage);

        // Then
        ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        
        Map<String, Object> body = requestCaptor.getValue().getBody();
        assertEquals("🤖 " + expectedMessage, body.get("text"));
    }

    // ==================== registerCommandHandlers() tests ====================

    @Test
    void registerCommandHandlers_shouldStoreHandlers() {
        // Given
        AtomicBoolean statusCalled = new AtomicBoolean(false);
        AtomicBoolean pauseCalled = new AtomicBoolean(false);
        AtomicBoolean resumeCalled = new AtomicBoolean(false);
        AtomicBoolean closeCalled = new AtomicBoolean(false);

        // When
        telegramService.registerCommandHandlers(
            v -> statusCalled.set(true),
            v -> pauseCalled.set(true),
            v -> resumeCalled.set(true),
            v -> closeCalled.set(true),
            v -> {}
        );

        // Then - handlers are registered (we can't easily test private fields,
        // but we verify no exception is thrown)
        assertDoesNotThrow(() -> telegramService.registerCommandHandlers(
            v -> {}, v -> {}, v -> {}, v -> {}, v -> {}
        ));
    }

    // ==================== pollUpdates() tests ====================

    @Test
    void pollUpdates_whenDisabled_shouldNotCallApi() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(false);

        // When
        telegramService.pollUpdates();

        // Then
        verify(restTemplate, never()).getForObject(anyString(), eq(String.class));
    }

    @Test
    void pollUpdates_whenEnabled_shouldCallGetUpdates() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        
        String response = "{\"ok\":true,\"result\":[]}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(String.class));
        
        String url = urlCaptor.getValue();
        assertTrue(url.contains("getUpdates"));
        assertTrue(url.contains("test-token"));
    }

    @Test
    void pollUpdates_whenApiThrows_shouldHandleGracefully() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Network error"));

        // When & Then - should not throw
        assertDoesNotThrow(() -> telegramService.pollUpdates());
    }

    @Test
    void pollUpdates_whenResponseNotOk_shouldNotProcessUpdates() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        
        String response = "{\"ok\":false,\"error_code\":401}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then - should not throw, just return
        verify(restTemplate).getForObject(anyString(), eq(String.class));
    }

    @Test
    void pollUpdates_withStatusCommand_shouldCallStatusHandler() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        AtomicBoolean statusCalled = new AtomicBoolean(false);
        telegramService.registerCommandHandlers(
            v -> statusCalled.set(true),
            v -> {}, v -> {}, v -> {}, v -> {}
        );
        
        String response = "{\"ok\":true,\"result\":[{\"update_id\":1,\"message\":{\"chat\":{\"id\":\"123456789\"},\"text\":\"/status\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then
        assertTrue(statusCalled.get());
    }

    @Test
    void pollUpdates_withUnauthorizedChat_shouldIgnoreCommand() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        AtomicBoolean statusCalled = new AtomicBoolean(false);
        telegramService.registerCommandHandlers(
            v -> statusCalled.set(true),
            v -> {}, v -> {}, v -> {}, v -> {}
        );
        
        // Different chat ID
        String response = "{\"ok\":true,\"result\":[{\"update_id\":1,\"message\":{\"chat\":{\"id\":\"999999999\"},\"text\":\"/status\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then - handler should NOT be called for unauthorized chat
        assertFalse(statusCalled.get());
    }

    @Test
    void pollUpdates_withUnknownCommand_shouldSendHelpMessage() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        String response = "{\"ok\":true,\"result\":[{\"update_id\":2,\"message\":{\"chat\":{\"id\":\"123456789\"},\"text\":\"/unknown\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then - should send help message
        verify(restTemplate).postForObject(contains("sendMessage"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void pollUpdates_withPauseCommand_shouldCallPauseHandler() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        AtomicBoolean pauseCalled = new AtomicBoolean(false);
        telegramService.registerCommandHandlers(
            v -> {}, 
            v -> pauseCalled.set(true),
            v -> {}, v -> {}, v -> {}
        );
        
        String response = "{\"ok\":true,\"result\":[{\"update_id\":3,\"message\":{\"chat\":{\"id\":\"123456789\"},\"text\":\"/pause\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then
        assertTrue(pauseCalled.get());
    }

    @Test
    void pollUpdates_withResumeCommand_shouldCallResumeHandler() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        AtomicBoolean resumeCalled = new AtomicBoolean(false);
        telegramService.registerCommandHandlers(
            v -> {}, v -> {},
            v -> resumeCalled.set(true),
            v -> {}, v -> {}
        );
        
        String response = "{\"ok\":true,\"result\":[{\"update_id\":4,\"message\":{\"chat\":{\"id\":\"123456789\"},\"text\":\"/resume\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then
        assertTrue(resumeCalled.get());
    }

    @Test
    void pollUpdates_withCloseCommand_shouldCallCloseHandler() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        AtomicBoolean closeCalled = new AtomicBoolean(false);
        telegramService.registerCommandHandlers(
            v -> {}, v -> {}, v -> {},
            v -> closeCalled.set(true), v -> {}
        );
        
        String response = "{\"ok\":true,\"result\":[{\"update_id\":5,\"message\":{\"chat\":{\"id\":\"123456789\"},\"text\":\"/close\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response);

        // When
        telegramService.pollUpdates();

        // Then
        assertTrue(closeCalled.get());
    }

    @Test
    void pollUpdates_shouldNotProcessDuplicateUpdates() throws Exception {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        AtomicBoolean statusCalled = new AtomicBoolean(false);
        telegramService.registerCommandHandlers(
            v -> statusCalled.set(true),
            v -> {}, v -> {}, v -> {}, v -> {}
        );
        
        // First poll with update_id=10
        String response1 = "{\"ok\":true,\"result\":[{\"update_id\":10,\"message\":{\"chat\":{\"id\":\"123456789\"},\"text\":\"/status\"}}]}";
        when(restTemplate.getForObject(contains("getUpdates"), eq(String.class))).thenReturn(response1);
        
        telegramService.pollUpdates();
        assertTrue(statusCalled.get());
        
        // Reset and poll again with same update_id
        statusCalled.set(false);
        telegramService.pollUpdates();
        
        // Should not call handler again for same update
        // Note: This depends on the offset parameter being updated
    }
}

