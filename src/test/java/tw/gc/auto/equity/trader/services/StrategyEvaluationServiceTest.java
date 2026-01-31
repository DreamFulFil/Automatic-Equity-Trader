package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;
import tw.gc.auto.equity.trader.services.StrategyEvaluationService.EvaluationResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyEvaluationServiceTest {
    
    @Mock
    private TradeRepository tradeRepository;
    
    @Mock
    private StrategyPerformanceRepository performanceRepository;
    
    private StrategyEvaluationService evaluationService;
    
    @BeforeEach
    void setUp() {
        evaluationService = new StrategyEvaluationService(tradeRepository, performanceRepository);
    }
    
    @Test
    void evaluateStrategy_withInsufficientTrades_shouldReturnIneligible() {
        // Given
        String strategyName = "TestStrategy";
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        
        List<Trade> trades = createTrades(20); // Less than 30
        when(tradeRepository.findByStrategyNameAndTimestampAfter(eq(strategyName), any()))
            .thenReturn(trades);
        
        // When
        EvaluationResult result = evaluationService.evaluateStrategy(strategyName, since);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).contains("Insufficient trades");
    }
    
    @Test
    void evaluateStrategy_withSufficientTrades_shouldCalculateMetrics() {
        // Given
        String strategyName = "ProfitableStrategy";
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        
        List<Trade> trades = createProfitableTrades(50);
        when(tradeRepository.findByStrategyNameAndTimestampAfter(eq(strategyName), any()))
            .thenReturn(trades);
        when(performanceRepository.findByStrategyNameOrderByPeriodEndDesc(strategyName))
            .thenReturn(List.of());
        
        // When
        EvaluationResult result = evaluationService.evaluateStrategy(strategyName, since);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getTradeCount()).isEqualTo(50);
        assertThat(result.getSharpeRatio()).isNotNull();
        assertThat(result.getMeanReturn()).isGreaterThan(0);
        assertThat(result.getStatisticallySignificant()).isNotNull();
    }
    
    @Test
    void evaluateStrategy_withPositiveReturns_shouldBeStatisticallySignificant() {
        // Given
        String strategyName = "StrongStrategy";
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        
        List<Trade> trades = createConsistentlyProfitableTrades(40);
        when(tradeRepository.findByStrategyNameAndTimestampAfter(eq(strategyName), any()))
            .thenReturn(trades);
        when(performanceRepository.findByStrategyNameOrderByPeriodEndDesc(strategyName))
            .thenReturn(List.of());
        
        // When
        EvaluationResult result = evaluationService.evaluateStrategy(strategyName, since);
        
        // Then
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getStatisticallySignificant()).isTrue();
        assertThat(result.getSharpeRatio()).isGreaterThan(0.5);
        assertThat(result.getReason()).contains("Statistically significant");
    }
    
    @Test
    void evaluateStrategy_withNegativeReturns_shouldHaveNegativeSharpe() {
        // Given
        String strategyName = "LosingStrategy";
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        
        List<Trade> trades = createLosingTrades(35);
        when(tradeRepository.findByStrategyNameAndTimestampAfter(eq(strategyName), any()))
            .thenReturn(trades);
        when(performanceRepository.findByStrategyNameOrderByPeriodEndDesc(strategyName))
            .thenReturn(List.of());
        
        // When
        EvaluationResult result = evaluationService.evaluateStrategy(strategyName, since);
        
        // Then
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getSharpeRatio()).isLessThan(0);
        assertThat(result.getMeanReturn()).isLessThan(0);
        assertThat(result.getReason()).contains("Negative risk-adjusted returns");
    }
    
    @Test
    void evaluateStrategy_withMixedReturns_shouldCalculateCorrectly() {
        // Given
        String strategyName = "MixedStrategy";
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        
        List<Trade> trades = createMixedTrades(50);
        when(tradeRepository.findByStrategyNameAndTimestampAfter(eq(strategyName), any()))
            .thenReturn(trades);
        
        StrategyPerformance mockPerf = StrategyPerformance.builder()
            .maxDrawdownPct(15.5)
            .build();
        when(performanceRepository.findByStrategyNameOrderByPeriodEndDesc(strategyName))
            .thenReturn(List.of(mockPerf));
        
        // When
        EvaluationResult result = evaluationService.evaluateStrategy(strategyName, since);
        
        // Then
        assertThat(result.isEligible()).isTrue();
        assertThat(result.getTradeCount()).isEqualTo(50);
        assertThat(result.getMaxDrawdownPct()).isEqualTo(15.5);
        assertThat(result.getTStatistic()).isNotNull();
        assertThat(result.getPValue()).isNotNull();
    }
    
    @Test
    void evaluateStrategy_withNoRealizedPnL_shouldReturnIneligible() {
        // Given
        String strategyName = "NoDataStrategy";
        LocalDateTime since = LocalDateTime.now().minusMonths(1);
        
        List<Trade> trades = createTradesWithoutPnL(40);
        when(tradeRepository.findByStrategyNameAndTimestampAfter(eq(strategyName), any()))
            .thenReturn(trades);
        
        // When
        EvaluationResult result = evaluationService.evaluateStrategy(strategyName, since);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReason()).contains("No realized P&L data");
    }
    
    // Helper methods
    
    private List<Trade> createTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(Trade.builder()
                .id((long) i)
                .symbol("TEST")
                .quantity(100)
                .realizedPnL(Math.random() * 200 - 100)
                .timestamp(LocalDateTime.now().minusDays(i))
                .build());
        }
        return trades;
    }
    
    private List<Trade> createProfitableTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(Trade.builder()
                .id((long) i)
                .symbol("TEST")
                .quantity(100)
                .realizedPnL(50.0 + Math.random() * 50) // 50-100 TWD profit
                .timestamp(LocalDateTime.now().minusDays(i))
                .build());
        }
        return trades;
    }
    
    private List<Trade> createConsistentlyProfitableTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 80% win rate
            double pnl = (i % 5 == 0) ? -30.0 : (80.0 + Math.random() * 40);
            trades.add(Trade.builder()
                .id((long) i)
                .symbol("TEST")
                .quantity(100)
                .realizedPnL(pnl)
                .timestamp(LocalDateTime.now().minusDays(i))
                .build());
        }
        return trades;
    }
    
    private List<Trade> createLosingTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(Trade.builder()
                .id((long) i)
                .symbol("TEST")
                .quantity(100)
                .realizedPnL(-80.0 - Math.random() * 40) // -80 to -120 TWD loss
                .timestamp(LocalDateTime.now().minusDays(i))
                .build());
        }
        return trades;
    }
    
    private List<Trade> createMixedTrades(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 50% win rate
            double pnl = (i % 2 == 0) ? 60.0 : -50.0;
            trades.add(Trade.builder()
                .id((long) i)
                .symbol("TEST")
                .quantity(100)
                .realizedPnL(pnl)
                .timestamp(LocalDateTime.now().minusDays(i))
                .build());
        }
        return trades;
    }
    
    private List<Trade> createTradesWithoutPnL(int count) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            trades.add(Trade.builder()
                .id((long) i)
                .symbol("TEST")
                .quantity(100)
                .realizedPnL(null) // No P&L data
                .timestamp(LocalDateTime.now().minusDays(i))
                .build());
        }
        return trades;
    }
}
