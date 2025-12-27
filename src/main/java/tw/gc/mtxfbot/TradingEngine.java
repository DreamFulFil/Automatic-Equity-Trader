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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MTXF Lunch-Break Trading Engine (2025 Bulletproof Edition)
 * 
 * Timing Strategy:
 * - Signal calculation (price/momentum/volume/confidence) → every 30 seconds
 * - News veto (Llama 3.1 8B via Python bridge) → every 10 minutes only
 * - Trading window: 11:30 - 13:00 Taipei time
 * - Auto-flatten and shutdown at 13:00
 * 
 * Safety Features:
 * - 45-minute hard exit for any position
 * - Weekly loss limit (-15,000 TWD) with auto-pause until Monday
 * - Earnings blackout dates
 * - Telegram command interface (/status, /pause, /resume, /close)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngine {
    
    // Explicit timezone for Taiwan futures trading
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final String WEEKLY_PNL_FILE = "logs/weekly-pnl.txt";
    private static final String EARNINGS_BLACKOUT_FILE = "config/earnings-blackout-dates.json";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;
    private final TradingProperties tradingProperties;
    private final ApplicationContext applicationContext;
    
    // Earnings blackout dates loaded from JSON
    private final Set<String> earningsBlackoutDates = new HashSet<>();
    private String earningsBlackoutStock = null; // Which stock triggered blackout
    
    // Position and P&L tracking
    private final AtomicInteger currentPosition = new AtomicInteger(0);
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> weeklyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> entryPrice = new AtomicReference<>(0.0);
    
    // Position entry time for 45-minute hard exit
    private final AtomicReference<LocalDateTime> positionEntryTime = new AtomicReference<>(null);
    
    // State flags
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    private volatile boolean tradingPaused = false;  // For /pause command
    private volatile boolean weeklyLimitHit = false; // Weekly loss limit triggered
    private volatile boolean earningsBlackout = false; // Earnings blackout day
    
    // News veto cache - updated every 10 minutes, used by all signal checks
    private final AtomicBoolean cachedNewsVeto = new AtomicBoolean(false);
    private final AtomicReference<String> cachedNewsReason = new AtomicReference<>("");
    
    @PostConstruct
    public void initialize() {
        log.info("🚀 MTXF Lunch Bot initializing...");
        
        // Load weekly P&L from file (persists across restarts)
        loadWeeklyPnL();
        
        // Load earnings blackout dates from JSON file
        loadEarningsBlackoutDates();
        
        // Check if today is earnings blackout day
        checkEarningsBlackout();
        
        // Check weekly loss limit
        checkWeeklyLossLimit();
        
        // Register Telegram command handlers
        registerTelegramCommands();
        
        String startupMessage = String.format(
            "🚀 MTXF Lunch Bot started\n" +
            "Trading window: %s - %s\n" +
            "Max position: %d contract\n" +
            "Daily loss limit: %d TWD\n" +
            "Weekly loss limit: %d TWD\n" +
            "Max hold time: %d min\n" +
            "Signal: 30s | News: 10min\n" +
            "Weekly P&L: %.0f TWD%s%s",
            tradingProperties.getWindow().getStart(),
            tradingProperties.getWindow().getEnd(),
            tradingProperties.getRisk().getMaxPosition(),
            tradingProperties.getRisk().getDailyLossLimit(),
            tradingProperties.getRisk().getWeeklyLossLimit(),
            tradingProperties.getRisk().getMaxHoldMinutes(),
            weeklyPnL.get(),
            weeklyLimitHit ? "\n⚠️ WEEKLY LIMIT HIT - Paused until Monday" : "",
            earningsBlackout ? String.format("\n📅 EARNINGS BLACKOUT (%s) - No trading today", 
                earningsBlackoutStock != null ? earningsBlackoutStock : "earnings day") : ""
        );
        telegramService.sendMessage(startupMessage);
        
        try {
            String response = restTemplate.getForObject(getBridgeUrl() + "/health", String.class);
            log.info("✅ Python bridge connected: {}", response);
            marketDataConnected = true;
            
            // Pre-market health check: test order endpoint with dry-run
            runPreMarketHealthCheck();
        } catch (Exception e) {
            log.error("❌ Failed to connect to Python bridge", e);
            telegramService.sendMessage("⚠️ Python bridge connection failed!");
        }
    }
    
    /**
     * Pre-market health check: validates the full order flow without executing
     */
    private void runPreMarketHealthCheck() {
        try {
            java.util.Map<String, Object> testOrder = new java.util.HashMap<>();
            testOrder.put("action", "BUY");
            testOrder.put("quantity", 1);
            testOrder.put("price", 20000.0);
            
            String result = restTemplate.postForObject(
                    getBridgeUrl() + "/order/dry-run", testOrder, String.class);
            
            if (result != null && result.contains("validated")) {
                log.info("✅ Pre-market health check PASSED: order endpoint working");
            } else {
                log.warn("⚠️ Pre-market health check: unexpected response: {}", result);
                telegramService.sendMessage("⚠️ Pre-market health check: order endpoint returned unexpected response");
            }
        } catch (Exception e) {
            log.error("❌ Pre-market health check FAILED: order endpoint broken", e);
            telegramService.sendMessage("🚨 PRE-MARKET CHECK FAILED!\nOrder endpoint error: " + e.getMessage() + 
                    "\n⚠️ Orders may fail during trading session!");
        }
    }
    
    /**
     * Register Telegram command handlers
     */
    private void registerTelegramCommands() {
        telegramService.registerCommandHandlers(
            v -> handleStatusCommand(),
            v -> handlePauseCommand(),
            v -> handleResumeCommand(),
            v -> handleCloseCommand()
        );
    }
    
    /**
     * /status - Show current position, P&L, and bot state
     */
    private void handleStatusCommand() {
        String state = "🟢 ACTIVE";
        if (emergencyShutdown) state = "🔴 EMERGENCY SHUTDOWN";
        else if (weeklyLimitHit) state = "🟡 WEEKLY LIMIT PAUSED";
        else if (earningsBlackout) state = "📅 EARNINGS BLACKOUT";
        else if (tradingPaused) state = "⏸️ PAUSED BY USER";
        
        String positionInfo = currentPosition.get() == 0 ? "No position" :
            String.format("%d @ %.0f (held %d min)",
                currentPosition.get(),
                entryPrice.get(),
                positionEntryTime.get() != null ?
                    java.time.Duration.between(positionEntryTime.get(), LocalDateTime.now(TAIPEI_ZONE)).toMinutes() : 0
            );
        
        String message = String.format(
            "📊 BOT STATUS\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "State: %s\n" +
            "Position: %s\n" +
            "Today P&L: %.0f TWD\n" +
            "Week P&L: %.0f TWD\n" +
            "News Veto: %s\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "Commands: /pause /resume /close",
            state, positionInfo, dailyPnL.get(), weeklyPnL.get(),
            cachedNewsVeto.get() ? "🚫 ACTIVE" : "✅ Clear"
        );
        telegramService.sendMessage(message);
    }
    
    /**
     * /pause - Pause new entries (still flattens at 13:00)
     */
    private void handlePauseCommand() {
        tradingPaused = true;
        log.info("⏸️ Trading paused by user command");
        telegramService.sendMessage("⏸️ Trading PAUSED\nNo new entries until /resume\nExisting positions will still flatten at 13:00");
    }
    
    /**
     * /resume - Resume trading
     */
    private void handleResumeCommand() {
        if (weeklyLimitHit) {
            telegramService.sendMessage("❌ Cannot resume - Weekly loss limit hit\nWait until next Monday");
            return;
        }
        if (earningsBlackout) {
            telegramService.sendMessage("❌ Cannot resume - Earnings blackout day\nNo trading today");
            return;
        }
        tradingPaused = false;
        log.info("▶️ Trading resumed by user command");
        telegramService.sendMessage("▶️ Trading RESUMED\nBot is active");
    }
    
    /**
     * /close - Immediately flatten all positions
     */
    private void handleCloseCommand() {
        if (currentPosition.get() == 0) {
            telegramService.sendMessage("ℹ️ No position to close");
            return;
        }
        log.info("🔴 Close command received from user");
        flattenPosition("Closed by user");
        telegramService.sendMessage("✅ Position closed by user command");
    }
    
    /**
     * Load earnings blackout dates from config/earnings-blackout-dates.json
     * This file is updated daily at 09:00 by the Python scraper
     */
    private void loadEarningsBlackoutDates() {
        try {
            File file = new File(EARNINGS_BLACKOUT_FILE);
            if (!file.exists()) {
                log.warn("📅 Earnings blackout file not found: {} - no blackout dates loaded", EARNINGS_BLACKOUT_FILE);
                return;
            }
            
            JsonNode root = objectMapper.readTree(file);
            JsonNode dates = root.path("dates");
            
            if (dates.isArray()) {
                for (JsonNode dateNode : dates) {
                    earningsBlackoutDates.add(dateNode.asText());
                }
            }
            
            String lastUpdated = root.path("last_updated").asText("unknown");
            log.info("📅 Loaded {} earnings blackout dates (last updated: {})", 
                earningsBlackoutDates.size(), lastUpdated);
            
        } catch (Exception e) {
            log.error("❌ Failed to load earnings blackout dates: {}", e.getMessage());
        }
    }
    
    /**
     * Check if today is an earnings blackout day
     */
    private void checkEarningsBlackout() {
        String today = LocalDate.now(TAIPEI_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (earningsBlackoutDates.contains(today)) {
            earningsBlackout = true;
            earningsBlackoutStock = "TSMC/major earnings"; // Could be enhanced to show which stock
            log.warn("📅 EARNINGS BLACKOUT DAY: {}", today);
        }
    }
    
    /**
     * Load weekly P&L from file (persists across restarts)
     */
    private void loadWeeklyPnL() {
        try {
            Path path = Paths.get(WEEKLY_PNL_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                String[] parts = content.split(",");
                if (parts.length >= 2) {
                    LocalDate savedDate = LocalDate.parse(parts[0]);
                    double savedPnL = Double.parseDouble(parts[1]);
                    
                    // Check if we're still in the same week (Mon-Fri)
                    LocalDate today = LocalDate.now(TAIPEI_ZONE);
                    LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    
                    if (!savedDate.isBefore(startOfWeek)) {
                        weeklyPnL.set(savedPnL);
                        log.info("📂 Loaded weekly P&L: {} TWD (from {})", savedPnL, savedDate);
                    } else {
                        // New week, reset P&L
                        weeklyPnL.set(0.0);
                        saveWeeklyPnL();
                        log.info("🔄 New week started - Weekly P&L reset to 0");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load weekly P&L: {}", e.getMessage());
        }
    }
    
    /**
     * Save weekly P&L to file
     */
    private void saveWeeklyPnL() {
        try {
            Path path = Paths.get(WEEKLY_PNL_FILE);
            Files.createDirectories(path.getParent());
            String content = LocalDate.now(TAIPEI_ZONE) + "," + weeklyPnL.get();
            Files.writeString(path, content);
        } catch (IOException e) {
            log.warn("Could not save weekly P&L: {}", e.getMessage());
        }
    }
    
    /**
     * Check if weekly loss limit is hit
     */
    private void checkWeeklyLossLimit() {
        double weekly = weeklyPnL.get();
        int limit = tradingProperties.getRisk().getWeeklyLossLimit();
        if (weekly <= -limit) {
            weeklyLimitHit = true;
            log.error("🛑 WEEKLY LOSS LIMIT HIT: {} TWD (limit: -{})", weekly, limit);
        }
    }
    
    private String getBridgeUrl() {
        return tradingProperties.getBridge().getUrl();
    }
    
    /**
     * Main trading loop - runs every 30 seconds
     * Signal calculation happens every call, news veto only every 10 minutes
     */
    @Scheduled(fixedRate = 30000)
    public void tradingLoop() {
        if (emergencyShutdown || !marketDataConnected) return;
        
        // Block all trading on earnings blackout days
        if (earningsBlackout) {
            log.debug("📅 Earnings blackout day - no trading");
            return;
        }
        
        // Block all new entries if weekly limit hit (but allow exits/flattens)
        if (weeklyLimitHit && currentPosition.get() == 0) {
            log.debug("⚠️ Weekly limit hit - waiting until next Monday");
            return;
        }
        
        // Use explicit Taipei timezone for all time checks
        LocalTime now = LocalTime.now(TAIPEI_ZONE);
        LocalTime start = LocalTime.parse(tradingProperties.getWindow().getStart());
        LocalTime end = LocalTime.parse(tradingProperties.getWindow().getEnd());
        
        // Only trade during 11:30 - 13:00 window (Asia/Taipei)
        if (now.isBefore(start) || now.isAfter(end)) {
            return;
        }
        
        try {
            // Check risk limits every cycle
            checkRiskLimits();
            
            // Check 45-minute hard exit for open positions
            check45MinuteHardExit();
            
            // Update news veto cache every 10 minutes (when minute % 10 == 0 and second < 30)
            // This ensures it runs once per 10-minute window, not twice
            if (now.getMinute() % 10 == 0 && now.getSecond() < 30) {
                updateNewsVetoCache();
            }
            
            // Signal check and trade execution every 30 seconds
            if (currentPosition.get() == 0) {
                // Don't enter new positions if paused or weekly limit hit
                if (!tradingPaused && !weeklyLimitHit) {
                    evaluateEntry();
                }
            } else {
                evaluateExit();
            }
            
        } catch (Exception e) {
            log.error("❌ Trading loop error", e);
            telegramService.sendMessage("⚠️ Trading loop error: " + e.getMessage());
        }
    }
    
    /**
     * Check and enforce 45-minute hard exit
     */
    private void check45MinuteHardExit() {
        if (currentPosition.get() == 0) return;
        
        LocalDateTime entryTime = positionEntryTime.get();
        if (entryTime == null) return;
        
        long minutesHeld = java.time.Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
        int maxHold = tradingProperties.getRisk().getMaxHoldMinutes();
        
        if (minutesHeld >= maxHold) {
            log.warn("⏰ 45-MINUTE HARD EXIT: Position held {} minutes", minutesHeld);
            telegramService.sendMessage(String.format(
                "⏰ 45-MIN HARD EXIT\nPosition held %d minutes\nForce-flattening now!",
                minutesHeld
            ));
            flattenPosition("45-minute time limit");
        }
    }
    
    /**
     * Update news veto cache - calls Ollama via Python bridge
     * Only called every 10 minutes to avoid false vetoes from transient news
     */
    private void updateNewsVetoCache() {
        try {
            log.info("📰 Checking news veto (10-min interval)...");
            String newsJson = restTemplate.getForObject(getBridgeUrl() + "/signal/news", String.class);
            JsonNode newsData = objectMapper.readTree(newsJson);
            
            boolean veto = newsData.path("news_veto").asBoolean(false);
            String reason = newsData.path("news_reason").asText("");
            double score = newsData.path("news_score").asDouble(0.5);
            
            cachedNewsVeto.set(veto);
            cachedNewsReason.set(reason);
            
            if (veto) {
                log.warn("🚫 News veto ACTIVE: {} (score: {})", reason, score);
                telegramService.sendMessage(String.format(
                        "🚫 NEWS VETO ACTIVE\nReason: %s\nScore: %.2f\nNo new entries until next check",
                        reason, score));
            } else {
                log.info("✅ News check passed (score: {})", score);
            }
            
        } catch (Exception e) {
            log.error("❌ News veto check failed: {}", e.getMessage());
            // On error, keep previous veto state (fail-safe)
        }
    }
    
    private void checkRiskLimits() {
        double currentPnL = dailyPnL.get();
        if (currentPnL <= -tradingProperties.getRisk().getDailyLossLimit()) {
            log.error("🛑 DAILY LOSS LIMIT HIT: {} TWD", currentPnL);
            telegramService.sendMessage(String.format(
                    "🛑 EMERGENCY SHUTDOWN\nDaily loss: %.0f TWD\nFlattening all positions!", 
                    currentPnL));
            flattenPosition("Daily loss limit");
            emergencyShutdown = true;
        }
    }
    
    /**
     * Evaluate entry - uses cached news veto + real-time signal
     */
    private void evaluateEntry() throws Exception {
        // Check cached news veto first (updated every 10 min)
        if (cachedNewsVeto.get()) {
            log.debug("🚫 News veto active (cached) - no entry. Reason: {}", cachedNewsReason.get());
            return;
        }
        
        // Get real-time signal (price/momentum/volume/confidence)
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        String direction = signal.path("direction").asText("NEUTRAL");
        double confidence = signal.path("confidence").asDouble(0.0);
        double currentPrice = signal.path("current_price").asDouble(0.0);
        
        if (confidence < 0.65) {
            log.debug("⏸️ Low confidence: {:.2f}", confidence);
            return;
        }
        
        if ("LONG".equals(direction)) {
            executeOrder("BUY", 1, currentPrice);
        } else if ("SHORT".equals(direction)) {
            executeOrder("SELL", 1, currentPrice);
        }
    }
    
    /**
     * Evaluate exit - real-time signal only (no news veto needed for exits)
     */
    private void evaluateExit() throws Exception {
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        double currentPrice = signal.path("current_price").asDouble(0.0);
        int pos = currentPosition.get();
        double entry = entryPrice.get();
        double unrealizedPnL = (currentPrice - entry) * pos * 50; // MTXF tick value = 50 TWD
        
        // Exit only on: stop-loss OR explicit exit signal (trend reversal)
        // NO PROFIT TARGETS - let winners run until reversal!
        boolean stopLoss = unrealizedPnL < -500; // -10 points stop = -500 TWD
        boolean exitSignal = signal.path("exit_signal").asBoolean(false);
        
        if (stopLoss) {
            log.warn("⛔ Stop-loss hit: {} TWD", unrealizedPnL);
            flattenPosition("Stop-loss");
        } else if (exitSignal) {
            log.info("🔄 Exit signal (trend reversal): {} TWD", unrealizedPnL);
            flattenPosition("Trend reversal");
        } else if (unrealizedPnL > 0) {
            log.debug("💰 Position running: +{} TWD (no cap, letting it run)", unrealizedPnL);
        }
    }
    
    private void executeOrder(String action, int quantity, double price) {
        try {
            java.util.Map<String, Object> orderMap = new java.util.HashMap<>();
            orderMap.put("action", action);
            orderMap.put("quantity", quantity);
            orderMap.put("price", price);
            
            log.info("📤 Sending order: {}", orderMap);
            String result = restTemplate.postForObject(
                    getBridgeUrl() + "/order", orderMap, String.class);
            log.debug("📥 Order response: {}", result);
            
            if ("BUY".equals(action)) {
                currentPosition.addAndGet(quantity);
            } else {
                currentPosition.addAndGet(-quantity);
            }
            entryPrice.set(price);
            positionEntryTime.set(LocalDateTime.now(TAIPEI_ZONE)); // Track entry time for 45-min exit
            
            telegramService.sendMessage(String.format(
                    "✅ ORDER FILLED\n%s %d MTXF @ %.0f\nPosition: %d", 
                    action, quantity, price, currentPosition.get()));
            
        } catch (Exception e) {
            log.error("❌ Order execution failed", e);
            telegramService.sendMessage("⚠️ Order failed: " + e.getMessage());
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
            dailyPnL.updateAndGet(v -> v + pnl);
            weeklyPnL.updateAndGet(v -> v + pnl);
            saveWeeklyPnL(); // Persist weekly P&L
            
            currentPosition.set(0);
            positionEntryTime.set(null); // Clear entry time
            
            log.info("💰 Position flattened - P&L: {} TWD (Reason: {})", pnl, reason);
            telegramService.sendMessage(String.format(
                    "💰 POSITION CLOSED\nReason: %s\nP&L: %.0f TWD\nDaily P&L: %.0f TWD\nWeekly P&L: %.0f TWD", 
                    reason, pnl, dailyPnL.get(), weeklyPnL.get()));
            
            // Check if weekly limit now hit
            checkWeeklyLossLimit();
            if (weeklyLimitHit) {
                telegramService.sendMessage(String.format(
                    "🛑 WEEKLY LOSS LIMIT HIT\nWeekly P&L: %.0f TWD\nTrading paused until next Monday!",
                    weeklyPnL.get()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Flatten failed", e);
        }
    }
    
    /**
     * Auto-flatten at 13:00 - end of trading window
     * Flattens positions, sends summary, shuts down all services
     */
    @Scheduled(cron = "0 0 13 * * MON-FRI", zone = "Asia/Taipei")
    public void autoFlatten() {
        log.info("⏰ 13:00 Auto-flatten triggered");
        flattenPosition("End of trading window");
        sendDailySummary();
        
        // Gracefully shutdown the application after trading window ends
        log.info("🛑 Trading window ended - shutting down application");
        telegramService.sendMessage("🛑 Trading window ended - Bot shutting down");
        
        // Notify Python bridge to shutdown
        shutdownPythonBridge();
        
        // Use a separate thread to allow this method to complete before shutdown
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Give time for Telegram message to be sent
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int exitCode = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(exitCode);
        }).start();
    }
    
    private void shutdownPythonBridge() {
        try {
            log.info("🐍 Requesting Python bridge shutdown...");
            restTemplate.postForObject(getBridgeUrl() + "/shutdown", "", String.class);
            log.info("✅ Python bridge shutdown requested");
        } catch (Exception e) {
            log.warn("⚠️ Could not notify Python bridge to shutdown: {}", e.getMessage());
        }
    }
    
    private void sendDailySummary() {
        double pnl = dailyPnL.get();
        String status = pnl > 0 ? "✅ Profitable" : "❌ Loss";
        String comment = "";
        
        // Celebrate exceptional days
        if (pnl > 3000) {
            comment = "\n🚀 EXCEPTIONAL DAY! Let winners run!";
        } else if (pnl > 2000) {
            comment = "\n🔥 Great performance!";
        } else if (pnl > 1000) {
            comment = "\n💪 Solid day!";
        }
        
        telegramService.sendMessage(String.format(
                "📊 DAILY SUMMARY\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "Today P&L: %.0f TWD\n" +
                "Week P&L: %.0f TWD\n" +
                "Status: %s%s\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "📈 NO PROFIT CAPS - Unlimited upside!",
                pnl, weeklyPnL.get(), status, comment));
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down - flattening positions");
        flattenPosition("System shutdown");
        shutdownPythonBridge();
        telegramService.sendMessage("🛑 Bot stopped");
    }
}
