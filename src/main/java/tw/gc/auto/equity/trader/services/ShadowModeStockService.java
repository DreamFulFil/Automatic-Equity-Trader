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
 * 
 * Maintains up to 10 shadow mode candidates dynamically selected from backtesting results.
 * The selection pipeline: Backtesting → Daily Forward-Testing (Simulations) → Rank Top 10 → Upsert.
 * Selection is data-driven based on recent performance metrics (no hardcoding).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShadowModeStockService {

    private static final int MAX_SHADOW_MODE_STOCKS = 10;

    @NonNull
    private final ShadowModeStockRepository shadowModeStockRepository;

    @PostConstruct
    public void initialize() {
        log.info("📊 ShadowModeStockService initialized (max {} candidates)", MAX_SHADOW_MODE_STOCKS);
    }

    /**
     * Get maximum number of shadow mode slots
     */
    public int getMaxShadowModeStocks() {
        return MAX_SHADOW_MODE_STOCKS;
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
     * Clear all shadow mode stocks
     */
    @Transactional
    public void clearAll() {
        shadowModeStockRepository.deleteAll();
        log.info("🗑️ Cleared all shadow mode stocks");
    }
    
    /**
     * Add a single shadow stock with full metrics (for auto-selection)
     * Uses find-or-create pattern to avoid duplicates
     */
    @Transactional
    public void addShadowStock(String symbol, String strategyName) {
        addShadowStockWithMetrics(symbol, symbol, strategyName, null, null, null, null, null);
    }

    /**
     * Add a single shadow stock with full performance metrics
     */
    @Transactional
    public void addShadowStockWithMetrics(String symbol, String stockName, String strategyName, 
                                          Double expectedReturnPct, Double sharpeRatio, 
                                          Double winRatePct, Double maxDrawdownPct, Double selectionScore) {
        ShadowModeStock stock = shadowModeStockRepository.findBySymbol(symbol)
                .orElse(ShadowModeStock.builder()
                        .symbol(symbol)
                        .stockName(stockName != null ? stockName : symbol)
                        .createdAt(LocalDateTime.now())
                        .build());
        
        stock.setStrategyName(strategyName);
        stock.setExpectedReturnPercentage(expectedReturnPct);
        stock.setSharpeRatio(sharpeRatio);
        stock.setWinRatePct(winRatePct);
        stock.setMaxDrawdownPct(maxDrawdownPct);
        stock.setSelectionScore(selectionScore);
        stock.setEnabled(true);
        stock.setUpdatedAt(LocalDateTime.now());
        
        shadowModeStockRepository.save(stock);
    }

    /**
     * Upsert top 10 shadow mode stocks from ranked candidates.
     * Clears existing and inserts new selections with proper ranking.
     */
    @Transactional
    public void upsertTopCandidates(List<ShadowModeStockConfig> rankedCandidates) {
        // Limit to max slots
        List<ShadowModeStockConfig> topCandidates = rankedCandidates.stream()
                .limit(MAX_SHADOW_MODE_STOCKS)
                .toList();

        // Clear existing
        shadowModeStockRepository.deleteAll();
        shadowModeStockRepository.flush();

        // Add new configurations with rank
        for (int i = 0; i < topCandidates.size(); i++) {
            ShadowModeStockConfig config = topCandidates.get(i);
            ShadowModeStock stock = ShadowModeStock.builder()
                    .symbol(config.getSymbol())
                    .stockName(config.getStockName() != null ? config.getStockName() : config.getSymbol())
                    .strategyName(config.getStrategyName())
                    .expectedReturnPercentage(config.getExpectedReturnPercentage())
                    .sharpeRatio(config.getSharpeRatio())
                    .winRatePct(config.getWinRatePct())
                    .maxDrawdownPct(config.getMaxDrawdownPct())
                    .selectionScore(config.getSelectionScore())
                    .rankPosition(i + 1)
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            shadowModeStockRepository.save(stock);
        }

        log.info("✅ Upserted {} shadow mode candidates (max {})", topCandidates.size(), MAX_SHADOW_MODE_STOCKS);
    }

    /**
     * Configuration DTO with full metrics for data-driven selection
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
        private Double sharpeRatio;
        private Double winRatePct;
        private Double maxDrawdownPct;
        private Double selectionScore;
    }
}
