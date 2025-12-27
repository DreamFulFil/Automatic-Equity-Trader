package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.StockSettingsRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * StockSettingsService - Enhanced stock trading configuration with real-time capital management
 * Features:
 * - Real-time equity fetching from Shioaji API
 * - Automated position scaling with risk management
 * - Beginner-friendly position sizing recommendations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockSettingsService {

    @NonNull
    private final StockSettingsRepository stockSettingsRepo;
    @NonNull
    private final RestTemplate restTemplate;
    @NonNull
    private final ObjectMapper objectMapper;
    
    private static final String PYTHON_BRIDGE_URL = "http://localhost:8888";
    private static final double FALLBACK_BASE_CAPITAL = 80000.0;
    private static final double FALLBACK_INCREMENT_CAPITAL = 20000.0;

    @PostConstruct
    public void initialize() {
        ensureDefaultSettings();
        log.info("📊 StockSettingsService initialized");
    }

    /**
     * Ensure default stock settings exist in database
     */
    @Transactional
    public void ensureDefaultSettings() {
        if (stockSettingsRepo.findFirst() == null) {
            StockSettings defaultSettings = StockSettings.builder()
                    .shares(70)
                    .shareIncrement(27)
                    .updatedAt(LocalDateTime.now())
                    .build();
            stockSettingsRepo.save(defaultSettings);
            log.info("✅ Created default stock settings");
        }
    }

    /**
     * Get current stock settings
     */
    public StockSettings getSettings() {
        StockSettings settings = stockSettingsRepo.findFirst();
        if (settings == null) {
            throw new IllegalStateException("Stock settings not found in database");
        }
        return settings;
    }

    /**
     * Update stock settings
     */
    @Transactional
    public StockSettings updateSettings(int shares, int shareIncrement) {
        StockSettings settings = getSettings();
        settings.setShares(shares);
        settings.setShareIncrement(shareIncrement);
        settings.setUpdatedAt(LocalDateTime.now());
        return stockSettingsRepo.save(settings);
    }

    /**
     * Fetch real-time account equity from Shioaji via Python bridge
     */
    public double fetchAccountEquity() {
        try {
            String url = PYTHON_BRIDGE_URL + "/account/balance";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                if (json.has("equity")) {
                    double equity = json.get("equity").asDouble();
                    log.info("✅ Fetched real-time equity from Shioaji: {}", equity);
                    return equity;
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch equity from Shioaji: {}, using fallback", e.getMessage());
        }
        
        log.warn("⚠️ Using fallback capital: {}", FALLBACK_BASE_CAPITAL);
        return FALLBACK_BASE_CAPITAL;
    }
    
    /**
     * Get base stock quantity with automated scaling
     */
    public int getBaseStockQuantity(double equity) {
        StockSettings settings = getSettings();
        int baseShares = settings.getShares();
        int increment = settings.getShareIncrement();

        int additionalShares = 0;
        if (equity > FALLBACK_BASE_CAPITAL) {
            additionalShares = (int) ((equity - FALLBACK_BASE_CAPITAL) / FALLBACK_INCREMENT_CAPITAL) * increment;
        }
        
        int totalShares = baseShares + additionalShares;
        log.info("📊 Position sizing: equity={}, base={}, additional={}, total={}",
            equity, baseShares, additionalShares, totalShares);
        
        return totalShares;
    }
    
    /**
     * Calculate safe position size (max 10% of equity per position)
     */
    public int calculateSafePositionSize(double equity, double stockPrice, double maxRiskPct) {
        double maxPositionValue = equity * (maxRiskPct / 100.0);
        int shares = (int) (maxPositionValue / stockPrice);
        
        log.info("🛡️ Safe position: equity={}, price={}, maxRisk={}%, shares={}",
            equity, stockPrice, maxRiskPct, shares);
        
        return shares;
    }
    
    /**
     * Check if position scaling is recommended
     */
    public boolean shouldScalePosition(double currentEquity, double lastEquity) {
        double growth = currentEquity - lastEquity;
        boolean shouldScale = growth >= FALLBACK_INCREMENT_CAPITAL;
        
        if (shouldScale) {
            log.info("📈 Position scaling recommended: equity grew by {}", growth);
        }
        
        return shouldScale;
    }
    
    /**
     * Get position scaling recommendation
     */
    public Map<String, Object> getScalingRecommendation(double currentEquity, double stockPrice) {
        StockSettings settings = getSettings();
        int currentShares = getBaseStockQuantity(currentEquity);
        int safeShares = calculateSafePositionSize(currentEquity, stockPrice, 10.0);
        
        Map<String, Object> recommendation = new HashMap<>();
        recommendation.put("currentEquity", currentEquity);
        recommendation.put("recommendedShares", Math.min(currentShares, safeShares));
        recommendation.put("maxSafeShares", safeShares);
        recommendation.put("baseShares", settings.getShares());
        recommendation.put("incrementPerLevel", settings.getShareIncrement());
        
        double positionRisk = (currentShares * stockPrice / currentEquity) * 100;
        recommendation.put("positionRiskPct", positionRisk);
        
        if (positionRisk > 15) {
            recommendation.put("warning", "Position size exceeds 15% - high risk!");
        } else if (positionRisk > 10) {
            recommendation.put("warning", "Position size above 10% - moderate risk");
        } else {
            recommendation.put("status", "Position size within safe limits");
        }
        
        return recommendation;
    }
}