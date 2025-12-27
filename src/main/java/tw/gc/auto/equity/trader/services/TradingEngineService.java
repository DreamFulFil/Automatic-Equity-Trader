package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.entities.Signal;
import tw.gc.auto.equity.trader.entities.Signal.SignalDirection;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.services.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngineService {
    
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
    private final DataLoggingService dataLoggingService;
    private final EndOfDayStatisticsService endOfDayStatisticsService;
    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final ShioajiSettingsService shioajiSettingsService;
    
    // New Services
    private final TradingStateService tradingStateService;
    private final TelegramCommandHandler telegramCommandHandler;
    private final OrderExecutionService orderExecutionService;
    private final PositionManager positionManager;
    private final StrategyManager strategyManager;
    private final ReportingService reportingService;

    @PostConstruct
    public void initialize() {
        tradingStateService.setTradingMode(System.getProperty("trading.mode", "stock"));
        
        log.info("🚀 Lunch Investor Bot initializing (December 2025 Production)...");
        
        // Strategies are initialized by StrategyManager @PostConstruct
        
        log.info("📈 Trading Mode: {} ({})", tradingStateService.getTradingMode().toUpperCase(), 
            "stock".equals(tradingStateService.getTradingMode()) ? "2454.TW odd lots" : "MTXF futures");
        
        telegramCommandHandler.registerCommands(strategyManager.getActiveStrategies());
        
        // Calculate statistics for yesterday on startup
        LocalDate yesterday = LocalDate.now(TAIPEI_ZONE).minusDays(1);
        String symbol = getActiveSymbol();
        if (dailyStatisticsRepository.findByTradeDateAndSymbol(yesterday, symbol).isEmpty()) {
            try {
                log.info("📊 Calculating statistics for {} on startup...", yesterday);
                endOfDayStatisticsService.calculateAndSaveStatisticsForDay(yesterday, symbol);
            } catch (Exception e) {
                log.error("❌ Failed to calculate yesterday's statistics on startup", e);
            }
        } else {
            log.info("📊 Statistics for {} already exist, skipping calculation on startup", yesterday);
        }
        
        sendStartupMessage();
        
        // Also log to console prominently
        log.warn("═══════════════════════════════════════════════════");
        log.warn(getTradingModeLabel());
        log.warn("═══════════════════════════════════════════════════");
        
        try {
            String response = restTemplate.getForObject(getBridgeUrl() + "/health", String.class);
            log.info("✅ Python bridge connected: {}", response);
            tradingStateService.setMarketDataConnected(true);
            
            runPreMarketHealthCheck();
            if ("futures".equals(tradingStateService.getTradingMode())) {
                contractScalingService.updateContractSizing();
            }
        } catch (Exception e) {
            log.warn("⚠️ Python bridge not available during startup (will retry during trading cycle): {}", e.getMessage());
        }
    }

    private void sendStartupMessage() {
        String tradingModeLabel = getTradingModeLabel();
        
        String modeDescription = "stock".equals(tradingStateService.getTradingMode()) 
            ? "Mode: STOCK (2454.TW odd lots)" 
            : "Mode: FUTURES (MTXF)";
        
        String scalingInfo = "stock".equals(tradingStateService.getTradingMode())
            ? String.format("Base shares: %d (+%d per 20k equity)", 
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Max contracts: %d (auto-scaling)", contractScalingService.getMaxContracts());
        
        String startupMessage = String.format(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "🚀 TRADING SYSTEM STARTED\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "%s\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "%s\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "%s\n" +
            "Daily loss limit: %d TWD\n" +
            "Weekly loss limit: %d TWD\n" +
            "Max hold time: %d min\n" +
            "Signal: 30s | News: 10min\n" +
            "Weekly P&L: %.0f TWD%s%s\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "App runs indefinitely - use /shutdown to stop",
            tradingModeLabel,
            modeDescription,
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
    }

    private String getTradingModeLabel() {
        boolean isSimulation = shioajiSettingsService.getSettings().isSimulation();
        return isSimulation ? "🟡 SIMULATION MODE" : "🔴 LIVE TRADING MODE";
    }

    private String getActiveSymbol() {
        return "stock".equals(tradingStateService.getTradingMode()) ? "2454.TW" : "AUTO_EQUITY_TRADER";
    }
    
    private String getBridgeUrl() {
        return tradingProperties.getBridge().getUrl();
    }
    
    /**
     * Main trading loop - executes every 30 seconds.
     * JUSTIFICATION: Core trading logic that fetches market data, generates signals,
     * and executes trades. 30s interval balances responsiveness with API rate limits.
     */
    @Scheduled(fixedRate = 30000)
    public void tradingLoop() {
        // Try to reconnect to bridge if not connected
        if (!tradingStateService.isMarketDataConnected()) {
            try {
                String response = restTemplate.getForObject(getBridgeUrl() + "/health", String.class);
                log.info("✅ Python bridge reconnected: {}", response);
                tradingStateService.setMarketDataConnected(true);
                telegramService.sendMessage("✅ Python bridge reconnected!");
                
                // Run pre-market checks now that bridge is available
                runPreMarketHealthCheck();
                if ("futures".equals(tradingStateService.getTradingMode())) {
                    contractScalingService.updateContractSizing();
                }
            } catch (Exception e) {
                log.debug("Bridge still unavailable: {}", e.getMessage());
                return; // Skip trading loop until bridge is available
            }
        }
        
        if (tradingStateService.isEmergencyShutdown()) return;
        
        if (riskManagementService.isEarningsBlackout()) {
            log.debug("📅 Earnings blackout day - no trading");
            return;
        }
        
        if (riskManagementService.isWeeklyLimitHit() && positionManager.getPosition(getActiveSymbol()) == 0) {
            log.debug("🟡 Weekly limit hit - waiting until next Monday");
            return;
        }
        
        LocalTime now = LocalTime.now(TAIPEI_ZONE);
        
        try {
            checkRiskLimits();
            check45MinuteHardExit();
            
            // Fetch Market Data once for all strategies
            String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
            JsonNode signalNode = objectMapper.readTree(signalJson);
            double currentPrice = signalNode.path("current_price").asDouble(0.0);
            
            MarketData marketData = MarketData.builder()
                .symbol(getActiveSymbol())
                .close(currentPrice)
                .high(currentPrice) // Approx for now
                .low(currentPrice)  // Approx for now
                .volume(100L)       // Placeholder
                .timestamp(LocalDateTime.now(TAIPEI_ZONE))
                .build();
            
            // Run All Strategies
            strategyManager.executeStrategies(marketData, currentPrice);
            
            if (now.getMinute() % 10 == 0 && now.getSecond() < 30) {
                updateNewsVetoCache();
            }
            
            // Legacy Bridge Strategy (Default if activeStrategyName is "LegacyBridge")
            if ("LegacyBridge".equalsIgnoreCase(tradingStateService.getActiveStrategyName())) {
                if (positionManager.getPosition(getActiveSymbol()) == 0) {
                    if (!tradingStateService.isTradingPaused() && !riskManagementService.isWeeklyLimitHit()) {
                        evaluateEntry(); // Legacy entry logic
                    }
                } else {
                    evaluateExit(); // Legacy exit logic
                }
            }
            
        } catch (RestClientException e) {
            // Bridge connection error - mark as disconnected and skip this cycle
            tradingStateService.setMarketDataConnected(false);
            log.debug("Bridge connection lost, will retry: {}", e.getMessage());
        } catch (Exception e) {
            log.error("🚨 Trading loop error", e);
            telegramService.sendMessage("🚨 Trading loop error: " + e.getMessage());
        }
    }
    
    void check45MinuteHardExit() { // package-private for testing
        String instrument = getActiveSymbol();
        int pos = positionManager.getPosition(instrument);
        if (pos == 0) return;
        
        LocalDateTime entryTime = positionManager.getEntryTime(instrument);
        if (entryTime == null) return;
        
        long minutesHeld = java.time.Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
        int maxHold = riskSettingsService.getMaxHoldMinutes();
        
        if (minutesHeld >= maxHold) {
            log.warn("⏰ 45-MINUTE HARD EXIT: Position held {} minutes", minutesHeld);
            telegramService.sendMessage(String.format(
                "⏰ 45-MIN HARD EXIT\\nPosition held %d minutes\\nForce-flattening now!",
                minutesHeld
            ));
            orderExecutionService.flattenPosition("45-minute time limit", instrument, tradingStateService.getTradingMode(), tradingStateService.isEmergencyShutdown());
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
            
            tradingStateService.setNewsVeto(veto, reason);
            
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
            orderExecutionService.flattenPosition("Daily loss limit", getActiveSymbol(), tradingStateService.getTradingMode(), true);
            tradingStateService.setEmergencyShutdown(true);
        }
    }
    
    void evaluateEntry() throws Exception { // package-private for testing
        if (tradingStateService.isNewsVeto()) {
            log.debug("🚨 News veto active (cached) - no entry. Reason: {}", tradingStateService.getNewsReason());
            return;
        }
        
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        String direction = signal.path("direction").asText("NEUTRAL");
        double confidence = signal.path("confidence").asDouble(0.0);
        double currentPrice = signal.path("current_price").asDouble(0.0);
        
        // Log the signal
        Signal signalEntity = Signal.builder()
                .timestamp(LocalDateTime.now(TAIPEI_ZONE))
                .direction("LONG".equals(direction) ? Signal.SignalDirection.LONG : 
                          "SHORT".equals(direction) ? Signal.SignalDirection.SHORT : 
                          Signal.SignalDirection.HOLD)
                .confidence(confidence)
                .currentPrice(currentPrice)
                .exitSignal(false)
                .symbol(getActiveSymbol())
                .marketData(signalJson)
                .newsVeto(tradingStateService.isNewsVeto())
                .build();
        dataLoggingService.logSignal(signalEntity);
        
        if (confidence < 0.65) {
            log.debug("📉 Low confidence: {}", confidence);
            return;
        }
        
        int quantity = getTradingQuantity();
        String instrument = getActiveSymbol();
        String tradingMode = tradingStateService.getTradingMode();
        
        // 🚨 CRITICAL: Check account balance before placing BUY orders
        if ("LONG".equals(direction)) {
            quantity = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", quantity, currentPrice, instrument, tradingMode);
            if (quantity <= 0) {
                log.warn("⚠️ Insufficient balance for BUY order - skipping trade");
                telegramService.sendMessage("⚠️ Insufficient account balance - BUY order skipped");
                return;
            }
        } else if ("SHORT".equals(direction) && "stock".equals(tradingMode)) {
            quantity = orderExecutionService.checkBalanceAndAdjustQuantity("SELL", quantity, currentPrice, instrument, tradingMode);
            if (quantity <= 0) {
                log.warn("⚠️ No position to sell - skipping SHORT signal");
                return;
            }
        }
        
        if ("LONG".equals(direction)) {
            orderExecutionService.executeOrderWithRetry("BUY", quantity, currentPrice, instrument, false, tradingStateService.isEmergencyShutdown());
        } else if ("SHORT".equals(direction)) {
            if ("stock".equals(tradingMode)) {
                log.warn("⚠️ SHORT signal ignored - Taiwan retail investors cannot short sell stocks");
                telegramService.sendMessage("⚠️ SHORT signal received but ignored (retail investors cannot short sell stocks in Taiwan)");
                return;
            } else {
                quantity = orderExecutionService.checkBalanceAndAdjustQuantity("BUY", quantity, currentPrice, instrument, tradingMode);
                if (quantity <= 0) {
                    log.warn("⚠️ Insufficient margin for SHORT order - skipping futures SHORT order");
                    return;
                }
                orderExecutionService.executeOrderWithRetry("SELL", quantity, currentPrice, instrument, false, tradingStateService.isEmergencyShutdown());
            }
        }
    }
    
    void evaluateExit() throws Exception { // package-private for testing
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        double currentPrice = signal.path("current_price").asDouble(0.0);
        String instrument = getActiveSymbol();
        int pos = positionManager.getPosition(instrument);
        double entry = positionManager.getEntryPrice(instrument);
        
        // ========================================================================
        // MINIMUM HOLD TIME CHECK (Anti-Whipsaw)
        // ========================================================================
        LocalDateTime entryTime = positionManager.getEntryTime(instrument);
        if (entryTime != null) {
            long holdMinutes = java.time.Duration.between(entryTime, LocalDateTime.now(TAIPEI_ZONE)).toMinutes();
            if (holdMinutes < 3) {  // 3-minute minimum hold time
                log.debug("⏳ Hold time: {} min (min 3 min required before exit evaluation)", holdMinutes);
                // Only allow stop-loss during minimum hold period
                double multiplier = "stock".equals(tradingStateService.getTradingMode()) ? 1.0 : 50.0;
                double unrealizedPnL = (currentPrice - entry) * pos * multiplier;
                double stopLossThreshold = -500 * Math.abs(pos);
                if (unrealizedPnL < stopLossThreshold) {
                    log.warn("🛑 Stop-loss hit during hold period: {} TWD", unrealizedPnL);
                    orderExecutionService.flattenPosition("Stop-loss", instrument, tradingStateService.getTradingMode(), tradingStateService.isEmergencyShutdown());
                }
                return;  // Skip normal exit evaluation during minimum hold period
            }
        }
        
        // P&L calculation differs by mode
        double multiplier = "stock".equals(tradingStateService.getTradingMode()) ? 1.0 : 50.0; // MTXF = 50 TWD per point
        double unrealizedPnL = (currentPrice - entry) * pos * multiplier;
        
        double stopLossThreshold = -500 * Math.abs(pos);
        boolean stopLoss = unrealizedPnL < stopLossThreshold;
        boolean exitSignal = signal.path("exit_signal").asBoolean(false);
        
        // Log exit signal evaluation
        if (exitSignal) {
            Signal exitSig = Signal.builder()
                .timestamp(LocalDateTime.now(TAIPEI_ZONE))
                .direction(SignalDirection.HOLD) // Exit signals are HOLD direction
                .confidence(signal.path("confidence").asDouble(0.0))
                .currentPrice(currentPrice)
                .exitSignal(true)
                .marketData(signal.toString())
                .symbol(instrument)
                .reason("Exit signal evaluation")
                .newsVeto(signal.path("news_veto").asBoolean(false))
                .newsScore(signal.path("news_score").asDouble(0.0))
                .build();
            dataLoggingService.logSignal(exitSig);
        }
        
        if (stopLoss) {
            log.warn("🛑 Stop-loss hit: {} TWD (threshold: {})", unrealizedPnL, stopLossThreshold);
            orderExecutionService.flattenPosition("Stop-loss", instrument, tradingStateService.getTradingMode(), tradingStateService.isEmergencyShutdown());
        } else if (exitSignal) {
            log.info("📈 Exit signal (trend reversal): {} TWD", unrealizedPnL);
            orderExecutionService.flattenPosition("Trend reversal", instrument, tradingStateService.getTradingMode(), tradingStateService.isEmergencyShutdown());
        } else if (unrealizedPnL > 0) {
            log.debug("💰 Position running: +{} TWD (no cap, letting it run)", unrealizedPnL);
        }
    }
    
    private void runPreMarketHealthCheck() {
        try {
            java.util.Map<String, Object> testOrder = new java.util.HashMap<>();
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
    
    private int getTradingQuantity() {
        if ("stock".equals(tradingStateService.getTradingMode())) {
            return stockSettingsService.getBaseStockQuantity(contractScalingService.getLastEquity());
        } else {
            return contractScalingService.getMaxContracts();
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down - flattening positions");
        flattenPosition("System shutdown");
        // Daily summary already sent by autoFlatten() - don't send twice
        telegramService.sendMessage("🛑 Bot stopped");
        
        // Calculate end-of-day statistics for today on shutdown
        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        String symbol = getActiveSymbol();
        if (dailyStatisticsRepository.findByTradeDateAndSymbol(today, symbol).isEmpty()) {
            try {
                log.info("📊 Calculating today's statistics on shutdown...");
                endOfDayStatisticsService.calculateAndSaveStatisticsForDay(today, symbol);
            } catch (Exception e) {
                log.error("❌ Failed to calculate today's statistics on shutdown", e);
            }
        } else {
            log.info("📊 Statistics for {} already exist, skipping calculation on shutdown", today);
        }
    }

    public void flattenPosition(String reason) {
        orderExecutionService.flattenPosition(reason, getActiveSymbol(), tradingStateService.getTradingMode(), tradingStateService.isEmergencyShutdown());
    }
}
