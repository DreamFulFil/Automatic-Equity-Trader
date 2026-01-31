package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.VetoEvent;
import tw.gc.auto.equity.trader.repositories.VetoEventRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TradeRiskScorer service.
 * Tests the calibrated risk scoring system for Phase 5 implementation.
 */
@ExtendWith(MockitoExtension.class)
class TradeRiskScorerTest {

    @Mock
    private VetoEventRepository vetoEventRepository;

    private TradeRiskScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new TradeRiskScorer(vetoEventRepository);
    }

    @Nested
    @DisplayName("Risk Score Calculation Tests")
    class RiskScoreCalculationTests {

        @Test
        @DisplayName("Low risk proposal should have score below threshold")
        void lowRiskProposal_shouldNotBeVetoed() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .strategyName("Momentum")
                    .dailyPnl(500)
                    .weeklyPnl(2000)
                    .drawdownPercent(0.5)
                    .tradesToday(1)
                    .winStreak(2)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.isShouldVeto()).isFalse();
            assertThat(result.getTotalScore()).isLessThan(70);
        }

        @Test
        @DisplayName("High drawdown should increase risk score significantly")
        void highDrawdown_shouldIncreaseRiskScore() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(-3000)
                    .weeklyPnl(-5000)
                    .drawdownPercent(4.0)  // Above 3% danger threshold
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getDrawdownRisk()).isGreaterThanOrEqualTo(100);  // Over or equal to 100% of danger level
        }

        @Test
        @DisplayName("Multiple negative news headlines should trigger high news risk")
        void negativeNews_shouldIncreaseRiskScore() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of(
                        "台積電股價下跌創新低",
                        "利空消息衝擊半導體產業",
                        "US-China tension escalates"
                    ))
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getNewsRisk()).isGreaterThanOrEqualTo(75);  // 3 negative * 25 each
        }

        @Test
        @DisplayName("High volatility should increase risk score")
        void highVolatility_shouldIncreaseRiskScore() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("extreme")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getVolatilityRisk()).isEqualTo(100);
        }

        @Test
        @DisplayName("Losing streak should increase streak risk")
        void losingStreak_shouldIncreaseRiskScore() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(3)  // At danger threshold
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getStreakRisk()).isEqualTo(100);
        }

        @Test
        @DisplayName("Large position size should increase size risk")
        void largePositionSize_shouldIncreaseRiskScore() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(250)  // Over max 200 shares
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getSizeRisk()).isGreaterThanOrEqualTo(50);  // Size component at max
        }
    }

    @Nested
    @DisplayName("Signal Confidence Adjustment Tests")
    class SignalConfidenceTests {

        @Test
        @DisplayName("High confidence signal should reduce effective risk score")
        void highConfidence_shouldReduceEffectiveScore() {
            TradeRiskScorer.TradeProposal lowConfidence = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(100)
                    .dailyPnl(-1000)
                    .weeklyPnl(-2000)
                    .drawdownPercent(2.0)
                    .tradesToday(2)
                    .winStreak(0)
                    .lossStreak(1)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .signalConfidence(null)  // No confidence
                    .build();

            TradeRiskScorer.TradeProposal highConfidence = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(100)
                    .dailyPnl(-1000)
                    .weeklyPnl(-2000)
                    .drawdownPercent(2.0)
                    .tradesToday(2)
                    .winStreak(0)
                    .lossStreak(1)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .signalConfidence(0.95)  // High confidence
                    .build();

            TradeRiskScorer.RiskScoreResult lowResult = scorer.calculateRiskScore(lowConfidence);
            TradeRiskScorer.RiskScoreResult highResult = scorer.calculateRiskScore(highConfidence);

            // High confidence should have 20% lower effective score
            assertThat(highResult.getTotalScore()).isLessThan(lowResult.getTotalScore());
            assertThat(highResult.getConfidenceAdjustment()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("Low confidence signal should use stricter threshold")
        void lowConfidence_shouldUseStricterThreshold() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(100)
                    .dailyPnl(-1000)
                    .weeklyPnl(-2000)
                    .drawdownPercent(1.5)
                    .tradesToday(2)
                    .winStreak(0)
                    .lossStreak(1)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .signalConfidence(0.3)  // Low confidence
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            // Low confidence uses 60 threshold (stricter) and 1.2x adjustment
            assertThat(result.getConfidenceAdjustment()).isEqualTo(1.2);
            assertThat((Double) result.getBreakdown().get("threshold")).isEqualTo(60.0);
        }

        @Test
        @DisplayName("High confidence signal should use relaxed threshold")
        void highConfidence_shouldUseRelaxedThreshold() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(2)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .signalConfidence(0.92)  // High confidence
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            // High confidence uses 80 threshold (relaxed)
            assertThat((Double) result.getBreakdown().get("threshold")).isEqualTo(80.0);
        }
    }

    @Nested
    @DisplayName("Session Timing Tests")
    class SessionTimingTests {

        @Test
        @DisplayName("Market open should add session risk")
        void marketOpen_shouldAddRisk() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(9, 10))  // First 30 min
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getSessionRisk()).isEqualTo(10);
        }

        @Test
        @DisplayName("Market close should add session risk")
        void marketClose_shouldAddRisk() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(13, 15))  // Last 30 min
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getSessionRisk()).isEqualTo(10);
        }

        @Test
        @DisplayName("Safe trading hours should have no session risk")
        void safeHours_shouldHaveNoSessionRisk() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(11, 0))  // Mid-session
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getSessionRisk()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Strategy Maturity Tests")
    class StrategyMaturityTests {

        @Test
        @DisplayName("New strategy should add maturity risk")
        void newStrategy_shouldAddRisk() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(2)  // Brand new
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getStrategyMaturityRisk()).isEqualTo(15);
        }

        @Test
        @DisplayName("Mature strategy should have no maturity risk")
        void matureStrategy_shouldHaveNoRisk() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(0)
                    .weeklyPnl(0)
                    .drawdownPercent(0)
                    .tradesToday(1)
                    .winStreak(0)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)  // Mature
                    .newsHeadlines(List.of())
                    .build();

            TradeRiskScorer.RiskScoreResult result = scorer.calculateRiskScore(proposal);

            assertThat(result.getStrategyMaturityRisk()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Quick Risk Check Tests")
    class QuickRiskCheckTests {

        @Test
        @DisplayName("Quick check should return veto decision in map format")
        void quickCheck_shouldReturnMapFormat() {
            Map<String, Object> proposal = Map.ofEntries(
                    Map.entry("symbol", "2330"),
                    Map.entry("direction", "LONG"),
                    Map.entry("shares", 50),
                    Map.entry("strategy_name", "Momentum"),
                    Map.entry("daily_pnl", "500"),
                    Map.entry("weekly_pnl", "2000"),
                    Map.entry("drawdown_percent", "0.5"),
                    Map.entry("trades_today", "1"),
                    Map.entry("win_streak", "2"),
                    Map.entry("loss_streak", "0"),
                    Map.entry("volatility_level", "normal"),
                    Map.entry("strategy_days_active", "30")
            );

            Map<String, Object> result = scorer.quickRiskCheck(proposal);

            assertThat(result).containsKey("veto");
            assertThat(result).containsKey("reason");
            assertThat(result).containsKey("risk_score");
            assertThat(result).containsKey("breakdown");
        }

        @Test
        @DisplayName("Quick check should handle missing fields gracefully")
        void quickCheck_shouldHandleMissingFields() {
            Map<String, Object> proposal = Map.of(
                    "symbol", "2330",
                    "direction", "LONG"
            );

            Map<String, Object> result = scorer.quickRiskCheck(proposal);

            assertThat(result).containsKey("veto");
            assertThat(result).containsKey("risk_score");
        }
    }

    @Nested
    @DisplayName("Veto Recording Tests")
    class VetoRecordingTests {

        @Test
        @DisplayName("Vetoed trade should record veto event")
        void vetoedTrade_shouldRecordEvent() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(300)  // Large
                    .strategyName("Momentum")
                    .dailyPnl(-3000)  // Negative
                    .weeklyPnl(-5000)
                    .drawdownPercent(5.0)  // High
                    .tradesToday(6)  // Many
                    .winStreak(0)
                    .lossStreak(4)  // Bad streak
                    .volatilityLevel("extreme")
                    .timeOfDay(LocalTime.of(9, 5))
                    .strategyDaysActive(1)  // New
                    .newsHeadlines(List.of("下跌", "利空"))
                    .build();

            scorer.calculateRiskScore(proposal);

            verify(vetoEventRepository, times(1)).save(any(VetoEvent.class));
        }

        @Test
        @DisplayName("Approved trade should not record veto event")
        void approvedTrade_shouldNotRecordEvent() {
            TradeRiskScorer.TradeProposal proposal = TradeRiskScorer.TradeProposal.builder()
                    .symbol("2330")
                    .direction("LONG")
                    .shares(50)
                    .dailyPnl(500)
                    .weeklyPnl(2000)
                    .drawdownPercent(0.5)
                    .tradesToday(1)
                    .winStreak(2)
                    .lossStreak(0)
                    .volatilityLevel("normal")
                    .timeOfDay(LocalTime.of(10, 30))
                    .strategyDaysActive(30)
                    .newsHeadlines(List.of())
                    .build();

            scorer.calculateRiskScore(proposal);

            verify(vetoEventRepository, never()).save(any(VetoEvent.class));
        }
    }
}
