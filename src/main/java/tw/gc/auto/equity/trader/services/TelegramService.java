package tw.gc.auto.equity.trader.services;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.agents.RiskManagerAgent;
import tw.gc.auto.equity.trader.config.TelegramProperties;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.services.telegram.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Telegram bot service for remote control and monitoring.
 * 
 * <p><b>Why Telegram integration:</b> Provides mobile-first interface for traders who need
 * real-time access without being desk-bound. Critical for manual interventions during market hours.</p>
 * 
 * <p><b>Design Decision:</b> Uses Command Pattern to decouple command processing from service logic.
 * This makes the service extensible and testable. New commands can be added without modifying
 * this class (Open/Closed Principle).</p>
 * 
 * <p><b>Architectural Choice:</b> Polling-based rather than webhooks. Simpler deployment
 * (no HTTPS requirement, no port exposure). Acceptable latency for manual commands.</p>
 */
@Service
@Slf4j
public class TelegramService {

    @NonNull
    private final RestTemplate restTemplate;
    @NonNull
    private final TelegramProperties telegramProperties;
    @NonNull
    private final ObjectMapper objectMapper;
    @NonNull
    private final StockSettingsService stockSettingsService;
    @NonNull
    private final TelegramCommandRegistry commandRegistry;
    @NonNull
    private final GoLiveStateManager goLiveStateManager;

    // Services for command context (nullable to handle circular dependencies)
    @Nullable
    private AgentService agentService;
    @Nullable
    private BotModeService botModeService;
    @Nullable
    private TradingStateService tradingStateService;
    @Nullable
    private PositionManager positionManager;
    @Nullable
    private RiskManagementService riskManagementService;
    @Nullable
    private ContractScalingService contractScalingService;
    @Nullable
    private OrderExecutionService orderExecutionService;
    @Nullable
    private ActiveStockService activeStockService;

    // Legacy command handlers for backward compatibility
    @Nullable
    private Consumer<Void> statusHandler;
    @Nullable
    private Consumer<Void> pauseHandler;
    @Nullable
    private Consumer<Void> resumeHandler;
    @Nullable
    private Consumer<Void> closeHandler;
    @Nullable
    private Consumer<Void> shutdownHandler;
    
    // Track last processed update ID to avoid duplicate processing
    private long lastUpdateId = 0;

    // Custom command handlers for dynamic registration
    private final Map<String, Consumer<String>> customCommands = new HashMap<>();

    public TelegramService(
            RestTemplate restTemplate,
            TelegramProperties telegramProperties,
            ObjectMapper objectMapper,
            StockSettingsService stockSettingsService,
            TelegramCommandRegistry commandRegistry,
            GoLiveStateManager goLiveStateManager) {
        this.restTemplate = restTemplate;
        this.telegramProperties = telegramProperties;
        this.objectMapper = objectMapper;
        this.stockSettingsService = stockSettingsService;
        this.commandRegistry = commandRegistry;
        this.goLiveStateManager = goLiveStateManager;
    }

    /**
     * Initialize Telegram service and register commands.
     * 
     * <p><b>Why clear old messages:</b> Prevents stale commands from executing after restart.
     * Fetching latest update ID ensures only new commands are processed.</p>
     */
    @PostConstruct
    public void init() {
        clearOldMessages();
        commandRegistry.registerCommands();
        log.info("📱 Telegram command interface initialized with {} commands", 
            commandRegistry.getCommandCount());
    }

