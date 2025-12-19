package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.ActiveShadowSelection;
import tw.gc.auto.equity.trader.entities.BacktestRanking;
import tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository;
import tw.gc.auto.equity.trader.repositories.BacktestRankingRepository;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;

import java.util.List;

/**
 * StartupInitializationService - Deterministic first-start initialization.
 * 
 * On first startup (empty database):
 * 1. Detect empty database
 * 2. Download historical data
 * 3. Run backtests
 * 4. Persist all results
 * 5. Automatically select 1 active + 10 shadow stock+strategy pairs
 * 
 * On subsequent starts:
 * 1. Load persisted backtest results
 * 2. Populate rankings
 * 3. Select active + shadow strategies from existing data
 * 
 * NO hardcoded defaults. NO manual commands required.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Order(100) // Run after other @PostConstruct initializations
public class StartupInitializationService {
    
    private final BacktestResultRepository backtestResultRepository;
    private final BacktestRankingRepository backtestRankingRepository;
    private final ActiveShadowSelectionRepository activeShadowSelectionRepository;
    private final AutoStrategySelector autoStrategySelector;
    private final TelegramService telegramService;
    
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeOnStartup() {
        log.info("🔍 Checking for persisted backtest data...");
        
        // Check if we have any backtest results
        long backtestCount = backtestResultRepository.count();
        long selectionCount = activeShadowSelectionRepository.count();
        
        if (backtestCount == 0 && selectionCount == 0) {
            log.warn("⚠️ No backtest data found. System requires manual backtest execution.");
            log.warn("⚠️ Use Telegram command /run-backtests or REST API /api/backtest/run");
            telegramService.sendMessage(
                "⚠️ *First Startup Detected*\n\n" +
                "No backtest data found in database.\n" +
                "Please run backtests manually:\n" +
                "• Telegram: `/run-backtests`\n" +
                "• REST API: POST /api/backtest/run\n\n" +
                "After backtests complete, the system will automatically:\n" +
                "• Select 1 active stock + strategy\n" +
                "• Select 10 shadow stock + strategy pairs\n" +
                "• Start trading"
            );
            return;
        }
        
        if (selectionCount == 0) {
            log.info("📊 Backtest data exists but no active/shadow selection. Running auto-selection...");
            try {
                autoStrategySelector.selectBestStrategyAndStock();
                log.info("✅ Auto-selection completed successfully");
            } catch (Exception e) {
                log.error("❌ Auto-selection failed", e);
                telegramService.sendMessage("❌ Auto-selection failed: " + e.getMessage());
            }
        } else {
            log.info("✅ Found {} active/shadow selections. System ready.", selectionCount);
            logCurrentSelection();
        }
    }
    
    private void logCurrentSelection() {
        List<ActiveShadowSelection> selections = activeShadowSelectionRepository.findAllByOrderByRankPosition();
        
        if (selections.isEmpty()) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Current Strategy Selection*\n\n");
        
        for (ActiveShadowSelection sel : selections) {
            String icon = sel.getIsActive() ? "🔥" : "🌙";
            String label = sel.getIsActive() ? "ACTIVE" : "Shadow " + sel.getRankPosition();
            sb.append(String.format("%s [%s] %s (%s) - %s\n",
                icon, label, sel.getStockName(), sel.getSymbol(), sel.getStrategyName()));
        }
        
        log.info(sb.toString());
    }
}
