package tw.gc.mtxfbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.config.TelegramProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Telegram Service with Command Interface
 * 
 * Supported commands (case-insensitive):
 * - /status → current position, today P&L, week P&L, bot state
 * - /pause → pause new entries until /resume (still flattens at 13:00)
 * - /resume → re-enable trading
 * - /close → immediately flatten everything
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    private final RestTemplate restTemplate;
    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper;

    // Command handlers - will be set by TradingEngine
    private Consumer<Void> statusHandler;
    private Consumer<Void> pauseHandler;
    private Consumer<Void> resumeHandler;
    private Consumer<Void> closeHandler;
    
    // Track last processed update ID to avoid duplicate processing
    private long lastUpdateId = 0;

    @PostConstruct
    public void init() {
        log.info("📱 Telegram command interface initialized");
    }

    /**
     * Register command handlers from TradingEngine
     */
    public void registerCommandHandlers(
            Consumer<Void> statusHandler,
            Consumer<Void> pauseHandler,
            Consumer<Void> resumeHandler,
            Consumer<Void> closeHandler) {
        this.statusHandler = statusHandler;
        this.pauseHandler = pauseHandler;
        this.resumeHandler = resumeHandler;
        this.closeHandler = closeHandler;
        log.info("✅ Telegram command handlers registered");
    }

    /**
     * Poll for Telegram updates every 5 seconds
     */
    @Scheduled(fixedRate = 5000)
    public void pollUpdates() {
        if (!telegramProperties.isEnabled()) return;
        
        try {
            String url = String.format(
                "https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=1",
                telegramProperties.getBotToken(),
                lastUpdateId + 1
            );
            
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            
            if (!root.path("ok").asBoolean(false)) return;
            
            JsonNode results = root.path("result");
            for (JsonNode update : results) {
                processUpdate(update);
            }
            
        } catch (Exception e) {
            log.debug("Telegram poll error: {}", e.getMessage());
        }
    }

    private void processUpdate(JsonNode update) {
        long updateId = update.path("update_id").asLong();
        if (updateId <= lastUpdateId) return;
        lastUpdateId = updateId;
        
        // Get message text
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;
        
        // Verify chat ID matches configured chat
        String chatId = message.path("chat").path("id").asText();
        if (!chatId.equals(telegramProperties.getChatId())) {
            log.warn("⚠️ Ignored command from unauthorized chat: {}", chatId);
            return;
        }
        
        String text = message.path("text").asText("").trim().toLowerCase();
        if (!text.startsWith("/")) return;
        
        log.info("📥 Received command: {}", text);
        
        // Process commands (case-insensitive)
        switch (text) {
            case "/status":
                if (statusHandler != null) statusHandler.accept(null);
                break;
            case "/pause":
                if (pauseHandler != null) pauseHandler.accept(null);
                break;
            case "/resume":
                if (resumeHandler != null) resumeHandler.accept(null);
                break;
            case "/close":
                if (closeHandler != null) closeHandler.accept(null);
                break;
            default:
                sendMessage("❓ Unknown command: " + text + "\n\nAvailable:\n/status\n/pause\n/resume\n/close");
        }
    }

    public void sendMessage(String message) {
        if (!telegramProperties.isEnabled()) {
            log.info("[Telegram disabled] {}", message);
            return;
        }

        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", 
                    telegramProperties.getBotToken());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", telegramProperties.getChatId());
            body.put("text", message);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
            log.debug("Telegram message sent");

        } catch (Exception e) {
            log.error("Failed to send Telegram message", e);
        }
    }
}
