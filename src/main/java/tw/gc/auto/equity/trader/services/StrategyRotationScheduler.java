package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.StrategyLifecycleStatus;
import tw.gc.auto.equity.trader.services.StrategyEvaluationService.EvaluationResult;
import tw.gc.auto.equity.trader.services.StrategyRankingService.RankingResult;
import tw.gc.auto.equity.trader.services.StrategyRankingService.StrategyTier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy Rotation Scheduler - Automated monthly strategy lifecycle management.
 * 
 * Phase 6: Strategy Performance Culling
 * - Monthly strategy review
 * - Promote paper-only strategies if performance improves
 * - Demote live strategies if performance degrades
 * - Maximum 5 strategies active simultaneously
 * 
 * Lifecycle: CANDIDATE → PAPER_TRADING → SHADOW_MODE → LIVE → RETIRED
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyRotationScheduler {
    
    private final StrategyEvaluationService evaluationService;
    private final StrategyRankingService rankingService;
    private final TelegramService telegramService;
    
    private static final int MAX_LIVE_STRATEGIES = 5;
    private static final int PAPER_TRADING_MIN_DAYS = 60;
    private static final int PAPER_TRADING_MIN_TRADES = 30;
    
    // In-memory lifecycle tracking (consider moving to database entity in production)
    private final Map<String, StrategyLifecycleRecord> lifecycleRegistry = new HashMap<>();
    
    /**
     * Monthly strategy rotation - First day of month at 02:00 AM
     */
    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Taipei")
    @Transactional
    public void monthlyRotation() {
        log.info("🔄 Starting monthly strategy rotation...");
        
        LocalDateTime evaluationStart = LocalDateTime.now().minusMonths(1);
        
        // Get all strategy names (in production, load from database)
        List<String> allStrategies = getAllStrategyNames();
        
        // Rank strategies
        RankingResult ranking = rankingService.rankStrategies(allStrategies, evaluationStart);
        
        // Process lifecycle transitions
        RotationResult result = processLifecycleTransitions(ranking);
        
        // Send report
        String report = generateRotationReport(result);
        telegramService.sendMessage(report);
        
        log.info("✅ Monthly rotation complete: {} promotions, {} demotions, {} retirements",
            result.promotions.size(), result.demotions.size(), result.retirements.size());
    }
    
    /**
     * Process lifecycle transitions based on ranking
     */
    private RotationResult processLifecycleTransitions(RankingResult ranking) {
        RotationResult result = new RotationResult();
        
        // Count current LIVE strategies
        long currentLiveCount = lifecycleRegistry.values().stream()
            .filter(r -> r.status == StrategyLifecycleStatus.LIVE)
            .count();
        
        // Process top tier (potential promotions to LIVE)
        for (StrategyTier tier : ranking.getTopTier()) {
            StrategyLifecycleRecord record = getOrCreateRecord(tier.getStrategyName());
            
            if (record.status == StrategyLifecycleStatus.LIVE) {
                // Already live, check for demotion
                if (!tier.getStatisticallySignificant() || tier.getSharpeRatio() < 0.3) {
                    demoteStrategy(record, StrategyLifecycleStatus.SHADOW_MODE, 
                        "Poor performance: Sharpe=" + String.format("%.2f", tier.getSharpeRatio()));
                    result.demotions.add(new LifecycleChange(tier.getStrategyName(), 
                        StrategyLifecycleStatus.LIVE, StrategyLifecycleStatus.SHADOW_MODE,
                        "Demoted due to declining performance"));
                }
            } else if (record.status == StrategyLifecycleStatus.SHADOW_MODE && currentLiveCount < MAX_LIVE_STRATEGIES) {
                // Promote from shadow to live
                if (tier.getStatisticallySignificant() && tier.getSharpeRatio() > 0.8) {
                    promoteStrategy(record, StrategyLifecycleStatus.LIVE,
                        "Excellent performance: Sharpe=" + String.format("%.2f", tier.getSharpeRatio()));
                    result.promotions.add(new LifecycleChange(tier.getStrategyName(),
                        StrategyLifecycleStatus.SHADOW_MODE, StrategyLifecycleStatus.LIVE,
                        "Promoted to live trading"));
                    currentLiveCount++;
                }
            } else if (record.status == StrategyLifecycleStatus.PAPER_TRADING) {
                // Check promotion to shadow
                if (canPromoteFromPaper(record, tier)) {
                    promoteStrategy(record, StrategyLifecycleStatus.SHADOW_MODE,
                        "Completed paper trading requirements");
                    result.promotions.add(new LifecycleChange(tier.getStrategyName(),
                        StrategyLifecycleStatus.PAPER_TRADING, StrategyLifecycleStatus.SHADOW_MODE,
                        "Promoted to shadow mode"));
                }
            }
        }
        
        // Process bottom tier (potential demotions/retirements)
        for (StrategyTier tier : ranking.getBottomTier()) {
            StrategyLifecycleRecord record = getOrCreateRecord(tier.getStrategyName());
            
            if (tier.getSharpeRatio() < 0) {
                // Retire strategies with negative Sharpe
                retireStrategy(record, "Negative Sharpe ratio: " + String.format("%.2f", tier.getSharpeRatio()));
                result.retirements.add(new LifecycleChange(tier.getStrategyName(),
                    record.status, StrategyLifecycleStatus.RETIRED,
                    "Retired due to negative returns"));
            } else if (record.status == StrategyLifecycleStatus.LIVE) {
                // Demote poor performing LIVE strategies
                demoteStrategy(record, StrategyLifecycleStatus.PAPER_TRADING,
                    "Bottom tier performance");
                result.demotions.add(new LifecycleChange(tier.getStrategyName(),
                    StrategyLifecycleStatus.LIVE, StrategyLifecycleStatus.PAPER_TRADING,
                    "Demoted to paper trading"));
            } else if (record.status == StrategyLifecycleStatus.SHADOW_MODE) {
                // Demote shadow strategies to paper
                demoteStrategy(record, StrategyLifecycleStatus.PAPER_TRADING,
                    "Insufficient performance for shadow mode");
                result.demotions.add(new LifecycleChange(tier.getStrategyName(),
                    StrategyLifecycleStatus.SHADOW_MODE, StrategyLifecycleStatus.PAPER_TRADING,
                    "Demoted to paper trading"));
            }
        }
        
        return result;
    }
    
    /**
     * Check if strategy can be promoted from PAPER_TRADING to SHADOW_MODE
     */
    private boolean canPromoteFromPaper(StrategyLifecycleRecord record, StrategyTier tier) {
        if (tier.getTradeCount() < PAPER_TRADING_MIN_TRADES) {
            log.debug("Strategy {} needs {} more trades for promotion", 
                record.strategyName, PAPER_TRADING_MIN_TRADES - tier.getTradeCount());
            return false;
        }
        
        long daysInPaper = java.time.Duration.between(
            record.enteredStatusAt, LocalDateTime.now()
        ).toDays();
        
        if (daysInPaper < PAPER_TRADING_MIN_DAYS) {
            log.debug("Strategy {} needs {} more days in paper trading", 
                record.strategyName, PAPER_TRADING_MIN_DAYS - daysInPaper);
            return false;
        }
        
        return tier.getStatisticallySignificant() && tier.getSharpeRatio() > 0.5;
    }
    
    private void promoteStrategy(StrategyLifecycleRecord record, StrategyLifecycleStatus newStatus, String reason) {
        log.info("⬆️ Promoting {} from {} to {}: {}", 
            record.strategyName, record.status, newStatus, reason);
        record.previousStatus = record.status;
        record.status = newStatus;
        record.enteredStatusAt = LocalDateTime.now();
        record.statusReason = reason;
    }
    
    private void demoteStrategy(StrategyLifecycleRecord record, StrategyLifecycleStatus newStatus, String reason) {
        log.info("⬇️ Demoting {} from {} to {}: {}", 
            record.strategyName, record.status, newStatus, reason);
        record.previousStatus = record.status;
        record.status = newStatus;
        record.enteredStatusAt = LocalDateTime.now();
        record.statusReason = reason;
    }
    
    private void retireStrategy(StrategyLifecycleRecord record, String reason) {
        log.info("🔴 Retiring {}: {}", record.strategyName, reason);
        record.previousStatus = record.status;
        record.status = StrategyLifecycleStatus.RETIRED;
        record.enteredStatusAt = LocalDateTime.now();
        record.statusReason = reason;
    }
    
    private StrategyLifecycleRecord getOrCreateRecord(String strategyName) {
        return lifecycleRegistry.computeIfAbsent(strategyName, name -> {
            StrategyLifecycleRecord record = new StrategyLifecycleRecord();
            record.strategyName = name;
            record.status = StrategyLifecycleStatus.CANDIDATE;
            record.enteredStatusAt = LocalDateTime.now();
            record.statusReason = "Initial registration";
            return record;
        });
    }
    
    /**
     * Get all strategy names (stub - implement based on your strategy registry)
     */
    private List<String> getAllStrategyNames() {
        // TODO: Load from database or strategy factory
        // For now, return strategies that have lifecycle records
        return new ArrayList<>(lifecycleRegistry.keySet());
    }
    
    private String generateRotationReport(RotationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 MONTHLY STRATEGY ROTATION REPORT\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        // Summary
        sb.append("📈 Promotions: ").append(result.promotions.size()).append("\n");
        sb.append("📉 Demotions: ").append(result.demotions.size()).append("\n");
        sb.append("🔴 Retirements: ").append(result.retirements.size()).append("\n\n");
        
        // Promotions
        if (!result.promotions.isEmpty()) {
            sb.append("✅ PROMOTIONS\n");
            for (LifecycleChange change : result.promotions) {
                sb.append(String.format("  • %s: %s → %s\n    %s\n",
                    change.strategyName, change.fromStatus, change.toStatus, change.reason));
            }
            sb.append("\n");
        }
        
        // Demotions
        if (!result.demotions.isEmpty()) {
            sb.append("⚠️ DEMOTIONS\n");
            for (LifecycleChange change : result.demotions) {
                sb.append(String.format("  • %s: %s → %s\n    %s\n",
                    change.strategyName, change.fromStatus, change.toStatus, change.reason));
            }
            sb.append("\n");
        }
        
        // Retirements
        if (!result.retirements.isEmpty()) {
            sb.append("❌ RETIREMENTS\n");
            for (LifecycleChange change : result.retirements) {
                sb.append(String.format("  • %s: %s\n",
                    change.strategyName, change.reason));
            }
            sb.append("\n");
        }
        
        // Current live strategies
        long liveCount = lifecycleRegistry.values().stream()
            .filter(r -> r.status == StrategyLifecycleStatus.LIVE)
            .count();
        sb.append("🎯 Current LIVE strategies: ").append(liveCount).append("/").append(MAX_LIVE_STRATEGIES).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Get current lifecycle status for a strategy
     */
    public StrategyLifecycleStatus getStatus(String strategyName) {
        StrategyLifecycleRecord record = lifecycleRegistry.get(strategyName);
        return record != null ? record.status : StrategyLifecycleStatus.CANDIDATE;
    }
    
    /**
     * Manually set strategy lifecycle status (for admin/testing)
     */
    public void setStatus(String strategyName, StrategyLifecycleStatus status, String reason) {
        StrategyLifecycleRecord record = getOrCreateRecord(strategyName);
        record.previousStatus = record.status;
        record.status = status;
        record.enteredStatusAt = LocalDateTime.now();
        record.statusReason = reason;
        log.info("Manual status change: {} → {} ({})", strategyName, status, reason);
    }
    
    /**
     * Internal lifecycle record
     */
    private static class StrategyLifecycleRecord {
        String strategyName;
        StrategyLifecycleStatus status;
        StrategyLifecycleStatus previousStatus;
        LocalDateTime enteredStatusAt;
        String statusReason;
    }
    
    private record LifecycleChange(
        String strategyName,
        StrategyLifecycleStatus fromStatus,
        StrategyLifecycleStatus toStatus,
        String reason
    ) {}
    
    private static class RotationResult {
        List<LifecycleChange> promotions = new ArrayList<>();
        List<LifecycleChange> demotions = new ArrayList<>();
        List<LifecycleChange> retirements = new ArrayList<>();
    }
}
