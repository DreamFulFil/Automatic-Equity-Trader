package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.config.TradingProperties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MTXF Lunch-Break Trading Engine
 * 
 * Core trading orchestrator responsible for:
 * - Signal polling and trade execution
 * - Position management
 * - Trading window enforcement
 * 
 * Delegates to:
 * - ContractScalingService: Contract sizing logic
 * - RiskManagementService: P&L tracking and limits
 * - TelegramService: Notifications and commands
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngine {
    
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;
    private final TradingProperties tradingProperties;
    private final ApplicationContext applicationContext;
    private final ContractScalingService contractScalingService;
    private final RiskManagementService riskManagementService;
    
    // Position tracking
    private final AtomicInteger currentPosition = new AtomicInteger(0);
    private final AtomicReference<Double> entryPrice = new AtomicReference<>(0.0);
    private final AtomicReference<LocalDateTime> positionEntryTime = new AtomicReference<>(null);
    
    // State flags
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    private volatile boolean tradingPaused = false;
    
    // News veto cache
    private final AtomicBoolean cachedNewsVeto = new AtomicBoolean(false);
    private final AtomicReference<String> cachedNewsReason = new AtomicReference<>("");
    
    @PostConstruct
    public void initialize() {
        log.info("MTXF Lunch Bot initializing...");
        
        registerTelegramCommands();
        
        String startupMessage = String.format(
            "MTXF Lunch Bot started\n" +
            "Trading window: %s - %s\n" +
            "Max position: %d contract(s)\n" +
            "Daily loss limit: %d TWD\n" +
            "Weekly loss limit: %d TWD\n" +
            "Max hold time: %d min\n" +
            "Signal: 30s | News: 10min\n" +
            "Weekly P&L: %.0f TWD%s%s",
            tradingProperties.getWindow().getStart(),
            tradingProperties.getWindow().getEnd(),
            contractScalingService.getMaxContracts(),
            tradingProperties.getRisk().getDailyLossLimit(),
            tradingProperties.getRisk().getWeeklyLossLimit(),
            tradingProperties.getRisk().getMaxHoldMinutes(),
            riskManagementService.getWeeklyPnL(),
            riskManagementService.isWeeklyLimitHit() ? "\nWEEKLY LIMIT HIT - Paused until Monday" : "",
            riskManagementService.isEarningsBlackout() ? String.format("\nEARNINGS BLACKOUT (%s) - No trading today", 
                riskManagementService.getEarningsBlackoutStock()) : ""
        );
        telegramService.sendMessage(startupMessage);
        
        try {
            String response = restTemplate.getForObject(getBridgeUrl() + "/health", String.class);
            log.info("Python bridge connected: {}", response);
            marketDataConnected = true;
            
            runPreMarketHealthCheck();
            contractScalingService.updateContractSizing();
        } catch (Exception e) {
            log.error("Failed to connect to Python bridge", e);
            telegramService.sendMessage("Python bridge connection failed!");
        }
    }
    
    private void runPreMarketHealthCheck() {
        try {
            java.util.Map<String, Object> testOrder = new java.util.HashMap<>();
            testOrder.put("action", "BUY");
            testOrder.put("quantity", 1);
            testOrder.put("price", 20000.0);
            
            String result = restTemplate.postForObject(
                    getBridgeUrl() + "/order/dry-run", testOrder, String.class);
            
            if (result != null && result.contains("validated")) {
                log.info("Pre-market health check PASSED: order endpoint working");
            } else {
                log.warn("Pre-market health check: unexpected response: {}", result);
                telegramService.sendMessage("Pre-market health check: order endpoint returned unexpected response");
            }
        } catch (Exception e) {
            log.error("Pre-market health check FAILED: order endpoint broken", e);
            telegramService.sendMessage("PRE-MARKET CHECK FAILED!\nOrder endpoint error: " + e.getMessage() + 
                    "\nOrders may fail during trading session!");
        }
    }
    
    private void registerTelegramCommands() {
        telegramService.registerCommandHandlers(
            v -> handleStatusCommand(),
            v -> handlePauseCommand(),
            v -> handleResumeCommand(),
            v -> handleCloseCommand()
        );
    }
    
    private void handleStatusCommand() {
        String state = "ACTIVE";
        if (emergencyShutdown) state = "EMERGENCY SHUTDOWN";
        else if (riskManagementService.isWeeklyLimitHit()) state = "WEEKLY LIMIT PAUSED";
        else if (riskManagementService.isEarningsBlackout()) state = "EARNINGS BLACKOUT";
        else if (tradingPaused) state = "PAUSED BY USER";
        
        String positionInfo = currentPosition.get() == 0 ? "No position" :
            String.format("%d @ %.0f (held %d min)",
                currentPosition.get(),
                entryPrice.get(),
                positionEntryTime.get() != null ?
                    java.time.Duration.between(positionEntryTime.get(), LocalDateTime.now(TAIPEI_ZONE)).toMinutes() : 0
            );
        
        String message = String.format(
            "BOT STATUS\n" +
            "State: %s\n" +
            "Position: %s\n" +
            "Max Contracts: %d\n" +
            "Equity: %.0f TWD\n" +
            "30d Profit: %.0f TWD\n" +
            "Today P&L: %.0f TWD\n" +
            "Week P&L: %.0f TWD\n" +
            "News Veto: %s\n" +
            "Commands: /pause /resume /close",
            state, positionInfo, contractScalingService.getMaxContracts(), 
            contractScalingService.getLastEquity(), contractScalingService.getLast30DayProfit(),
            riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL(),
            cachedNewsVeto.get() ? "ACTIVE" : "Clear"
        );
        telegramService.sendMessage(message);
    }
    
    private void handlePauseCommand() {
        tradingPaused = true;
        log.info("Trading paused by user command");
        telegramService.sendMessage("Trading PAUSED\nNo new entries until /resume\nExisting positions will still flatten at 13:00");
    }
    
    private void handleResumeCommand() {
        if (riskManagementService.isWeeklyLimitHit()) {
            telegramService.sendMessage("Cannot resume - Weekly loss limit hit\nWait until next Monday");
            return;
        }
        if (riskManagementService.isEarningsBlackout()) {
            telegramService.sendMessage("Cannot resume - Earnings blackout day\nNo trading today");
            return;
        }
        tradingPaused = false;
        log.info("Trading resumed by user command");
        telegramService.sendMessage("Trading RESUMED\nBot is active");
    }
    
    private void handleCloseCommand() {
        if (currentPosition.get() == 0) {
            telegramService.sendMessage("No position to close");
            return;
        }
        log.info("Close command received from user");
        flattenPosition("Closed by user");
        telegramService.sendMessage("Position closed by user command");
    }
    
    private String getBridgeUrl() {
        return tradingProperties.getBridge().getUrl();
    }
    
    @Scheduled(fixedRate = 30000)
    public void tradingLoop() {
        if (emergencyShutdown || !marketDataConnected) return;
        
        if (riskManagementService.isEarningsBlackout()) {
            log.debug("Earnings blackout day - no trading");
            return;
        }
        
        if (riskManagementService.isWeeklyLimitHit() && currentPosition.get() == 0) {
            log.debug("Weekly limit hit - waiting until next Monday");
            return;
        }
        
        LocalTime now = LocalTime.now(TAIPEI_ZONE);
        LocalTime start = LocalTime.parse(tradingProperties.getWindow().getStart());
        LocalTime end = LocalTime.parse(tradingProperties.getWindow().getEnd());
        
        if (now.isBefore(start) || now.isAfter(end)) {
            return;
        }
        
        try {
            checkRiskLimits();
            check45MinuteHardExit();
            
            if (now.getMinute() % 10 == 0 && now.getSecond() < 30) {
                updateNewsVetoCache();
            }
            
            if (currentPosition.get() == 0) {
                if (!tradingPaused && !riskManagementService.isWeeklyLimitHit()) {
                    evaluateEntry();
                }
            } else {
                evaluateExit();
            }
            
        } catch (Exception e) {
            log.error("Trading loop error", e);
            telegramService.sendMessage("Trading loop error: " + e.getMessage());
        }
    }
    
    private void check45MinuteHardExit() {
        if (currentPosition.get() == 0) return;
        
        LocalDateTime entryTime = positionEntryTime.get();
        if (entryTime == null) return;
        
        long minutesHeld = java.time.Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
        int maxHold = tradingProperties.getRisk().getMaxHoldMinutes();
        
        if (minutesHeld >= maxHold) {
            log.warn("45-MINUTE HARD EXIT: Position held {} minutes", minutesHeld);
            telegramService.sendMessage(String.format(
                "45-MIN HARD EXIT\nPosition held %d minutes\nForce-flattening now!",
                minutesHeld
            ));
            flattenPosition("45-minute time limit");
        }
    }
    
    private void updateNewsVetoCache() {
        try {
            log.info("Checking news veto (10-min interval)...");
            String newsJson = restTemplate.getForObject(getBridgeUrl() + "/signal/news", String.class);
            JsonNode newsData = objectMapper.readTree(newsJson);
            
            boolean veto = newsData.path("news_veto").asBoolean(false);
            String reason = newsData.path("news_reason").asText("");
            double score = newsData.path("news_score").asDouble(0.5);
            
            cachedNewsVeto.set(veto);
            cachedNewsReason.set(reason);
            
            if (veto) {
                log.warn("News veto ACTIVE: {} (score: {})", reason, score);
                telegramService.sendMessage(String.format(
                        "NEWS VETO ACTIVE\nReason: %s\nScore: %.2f\nNo new entries until next check",
                        reason, score));
            } else {
                log.info("News check passed (score: {})", score);
            }
            
        } catch (Exception e) {
            log.error("News veto check failed: {}", e.getMessage());
        }
    }
    
    private void checkRiskLimits() {
        if (riskManagementService.isDailyLimitExceeded(tradingProperties.getRisk().getDailyLossLimit())) {
            log.error("DAILY LOSS LIMIT HIT: {} TWD", riskManagementService.getDailyPnL());
            telegramService.sendMessage(String.format(
                    "EMERGENCY SHUTDOWN\nDaily loss: %.0f TWD\nFlattening all positions!", 
                    riskManagementService.getDailyPnL()));
            flattenPosition("Daily loss limit");
            emergencyShutdown = true;
        }
    }
    
    private void evaluateEntry() throws Exception {
        if (cachedNewsVeto.get()) {
            log.debug("News veto active (cached) - no entry. Reason: {}", cachedNewsReason.get());
            return;
        }
        
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        String direction = signal.path("direction").asText("NEUTRAL");
        double confidence = signal.path("confidence").asDouble(0.0);
        double currentPrice = signal.path("current_price").asDouble(0.0);
        
        if (confidence < 0.65) {
            log.debug("Low confidence: {}", confidence);
            return;
        }
        
        int quantity = contractScalingService.getMaxContracts();
        
        if ("LONG".equals(direction)) {
            executeOrder("BUY", quantity, currentPrice);
        } else if ("SHORT".equals(direction)) {
            executeOrder("SELL", quantity, currentPrice);
        }
    }
    
    private void evaluateExit() throws Exception {
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        double currentPrice = signal.path("current_price").asDouble(0.0);
        int pos = currentPosition.get();
        double entry = entryPrice.get();
        double unrealizedPnL = (currentPrice - entry) * pos * 50;
        
        double stopLossThreshold = -500 * Math.abs(pos);
        boolean stopLoss = unrealizedPnL < stopLossThreshold;
        boolean exitSignal = signal.path("exit_signal").asBoolean(false);
        
        if (stopLoss) {
            log.warn("Stop-loss hit: {} TWD (threshold: {})", unrealizedPnL, stopLossThreshold);
            flattenPosition("Stop-loss");
        } else if (exitSignal) {
            log.info("Exit signal (trend reversal): {} TWD", unrealizedPnL);
            flattenPosition("Trend reversal");
        } else if (unrealizedPnL > 0) {
            log.debug("Position running: +{} TWD (no cap, letting it run)", unrealizedPnL);
        }
    }
    
    private void executeOrder(String action, int quantity, double price) {
        try {
            java.util.Map<String, Object> orderMap = new java.util.HashMap<>();
            orderMap.put("action", action);
            orderMap.put("quantity", quantity);
            orderMap.put("price", price);
            
            log.info("Sending order: {}", orderMap);
            String result = restTemplate.postForObject(
                    getBridgeUrl() + "/order", orderMap, String.class);
            log.debug("Order response: {}", result);
            
            if ("BUY".equals(action)) {
                currentPosition.addAndGet(quantity);
            } else {
                currentPosition.addAndGet(-quantity);
            }
            entryPrice.set(price);
            positionEntryTime.set(LocalDateTime.now(TAIPEI_ZONE));
            
            telegramService.sendMessage(String.format(
                    "ORDER FILLED\n%s %d MTXF @ %.0f\nPosition: %d", 
                    action, quantity, price, currentPosition.get()));
            
        } catch (Exception e) {
            log.error("Order execution failed", e);
            telegramService.sendMessage("Order failed: " + e.getMessage());
        }
    }
    
    private void flattenPosition(String reason) {
        int pos = currentPosition.get();
        if (pos == 0) return;
        
        String action = pos > 0 ? "SELL" : "BUY";
        try {
            String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
            JsonNode signal = objectMapper.readTree(signalJson);
            double currentPrice = signal.path("current_price").asDouble(0.0);
            
            executeOrder(action, Math.abs(pos), currentPrice);
            
            double pnl = (currentPrice - entryPrice.get()) * pos * 50;
            riskManagementService.recordPnL(pnl, tradingProperties.getRisk().getWeeklyLossLimit());
            
            currentPosition.set(0);
            positionEntryTime.set(null);
            
            log.info("Position flattened - P&L: {} TWD (Reason: {})", pnl, reason);
            telegramService.sendMessage(String.format(
                    "POSITION CLOSED\nReason: %s\nP&L: %.0f TWD\nDaily P&L: %.0f TWD\nWeekly P&L: %.0f TWD", 
                    reason, pnl, riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL()));
            
            if (riskManagementService.isWeeklyLimitHit()) {
                telegramService.sendMessage(String.format(
                    "WEEKLY LOSS LIMIT HIT\nWeekly P&L: %.0f TWD\nTrading paused until next Monday!",
                    riskManagementService.getWeeklyPnL()
                ));
            }
            
        } catch (Exception e) {
            log.error("Flatten failed", e);
        }
    }
    
    @Scheduled(cron = "0 0 13 * * MON-FRI", zone = "Asia/Taipei")
    public void autoFlatten() {
        log.info("13:00 Auto-flatten triggered");
        flattenPosition("End of trading window");
        sendDailySummary();
        
        log.info("Trading window ended - shutting down application");
        telegramService.sendMessage("Trading window ended - Bot shutting down");
        
        // Exit the Spring context, which will then trigger @PreDestroy
        System.exit(SpringApplication.exit(applicationContext));
    }
    
    private void shutdownPythonBridge() {
        try {
            log.info("Requesting Python bridge shutdown...");
            restTemplate.postForObject(getBridgeUrl() + "/shutdown", "", String.class);
            log.info("Python bridge shutdown requested");
        } catch (Exception e) {
            log.warn("Could not notify Python bridge to shutdown: {}", e.getMessage());
        }
    }
    
    private void sendDailySummary() {
        double pnl = riskManagementService.getDailyPnL();
        String status = pnl > 0 ? "Profitable" : "Loss";
        String comment = "";
        
        if (pnl > 3000) {
            comment = "\nEXCEPTIONAL DAY! Let winners run!";
        } else if (pnl > 2000) {
            comment = "\nGreat performance!";
        } else if (pnl > 1000) {
            comment = "\nSolid day!";
        }
        
        telegramService.sendMessage(String.format(
                "DAILY SUMMARY\n" +
                "Today P&L: %.0f TWD\n" +
                "Week P&L: %.0f TWD\n" +
                "Contracts: %d\n" +
                "Equity: %.0f TWD\n" +
                "Status: %s%s\n" +
                "NO PROFIT CAPS - Unlimited upside!",
                pnl, riskManagementService.getWeeklyPnL(), contractScalingService.getMaxContracts(), 
                contractScalingService.getLastEquity(), status, comment));
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down - flattening positions");
        flattenPosition("System shutdown");
        // The shutdownPythonBridge() call is removed.
        telegramService.sendMessage("Bot stopped");
    }
}
