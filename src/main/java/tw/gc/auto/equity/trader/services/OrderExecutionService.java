package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.services.RiskManagementService;
import tw.gc.auto.equity.trader.services.RiskSettingsService;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.Trade;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderExecutionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final TelegramService telegramService;
    private final DataLoggingService dataLoggingService;
    private final PositionManager positionManager;
    private final RiskManagementService riskManagementService;
    private final RiskSettingsService riskSettingsService;

    private String getBridgeUrl() {
        return tradingProperties.getBridge().getUrl();
    }

    /**
     * Check account balance/position and adjust quantity to prevent insufficient funds/shares
     */
    public int checkBalanceAndAdjustQuantity(String action, int requestedQuantity, double price, String instrument, String tradingMode) {
        try {
            if ("BUY".equals(action)) {
                // Check available balance for BUY orders
                String accountJson = restTemplate.getForObject(getBridgeUrl() + "/account", String.class);
                JsonNode accountData = objectMapper.readTree(accountJson);
                
                if (!"ok".equals(accountData.path("status").asText())) {
                    log.warn("⚠️ Could not get account balance - proceeding with requested quantity");
                    return requestedQuantity;
                }
                
                double availableBalance = accountData.path("available_margin").asDouble(0.0);
                if (availableBalance <= 0) {
                    log.warn("⚠️ No available balance in account");
                    return 0;
                }
                
                // Calculate maximum affordable quantity
                double maxAffordable = availableBalance / price;
                int maxQuantity = (int) Math.floor(maxAffordable);
                
                // For stocks, ensure we don't exceed reasonable limits
                if ("stock".equals(tradingMode)) {
                    maxQuantity = Math.min(maxQuantity, 1000); // Safety limit
                }
                
                if (maxQuantity < requestedQuantity) {
                    log.warn("⚠️ Balance insufficient - reducing quantity from {} to {} (balance: {:.0f} TWD, price: {:.0f} TWD)",
                        requestedQuantity, maxQuantity, availableBalance, price);
                    return Math.max(0, maxQuantity);
                }
                
            } else if ("SELL".equals(action)) {
                // Check current position for SELL orders
                int availableShares = Math.abs(positionManager.getPosition(instrument));
                
                if (availableShares <= 0) {
                    log.warn("⚠️ No position to sell for {}", instrument);
                    return 0;
                }
                
                if (requestedQuantity > availableShares) {
                    log.warn("⚠️ Insufficient shares - reducing quantity from {} to {} (position: {} shares)",
                        requestedQuantity, availableShares, availableShares);
                    return availableShares;
                }
            }
            
            return requestedQuantity;
            
        } catch (Exception e) {
            log.error("❌ Failed to check account balance/position: {}", e.getMessage());
            // On error, proceed with requested quantity to avoid blocking trades
            return requestedQuantity;
        }
    }

    /**
     * Execute order with retry wrapper and quantity bug fix
     */
    public void executeOrderWithRetry(String action, int quantity, double price, String instrument, boolean isExit, boolean emergencyShutdown, String strategyName) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("action", action);
                orderMap.put("quantity", String.valueOf(quantity));
                orderMap.put("price", price);
                orderMap.put("is_exit", isExit);
                orderMap.put("strategy", strategyName != null ? strategyName : "Unknown");
                
                log.info("📤 [Strategy: {}] Sending {} order (attempt {}, exit={}): {}", 
                    strategyName != null ? strategyName : "Unknown", instrument, attempt, isExit, orderMap);
                String result = restTemplate.postForObject(
                        getBridgeUrl() + "/order", orderMap, String.class);
                log.debug("📥 Order response: {}", result);
                
                // Success - update position
                if ("BUY".equals(action)) {
                    positionManager.updatePosition(instrument, quantity);
                } else {
                    positionManager.updatePosition(instrument, -quantity);
                }

                if (!isExit) {
                    positionManager.updateEntry(instrument, price, LocalDateTime.now(ZoneId.of("Asia/Taipei")));
                }
                
                telegramService.sendMessage(String.format(
                        "✅ ORDER FILLED [%s]\n%s %d %s @ %.0f\nPosition: %d", 
                        strategyName != null ? strategyName : "Unknown", action, quantity, instrument, price, positionManager.getPosition(instrument)));
                
                if (!isExit) {
                    Trade trade = Trade.builder()
                            .timestamp(LocalDateTime.now(ZoneId.of("Asia/Taipei")))
                            .action("BUY".equals(action) ? Trade.TradeAction.BUY : Trade.TradeAction.SELL)
                            .quantity(quantity)
                            .entryPrice(price)
                            .symbol(instrument)
                            .strategyName(strategyName != null ? strategyName : "Unknown")
                            .reason("Signal execution")
                            .mode(emergencyShutdown ? Trade.TradingMode.SIMULATION : Trade.TradingMode.LIVE)
                            .status(Trade.TradeStatus.OPEN)
                            .build();
                    dataLoggingService.logTrade(trade);
                }
                
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
    
    /**
     * Legacy method without strategyName parameter for backward compatibility
     */
    public void executeOrderWithRetry(String action, int quantity, double price, String instrument, boolean isExit, boolean emergencyShutdown) {
        executeOrderWithRetry(action, quantity, price, instrument, isExit, emergencyShutdown, null);
    }

    public void flattenPosition(String reason, String instrument, String tradingMode, boolean emergencyShutdown) {
        int pos = positionManager.getPosition(instrument);
        if (pos == 0) return;
        
        String action = pos > 0 ? "SELL" : "BUY";
        try {
            String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
            JsonNode signal = objectMapper.readTree(signalJson);
            double currentPrice = signal.path("current_price").asDouble(0.0);
            
            // Store entry metadata before executeOrderWithRetry overwrites it (though isExit=true prevents overwrite)
            double originalEntryPrice = positionManager.getEntryPrice(instrument);
            LocalDateTime entryTime = positionManager.getEntryTime(instrument);
            int holdDurationMinutes = entryTime == null ? 0 :
                    (int) java.time.Duration.between(entryTime, LocalDateTime.now(ZoneId.of("Asia/Taipei"))).toMinutes();
            
            executeOrderWithRetry(action, Math.abs(pos), currentPrice, instrument, true, emergencyShutdown); // isExit=true triggers cooldown
            
            double multiplier = "stock".equals(tradingMode) ? 1.0 : 50.0;
            double pnl = (currentPrice - originalEntryPrice) * pos * multiplier;
            riskManagementService.recordPnL(instrument, pnl, riskSettingsService.getWeeklyLossLimit());

            Trade.TradingMode mode = emergencyShutdown ? Trade.TradingMode.SIMULATION : Trade.TradingMode.LIVE;
            dataLoggingService.closeLatestTrade(instrument, mode, currentPrice, pnl, holdDurationMinutes);
            
            positionManager.setPosition(instrument, 0);
            positionManager.clearEntry(instrument);
            
            log.info("🔒 Position flattened - P&L: {} TWD (Reason: {})", pnl, reason);
            telegramService.sendMessage(String.format(
                    "🔒 POSITION CLOSED\nReason: %s\nP&L: %.0f TWD\nDaily P&L: %.0f TWD\nWeekly P&L: %.0f TWD", 
                    reason, pnl, riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL()));
            
            if (riskManagementService.isWeeklyLimitHit()) {
                telegramService.sendMessage(String.format(
                    "🚨 WEEKLY LOSS LIMIT HIT\nWeekly P&L: %.0f TWD\nTrading paused until next Monday!",
                    riskManagementService.getWeeklyPnL()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Flatten failed", e);
        }
    }
}
