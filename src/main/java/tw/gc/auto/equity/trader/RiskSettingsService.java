package tw.gc.auto.equity.trader;

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
                    .maxPosition(1)
                    .dailyLossLimit(1500)
                    .weeklyLossLimit(7000)
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
        settings.setMaxPosition(maxPosition);
        settings.setDailyLossLimit(dailyLossLimit);
        settings.setWeeklyLossLimit(weeklyLossLimit);
        settings.setMaxHoldMinutes(maxHoldMinutes);
        settings.setUpdatedAt(LocalDateTime.now());
        return riskSettingsRepo.save(settings);
    }

    /**
     * Get maximum position size
     */
    public int getMaxPosition() {
        return getSettings().getMaxPosition();
    }

    /**
     * Get daily loss limit
     */
    public int getDailyLossLimit() {
        return getSettings().getDailyLossLimit();
    }

    /**
     * Get weekly loss limit
     */
    public int getWeeklyLossLimit() {
        return getSettings().getWeeklyLossLimit();
    }

    /**
     * Get maximum hold time in minutes
     */
    public int getMaxHoldMinutes() {
        return getSettings().getMaxHoldMinutes();
    }
}