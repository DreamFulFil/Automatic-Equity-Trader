package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.StockSettingsRepository;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AutoStrategySelector {

    private static final double MIN_CAPITAL_FOR_ODD_LOT_DAY_TRADING = 2_000_000.0;

    private final StrategyStockMappingRepository mappingRepository;
    private final BacktestResultRepository backtestResultRepository;
    private final StockSettingsRepository stockSettingsRepository;
    private final ActiveStrategyService activeStrategyService;
    private final ActiveStockService activeStockService;
    private final ShadowModeStockService shadowModeStockService;
    private final TelegramService telegramService;
    private final TaiwanStockComplianceService complianceService;
    private final tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository activeShadowSelectionRepository;

    /**
     * DISABLED: Automated daily strategy selection.
     * 
     * Manual trigger only via REST API or Telegram /selectstrategy command.
     * Automated time-based selection removed per system rebuild mandate (no 08:30 schedules).
     * 
     * Selection is based on backtest data, simulation stats, and LLM insights.
     */
    // @Scheduled(cron = "0 30 8 * * MON-FRI", zone = "Asia/Taipei")
    @Transactional
    public void selectBestStrategyAndStock() {
        log.info("🤖 AUTO-SELECTION: Starting daily strategy and stock selection...");
        
        try {
            // Find best performing strategy-stock combination
            Optional<StrategyStockMapping> bestCombo = findBestStrategyStockCombo();
            
            if (bestCombo.isEmpty()) {
                log.warn("⚠️ No backtest results found. Skipping auto-selection.");
                telegramService.sendMessage("⚠️ Auto-selection skipped: No backtest results available");
                return;
            }
            
            StrategyStockMapping best = bestCombo.get();
            String currentStrategy = activeStrategyService.getActiveStrategyName();
            String currentStock = activeStockService.getActiveStock();
            
            // Check if change is beneficial (at least 10% better)
            boolean shouldChange = shouldSwitchStrategy(best, currentStrategy, currentStock);
            
            if (shouldChange) {
                // Switch main strategy and stock
                activeStrategyService.switchStrategy(
                    best.getStrategyName(), 
                    null,  // parameters
                    "Auto-selected based on backtest results",
                    true,  // autoSwitched
                    best.getSharpeRatio(),
                    best.getMaxDrawdownPct(),
                    best.getTotalReturnPct(),
                    best.getWinRatePct()
                );
                activeStockService.setActiveStock(best.getSymbol());
                
                log.info("✅ AUTO-SELECTED: {} + {} (Return: {}%, Sharpe: {})", 
                    best.getSymbol(), best.getStrategyName(), 
                    String.format("%.2f", best.getTotalReturnPct()), 
                    String.format("%.2f", best.getSharpeRatio()));
                
                telegramService.sendMessage(String.format(
                    "🎯 *Auto-Selected Strategy*\n\n" +
                    "Stock: %s\n" +
                    "Strategy: %s\n" +
                    "Expected Return: %.2f%%\n" +
                    "Sharpe Ratio: %.2f\n" +
                    "Win Rate: %.2f%%\n" +
                    "Max Drawdown: %.2f%%\n\n" +
                    "Previous: %s + %s",
                    best.getSymbol(), best.getStrategyName(),
                    best.getTotalReturnPct(),
                    best.getSharpeRatio(),
                    best.getWinRatePct(),
                    best.getMaxDrawdownPct(),
                    currentStock, currentStrategy
                ));
            } else {
                log.info("✅ Keeping current strategy: {} + {}", currentStock, currentStrategy);
            }
            
            // Auto-select top 10 shadow mode strategies and populate selection table
            selectShadowModeStrategiesAndPopulateTable(best);
            
        } catch (Exception e) {
            log.error("❌ Auto-selection failed", e);
            telegramService.sendMessage("❌ Auto-selection failed: " + e.getMessage());
        }
    }
    
    /**
     * Select shadow mode strategies and populate the unified Active/Shadow selection table.
     * Ensures exactly 11 rows: 1 active + 10 shadow.
     */
    @Transactional
    public void selectShadowModeStrategiesAndPopulateTable(StrategyStockMapping activeMapping) {
        log.info("🌙 Selecting shadow strategies and populating selection table...");
        
        // Get all eligible mappings
        List<StrategyStockMapping> allMappings = mappingRepository.findAll().stream()
            .filter(m -> m.getTotalReturnPct() != null && m.getSharpeRatio() != null)
            .filter(m -> !isIntradayStrategy(m.getStrategyName()))
            .filter(m -> m.getTotalReturnPct() > 3.0)
            .filter(m -> m.getSharpeRatio() > 0.8)
            .toList();
        
        // Group by symbol and pick best strategy per stock
        java.util.Map<String, StrategyStockMapping> bestPerStock = new java.util.HashMap<>();
        for (StrategyStockMapping mapping : allMappings) {
            String symbol = mapping.getSymbol();
            // Exclude the active stock
            if (symbol.equals(activeMapping.getSymbol())) {
                continue;
            }
            if (!bestPerStock.containsKey(symbol) || 
                calculateScore(mapping) > calculateScore(bestPerStock.get(symbol))) {
                bestPerStock.put(symbol, mapping);
            }
        }
        
        // Select top 10 for shadow mode
        List<StrategyStockMapping> shadowMappings = bestPerStock.values().stream()
            .sorted((a, b) -> Double.compare(calculateScore(b), calculateScore(a)))
            .limit(10)
            .toList();
        
        // Clear existing selection table (use deleteAllInBatch for immediate execution)
        activeShadowSelectionRepository.deleteAllInBatch();
        log.debug("🗑️ Cleared existing active_shadow_selection entries");
        
        // Insert active (rank 1)
        tw.gc.auto.equity.trader.entities.ActiveShadowSelection activeSelection = 
            tw.gc.auto.equity.trader.entities.ActiveShadowSelection.builder()
                .rankPosition(1)
                .isActive(true)
                .symbol(activeMapping.getSymbol())
                .stockName(activeMapping.getStockName() != null ? activeMapping.getStockName() : activeMapping.getSymbol())
                .strategyName(activeMapping.getStrategyName())
                .source(tw.gc.auto.equity.trader.entities.ActiveShadowSelection.SelectionSource.BACKTEST)
                .sharpeRatio(activeMapping.getSharpeRatio())
                .totalReturnPct(activeMapping.getTotalReturnPct())
                .winRatePct(activeMapping.getWinRatePct())
                .maxDrawdownPct(activeMapping.getMaxDrawdownPct())
                .compositeScore(calculateScore(activeMapping))
                .build();
        activeShadowSelectionRepository.save(activeSelection);
        
        // Insert shadow (ranks 2-11)
        int rank = 2;
        for (StrategyStockMapping mapping : shadowMappings) {
            tw.gc.auto.equity.trader.entities.ActiveShadowSelection shadowSelection = 
                tw.gc.auto.equity.trader.entities.ActiveShadowSelection.builder()
                    .rankPosition(rank++)
                    .isActive(false)
                    .symbol(mapping.getSymbol())
                    .stockName(mapping.getStockName() != null ? mapping.getStockName() : mapping.getSymbol())
                    .strategyName(mapping.getStrategyName())
                    .source(tw.gc.auto.equity.trader.entities.ActiveShadowSelection.SelectionSource.BACKTEST)
                    .sharpeRatio(mapping.getSharpeRatio())
                    .totalReturnPct(mapping.getTotalReturnPct())
                    .winRatePct(mapping.getWinRatePct())
                    .maxDrawdownPct(mapping.getMaxDrawdownPct())
                    .compositeScore(calculateScore(mapping))
                    .build();
            activeShadowSelectionRepository.save(shadowSelection);
        }
        
        // Also update shadow mode stock service for backward compatibility
        selectShadowModeStrategies();
        
        log.info("✅ Selection table populated: 1 active + {} shadow entries", shadowMappings.size());
        
        // Send Telegram notification
        StringBuilder msg = new StringBuilder();
        msg.append("📊 *Strategy Selection Complete*\n\n");
        msg.append(String.format("🔥 ACTIVE: %s (%s) - %s\n", 
            activeMapping.getStockName(), activeMapping.getSymbol(), activeMapping.getStrategyName()));
        msg.append(String.format("   Return: %.2f%% | Sharpe: %.2f | Win: %.2f%%\n\n", 
            activeMapping.getTotalReturnPct(), activeMapping.getSharpeRatio(), activeMapping.getWinRatePct()));
        
        msg.append(String.format("🌙 SHADOW MODE (%d stocks):\n", shadowMappings.size()));
        for (int i = 0; i < shadowMappings.size(); i++) {
            StrategyStockMapping m = shadowMappings.get(i);
            msg.append(String.format("%d. %s - %s (%.2f%%)\n", 
                i + 1, m.getStockName(), m.getStrategyName(), m.getTotalReturnPct()));
        }
        
        telegramService.sendMessage(msg.toString());
    }
    
    private Optional<StrategyStockMapping> findBestStrategyStockCombo() {
        // Read directly from backtest_results table
        List<BacktestResult> allResults = backtestResultRepository.findAll();
        
        if (allResults.isEmpty()) {
            log.warn("⚠️ No backtest results found in backtest_results table");
            return Optional.empty();
        }
        
        // Get stock settings to determine round-lot stocks
        StockSettings settings = stockSettingsRepository.findFirstByOrderByIdDesc()
            .orElse(StockSettings.builder().shareIncrement(27).build());
        // Round-lot: shareIncrement must be divisible by 1000 (no remainder)
        boolean isRoundLot = (settings.getShareIncrement() % 1000) == 0;
        
        log.info("📊 Stock trading mode: {} (share_increment={})", 
            isRoundLot ? "Round-lot (CAN day-trade)" : "Odd-lot (CANNOT day-trade)", settings.getShareIncrement());
        
        // Filter and rank backtest results
        return allResults.stream()
            .filter(r -> r.getTotalReturnPct() != null && r.getSharpeRatio() != null)
            .filter(r -> r.getTotalTrades() != null && r.getTotalTrades() > 10) // Sufficient sample size
            .filter(r -> !shouldFilterStrategy(r.getStrategyName(), isRoundLot)) // Filter intraday for round-lot
            .filter(r -> r.getTotalReturnPct() > 5.0)
            .filter(r -> r.getSharpeRatio() > 1.0)
            .filter(r -> r.getWinRatePct() != null && r.getWinRatePct() > 50.0)
            .filter(r -> r.getMaxDrawdownPct() != null && r.getMaxDrawdownPct() > -20.0)
            .max((a, b) -> {
                double scoreA = calculateScoreFromBacktest(a);
                double scoreB = calculateScoreFromBacktest(b);
                return Double.compare(scoreA, scoreB);
            })
            .map(this::convertToMapping); // Convert BacktestResult to StrategyStockMapping for compatibility
    }
    
    /**
     * Convert BacktestResult to StrategyStockMapping for backward compatibility
     */
    private StrategyStockMapping convertToMapping(BacktestResult result) {
        return StrategyStockMapping.builder()
            .symbol(result.getSymbol())
            .stockName(result.getStockName())
            .strategyName(result.getStrategyName())
            .totalReturnPct(result.getTotalReturnPct())
            .sharpeRatio(result.getSharpeRatio())
            .winRatePct(result.getWinRatePct())
            .maxDrawdownPct(result.getMaxDrawdownPct())
            .build();
    }
    
    /**
     * Calculate score from BacktestResult
     */
    private double calculateScoreFromBacktest(BacktestResult r) {
        double returnScore = r.getTotalReturnPct() != null ? r.getTotalReturnPct() : 0;
        double sharpeScore = r.getSharpeRatio() != null ? r.getSharpeRatio() : 0;
        double winRateScore = r.getWinRatePct() != null ? r.getWinRatePct() : 0;
        double ddScore = r.getMaxDrawdownPct() != null ? Math.abs(r.getMaxDrawdownPct()) : 1.0;
        
        if (ddScore < 0.01) ddScore = 0.01;
        return (returnScore * sharpeScore * winRateScore) / ddScore;
    }
    
    /**
     * Determine if strategy should be filtered based on trading mode.
     * In Taiwan: ONLY ROUND LOTS (shareIncrement % 1000 == 0) CAN do intraday/day-trading.
     * Odd lots (shareIncrement % 1000 != 0) CANNOT do intraday/day-trading.
     */
    private boolean shouldFilterStrategy(String strategyName, boolean isRoundLot) {
        if (isRoundLot) {
            return false; // Round-lot mode: can trade ALL strategies including intraday
        }
        
        // Odd-lot mode: FILTER OUT intraday strategies (not allowed)
        return isIntradayStrategy(strategyName);
    }
    
    private double calculateScore(StrategyStockMapping m) {
        double returnScore = m.getTotalReturnPct() != null ? m.getTotalReturnPct() : 0;
        double sharpeScore = m.getSharpeRatio() != null ? m.getSharpeRatio() : 0;
        double winRateScore = m.getWinRatePct() != null ? m.getWinRatePct() : 0;
        double ddScore = m.getMaxDrawdownPct() != null ? Math.abs(m.getMaxDrawdownPct()) : 1.0;
        
        // Avoid division by zero
        if (ddScore < 0.01) ddScore = 0.01;
        
        return (returnScore * sharpeScore * winRateScore) / ddScore;
    }
    
    private boolean shouldSwitchStrategy(StrategyStockMapping best, String currentStrategy, String currentStock) {
        if (currentStrategy == null || currentStock == null) {
            return true; // No current strategy, definitely switch
        }
        
        // Find current combo performance
        Optional<StrategyStockMapping> current = mappingRepository
            .findBySymbolAndStrategyName(currentStock, currentStrategy);
        
        if (current.isEmpty()) {
            return true; // Current combo not tested, switch to tested one
        }
        
        double currentScore = calculateScore(current.get());
        double bestScore = calculateScore(best);
        
        // Switch if new strategy is at least 10% better
        return bestScore > currentScore * 1.10;
    }
    
    @Transactional
    public void selectShadowModeStrategies() {
        log.info("🌙 AUTO-SELECTION: Selecting top 10 shadow mode strategies...");
        
        // Get stock settings to determine round-lot mode
        StockSettings settings = stockSettingsRepository.findFirstByOrderByIdDesc()
            .orElse(StockSettings.builder().shareIncrement(27).build());
        // Round-lot: shareIncrement must be divisible by 1000 (no remainder)
        boolean isRoundLot = (settings.getShareIncrement() % 1000) == 0;
        
        // Read from backtest_results and convert to mappings
        List<BacktestResult> allResults = backtestResultRepository.findAll();
        List<StrategyStockMapping> allMappings = allResults.stream()
            .filter(r -> r.getTotalReturnPct() != null && r.getSharpeRatio() != null)
            .filter(r -> r.getTotalTrades() != null && r.getTotalTrades() > 10)
            .filter(r -> !shouldFilterStrategy(r.getStrategyName(), isRoundLot)) // Filter based on trading mode
            .filter(r -> r.getTotalReturnPct() > 3.0)
            .filter(r -> r.getSharpeRatio() > 0.8)
            .map(this::convertToMapping)
            .toList();
        
        // Group by symbol and pick best strategy for each stock
        java.util.Map<String, StrategyStockMapping> bestPerStock = new java.util.HashMap<>();
        for (StrategyStockMapping mapping : allMappings) {
            String symbol = mapping.getSymbol();
            if (!bestPerStock.containsKey(symbol) || 
                calculateScore(mapping) > calculateScore(bestPerStock.get(symbol))) {
                bestPerStock.put(symbol, mapping);
            }
        }
        
        // Select top 10 unique stocks by score (data-driven, no hardcoding)
        List<StrategyStockMapping> topStrategies = bestPerStock.values().stream()
            .sorted((a, b) -> Double.compare(calculateScore(b), calculateScore(a)))
            .limit(shadowModeStockService.getMaxShadowModeStocks())
            .toList();
        
        // Build config list with full metrics for upsert
        List<ShadowModeStockService.ShadowModeStockConfig> configs = new java.util.ArrayList<>();
        int rank = 1;
        for (StrategyStockMapping mapping : topStrategies) {
            double score = calculateScore(mapping);
            configs.add(ShadowModeStockService.ShadowModeStockConfig.builder()
                .symbol(mapping.getSymbol())
                .stockName(mapping.getStockName())
                .strategyName(mapping.getStrategyName())
                .expectedReturnPercentage(mapping.getTotalReturnPct())
                .sharpeRatio(mapping.getSharpeRatio())
                .winRatePct(mapping.getWinRatePct())
                .maxDrawdownPct(mapping.getMaxDrawdownPct())
                .selectionScore(score)
                .build());
            rank++;
        }

        // Upsert top candidates (clears old, inserts new with ranking)
        shadowModeStockService.upsertTopCandidates(configs);
        
        if (!topStrategies.isEmpty()) {
            StringBuilder msg = new StringBuilder("🌙 *Shadow Mode Strategies (Top 10)*\n\n");
            for (int i = 0; i < topStrategies.size(); i++) {
                StrategyStockMapping m = topStrategies.get(i);
                msg.append(String.format("%d. %s + %s\n   Return: %.2f%%, Sharpe: %.2f\n",
                    i + 1, m.getSymbol(), m.getStrategyName(),
                    m.getTotalReturnPct(),
                    m.getSharpeRatio()));
            }
            telegramService.sendMessage(msg.toString());
        }
    }
    
    /**
     * Check if a strategy is intraday/day trading based.
     * Taiwan stocks don't support odd lot for intraday trading, so exclude these.
     */
    private boolean isIntradayStrategy(String strategyName) {
        if (strategyName == null) return false;
        
        String lower = strategyName.toLowerCase();
        return lower.contains("intraday") ||
               lower.contains("pivot points") ||
               lower.contains("vwap") ||
               lower.contains("twap") ||
               lower.contains("day trad") ||
               lower.contains("price volume rank");
    }
}
