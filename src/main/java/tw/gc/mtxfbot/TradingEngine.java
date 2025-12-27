package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class TradingEngine {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TelegramService telegramService;
    
    @Value("${trading.window.start}")
    private String windowStart;
    
    @Value("${trading.window.end}")
    private String windowEnd;
    
    @Value("${trading.risk.max-position}")
    private int maxPosition;
    
    @Value("${trading.risk.daily-loss-limit}")
    private int dailyLossLimit;
    
    @Value("${trading.bridge.url}")
    private String bridgeUrl;
    
    private final AtomicInteger currentPosition = new AtomicInteger(0);
    private final AtomicReference<Double> dailyPnL = new AtomicReference<>(0.0);
    private final AtomicReference<Double> entryPrice = new AtomicReference<>(0.0);
    private volatile boolean emergencyShutdown = false;
    private volatile boolean marketDataConnected = false;
    
    @PostConstruct
    public void initialize() {
        log.info("🚀 MTXF Lunch Bot initializing...");
        telegramService.sendMessage("🚀 MTXF Lunch Bot started\n" +
                "Trading window: " + windowStart + " - " + windowEnd + "\n" +
                "Max position: " + maxPosition + " contract\n" +
                "Daily loss limit: " + dailyLossLimit + " TWD");
        
        try {
            String response = restTemplate.getForObject(bridgeUrl + "/health", String.class);
            log.info("✅ Python bridge connected: {}", response);
            marketDataConnected = true;
        } catch (Exception e) {
            log.error("❌ Failed to connect to Python bridge", e);
            telegramService.sendMessage("⚠️ Python bridge connection failed!");
        }
    }
    
    @Scheduled(fixedRate = 60000)
    public void tradingLoop() {
        if (emergencyShutdown || !marketDataConnected) return;
        
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.parse(windowStart);
        LocalTime end = LocalTime.parse(windowEnd);
        
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
        if (currentPnL <= -dailyLossLimit) {
            log.error("🛑 DAILY LOSS LIMIT HIT: {} TWD", currentPnL);
            telegramService.sendMessage(String.format(
                    "🛑 EMERGENCY SHUTDOWN\nDaily loss: %.0f TWD\nFlattening all positions!", 
                    currentPnL));
            flattenPosition("Daily loss limit");
            emergencyShutdown = true;
        }
    }
    
    private void evaluateEntry() throws Exception {
        String signalJson = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
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
        String signalJson = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
        JsonNode signal = objectMapper.readTree(signalJson);
        
        double currentPrice = signal.path("current_price").asDouble(0.0);
        int pos = currentPosition.get();
        double entry = entryPrice.get();
        double unrealizedPnL = (currentPrice - entry) * pos * 50; // MTXF tick value
        
        if (Math.abs(unrealizedPnL) > 1000 || signal.path("exit_signal").asBoolean(false)) {
            flattenPosition("Exit signal / Target hit");
        }
    }
    
    private void executeOrder(String action, int quantity, double price) {
        try {
            String orderJson = String.format(
                    "{\"action\":\"%s\",\"quantity\":%d,\"price\":%.0f}", 
                    action, quantity, price);
            
            log.info("📤 Sending order: {}", orderJson);
            String result = restTemplate.postForObject(
                    bridgeUrl + "/order", orderJson, String.class);
            
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
            String signalJson = restTemplate.getForObject(bridgeUrl + "/signal", String.class);
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
    }
    
    private void sendDailySummary() {
        telegramService.sendMessage(String.format(
                "📊 DAILY SUMMARY\nFinal P&L: %.0f TWD\n" +
                "Target: 1,000 TWD/day (30,000 TWD/month)\n" +
                "Status: %s",
                dailyPnL.get(),
                dailyPnL.get() > 0 ? "✅ Profitable" : "❌ Loss"));
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down - flattening positions");
        flattenPosition("System shutdown");
        telegramService.sendMessage("🛑 Bot stopped");
    }
}
