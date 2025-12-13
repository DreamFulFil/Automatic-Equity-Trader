package tw.gc.auto.equity.trader;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.repositories.ShioajiSettingsRepository;

import java.time.LocalDateTime;

/**
 * ShioajiSettingsService - Manages Shioaji API configuration.
 * Provides access to Shioaji settings stored in database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShioajiSettingsService {

    @NonNull
    private final ShioajiSettingsRepository shioajiSettingsRepo;

    @PostConstruct
    public void initialize() {
        ensureDefaultSettings();
        log.info("🔧 ShioajiSettingsService initialized");
    }

    @Transactional
    public void ensureDefaultSettings() {
        if (shioajiSettingsRepo.findFirst() == null) {
            ShioajiSettings defaultSettings = ShioajiSettings.builder()
                    .simulation(true) // Default to simulation for safety
                    .updatedAt(LocalDateTime.now())
                    .build();
            shioajiSettingsRepo.save(defaultSettings);
            log.info("✅ Created default Shioaji settings");
        }
    }

    /**
     * Get current Shioaji settings
     */
    public ShioajiSettings getSettings() {
        ShioajiSettings settings = shioajiSettingsRepo.findFirst();
        if (settings == null) {
            throw new IllegalStateException("Shioaji settings not found in database");
        }
        return settings;
    }

    /**
     * Update simulation mode
     */
    @Transactional
    public void setSimulation(boolean simulation) {
        ShioajiSettings settings = getSettings();
        settings.setSimulation(simulation);
        settings.setUpdatedAt(LocalDateTime.now());
        shioajiSettingsRepo.save(settings);
        log.info("🔄 Shioaji simulation mode updated to: {}", simulation);
    }

    /**
     * Check if in simulation mode
     */
    public boolean isSimulationMode() {
        return getSettings().isSimulation();
    }
}