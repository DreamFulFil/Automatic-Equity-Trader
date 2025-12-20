package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.BotSettings;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * BotModeService - Manages simulation/live mode and bot settings.
 * 
 * All operations START in simulation mode (stored in DB).
 * Live mode requires explicit /golive command after eligibility check.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BotModeService {
    
    @NonNull
    private final BotSettingsRepository settingsRepo;
    @NonNull
    private final ShioajiSettingsService shioajiSettingsService;
    @NonNull
    private final RestTemplate restTemplate;
    @NonNull
    private final BridgeManagerService bridgeManager;
    
    @PostConstruct
    public void initialize() {
        // Ensure default settings exist
        ensureDefaultSettings();
        
        TradingMode mode = getTradingMode();
        // Sync Shioaji simulation with trading mode
        shioajiSettingsService.updateSimulationMode(mode == TradingMode.SIMULATION);
        log.info("🎯 BotModeService initialized - Current mode: {}", mode);
    }
    
    /**
     * Get current trading mode (simulation or live)
     */
    public TradingMode getTradingMode() {
        return settingsRepo.findByKey(BotSettings.TRADING_MODE)
                .map(s -> TradingMode.valueOf(s.getValue().toUpperCase()))
                .orElse(TradingMode.SIMULATION);
    }
    
    /**
     * Check if in simulation mode
     */
    public boolean isSimulationMode() {
        return getTradingMode() == TradingMode.SIMULATION;
    }
    
    /**
     * Check if in live mode
     */
    public boolean isLiveMode() {
        return getTradingMode() == TradingMode.LIVE;
    }
    
    /**
     * Switch to live mode (called after /golive eligibility check)
     */
    public void switchToLiveMode() {
        updateSetting(BotSettings.TRADING_MODE, TradingMode.LIVE.name().toLowerCase());
        shioajiSettingsService.updateSimulationMode(false);
        shutdownBridge();
        log.warn("🔴 SWITCHED TO LIVE MODE - Real money at risk!");
    }
    
    /**
     * Switch back to simulation mode
     */
    public void switchToSimulationMode() {
        updateSetting(BotSettings.TRADING_MODE, TradingMode.SIMULATION.name().toLowerCase());
        shioajiSettingsService.updateSimulationMode(true);
        shutdownBridge();
        log.info("🟢 Switched to simulation mode");
    }
    
    /**
     * Shutdown and restart the Python bridge to force config reload
     */
    private void shutdownBridge() {
        String jasyptPassword = System.getProperty("jasypt.encryptor.password");
        if (jasyptPassword == null) {
            jasyptPassword = System.getenv("JASYPT_PASSWORD");
        }
        if (jasyptPassword == null) {
            log.error("Cannot restart bridge: JASYPT_PASSWORD not found");
            return;
        }
        
        try {
            restTemplate.postForObject("http://localhost:8888/shutdown", null, String.class);
            log.info("🛑 Bridge shutdown requested for config reload");
            
            // Wait a bit for shutdown
            Thread.sleep(2000);
            
            // Restart bridge
            bridgeManager.restartBridge(jasyptPassword);
            
        } catch (Exception e) {
            log.warn("Failed to restart bridge: {}", e.getMessage());
        }
    }
    public Optional<String> getSetting(String key) {
        return settingsRepo.findByKey(key).map(BotSettings::getValue);
    }
    
    /**
     * Get a setting value as integer
     */
    public int getSettingAsInt(String key, int defaultValue) {
        return settingsRepo.findByKey(key)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getValue());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
    
    /**
     * Update or create a setting
     */
    public void updateSetting(String key, String value) {
        BotSettings setting = settingsRepo.findByKey(key)
                .orElse(BotSettings.builder()
                        .key(key)
                        .build());
        setting.setValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        settingsRepo.save(setting);
        log.debug("Updated setting: {} = {}", key, value);
    }
    
    /**
     * Ensure default settings exist in database
     */
    private void ensureDefaultSettings() {
        // Trading mode defaults to simulation
        if (!settingsRepo.existsByKey(BotSettings.TRADING_MODE)) {
            settingsRepo.save(BotSettings.builder()
                    .key(BotSettings.TRADING_MODE)
                    .value("simulation")
                    .description("Current trading mode: simulation or live")
                    .build());
            log.info("💾 Created default setting: trading_mode = simulation");
        }
        
        // Risk limits
        ensureSetting(BotSettings.DAILY_LOSS_LIMIT, "2500", "Daily loss limit in TWD");
        ensureSetting(BotSettings.WEEKLY_LOSS_LIMIT, "7000", "Weekly loss limit in TWD");
        ensureSetting(BotSettings.MONTHLY_LOSS_LIMIT, "7000", "Monthly loss limit in TWD");
        ensureSetting(BotSettings.WEEKLY_PROFIT_LIMIT, "0", "Weekly profit cap (0 = unlimited)");
        ensureSetting(BotSettings.MONTHLY_PROFIT_LIMIT, "0", "Monthly profit cap (0 = unlimited)");
        
        // TutorBot limits
        ensureSetting(BotSettings.TUTOR_QUESTIONS_PER_DAY, "10", "Max questions to TutorBot per day");
        ensureSetting(BotSettings.TUTOR_INSIGHTS_PER_DAY, "3", "Max insights from TutorBot per day");
        
        // Ollama model (dynamic, not in application.yml)
        ensureSetting(BotSettings.OLLAMA_MODEL, "phi3:3.8b", "Ollama model for AI agents");
    }
    
    private void ensureSetting(String key, String defaultValue, String description) {
        if (!settingsRepo.existsByKey(key)) {
            settingsRepo.save(BotSettings.builder()
                    .key(key)
                    .value(defaultValue)
                    .description(description)
                    .build());
            log.debug("Created default setting: {} = {}", key, defaultValue);
        }
    }
}