    private void clearOldMessages() {
        if (!telegramProperties.isEnabled()) return;
        
        try {
            String url = String.format(
                "https://api.telegram.org/bot%s/getUpdates?offset=-1&limit=1",
                telegramProperties.getBotToken()
            );
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            
            if (root.path("ok").asBoolean(false)) {
                JsonNode results = root.path("result");
                if (results.isArray() && results.size() > 0) {
                    lastUpdateId = results.get(0).path("update_id").asLong();
                    log.info("📱 Telegram initialized - skipped {} old messages", lastUpdateId);
                } else {
                    log.info("📱 Telegram initialized - no pending messages");
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not clear old Telegram messages: {}", e.getMessage());
        }
    }
    
    // ========== Dependency Injection Setters ==========
    // These setters handle circular dependencies by allowing late binding

    public void setAgentService(AgentService agentService) {
        this.agentService = agentService;
    }
    
    public void setBotModeService(BotModeService botModeService) {
        this.botModeService = botModeService;
    }
    
    public void setTradingStateService(TradingStateService tradingStateService) {
        this.tradingStateService = tradingStateService;
    }
    
    public void setPositionManager(PositionManager positionManager) {
        this.positionManager = positionManager;
    }
    
    public void setRiskManagementService(RiskManagementService riskManagementService) {
        this.riskManagementService = riskManagementService;
    }
    
    public void setContractScalingService(ContractScalingService contractScalingService) {
        this.contractScalingService = contractScalingService;
    }
    
    public void setOrderExecutionService(OrderExecutionService orderExecutionService) {
        this.orderExecutionService = orderExecutionService;
    }
    
    public void setActiveStockService(ActiveStockService activeStockService) {
        this.activeStockService = activeStockService;
    }

    /**
     * Register command handlers for backward compatibility.
     * 
     * <p><b>Why keep legacy handlers:</b> Gradual migration strategy. TelegramCommandHandler
     * still uses these callbacks. Will be removed once all command logic migrates to Command Pattern.</p>
     */
    public void registerCommandHandlers(
            Consumer<Void> statusHandler,
            Consumer<Void> pauseHandler,
            Consumer<Void> resumeHandler,
            Consumer<Void> closeHandler,
            Consumer<Void> shutdownHandler) {
        this.statusHandler = statusHandler;
        this.pauseHandler = pauseHandler;
        this.resumeHandler = resumeHandler;
        this.closeHandler = closeHandler;
        this.shutdownHandler = shutdownHandler;
        log.info("✅ Telegram command handlers registered");
    }

    /**
     * Register dynamic custom commands.
     * 
     * <p><b>Why custom commands:</b> Enables TelegramCommandHandler to register domain-specific
     * commands (backtest, strategy switching, etc.) without tight coupling.</p>
     */
    public void registerCustomCommand(String command, Consumer<String> handler) {
        String key = command.toLowerCase();
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        customCommands.put(key, handler);
        log.info("✅ Registered custom command: {}", command);
    }

    public List<String> getRegistryHelpLines() {
        return commandRegistry.getAllCommands().stream()
            .map(TelegramCommand::getHelpText)
            .filter(Objects::nonNull)
            .filter(text -> !text.isBlank())
            .sorted()
            .toList();
    }

    public List<String> getCustomCommandNames() {
        List<String> names = new ArrayList<>(customCommands.keySet());
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    /**
     * Poll for Telegram updates every 5 seconds.
     * JUSTIFICATION: Required for Telegram bot command interface to work.
     * Telegram API uses long-polling for updates, polling every 5s provides responsive command handling.
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

    /**
     * Process a single Telegram update.
     * 
     * <p><b>Why Command Pattern dispatch:</b> Clean separation between message parsing and
     * command execution. Adding new commands requires no changes to this method.</p>
     */
    private void processUpdate(JsonNode update) {
        long updateId = update.path("update_id").asLong();
        if (updateId <= lastUpdateId) return;
        lastUpdateId = updateId;
        
        JsonNode message = update.path("message");
        if (message.isMissingNode()) return;
        
        if (!isAuthorizedChat(message)) return;
        
        String text = message.path("text").asText("").trim();
        if (!text.startsWith("/")) return;
        
        log.info("📥 Received command: {}", text);
        
        String[] parts = text.split(" ", 2);
        String commandName = parts[0].substring(1).toLowerCase(); // Remove leading /
        String args = parts.length > 1 ? parts[1] : "";
        
        dispatchCommand(commandName, args);
    }
    
    private boolean isAuthorizedChat(JsonNode message) {
        String chatId = message.path("chat").path("id").asText();
        if (!chatId.equals(telegramProperties.getChatId())) {
            log.warn("⚠️ Ignored command from unauthorized chat: {}", chatId);
            return false;
        }
        return true;
    }
    
    /**
     * Dispatch command to appropriate handler.
     * 
     * <p><b>Dispatch priority:</b>
     * 1. Command Pattern commands (new architecture)
     * 2. Custom commands (TelegramCommandHandler integration)
     * 3. Unknown command fallback
     * </p>
     */
    private void dispatchCommand(String commandName, String args) {
        // Try Command Pattern first
        TelegramCommand command = commandRegistry.getCommand(commandName);
        if (command != null) {
            executeCommand(command, args);
            return;
        }
        
        // Try custom commands
        if (customCommands.containsKey(commandName)) {
            customCommands.get(commandName).accept(args);
            return;
        }
        
        // Check for alternate command names
        if ("agents".equals(commandName)) {
            command = commandRegistry.getCommand("agent");
            if (command != null) {
                executeCommand(command, args);
                return;
            }
        }
        
        // Unknown command
        sendMessage("❓ Unknown command: /" + commandName + "\n\nUse /help to see all available commands");
    }
    
    /**
     * Execute a Command Pattern command with context.
     */
    private void executeCommand(TelegramCommand command, String args) {
        TelegramCommandContext context = buildCommandContext();
        
        try {
            command.execute(args, context);
        } catch (Exception e) {
            log.error("❌ Command execution failed: {} - {}", command.getCommandName(), e.getMessage(), e);
            sendMessage("❌ Command failed: " + e.getMessage());
        }
    }
    
    /**
     * Build command execution context from current state.
     * 
     * <p><b>Why builder pattern:</b> Many dependencies are optional (circular dependency resolution).
     * Builder makes it clear which dependencies are available at execution time.</p>
     */
    private TelegramCommandContext buildCommandContext() {
        return TelegramCommandContext.builder()
            .telegramService(this)
            .tradingStateService(tradingStateService)
            .positionManager(positionManager)
            .riskManagementService(riskManagementService)
            .contractScalingService(contractScalingService)
            .stockSettingsService(stockSettingsService)
            .orderExecutionService(orderExecutionService)
            .activeStockService(activeStockService)
            .agentService(agentService)
            .botModeService(botModeService)
            .goLiveStateManager(goLiveStateManager)
            .statusHandler(statusHandler)
            .pauseHandler(pauseHandler)
            .resumeHandler(resumeHandler)
            .closeHandler(closeHandler)
            .shutdownHandler(shutdownHandler)
            .build();
    }
    
    // ========== Message Sending ==========
    
    /**
     * Send daily trading summary digest at 13:05 (5 minutes after market close).
     * 
     * <p><b>Why disabled:</b> Superseded by EndOfDayStatisticsService which provides
     * more comprehensive reporting. Kept here for potential re-enablement.</p>
     */
    // @Scheduled(cron = "0 5 13 * * MON-FRI", zone = "Asia/Taipei")
    public void sendDailySummaryDigest() {
        if (agentService == null || botModeService == null) {
            return;
        }

        RiskManagerAgent riskManager = agentService.getRiskManager();
        TradingMode mode = botModeService.isLiveMode() ? TradingMode.LIVE : TradingMode.SIMULATION;
        Map<String, Object> stats = riskManager.getTradeStats(mode);

        if (!(boolean) stats.getOrDefault("success", false)) {
            return;
        }

        sendMessage(String.format(
                "📆 DAILY DIGEST (%s)\nTrades 30d: %s\nWin Rate: %s\nPnL 30d: %.0f",
                mode.name(),
                stats.getOrDefault("total_trades_30d", "0"),
                stats.getOrDefault("win_rate", "N/A"),
                stats.getOrDefault("total_pnl_30d", 0.0)
        ));
    }

    public void sendMessage(String message) {
        sendMessageInternal(message, true);
    }

    public void sendHtmlMessage(String htmlMessage) {
        sendMessageInternal(htmlMessage, false);
    }

    private void sendMessageInternal(String message, boolean escapeHtml) {
        if (!telegramProperties.isEnabled()) {
            log.info("[Telegram disabled] {}", message);
            return;
        }

        if (message == null) {
            return;
        }

        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", 
                    telegramProperties.getBotToken());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add bot emoticon prefix if not already present
            if (!message.startsWith("🤖")) {
                message = "🤖 " + message;
            }

            String payload = escapeHtml ? escapeHtml(message) : message;

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", telegramProperties.getChatId());
            body.put("text", payload);
            body.put("parse_mode", "HTML");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
            log.debug("Telegram message sent");

        } catch (Exception e) {
            log.error("Failed to send Telegram message", e);
        }
    }

    private String escapeHtml(String input) {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
