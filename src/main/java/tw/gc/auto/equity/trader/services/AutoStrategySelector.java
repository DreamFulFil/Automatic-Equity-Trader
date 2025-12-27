package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AutoStrategySelector {

    private final StrategyPerformanceRepository performanceRepository;
    private final StrategyStockMappingRepository mappingRepository;
    private final ActiveStrategyService activeStrategyService;
    private final ActiveStockService activeStockService;
    private final ShadowModeStockService shadowModeStockService;
    private final TelegramService telegramService;

    @Transactional
    @Scheduled(cron = "0 30 8 * * MON-FRI") // Daily at 08:30 before market opens
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
            
            // Auto-select top 5 shadow mode strategies
            selectShadowModeStrategies();
            
        } catch (Exception e) {
            log.error("❌ Auto-selection failed", e);
            telegramService.sendMessage("❌ Auto-selection failed: " + e.getMessage());
        }
    }
    
    private Optional<StrategyStockMapping> findBestStrategyStockCombo() {
        // Criteria: High return, low risk, good Sharpe ratio, high win rate
        List<StrategyStockMapping> all = mappingRepository.findAll();
        
        return all.stream()
            .filter(m -> m.getTotalReturnPct() != null && m.getSharpeRatio() != null)
            .filter(m -> m.getTotalReturnPct() > 5.0) // At least 5% return
            .filter(m -> m.getSharpeRatio() > 1.0) // Good risk-adjusted return
            .filter(m -> m.getWinRatePct() != null && m.getWinRatePct() > 50.0) // Win rate > 50%
            .filter(m -> m.getMaxDrawdownPct() != null && m.getMaxDrawdownPct() > -20.0) // Max DD < 20%
            .max((a, b) -> {
                // Score = return * sharpe * winRate / abs(maxDrawdown)
                double scoreA = calculateScore(a);
                double scoreB = calculateScore(b);
                return Double.compare(scoreA, scoreB);
            });
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
        log.info("🌙 AUTO-SELECTION: Selecting top 5 shadow mode strategies...");
        
        List<StrategyStockMapping> topStrategies = mappingRepository.findAll().stream()
            .filter(m -> m.getTotalReturnPct() != null && m.getSharpeRatio() != null)
            .filter(m -> m.getTotalReturnPct() > 3.0) // At least 3% return
            .filter(m -> m.getSharpeRatio() > 0.8)
            .sorted((a, b) -> Double.compare(calculateScore(b), calculateScore(a)))
            .limit(5)
            .toList();
        
        // Clear existing shadow mode stocks
        shadowModeStockService.clearAll();
        
        // Add top 5
        for (StrategyStockMapping mapping : topStrategies) {
            shadowModeStockService.addShadowStock(
                mapping.getSymbol(), 
                mapping.getStrategyName()
            );
            log.info("🌙 Added to shadow mode: {} + {} (Score: {})", 
                mapping.getSymbol(), 
                mapping.getStrategyName(),
                String.format("%.4f", calculateScore(mapping)));
        }
        
        if (!topStrategies.isEmpty()) {
            StringBuilder msg = new StringBuilder("🌙 *Shadow Mode Strategies*\n\n");
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
}
