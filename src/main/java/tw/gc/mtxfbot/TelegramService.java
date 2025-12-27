package tw.gc.mtxfbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.agents.AgentService;
import tw.gc.mtxfbot.agents.RiskManagerAgent;
import tw.gc.mtxfbot.agents.TutorBotAgent;
import tw.gc.mtxfbot.config.TelegramProperties;
import tw.gc.mtxfbot.entities.AgentInteraction.InteractionType;

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
 * - /agent → list available agents
 * - /talk <question> → ask TutorBot a trading question
 * - /insight → get daily trading insight
 * - /golive → check eligibility to switch to live mode
 * - /backtosim → switch back to simulation mode
 * - /change-share <number> → change base shares for stock trading
 * - /change-increment <number> → change share increment per 20k equity
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TelegramService {

    private final RestTemplate restTemplate;
    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper;
    private final StockSettingsService stockSettingsService;

    // Command handlers - will be set by TradingEngine
    private Consumer<Void> statusHandler;
    private Consumer<Void> pauseHandler;
    private Consumer<Void> resumeHandler;
    private Consumer<Void> closeHandler;
    
    // Agent service for agent commands (set via setter to avoid circular dependency)
    private AgentService agentService;
    private BotModeService botModeService;
    
    // Track last processed update ID to avoid duplicate processing
    private long lastUpdateId = 0;

    @PostConstruct
    public void init() {
        log.info("📱 Telegram command interface initialized");
    }
    
    /**
     * Set agent service (called by AgentService to avoid circular dependency)
     */
    public void setAgentService(AgentService agentService) {
        this.agentService = agentService;
    }
    
    /**
     * Set bot mode service
     */
    public void setBotModeService(BotModeService botModeService) {
        this.botModeService = botModeService;
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
    @SuppressWarnings("null")
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
        
        String text = message.path("text").asText("").trim();
        if (!text.startsWith("/")) return;
        
        String lowerText = text.toLowerCase();
        log.info("📥 Received command: {}", text);
        
        // Process commands (case-insensitive for command, preserve case for arguments)
        if (lowerText.equals("/status")) {
            if (statusHandler != null) statusHandler.accept(null);
        } else if (lowerText.equals("/pause")) {
            if (pauseHandler != null) pauseHandler.accept(null);
        } else if (lowerText.equals("/resume")) {
            if (resumeHandler != null) resumeHandler.accept(null);
        } else if (lowerText.equals("/close")) {
            if (closeHandler != null) closeHandler.accept(null);
        } else if (lowerText.equals("/agent") || lowerText.equals("/agents")) {
            handleAgentCommand(chatId);
        } else if (lowerText.startsWith("/talk ")) {
            String question = text.substring(6).trim();
            handleTalkCommand(chatId, question);
        } else if (lowerText.equals("/insight")) {
            handleInsightCommand(chatId);
        } else if (lowerText.equals("/golive")) {
            handleGoLiveCommand(chatId);
        } else if (lowerText.equals("/backtosim")) {
            handleBackToSimCommand(chatId);
        } else if (lowerText.startsWith("/change-share ")) {
            String arg = text.substring(14).trim();
            handleChangeShareCommand(arg);
        } else if (lowerText.startsWith("/change-increment ")) {
            String arg = text.substring(18).trim();
            handleChangeIncrementCommand(arg);
        } else {
            sendMessage("❓ Unknown command: " + text + "\n\nAvailable:\n" +
                    "/status - Bot status\n" +
                    "/pause - Pause trading\n" +
                    "/resume - Resume trading\n" +
                    "/close - Close position\n" +
                    "/agent - List agents\n" +
                    "/talk <question> - Ask TutorBot\n" +
                    "/insight - Daily insight\n" +
                    "/golive - Check live eligibility\n" +
                    "/backtosim - Switch to simulation\n" +
                    "/change-share <number> - Change base shares\n" +
                    "/change-increment <number> - Change share increment");
        }
    }
    
    private void handleAgentCommand(String chatId) {
        if (agentService == null) {
            sendMessage("⚠️ Agent service not initialized");
            return;
        }
        sendMessage(agentService.getAgentListMessage());
    }
    
    private void handleTalkCommand(String chatId, String question) {
        if (agentService == null) {
            sendMessage("⚠️ Agent service not initialized");
            return;
        }
        
        if (question.isEmpty()) {
            sendMessage("❓ Usage: /talk <your question>\n\nExample: /talk What is momentum trading?");
            return;
        }
        
        sendMessage("🤔 Thinking...");
        
        TutorBotAgent tutor = agentService.getTutorBot();
        Map<String, Object> input = new HashMap<>();
        input.put("question", question);
        input.put("userId", chatId);
        input.put("type", InteractionType.QUESTION);
        
        Map<String, Object> result = tutor.safeExecute(input);
        
        if ((boolean) result.getOrDefault("success", false)) {
            String response = (String) result.get("response");
            int remaining = (int) result.getOrDefault("remaining", 0);
            sendMessage(String.format("📚 TutorBot:\n\n%s\n\n[%d questions remaining today]", 
                    response, remaining));
        } else {
            sendMessage("❌ " + result.getOrDefault("error", "TutorBot unavailable"));
        }
    }
    
    private void handleInsightCommand(String chatId) {
        if (agentService == null) {
            sendMessage("⚠️ Agent service not initialized");
            return;
        }
        
        sendMessage("💡 Generating insight...");
        
        TutorBotAgent tutor = agentService.getTutorBot();
        Map<String, Object> result = tutor.generateDailyInsight(chatId);
        
        if ((boolean) result.getOrDefault("success", false)) {
            String response = (String) result.get("response");
            int remaining = (int) result.getOrDefault("remaining", 0);
            sendMessage(String.format("💡 Daily Insight:\n\n%s\n\n[%d insights remaining today]", 
                    response, remaining));
        } else {
            sendMessage("❌ " + result.getOrDefault("error", "Could not generate insight"));
        }
    }
    
    private void handleGoLiveCommand(String chatId) {
        if (agentService == null || botModeService == null) {
            sendMessage("⚠️ Services not initialized");
            return;
        }
        
        if (botModeService.isLiveMode()) {
            sendMessage("ℹ️ Already in LIVE mode!\nUse /backtosim to switch back to simulation.");
            return;
        }
        
        RiskManagerAgent riskManager = agentService.getRiskManager();
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        boolean eligible = (boolean) result.getOrDefault("eligible", false);
        
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 GO-LIVE ELIGIBILITY CHECK\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append(String.format("Total Trades: %d\n", result.get("total_trades")));
        sb.append(String.format("Win Rate: %s %s\n", result.get("win_rate"), 
                (boolean) result.get("win_rate_ok") ? "✅" : "❌"));
        sb.append(String.format("Max Drawdown: %s %s\n", result.get("max_drawdown"),
                (boolean) result.get("drawdown_ok") ? "✅" : "❌"));
        sb.append(String.format("Has Enough Trades: %s\n\n", 
                (boolean) result.get("has_enough_trades") ? "✅" : "❌"));
        sb.append(String.format("Requirements: %s\n\n", result.get("requirements")));
        
        if (eligible) {
            sb.append("🟢 ELIGIBLE FOR LIVE TRADING!\n");
            sb.append("⚠️ Type /confirmlive to switch (real money at risk!)");
        } else {
            sb.append("🔴 NOT YET ELIGIBLE\n");
            sb.append("Keep trading in simulation to build your track record.");
        }
        
        sendMessage(sb.toString());
    }
    
    private void handleBackToSimCommand(String chatId) {
        if (botModeService == null) {
            sendMessage("⚠️ Bot mode service not initialized");
            return;
        }
        
        if (botModeService.isSimulationMode()) {
            sendMessage("ℹ️ Already in SIMULATION mode!");
            return;
        }
        
        botModeService.switchToSimulationMode();
        sendMessage("✅ Switched back to SIMULATION mode\nNo real money at risk.");
    }
    
    private void handleChangeShareCommand(String arg) {
        try {
            int shares = Integer.parseInt(arg);
            if (shares <= 0) {
                sendMessage("❌ Shares must be positive");
                return;
            }
            stockSettingsService.updateSettings(shares, stockSettingsService.getSettings().getShareIncrement());
            sendMessage("✅ Base shares updated to: " + shares);
        } catch (NumberFormatException e) {
            sendMessage("❌ Invalid number: " + arg);
        } catch (Exception e) {
            log.error("Failed to update shares", e);
            sendMessage("❌ Failed to update shares");
        }
    }
    
    private void handleChangeIncrementCommand(String arg) {
        try {
            int increment = Integer.parseInt(arg);
            if (increment <= 0) {
                sendMessage("❌ Increment must be positive");
                return;
            }
            stockSettingsService.updateSettings(stockSettingsService.getSettings().getShares(), increment);
            sendMessage("✅ Share increment updated to: " + increment);
        } catch (NumberFormatException e) {
            sendMessage("❌ Invalid number: " + arg);
        } catch (Exception e) {
            log.error("Failed to update increment", e);
            sendMessage("❌ Failed to update increment");
        }
    }

    @SuppressWarnings("null")
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
