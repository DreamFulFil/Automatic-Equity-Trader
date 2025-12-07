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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dual-Mode Lunch-Break Trading Engine (December 2025 Production Version)
 * 
 * Supports two trading modes via -Dtrading.mode system property:
 * - "stock" (default): Trades 2454.TW odd lots (70 shares base, +27 per 20k equity)
 * - "futures": Trades MTXF with full scaling (1→2→3→4 contracts)
 * 
 * Core trading orchestrator responsible for:
 * - Signal polling and trade execution with auto-scaling
 * - Position management with 45-minute hard exit
 * - Trading window enforcement with weekly loss breaker
 * - Shioaji auto-reconnect wrapper with retry logic
 * 
 * Delegates to:
 * - ContractScalingService: Auto sizing based on equity + 30d profit
 * - RiskManagementService: P&L tracking and weekly -15k TWD limit
 * - TelegramService: Notifications and remote commands (/status /pause /resume /close)
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
    private final StockSettingsService stockSettingsService;
    private final RiskSettingsService riskSettingsService;
    
    // Trading mode: "stock" or "futures"
    private String tradingMode;
    
    // Position tracking (package-private for testing)
    final AtomicInteger currentPosition = new AtomicInteger(0);
    final AtomicReference<Double> entryPrice = new AtomicReference<>(0.0);
    final AtomicReference<LocalDateTime> positionEntryTime = new AtomicReference<>(null);
    
    // State flags (package-private for testing)
    volatile boolean emergencyShutdown = false;
    volatile boolean marketDataConnected = false;
    volatile boolean tradingPaused = false;
    
    // News veto cache (package-private for testing)
    final AtomicBoolean cachedNewsVeto = new AtomicBoolean(false);
    final AtomicReference<String> cachedNewsReason = new AtomicReference<>("");
    
    @PostConstruct
    public void initialize() {
        tradingMode = System.getProperty("trading.mode", "stock");
        log.info("🚀 Lunch Investor Bot initializing (December 2025 Production)...");
        log.info("📈 Trading Mode: {} ({})", tradingMode.toUpperCase(), 
            "stock".equals(tradingMode) ? "2454.TW odd lots" : "MTXF futures");
        
        registerTelegramCommands();
        
        String modeDescription = "stock".equals(tradingMode) 
            ? "Mode: STOCK (2454.TW odd lots)" 
            : "Mode: FUTURES (MTXF)";
        
        String scalingInfo = "stock".equals(tradingMode)
            ? String.format("Base shares: %d (+%d per 20k equity)", 
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Max contracts: %d (auto-scaling)", contractScalingService.getMaxContracts());
        
        String startupMessage = String.format(
            "🤖 Bot started — %s\n" +
            "Trading window: %s - %s\n" +
            "%s\n" +
            "Daily loss limit: %d TWD\n" +
            "Weekly loss limit: %d TWD\n" +
            "Max hold time: %d min\n" +
            "Signal: 30s | News: 10min\n" +
            "Weekly P&L: %.0f TWD%s%s",
            modeDescription,
            tradingProperties.getWindow().getStart(),
            tradingProperties.getWindow().getEnd(),
            scalingInfo,
            riskSettingsService.getSettings().getDailyLossLimit(),
            riskSettingsService.getSettings().getWeeklyLossLimit(),
            riskSettingsService.getSettings().getMaxHoldMinutes(),
            riskManagementService.getWeeklyPnL(),
            riskManagementService.isWeeklyLimitHit() ? "\n🚨 WEEKLY LIMIT HIT - Paused until Monday" : "",
            riskManagementService.isEarningsBlackout() ? String.format("\n📅 EARNINGS BLACKOUT (%s) - No trading today", 
                riskManagementService.getEarningsBlackoutStock()) : ""
        );
        telegramService.sendMessage(startupMessage);
        
        try {
            String response = restTemplate.getForObject(getBridgeUrl() + "/health", String.class);
            log.info("✅ Python bridge connected: {}", response);
            marketDataConnected = true;
            
            runPreMarketHealthCheck();
            if ("futures".equals(tradingMode)) {
                contractScalingService.updateContractSizing();
            }
        } catch (Exception e) {
            log.error("❌ Failed to connect to Python bridge", e);
            telegramService.sendMessage("🚨 Python bridge connection failed!");
        }
    }
    
    /**
     * Get base stock quantity for stock mode (2454.TW odd lots)
     * Base: 70 shares (≈77k NTD at NT$1,100/share, perfect for 80k capital)
     * Auto-scale: +27 shares for every additional 20k equity above 80k base
     */
    int getBaseStockQuantity() {
        return stockSettingsService.getBaseStockQuantity(contractScalingService.getLastEquity());
    }
    
    /**
     * Get trading quantity based on mode
     */
    int getTradingQuantity() {
        if ("stock".equals(tradingMode)) {
            return getBaseStockQuantity();
        } else {
            return contractScalingService.getMaxContracts();
        }
    }
    
    public String getTradingMode() {
        return tradingMode;
    }
    
    private void runPreMarketHealthCheck() {
        try {
            Map<String, Object> testOrder = new HashMap<>();
            testOrder.put("action", "BUY");
            testOrder.put("quantity", String.valueOf(1));
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
    
    void handleStatusCommand() { // package-private for testing
        String state = "🟢 ACTIVE";
        if (emergencyShutdown) state = "🔴 EMERGENCY SHUTDOWN";
        else if (riskManagementService.isWeeklyLimitHit()) state = "🟡 WEEKLY LIMIT PAUSED";
        else if (riskManagementService.isEarningsBlackout()) state = "📅 EARNINGS BLACKOUT";
        else if (tradingPaused) state = "⏸️ PAUSED BY USER";
        
        String positionInfo = currentPosition.get() == 0 ? "No position" :
            String.format("%d @ %.0f (held %d min)",
                currentPosition.get(),
                entryPrice.get(),
                positionEntryTime.get() != null ?
                    java.time.Duration.between(positionEntryTime.get(), LocalDateTime.now(TAIPEI_ZONE)).toMinutes() : 0
            );
        
        String modeInfo = "stock".equals(tradingMode) 
            ? String.format("Mode: STOCK (2454.TW)\nShares: %d (base %d +%d/20k)", 
                getBaseStockQuantity(),
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Mode: FUTURES (MTXF)\nContracts: %d", contractScalingService.getMaxContracts());
        
        String message = String.format(
            "📊 BOT STATUS\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "State: %s\n" +
            "%s\n" +
            "Position: %s\n" +
            "Equity: %.0f TWD\n" +
            "30d Profit: %.0f TWD\n" +
            "Today P&L: %.0f TWD\n" +
            "Week P&L: %.0f TWD\n" +
            "News Veto: %s\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "Commands: /pause /resume /close",
            state, modeInfo, positionInfo,
            contractScalingService.getLastEquity(), contractScalingService.getLast30DayProfit(),
            riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL(),
            cachedNewsVeto.get() ? "🚨 ACTIVE" : "✅ Clear"
        );
        telegramService.sendMessage(message);
    }
    
    private void handlePauseCommand() {
        tradingPaused = true;
        log.info("⏸️ Trading paused by user command");
        telegramService.sendMessage("⏸️ Trading PAUSED\nNo new entries until /resume\nExisting positions will still flatten at 13:00");
    }
    
    void handleResumeCommand() { // package-private for testing
        if (riskManagementService.isWeeklyLimitHit()) {
            telegramService.sendMessage("❌ Cannot resume - Weekly loss limit hit\nWait until next Monday");
            return;
        }
        if (riskManagementService.isEarningsBlackout()) {
            telegramService.sendMessage("❌ Cannot resume - Earnings blackout day\nNo trading today");
            return;
        }
        tradingPaused = false;
        log.info("▶️ Trading resumed by user command");
        telegramService.sendMessage("▶️ Trading RESUMED\nBot is active");
    }
    
    private void handleCloseCommand() {
        if (currentPosition.get() == 0) {
            telegramService.sendMessage("ℹ️ No position to close");
            return;
        }
        log.info("🔒 Close command received from user");
        flattenPosition("Closed by user");
        telegramService.sendMessage("✅ Position closed by user command");
    }
    
    private String getBridgeUrl() {
        return tradingProperties.getBridge().getUrl();
    }
    
    @Scheduled(fixedRate = 30000)
    public void tradingLoop() {
        if (emergencyShutdown || !marketDataConnected) return;
        
        if (riskManagementService.isEarningsBlackout()) {
            log.debug("📅 Earnings blackout day - no trading");
            return;
        }
        
        if (riskManagementService.isWeeklyLimitHit() && currentPosition.get() == 0) {
            log.debug("🟡 Weekly limit hit - waiting until next Monday");
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
            log.error("🚨 Trading loop error", e);
            telegramService.sendMessage("🚨 Trading loop error: " + e.getMessage());
        }
    }
    
    void check45MinuteHardExit() { // package-private for testing
        if (currentPosition.get() == 0) return;
        
        LocalDateTime entryTime = positionEntryTime.get();
        if (entryTime == null) return;
        
        long minutesHeld = java.time.Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
        int maxHold = riskSettingsService.getMaxHoldMinutes();
        
        if (minutesHeld >= maxHold) {
            log.warn("⏰ 45-MINUTE HARD EXIT: Position held {} minutes", minutesHeld);
            telegramService.sendMessage(String.format(
                "⏰ 45-MIN HARD EXIT\\nPosition held %d minutes\\nForce-flattening now!",
                minutesHeld
            ));
            flattenPosition("45-minute time limit");
        }
    }
    
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
                log.warn("🚨 News veto ACTIVE: {} (score: {})", reason, score);
                telegramService.sendMessage(String.format(
                        "🚨 NEWS VETO ACTIVE\\nReason: %s\\nScore: %.2f\\nNo new entries until next check",
                        reason, score));
            } else {
                log.info("✅ News check passed (score: {})", score);
            }
            
        } catch (Exception e) {
            log.error("❌ News veto check failed: {}", e.getMessage());
        }
    }
    
    void checkRiskLimits() { // package-private for testing
        if (riskManagementService.isDailyLimitExceeded(riskSettingsService.getDailyLossLimit())) {
            log.error("🚨 DAILY LOSS LIMIT HIT: {} TWD", riskManagementService.getDailyPnL());
            telegramService.sendMessage(String.format(
                    "🚨 EMERGENCY SHUTDOWN\\nDaily loss: %.0f TWD\\nFlattening all positions!", 
                    riskManagementService.getDailyPnL()));
            flattenPosition("Daily loss limit");
            emergencyShutdown = true;
        }
    }
    
    void evaluateEntry() throws Exception { // package-private for testing
        if (cachedNewsVeto.get()) {
            log.debug("🚨 News veto active (cached) - no entry. Reason: {}", cachedNewsReason.get());
            return;
        }
        
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        String direction = signal.path("direction").asText("NEUTRAL");
        double confidence = signal.path("confidence").asDouble(0.0);
        double currentPrice = signal.path("current_price").asDouble(0.0);
        
        if (confidence < 0.65) {
            log.debug("📉 Low confidence: {}", confidence);
            return;
        }
        
        int quantity = getTradingQuantity();
        
        if ("LONG".equals(direction)) {
            executeOrderWithRetry("BUY", quantity, currentPrice);
        } else if ("SHORT".equals(direction)) {
            executeOrderWithRetry("SELL", quantity, currentPrice);
        }
    }
    
    void evaluateExit() throws Exception { // package-private for testing
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        double currentPrice = signal.path("current_price").asDouble(0.0);
        int pos = currentPosition.get();
        double entry = entryPrice.get();
        
        // ========================================================================
        // MINIMUM HOLD TIME CHECK (Anti-Whipsaw)
        // Prevent exiting too quickly after entry - give position time to develop
        // ========================================================================
        LocalDateTime entryTime = positionEntryTime.get();
        if (entryTime != null) {
            long holdMinutes = java.time.Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
            if (holdMinutes < 3) {  // 3-minute minimum hold time
                log.debug("⏳ Hold time: {} min (min 3 min required before exit evaluation)", holdMinutes);
                // Only allow stop-loss during minimum hold period
                double multiplier = "stock".equals(tradingMode) ? 1.0 : 50.0;
                double unrealizedPnL = (currentPrice - entry) * pos * multiplier;
                double stopLossThreshold = "stock".equals(tradingMode) 
                    ? -500 * Math.abs(pos)
                    : -500 * Math.abs(pos);
                if (unrealizedPnL < stopLossThreshold) {
                    log.warn("🛑 Stop-loss hit during hold period: {} TWD", unrealizedPnL);
                    flattenPosition("Stop-loss");
                }
                return;  // Skip normal exit evaluation during minimum hold period
            }
        }
        
        // P&L calculation differs by mode
        double multiplier = "stock".equals(tradingMode) ? 1.0 : 50.0; // MTXF = 50 TWD per point
        double unrealizedPnL = (currentPrice - entry) * pos * multiplier;
        
        double stopLossThreshold = "stock".equals(tradingMode) 
            ? -500 * Math.abs(pos)  // Stock: -500 TWD per unit
            : -500 * Math.abs(pos); // Futures: -500 TWD per contract
        boolean stopLoss = unrealizedPnL < stopLossThreshold;
        boolean exitSignal = signal.path("exit_signal").asBoolean(false);
        
        if (stopLoss) {
            log.warn("🛑 Stop-loss hit: {} TWD (threshold: {})", unrealizedPnL, stopLossThreshold);
            flattenPosition("Stop-loss");
        } else if (exitSignal) {
            log.info("📈 Exit signal (trend reversal): {} TWD", unrealizedPnL);
            flattenPosition("Trend reversal");
        } else if (unrealizedPnL > 0) {
            log.debug("💰 Position running: +{} TWD (no cap, letting it run)", unrealizedPnL);
        }
    }
    
    /**
     * Execute order with retry wrapper and quantity bug fix
     * Fixes the 422 error by ensuring quantity is always sent as string
     */
    private void executeOrderWithRetry(String action, int quantity, double price) {
        executeOrderWithRetry(action, quantity, price, false);
    }
    
    /**
     * Execute order with retry wrapper, optional exit flag for cooldown tracking
     * @param isExit true if this is an exit/close position order (triggers cooldown on bridge)
     */
    private void executeOrderWithRetry(String action, int quantity, double price, boolean isExit) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("action", action);
                orderMap.put("quantity", String.valueOf(quantity)); // Fix: Force string conversion
                orderMap.put("price", price);
                orderMap.put("is_exit", isExit); // Signal to bridge to start cooldown
                
                String instrument = "stock".equals(tradingMode) ? "2454.TW" : "MTXF";
                log.info("📤 Sending {} order (attempt {}, exit={}): {}", instrument, attempt, isExit, orderMap);
                String result = restTemplate.postForObject(
                        getBridgeUrl() + "/order", orderMap, String.class);
                log.debug("📥 Order response: {}", result);
                
                // Success - update position
                if ("BUY".equals(action)) {
                    currentPosition.addAndGet(quantity);
                } else {
                    currentPosition.addAndGet(-quantity);
                }
                entryPrice.set(price);
                positionEntryTime.set(LocalDateTime.now(TAIPEI_ZONE));
                
                telegramService.sendMessage(String.format(
                        "✅ ORDER FILLED\\n%s %d %s @ %.0f\\nPosition: %d", 
                        action, quantity, instrument, price, currentPosition.get()));
                
                return; // Success - exit retry loop
                
            } catch (Exception e) {
                log.error("❌ Order execution failed (attempt {}): {}", attempt, e.getMessage());
                
                if (attempt >= maxRetries) {
                    telegramService.sendMessage("🚨 Order failed after " + maxRetries + " attempts: " + e.getMessage());
                    return;
                }
                
                // Exponential backoff: 1s, 2s, 4s
                try {
                    Thread.sleep(1000 * (1L << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
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
            
            // Store entry price before executeOrderWithRetry overwrites it
            double originalEntryPrice = entryPrice.get();
            
            executeOrderWithRetry(action, Math.abs(pos), currentPrice, true); // isExit=true triggers cooldown
            
            double multiplier = "stock".equals(tradingMode) ? 1.0 : 50.0;
            double pnl = (currentPrice - originalEntryPrice) * pos * multiplier;
            riskManagementService.recordPnL(pnl, riskSettingsService.getWeeklyLossLimit());
            
            currentPosition.set(0);
            positionEntryTime.set(null);
            
            log.info("🔒 Position flattened - P&L: {} TWD (Reason: {})", pnl, reason);
            telegramService.sendMessage(String.format(
                    "🔒 POSITION CLOSED\\nReason: %s\\nP&L: %.0f TWD\\nDaily P&L: %.0f TWD\\nWeekly P&L: %.0f TWD", 
                    reason, pnl, riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL()));
            
            if (riskManagementService.isWeeklyLimitHit()) {
                telegramService.sendMessage(String.format(
                    "🚨 WEEKLY LOSS LIMIT HIT\\nWeekly P&L: %.0f TWD\\nTrading paused until next Monday!",
                    riskManagementService.getWeeklyPnL()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Flatten failed", e);
        }
    }
    
    @Scheduled(cron = "0 0 13 * * MON-FRI", zone = "Asia/Taipei")
    public void autoFlatten() {
        log.info("🕐 13:00 Auto-flatten triggered");
        flattenPosition("End of trading window");
        sendDailySummary();
        
        log.info("🛑 Trading window ended - shutting down application");
        telegramService.sendMessage("🛑 Trading window ended - Bot shutting down");
        
        // Exit the Spring context, which will then trigger @PreDestroy
        System.exit(SpringApplication.exit(applicationContext));
    }
    
    private void sendDailySummary() {
        double pnl = riskManagementService.getDailyPnL();
        String status = pnl > 0 ? "💰 Profitable" : "📉 Loss";
        String comment = "";
        
        if (pnl > 3000) {
            comment = "\\n🚀 EXCEPTIONAL DAY! Let winners run!";
        } else if (pnl > 2000) {
            comment = "\\n🎯 Great performance!";
        } else if (pnl > 1000) {
            comment = "\\n✅ Solid day!";
        }
        
        String modeInfo = "stock".equals(tradingMode) 
            ? String.format("Mode: STOCK\\nShares: %d (base %d +%d/20k)",
                getBaseStockQuantity(),
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Mode: FUTURES\\nContracts: %d", contractScalingService.getMaxContracts());
        
        telegramService.sendMessage(String.format(
                "📊 DAILY SUMMARY\\n" +
                "━━━━━━━━━━━━━━━━\\n" +
                "%s\\n" +
                "Today P&L: %.0f TWD\\n" +
                "Week P&L: %.0f TWD\\n" +
                "Equity: %.0f TWD\\n" +
                "Status: %s%s\\n" +
                "━━━━━━━━━━━━━━━━\\n" +
                "🚀 NO PROFIT CAPS - Unlimited upside!",
                modeInfo, pnl, riskManagementService.getWeeklyPnL(),
                contractScalingService.getLastEquity(), status, comment));
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down - flattening positions");
        flattenPosition("System shutdown");
        // Daily summary already sent by autoFlatten() - don't send twice
        telegramService.sendMessage("🛑 Bot stopped");
    }
}