package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.config.TelegramProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TelegramProperties telegramProperties;

    @Mock
    private ObjectMapper objectMapper;

    private TelegramService telegramService;

    @BeforeEach
    void setUp() {
        telegramService = new TelegramService(restTemplate, telegramProperties, objectMapper);
    }

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
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        
        verify(restTemplate).postForObject(urlCaptor.capture(), requestCaptor.capture(), eq(String.class));
        
        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.contains("test-bot-token"));
        assertTrue(capturedUrl.contains("sendMessage"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestCaptor.getValue().getBody();
        assertEquals("123456789", body.get("chat_id"));
        assertEquals("Test message", body.get("text"));
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
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestCaptor.getValue().getBody();
        assertEquals("🚀 MTXF Bot started", body.get("text"));
    }

    @Test
    void sendMessage_withMultilineMessage_shouldSendCorrectly() {
        // Given
        when(telegramProperties.isEnabled()).thenReturn(true);
        when(telegramProperties.getBotToken()).thenReturn("test-bot-token");
        when(telegramProperties.getChatId()).thenReturn("123456789");
        
        String multilineMessage = "Line 1\nLine 2\nLine 3";

        // When
        telegramService.sendMessage(multilineMessage);

        // Then
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) requestCaptor.getValue().getBody();
        assertEquals(multilineMessage, body.get("text"));
    }
}
