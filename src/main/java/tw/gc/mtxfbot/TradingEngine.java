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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngine {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TelegramService telegramService;
    private final TradingProperties tradingProperties;
    private final ApplicationContext applicationContext;
    
    private final AtomicInteger currentPosition = new AtomicInteger(0);
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> entryPrice = new AtomicReference<>(0.0);
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    
    @PostConstruct
    public void initialize() {
        log.info("🚀 MTXF Lunch Bot initializing...");
        telegramService.sendMessage("🚀 MTXF Lunch Bot started\n" +
                "Trading window: " + tradingProperties.getWindow().getStart() + " - " + tradingProperties.getWindow().getEnd() + "\n" +
                "Max position: " + tradingProperties.getRisk().getMaxPosition() + " contract\n" +
                "Daily loss limit: " + tradingProperties.getRisk().getDailyLossLimit() + " TWD");
        
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
    
    @Scheduled(fixedRate = 60000)
    public void tradingLoop() {
        if (emergencyShutdown || !marketDataConnected) return;
        
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.parse(tradingProperties.getWindow().getStart());
        LocalTime end = LocalTime.parse(tradingProperties.getWindow().getEnd());
        
        if (now.isBefore(start) || now.isAfter(end)) {
            return;
        }
        
        try {
            checkRiskLimits();
            
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
    
    private void evaluateEntry() throws Exception {
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        boolean newsVeto = signal.path("news_veto").asBoolean(false);
        String direction = signal.path("direction").asText("NEUTRAL");
        double confidence = signal.path("confidence").asDouble(0.0);
        double currentPrice = signal.path("current_price").asDouble(0.0);
        
        if (newsVeto) {
            log.info("🚫 News veto active - no entry");
            return;
        }
        
        if (confidence < 0.65) {
            log.debug("⏸️ Low confidence: {}", confidence);
            return;
        }
        
        if ("LONG".equals(direction)) {
            executeOrder("BUY", 1, currentPrice);
        } else if ("SHORT".equals(direction)) {
            executeOrder("SELL", 1, currentPrice);
        }
    }
    
    private void evaluateExit() throws Exception {
        String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        double currentPrice = signal.path("current_price").asDouble(0.0);
        int pos = currentPosition.get();
        double entry = entryPrice.get();
        double unrealizedPnL = (currentPrice - entry) * pos * 50; // MTXF tick value
        
        // Exit only on: stop-loss OR explicit exit signal (trend reversal)
        // NO PROFIT TARGETS - let winners run until reversal!
        boolean stopLoss = unrealizedPnL < -500; // -10 points stop
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
    
    @Scheduled(cron = "0 0 13 * * MON-FRI")
    public void autoFlatten() {
        log.info("⏰ 13:00 Auto-flatten triggered");
        flattenPosition("End of trading window");
        sendDailySummary();
        
        // Gracefully shutdown the application after trading window ends
        log.info("🛑 Trading window ended - shutting down application");
        telegramService.sendMessage("🛑 Trading window ended - Bot shutting down");
        
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
        telegramService.sendMessage("🛑 Bot stopped");
    }
}
