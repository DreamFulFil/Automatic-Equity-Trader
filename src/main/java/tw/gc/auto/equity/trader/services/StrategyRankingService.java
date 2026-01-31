package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.services.StrategyEvaluationService.EvaluationResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Strategy Ranking Service - Rank strategies by risk-adjusted returns.
 * 
 * Phase 6: Strategy Performance Culling
 * - Rank strategies by risk-adjusted returns
 * - Top 10-20 strategies for live trading
 * - Bottom 20% moved to paper trading only
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyRankingService {
    
    private final StrategyEvaluationService evaluationService;
    
    private static final int TOP_TIER_SIZE = 20;
    private static final double BOTTOM_TIER_PERCENTILE = 0.20; // Bottom 20%
    
    /**
     * Rank all strategies and categorize them into tiers.
     * 
     * @param strategyNames List of all strategy names to evaluate
     * @param evaluationPeriodStart Start date for evaluation period
     * @return Ranking result with tiered strategy lists
     */
    public RankingResult rankStrategies(List<String> strategyNames, LocalDateTime evaluationPeriodStart) {
        log.info("Ranking {} strategies since {}", strategyNames.size(), evaluationPeriodStart);
        
        // Evaluate all strategies
        List<EvaluationResult> evaluations = new ArrayList<>();
        for (String strategyName : strategyNames) {
            try {
                EvaluationResult eval = evaluationService.evaluateStrategy(strategyName, evaluationPeriodStart);
                if (eval != null && eval.isEligible()) {
                    evaluations.add(eval);
                }
            } catch (Exception e) {
                log.error("Failed to evaluate strategy {}: {}", strategyName, e.getMessage());
            }
        }
        
        if (evaluations.isEmpty()) {
            log.warn("No eligible strategies found for ranking");
            return RankingResult.builder()
                .totalEvaluated(0)
                .topTier(List.of())
                .middleTier(List.of())
                .bottomTier(List.of())
                .rankedAt(LocalDateTime.now())
                .build();
        }
        
        // Sort by composite score: Sharpe ratio (primary), statistical significance (secondary)
        evaluations.sort(Comparator
            .comparingDouble((EvaluationResult e) -> e.getSharpeRatio() != null ? e.getSharpeRatio() : -999.0)
            .reversed()
            .thenComparing(e -> e.getStatisticallySignificant() != null && e.getStatisticallySignificant())
            .thenComparingDouble(e -> e.getMeanReturn() != null ? e.getMeanReturn() : -999.0));
        
        // Categorize into tiers
        int totalStrategies = evaluations.size();
        int topTierSize = Math.min(TOP_TIER_SIZE, totalStrategies);
        int bottomTierSize = (int) Math.ceil(totalStrategies * BOTTOM_TIER_PERCENTILE);
        
        List<StrategyTier> topTier = new ArrayList<>();
        List<StrategyTier> middleTier = new ArrayList<>();
        List<StrategyTier> bottomTier = new ArrayList<>();
        
        for (int i = 0; i < evaluations.size(); i++) {
            EvaluationResult eval = evaluations.get(i);
            int rank = i + 1;
            
            StrategyTier tier = StrategyTier.builder()
                .strategyName(eval.getStrategyName())
                .rank(rank)
                .sharpeRatio(eval.getSharpeRatio())
                .meanReturn(eval.getMeanReturn())
                .statisticallySignificant(eval.getStatisticallySignificant())
                .tradeCount(eval.getTradeCount())
                .maxDrawdownPct(eval.getMaxDrawdownPct())
                .recommendation(determineRecommendation(rank, totalStrategies, topTierSize, bottomTierSize, eval))
                .build();
            
            if (i < topTierSize) {
                topTier.add(tier);
            } else if (i >= totalStrategies - bottomTierSize) {
                bottomTier.add(tier);
            } else {
                middleTier.add(tier);
            }
        }
        
        log.info("Ranking complete: Top={}, Middle={}, Bottom={}", 
            topTier.size(), middleTier.size(), bottomTier.size());
        
        return RankingResult.builder()
            .totalEvaluated(totalStrategies)
            .topTier(topTier)
            .middleTier(middleTier)
            .bottomTier(bottomTier)
            .rankedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Determine recommendation based on tier and performance metrics
     */
    private String determineRecommendation(int rank, int totalStrategies, int topTierSize, 
                                           int bottomTierSize, EvaluationResult eval) {
        if (rank <= topTierSize) {
            if (eval.getStatisticallySignificant() && eval.getSharpeRatio() > 1.0) {
                return "LIVE - Excellent risk-adjusted returns with statistical significance";
            } else if (eval.getSharpeRatio() > 0.5) {
                return "LIVE - Good risk-adjusted returns, continue monitoring";
            } else {
                return "SHADOW - Promote to top tier but needs improvement";
            }
        } else if (rank >= totalStrategies - bottomTierSize) {
            if (!eval.getStatisticallySignificant()) {
                return "PAPER - No statistical significance, demote to paper trading";
            } else if (eval.getSharpeRatio() < 0) {
                return "RETIRED - Negative returns, consider retirement";
            } else {
                return "PAPER - Weak performance, demote to paper trading";
            }
        } else {
            if (eval.getStatisticallySignificant() && eval.getSharpeRatio() > 0.5) {
                return "SHADOW - Monitor for promotion to live trading";
            } else {
                return "SHADOW - Continue monitoring performance";
            }
        }
    }
    
    /**
     * Strategy tier with ranking information
     */
    @lombok.Builder
    @lombok.Data
    public static class StrategyTier {
        private String strategyName;
        private int rank;
        private Double sharpeRatio;
        private Double meanReturn;
        private Boolean statisticallySignificant;
        private Integer tradeCount;
        private Double maxDrawdownPct;
        private String recommendation;
    }
    
    /**
     * Result of strategy ranking
     */
    @lombok.Builder
    @lombok.Data
    public static class RankingResult {
        private int totalEvaluated;
        private List<StrategyTier> topTier;
        private List<StrategyTier> middleTier;
        private List<StrategyTier> bottomTier;
        private LocalDateTime rankedAt;
        
        /**
         * Get all strategies recommended for LIVE trading
         */
        public List<StrategyTier> getLiveRecommendations() {
            return topTier.stream()
                .filter(t -> t.getRecommendation().startsWith("LIVE"))
                .toList();
        }
        
        /**
         * Get all strategies recommended for PAPER trading (demotion)
         */
        public List<StrategyTier> getPaperRecommendations() {
            return bottomTier.stream()
                .filter(t -> t.getRecommendation().startsWith("PAPER"))
                .toList();
        }
        
        /**
         * Get all strategies recommended for RETIREMENT
         */
        public List<StrategyTier> getRetirementRecommendations() {
            return bottomTier.stream()
                .filter(t -> t.getRecommendation().startsWith("RETIRED"))
                .toList();
        }
    }
}
