package tw.gc.auto.equity.trader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contract Scaling Service
 * 
 * Responsible for determining the optimal number of contracts to trade
 * based on account equity and 30-day profit history.
 * 
 * Scaling Table (December 2025 Production):
 * | Account Equity    | 30-day Profit    | Contracts |
 * |-------------------|------------------|-----------|
 * | < 250,000 TWD     | any              | 1         |
 * | >= 250,000 TWD    | >= 80,000 TWD    | 2         |
 * | >= 500,000 TWD    | >= 180,000 TWD   | 3         |
 * | >= 1,000,000 TWD  | >= 400,000 TWD   | 4         |
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ContractScalingService {
    
    @NonNull
    private final RestTemplate restTemplate;
    @NonNull
    private final ObjectMapper objectMapper;
    @NonNull
    private final TelegramService telegramService;
    @NonNull
    private final TradingProperties tradingProperties;
    
    private final AtomicInteger maxContracts = new AtomicInteger(1);
    private final AtomicReference<Double> lastEquity = new AtomicReference<>(0.0);
    private final AtomicReference<Double> last30DayProfit = new AtomicReference<>(0.0);
    
    @PostConstruct
    public void initialize() {
        // Don't update on startup - let TradingEngine call it after bridge connection
    }
    
    public int getMaxContracts() {
        return maxContracts.get();
    }
    
    public double getLastEquity() {
        return lastEquity.get();
    }
    
    public double getLast30DayProfit() {
        return last30DayProfit.get();
    }
    
    /**
     * Calculate contract size based on equity and profit thresholds.
     * Both conditions must be met to scale up.
     */
    public int calculateContractSize(double equity, double profit30d) {
        if (equity >= 1_000_000 && profit30d >= 400_000) return 4;
        if (equity >= 500_000 && profit30d >= 180_000) return 3;
        if (equity >= 250_000 && profit30d >= 80_000) return 2;
        return 1;
    }
    
    /**
     * Update contract sizing by fetching account data from Python bridge.
     * Called at startup and daily at 11:15.
     */
    public void updateContractSizing() {
        try {
            log.info("Updating contract sizing...");
            
            String bridgeUrl = tradingProperties.getBridge().getUrl();
            
            String accountJson = restTemplate.getForObject(bridgeUrl + "/account", String.class);
            JsonNode accountData = objectMapper.readTree(accountJson);
            
            if (!"ok".equals(accountData.path("status").asText())) {
                log.warn("Failed to get account info: {} - defaulting to 1 contract", 
                    accountData.path("error").asText("unknown error"));
                maxContracts.set(1);
                return;
            }
            
            double equity = accountData.path("equity").asDouble(0.0);
            lastEquity.set(equity);
            
            String profitJson = restTemplate.getForObject(bridgeUrl + "/account/profit-history?days=30", String.class);
            JsonNode profitData = objectMapper.readTree(profitJson);
            
            double profit30d = 0.0;
            if ("ok".equals(profitData.path("status").asText())) {
                profit30d = profitData.path("total_pnl").asDouble(0.0);
            } else {
                log.warn("Failed to get profit history: {} - using 0 for 30d profit", 
                    profitData.path("error").asText("unknown error"));
            }
            last30DayProfit.set(profit30d);
            
            int previousContracts = maxContracts.get();
            int newContracts = calculateContractSize(equity, profit30d);
            maxContracts.set(newContracts);
            
            String message = String.format(
                "Contract sizing updated: %d contract%s\n" +
                "Equity: %.0f TWD\n" +
                "30d profit: %.0f TWD%s",
                newContracts,
                newContracts > 1 ? "s" : "",
                equity,
                profit30d,
                newContracts != previousContracts ? 
                    String.format("\n%s Changed from %d", newContracts > previousContracts ? "UP" : "DOWN", previousContracts) : ""
            );
            
            log.info(message.replace("\n", " | "));
            telegramService.sendMessage(message);
            
        } catch (Exception e) {
            log.error("Contract sizing update failed: {} - defaulting to 1 contract", e.getMessage());
            maxContracts.set(1);
            telegramService.sendMessage("Contract sizing failed: " + e.getMessage() + "\nDefaulting to 1 contract");
        }
    }
    
    /**
     * Daily update at 11:15 (before trading window opens at 11:30).
     * JUSTIFICATION: Updates contract sizing based on account equity before market opens.
     * Runs before 11:30 trading window to ensure correct position sizes are ready.
     */
    @Scheduled(cron = "0 15 11 * * MON-FRI", zone = AppConstants.SCHEDULER_TIMEZONE)
    public void dailyContractSizingUpdate() {
        log.info("11:15 Daily contract sizing update triggered");
        updateContractSizing();
    }
}
