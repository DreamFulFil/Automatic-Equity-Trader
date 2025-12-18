package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.RiskSettings;
import tw.gc.auto.equity.trader.repositories.RiskSettingsRepository;

import java.time.LocalDateTime;

/**
 * RiskSettingsService - Manages risk management configuration.
 * Provides access to risk-specific settings stored in database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskSettingsService {

    @NonNull
    private final RiskSettingsRepository riskSettingsRepo;

    @PostConstruct
    public void initialize() {
        ensureDefaultSettings();
        log.info("⚠️ RiskSettingsService initialized");
    }

    /**
     * Ensure default risk settings exist in database
     */
    @Transactional
    public void ensureDefaultSettings() {
        if (riskSettingsRepo.findFirst() == null) {
            RiskSettings defaultSettings = RiskSettings.builder()
                    .maxSharesPerTrade(50)
                    .dailyLossLimitTwd(1000)
                    .weeklyLossLimitTwd(4000)
                    .maxHoldMinutes(45)
                    .updatedAt(LocalDateTime.now())
                    .build();
            riskSettingsRepo.save(defaultSettings);
            log.info("✅ Created default risk settings");
        }
    }

    /**
     * Get current risk settings
     */
    public RiskSettings getSettings() {
        RiskSettings settings = riskSettingsRepo.findFirst();
        if (settings == null) {
            throw new IllegalStateException("Risk settings not found in database");
        }
        return settings;
    }

    /**
     * Update risk settings
     */
    @Transactional
    public RiskSettings updateSettings(int maxPosition, int dailyLossLimit, int weeklyLossLimit, int maxHoldMinutes) {
        RiskSettings settings = getSettings();
        settings.setMaxSharesPerTrade(maxPosition);
        settings.setDailyLossLimitTwd(dailyLossLimit);
        settings.setWeeklyLossLimitTwd(weeklyLossLimit);
        settings.setMaxHoldMinutes(maxHoldMinutes);
        settings.setUpdatedAt(LocalDateTime.now());
        return riskSettingsRepo.save(settings);
    }

    /**
     * Get maximum position size (deprecated - use getMaxSharesPerTrade)
     */
    @Deprecated
    public int getMaxPosition() {
        return getSettings().getMaxSharesPerTrade();
    }

    /**
     * Get daily loss limit
     */
    public int getDailyLossLimit() {
        return getSettings().getDailyLossLimitTwd();
    }

    /**
     * Get weekly loss limit
     */
    public int getWeeklyLossLimit() {
        return getSettings().getWeeklyLossLimitTwd();
    }

    /**
     * Get maximum hold time in minutes
     */
    public int getMaxHoldMinutes() {
        return getSettings().getMaxHoldMinutes();
    }
    
    /**
     * Get all risk settings as RiskSettings entity
     */
    public RiskSettings getRiskSettings() {
        return getSettings();
    }
}