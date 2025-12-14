package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;
import tw.gc.auto.equity.trader.repositories.ShadowModeStockRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ShadowModeStockService - Manages shadow mode stock configurations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShadowModeStockService {

    @NonNull
    private final ShadowModeStockRepository shadowModeStockRepository;

    @PostConstruct
    public void initialize() {
        log.info("📊 ShadowModeStockService initialized");
    }

    /**
     * Get all enabled shadow mode stocks
     */
    public List<ShadowModeStock> getEnabledStocks() {
        return shadowModeStockRepository.findByEnabledTrueOrderByRankPosition();
    }

    /**
     * Add or update a shadow mode stock
     */
    @Transactional
    public ShadowModeStock addOrUpdateStock(String symbol, String stockName, String strategyName, 
                                           Double expectedReturnPercentage, Integer rankPosition) {
        ShadowModeStock stock = shadowModeStockRepository.findBySymbol(symbol)
                .orElse(ShadowModeStock.builder()
                        .symbol(symbol)
                        .createdAt(LocalDateTime.now())
                        .build());

        stock.setStockName(stockName);
        stock.setStrategyName(strategyName);
        stock.setExpectedReturnPercentage(expectedReturnPercentage);
        stock.setRankPosition(rankPosition);
        stock.setEnabled(true);
        stock.setUpdatedAt(LocalDateTime.now());

        return shadowModeStockRepository.save(stock);
    }

    /**
     * Bulk configure shadow mode stocks (replaces all existing)
     */
    @Transactional
    public void configureStocks(List<ShadowModeStockConfig> configs) {
        // Clear existing
        shadowModeStockRepository.deleteAll();

        // Add new configurations
        for (int i = 0; i < configs.size(); i++) {
            ShadowModeStockConfig config = configs.get(i);
            ShadowModeStock stock = ShadowModeStock.builder()
                    .symbol(config.getSymbol())
                    .stockName(config.getStockName())
                    .strategyName(config.getStrategyName())
                    .expectedReturnPercentage(config.getExpectedReturnPercentage())
                    .rankPosition(i + 1)
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            shadowModeStockRepository.save(stock);
        }

        log.info("✅ Configured {} shadow mode stocks", configs.size());
    }

    /**
     * Remove a shadow mode stock
     */
    @Transactional
    public void removeStock(String symbol) {
        shadowModeStockRepository.deleteBySymbol(symbol);
        log.info("🗑️ Removed shadow mode stock: {}", symbol);
    }

    /**
     * Configuration DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ShadowModeStockConfig {
        private String symbol;
        private String stockName;
        private String strategyName;
        private Double expectedReturnPercentage;
    }
}
