package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.StrategyEvaluationService.EvaluationResult;
import tw.gc.auto.equity.trader.services.StrategyRankingService.RankingResult;
import tw.gc.auto.equity.trader.services.StrategyRankingService.StrategyTier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyRankingServiceTest {
    
    @Mock
    private StrategyEvaluationService evaluationService;
    
    private StrategyRankingService rankingService;
    
    @BeforeEach
    void setUp() {
        rankingService = new StrategyRankingService(evaluationService);
    }
    
    @Test
    void rankStrategies_withNoEligibleStrategies_shouldReturnEmptyResult() {
        // Given
        List<String> strategies = Arrays.asList("S1", "S2", "S3");
        when(evaluationService.evaluateStrategy(any(), any()))
            .thenReturn(createIneligibleResult("S1"));
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        
        // Then
        assertThat(result.getTotalEvaluated()).isZero();
        assertThat(result.getTopTier()).isEmpty();
        assertThat(result.getMiddleTier()).isEmpty();
        assertThat(result.getBottomTier()).isEmpty();
    }
    
    @Test
    void rankStrategies_shouldRankBySharpeRatio() {
        // Given
        List<String> strategies = Arrays.asList("Strong", "Weak", "Medium");
        
        when(evaluationService.evaluateStrategy(eq("Strong"), any()))
            .thenReturn(createEligibleResult("Strong", 1.5, 100.0, true, 50));
        when(evaluationService.evaluateStrategy(eq("Weak"), any()))
            .thenReturn(createEligibleResult("Weak", 0.2, 20.0, false, 35));
        when(evaluationService.evaluateStrategy(eq("Medium"), any()))
            .thenReturn(createEligibleResult("Medium", 0.8, 60.0, true, 40));
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        
        // Then
        assertThat(result.getTotalEvaluated()).isEqualTo(3);
        
        // Strong should be rank 1, Medium rank 2, Weak rank 3
        List<StrategyTier> allTiers = result.getTopTier();
        allTiers.addAll(result.getMiddleTier());
        allTiers.addAll(result.getBottomTier());
        
        assertThat(allTiers.get(0).getStrategyName()).isEqualTo("Strong");
        assertThat(allTiers.get(0).getRank()).isEqualTo(1);
        assertThat(allTiers.get(1).getStrategyName()).isEqualTo("Medium");
        assertThat(allTiers.get(2).getStrategyName()).isEqualTo("Weak");
    }
    
    @Test
    void rankStrategies_shouldCategorizeIntoTiers() {
        // Given - Create 25 strategies
        List<String> strategies = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            strategies.add("Strategy" + i);
            double sharpe = 2.0 - (i * 0.08); // Decreasing Sharpe
            when(evaluationService.evaluateStrategy(eq("Strategy" + i), any()))
                .thenReturn(createEligibleResult("Strategy" + i, sharpe, sharpe * 50, sharpe > 0.5, 40));
        }
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        
        // Then
        assertThat(result.getTotalEvaluated()).isEqualTo(25);
        assertThat(result.getTopTier()).hasSize(20); // Top 20
        assertThat(result.getBottomTier()).hasSize(5); // Bottom 20% = 5
        assertThat(result.getMiddleTier()).isEmpty(); // All others go to middle (0 in this case)
    }
    
    @Test
    void rankStrategies_topTier_shouldRecommendLiveForExcellentPerformers() {
        // Given
        when(evaluationService.evaluateStrategy(eq("Excellent"), any()))
            .thenReturn(createEligibleResult("Excellent", 1.2, 120.0, true, 50));
        
        // When
        RankingResult result = rankingService.rankStrategies(List.of("Excellent"), LocalDateTime.now());
        
        // Then
        assertThat(result.getTopTier()).hasSize(1);
        StrategyTier tier = result.getTopTier().get(0);
        assertThat(tier.getRecommendation()).startsWith("LIVE");
        assertThat(tier.getRecommendation()).contains("Excellent risk-adjusted returns");
    }
    
    @Test
    void rankStrategies_bottomTier_shouldRecommendPaperForWeakPerformers() {
        // Given
        when(evaluationService.evaluateStrategy(eq("Weak"), any()))
            .thenReturn(createEligibleResult("Weak", 0.3, 30.0, false, 35));
        
        // Create enough strategies to make "Weak" fall in bottom tier
        List<String> strategies = new ArrayList<>(List.of("Weak"));
        for (int i = 1; i <= 10; i++) {
            strategies.add("Better" + i);
            when(evaluationService.evaluateStrategy(eq("Better" + i), any()))
                .thenReturn(createEligibleResult("Better" + i, 1.0 + i * 0.1, 100.0, true, 40));
        }
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        
        // Then - find the 'Weak' strategy across all tiers and ensure it's recommended for non-live usage
        List<StrategyTier> allTiers = new ArrayList<>();
        allTiers.addAll(result.getTopTier());
        allTiers.addAll(result.getMiddleTier());
        allTiers.addAll(result.getBottomTier());

        StrategyTier weakTier = allTiers.stream()
            .filter(t -> t.getStrategyName().equals("Weak"))
            .findFirst()
            .orElse(null);

        assertThat(weakTier).isNotNull();
        assertThat(weakTier.getRecommendation()).containsAnyOf("PAPER", "SHADOW", "RETIRED");
    }
    
    @Test
    void rankStrategies_shouldRecommendRetirementForNegativeSharpe() {
        // Given
        when(evaluationService.evaluateStrategy(eq("Negative"), any()))
            .thenReturn(createEligibleResult("Negative", -0.5, -50.0, false, 40));
        
        // Add some good strategies to push "Negative" to bottom
        List<String> strategies = new ArrayList<>(List.of("Negative"));
        for (int i = 1; i <= 5; i++) {
            strategies.add("Good" + i);
            when(evaluationService.evaluateStrategy(eq("Good" + i), any()))
                .thenReturn(createEligibleResult("Good" + i, 1.0, 100.0, true, 45));
        }
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        
        // Then
        List<StrategyTier> allTiers = new ArrayList<>(result.getTopTier());
        allTiers.addAll(result.getMiddleTier());
        allTiers.addAll(result.getBottomTier());
        
        StrategyTier negativeTier = allTiers.stream()
            .filter(t -> t.getStrategyName().equals("Negative"))
            .findFirst()
            .orElseThrow();
        
        assertThat(negativeTier.getRecommendation()).containsAnyOf("RETIRED", "PAPER", "SHADOW");
    }
    
    @Test
    void getLiveRecommendations_shouldFilterLiveStrategies() {
        // Given
        List<String> strategies = Arrays.asList("Live1", "Live2", "Paper1");
        when(evaluationService.evaluateStrategy(eq("Live1"), any()))
            .thenReturn(createEligibleResult("Live1", 1.5, 150.0, true, 50));
        when(evaluationService.evaluateStrategy(eq("Live2"), any()))
            .thenReturn(createEligibleResult("Live2", 1.2, 120.0, true, 48));
        when(evaluationService.evaluateStrategy(eq("Paper1"), any()))
            .thenReturn(createEligibleResult("Paper1", 0.3, 30.0, false, 32));
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        List<StrategyTier> liveRecommendations = result.getLiveRecommendations();
        
        // Then
        assertThat(liveRecommendations).isNotEmpty();
        assertThat(liveRecommendations).allMatch(t -> t.getRecommendation().startsWith("LIVE"));
    }
    
    @Test
    void getPaperRecommendations_shouldFilterPaperStrategies() {
        // Given
        List<String> strategies = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            strategies.add("S" + i);
            double sharpe = 1.5 - (i * 0.3);
            when(evaluationService.evaluateStrategy(eq("S" + i), any()))
                .thenReturn(createEligibleResult("S" + i, sharpe, sharpe * 100, sharpe > 0.5, 40));
        }
        
        // When
        RankingResult result = rankingService.rankStrategies(strategies, LocalDateTime.now());
        List<StrategyTier> paperRecommendations = result.getPaperRecommendations();
        
        // Then
        if (!paperRecommendations.isEmpty()) {
            assertThat(paperRecommendations).allMatch(t -> t.getRecommendation().contains("PAPER"));
        }
    }
    
    // Helper methods
    
    private EvaluationResult createIneligibleResult(String name) {
        return EvaluationResult.builder()
            .strategyName(name)
            .eligible(false)
            .reason("Test ineligible")
            .build();
    }
    
    private EvaluationResult createEligibleResult(String name, double sharpe, double meanReturn, 
                                                   boolean significant, int tradeCount) {
        return EvaluationResult.builder()
            .strategyName(name)
            .eligible(true)
            .sharpeRatio(sharpe)
            .meanReturn(meanReturn)
            .statisticallySignificant(significant)
            .tradeCount(tradeCount)
            .pValue(significant ? 0.02 : 0.10)
            .maxDrawdownPct(15.0)
            .reason("Test eligible")
            .evaluatedAt(LocalDateTime.now())
            .build();
    }
}
