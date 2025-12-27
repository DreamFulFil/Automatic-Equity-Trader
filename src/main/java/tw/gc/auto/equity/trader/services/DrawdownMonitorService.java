package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;

import java.time.LocalDateTime;

/**
 * Drawdown Monitor Service
 * 
 * Monitors the Maximum Drawdown (MDD) of the active strategy and triggers
 * automatic strategy switching if MDD exceeds a critical threshold.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DrawdownMonitorService {
    
    private final ActiveStrategyService activeStrategyService;
    private final StrategyPerformanceService strategyPerformanceService;
    private final TelegramService telegramService;
    private final OrderExecutionService orderExecutionService;
    private final TradingStateService tradingStateService;
    private final PositionManager positionManager;
    private final ActiveStockService activeStockService;
    
    /**
     * Maximum allowed drawdown percentage before triggering emergency switch
     */
    private static final double MAX_DRAWDOWN_THRESHOLD = 15.0; // 15%
    
    /**
     * Monitor drawdown every 5 minutes during trading hours
     */
    @Scheduled(cron = "0 */5 * * * MON-FRI", zone = "Asia/Taipei")
    public void monitorDrawdown() {
        try {
            String activeStrategyName = activeStrategyService.getActiveStrategyName();
            
            // Calculate current drawdown for active strategy
            LocalDateTime periodStart = LocalDateTime.now().minusDays(7); // Last 7 days
            LocalDateTime periodEnd = LocalDateTime.now();
            
            StrategyPerformance performance = strategyPerformanceService.calculatePerformance(
                activeStrategyName,
                StrategyPerformance.PerformanceMode.MAIN,
                periodStart,
                periodEnd,
                null,
                activeStrategyService.getActiveStrategyParameters()
            );
            
            if (performance == null) {
                log.debug("No performance data for drawdown monitoring");
                return;
            }
            
            Double maxDrawdownPct = performance.getMaxDrawdownPct();
            if (maxDrawdownPct == null) {
                return;
            }
            
            log.debug("Current MDD for {}: {:.2f}%", activeStrategyName, maxDrawdownPct);
            
            if (maxDrawdownPct > MAX_DRAWDOWN_THRESHOLD) {
                handleDrawdownBreach(activeStrategyName, maxDrawdownPct, performance);
            }
            
        } catch (Exception e) {
            log.error("Error monitoring drawdown", e);
        }
    }
    
    /**
     * Handle drawdown breach - flatten positions and switch strategy
     */
    private void handleDrawdownBreach(
        String currentStrategy, 
        double currentDrawdown,
        StrategyPerformance currentPerformance
    ) {
        log.error("🚨 DRAWDOWN BREACH: {} MDD = {:.2f}% (threshold: {:.2f}%)", 
            currentStrategy, currentDrawdown, MAX_DRAWDOWN_THRESHOLD);
        
        // Send urgent alert
        telegramService.sendMessage(String.format(
            "🚨 EMERGENCY: DRAWDOWN BREACH\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "Strategy: %s\n" +
            "Max Drawdown: %.2f%%\n" +
            "Threshold: %.2f%%\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "⚠️ Flattening all positions...",
            currentStrategy, currentDrawdown, MAX_DRAWDOWN_THRESHOLD
        ));
        
        // Flatten all positions
        String symbol = getActiveSymbol();
        if (positionManager.getPosition(symbol) != 0) {
            orderExecutionService.flattenPosition(
                "Emergency: MDD threshold breach",
                symbol,
                tradingStateService.getTradingMode(),
                false
            );
        }
        
        // Find best alternative strategy
        StrategyPerformance bestAlternative = strategyPerformanceService.getBestPerformer(30);
        
        if (bestAlternative != null && !bestAlternative.getStrategyName().equals(currentStrategy)) {
            // Switch to best performing strategy
            activeStrategyService.switchStrategy(
                bestAlternative.getStrategyName(),
                activeStrategyService.getActiveStrategyParameters(),
                String.format("Automated switch due to MDD breach: %.2f%%", currentDrawdown),
                true, // auto-switched
                bestAlternative.getSharpeRatio(),
                bestAlternative.getMaxDrawdownPct(),
                bestAlternative.getTotalReturnPct(),
                bestAlternative.getWinRatePct()
            );
            
            telegramService.sendMessage(String.format(
                "✅ Strategy Switched\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "From: %s\n" +
                "To: %s\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "New Strategy Metrics:\n" +
                "📈 Sharpe Ratio: %.2f\n" +
                "📉 Max Drawdown: %.2f%%\n" +
                "💰 Total Return: %.2f%%\n" +
                "🎯 Win Rate: %.2f%%",
                currentStrategy,
                bestAlternative.getStrategyName(),
                bestAlternative.getSharpeRatio() != null ? bestAlternative.getSharpeRatio() : 0.0,
                bestAlternative.getMaxDrawdownPct() != null ? bestAlternative.getMaxDrawdownPct() : 0.0,
                bestAlternative.getTotalReturnPct() != null ? bestAlternative.getTotalReturnPct() : 0.0,
                bestAlternative.getWinRatePct() != null ? bestAlternative.getWinRatePct() : 0.0
            ));
            
            log.info("✅ Emergency strategy switch completed: {} → {}", 
                currentStrategy, bestAlternative.getStrategyName());
        } else {
            telegramService.sendMessage(
                "⚠️ No suitable alternative strategy found\n" +
                "Position flattened - manual intervention required"
            );
            log.warn("No suitable alternative strategy found for emergency switch");
        }
    }
    
    private String getActiveSymbol() {
        return activeStockService.getActiveSymbol(tradingStateService.getTradingMode());
    }
}
