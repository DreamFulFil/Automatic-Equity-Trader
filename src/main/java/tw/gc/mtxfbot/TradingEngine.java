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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MTXF Lunch-Break Trading Engine (2025 Optimal Timing)
 * 
 * Timing Strategy:
 * - Signal calculation (price/momentum/volume/confidence) → every 30 seconds
 * - News veto (Llama 3.1 8B via Python bridge) → every 10 minutes only
 * - Trading window: 11:30 - 13:00 Taipei time
 * - Auto-flatten and shutdown at 13:00
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngine {
    
    // Explicit timezone for Taiwan futures trading
    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;
    private final TradingProperties tradingProperties;
    private final ApplicationContext applicationContext;
    
    // Position and P&L tracking
    private final AtomicInteger currentPosition = new AtomicInteger(0);
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> entryPrice = new AtomicReference<>(0.0);
    
    // State flags
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    
    // News veto cache - updated every 10 minutes, used by all signal checks
    private final AtomicBoolean cachedNewsVeto = new AtomicBoolean(false);
    private final AtomicReference<String> cachedNewsReason = new AtomicReference<>("");
    
    @PostConstruct
    public void initialize() {
        log.info("🚀 MTXF Lunch Bot initializing...");
        telegramService.sendMessage("🚀 MTXF Lunch Bot started\n" +
                "Trading window: " + tradingProperties.getWindow().getStart() + " - " + tradingProperties.getWindow().getEnd() + "\n" +
                "Max position: " + tradingProperties.getRisk().getMaxPosition() + " contract\n" +
                "Daily loss limit: " + tradingProperties.getRisk().getDailyLossLimit() + " TWD\n" +
                "Signal interval: 30s | News check: 10min");
        
        try {
            String response = restTemplate.getForObject(getBridgeUrl() + "/health", String.class);
            log.info("✅ Python bridge connected: {}", response);
            marketDataConnected = true;
        } catch (Exception e) {
            log.error("❌ Failed to connect to Python bridge", e);
            telegramService.sendMessage("⚠️ Python bridge connection failed!");
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
            
            // Update news veto cache every 10 minutes (when minute % 10 == 0 and second < 30)
            // This ensures it runs once per 10-minute window, not twice
            if (now.getMinute() % 10 == 0 && now.getSecond() < 30) {
                updateNewsVetoCache();
            }
            
            // Signal check and trade execution every 30 seconds
            if (currentPosition.get() == 0) {
                evaluateEntry();
            } else {
                evaluateExit();
            }
            
        } catch (Exception e) {
            log.error("❌ Trading loop error", e);
            telegramService.sendMessage("⚠️ Trading loop error: " + e.getMessage());
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
            String orderJson = String.format(
                    "{\"action\":\"%s\",\"quantity\":%d,\"price\":%.0f}", 
                    action, quantity, price);
            
            log.info("📤 Sending order: {}", orderJson);
            String result = restTemplate.postForObject(
                    getBridgeUrl() + "/order", orderJson, String.class);
            log.debug("📥 Order response: {}", result);
            
            if ("BUY".equals(action)) {
                currentPosition.addAndGet(quantity);
            } else {
                currentPosition.addAndGet(-quantity);
            }
            entryPrice.set(price);
            
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
            currentPosition.set(0);
            
            log.info("💰 Position flattened - P&L: {} TWD (Reason: {})", pnl, reason);
            telegramService.sendMessage(String.format(
                    "💰 POSITION CLOSED\nReason: %s\nP&L: %.0f TWD\nDaily P&L: %.0f TWD", 
                    reason, pnl, dailyPnL.get()));
            
        } catch (Exception e) {
            log.error("❌ Flatten failed", e);
        }
    }
    
    /**
     * Auto-flatten at 13:00 - end of trading window
     * Flattens positions, sends summary, shuts down all services
     */
    @Scheduled(cron = "0 0 13 * * MON-FRI")
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
                "📊 DAILY SUMMARY\nFinal P&L: %.0f TWD\n" +
                "Status: %s%s\n" +
                "📈 NO PROFIT CAPS - Unlimited upside potential!",
                pnl, status, comment));
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down - flattening positions");
        flattenPosition("System shutdown");
        shutdownPythonBridge();
        telegramService.sendMessage("🛑 Bot stopped");
    }
}
