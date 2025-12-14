package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.StockSettingsRepository;

import java.time.LocalDateTime;

/**
 * StockSettingsService - Manages stock trading configuration.
 * Provides access to stock-specific settings stored in database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockSettingsService {

    @NonNull
    private final StockSettingsRepository stockSettingsRepo;

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
     * Get base stock quantity for current equity
     */
    public int getBaseStockQuantity(double equity) {
        StockSettings settings = getSettings();
        int baseShares = settings.getShares();
        int increment = settings.getShareIncrement();

        // Default base capital for 70 shares at ~1100/share MediaTek
        double baseCapital = 80000.0;
        double incrementCapital = 20000.0;

        // Calculate additional shares based on equity above base
        int additionalShares = 0;
        if (equity > baseCapital) {
            additionalShares = (int) ((equity - baseCapital) / incrementCapital) * increment;
        }
        return baseShares + additionalShares;
    }
}