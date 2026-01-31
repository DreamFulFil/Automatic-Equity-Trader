package tw.gc.auto.equity.trader.services.positionsizing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CorrelationTracker.
 */
@DisplayName("CorrelationTracker")
class CorrelationTrackerTest {

    private CorrelationTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CorrelationTracker();
    }

    @Nested
    @DisplayName("Correlation Calculation")
    class CorrelationCalculation {

        @Test
        @DisplayName("should calculate perfect positive correlation")
        void shouldCalculatePerfectPositiveCorrelation() {
            double[] returns1 = {0.01, 0.02, -0.01, 0.03, -0.02, 0.01, 0.02, -0.01, 0.03, -0.02};
            double[] returns2 = {0.01, 0.02, -0.01, 0.03, -0.02, 0.01, 0.02, -0.01, 0.03, -0.02};

            double correlation = tracker.calculateCorrelation(returns1, returns2);

            assertThat(correlation).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("should calculate perfect negative correlation")
        void shouldCalculatePerfectNegativeCorrelation() {
            double[] returns1 = {0.01, 0.02, -0.01, 0.03, -0.02, 0.01, 0.02, -0.01, 0.03, -0.02};
            double[] returns2 = {-0.01, -0.02, 0.01, -0.03, 0.02, -0.01, -0.02, 0.01, -0.03, 0.02};

            double correlation = tracker.calculateCorrelation(returns1, returns2);

            assertThat(correlation).isCloseTo(-1.0, within(0.001));
        }

        @Test
        @DisplayName("should calculate zero correlation for uncorrelated series")
        void shouldCalculateZeroCorrelationForUncorrelated() {
            double[] returns1 = {0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.01, -0.01, 0.01, -0.01};
            double[] returns2 = {0.01, 0.01, -0.01, -0.01, 0.01, 0.01, -0.01, -0.01, 0.01, 0.01};

            double correlation = tracker.calculateCorrelation(returns1, returns2);

            // Should be close to 0 for uncorrelated
            assertThat(Math.abs(correlation)).isLessThan(0.5);
        }

        @Test
        @DisplayName("should return 0 for insufficient data")
        void shouldReturnZeroForInsufficientData() {
            double[] returns1 = {0.01, 0.02, 0.03};
            double[] returns2 = {0.01, 0.02, 0.03};

            double correlation = tracker.calculateCorrelation(returns1, returns2);

            assertThat(correlation).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should return 0 for null inputs")
        void shouldReturnZeroForNullInputs() {
            assertThat(tracker.calculateCorrelation(null, null)).isEqualTo(0.0);
            assertThat(tracker.calculateCorrelation(new double[]{0.01}, null)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Returns Calculation")
    class ReturnsCalculation {

        @Test
        @DisplayName("should calculate returns from market data")
        void shouldCalculateReturnsFromMarketData() {
            List<MarketData> history = createMarketDataWithPrices(
                    List.of(100.0, 102.0, 101.0, 105.0)
            );

            double[] returns = tracker.calculateReturns(history);

            assertThat(returns).hasSize(3);
            assertThat(returns[0]).isCloseTo(0.02, within(0.001)); // 100 -> 102
            assertThat(returns[1]).isCloseTo(-0.0098, within(0.001)); // 102 -> 101
            assertThat(returns[2]).isCloseTo(0.0396, within(0.001)); // 101 -> 105
        }

        @Test
        @DisplayName("should return empty array for insufficient data")
        void shouldReturnEmptyForInsufficientData() {
            List<MarketData> history = createMarketDataWithPrices(List.of(100.0));

            double[] returns = tracker.calculateReturns(history);

            assertThat(returns).isEmpty();
        }
    }

    @Nested
    @DisplayName("Correlation Caching")
    class CorrelationCaching {

        @Test
        @DisplayName("should cache correlation estimate")
        void shouldCacheCorrelationEstimate() {
            List<MarketData> history1 = createCorrelatedHistory("A", 30, 0.01);
            List<MarketData> history2 = createCorrelatedHistory("B", 30, 0.01);

            tracker.calculateCorrelation(history1, history2, "A", "B");

            var cached = tracker.getCachedCorrelation("A", "B");
            assertThat(cached).isPresent();

            // Should also be cached in reverse
            var cachedReverse = tracker.getCachedCorrelation("B", "A");
            assertThat(cachedReverse).isPresent();
        }

        @Test
        @DisplayName("should clear cache")
        void shouldClearCache() {
            List<MarketData> history1 = createCorrelatedHistory("A", 30, 0.01);
            List<MarketData> history2 = createCorrelatedHistory("B", 30, 0.01);
            tracker.calculateCorrelation(history1, history2, "A", "B");

            tracker.clearCache();

            assertThat(tracker.getCachedCorrelation("A", "B")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Portfolio Analysis")
    class PortfolioAnalysis {

        @Test
        @DisplayName("should detect single position concentration")
        void shouldDetectSinglePositionConcentration() {
            List<CorrelationTracker.PositionInfo> positions = List.of(
                    new CorrelationTracker.PositionInfo("A", 0.30, "Tech"),
                    new CorrelationTracker.PositionInfo("B", 0.70, "Tech")
            );

            var analysis = tracker.analyzePortfolio(positions, Map.of());

            assertThat(analysis.concentrationWarnings())
                    .anyMatch(w -> w.contains("Position B") && w.contains("exceeds max"));
        }

        @Test
        @DisplayName("should detect sector concentration")
        void shouldDetectSectorConcentration() {
            List<CorrelationTracker.PositionInfo> positions = List.of(
                    new CorrelationTracker.PositionInfo("A", 0.25, "Tech"),
                    new CorrelationTracker.PositionInfo("B", 0.25, "Tech")
            );

            var analysis = tracker.analyzePortfolio(positions, Map.of());

            assertThat(analysis.sectorConcentrations().get("Tech")).isEqualTo(0.50);
            assertThat(analysis.concentrationWarnings())
                    .anyMatch(w -> w.contains("Sector Tech") && w.contains("exceeds max"));
        }

        @Test
        @DisplayName("should detect highly correlated pairs")
        void shouldDetectHighlyCorrelatedPairs() {
            List<CorrelationTracker.PositionInfo> positions = List.of(
                    new CorrelationTracker.PositionInfo("A", 0.20, "Tech"),
                    new CorrelationTracker.PositionInfo("B", 0.20, "Tech")
            );

            Map<String, Double> correlations = Map.of(
                    "A-B", 0.85
            );

            var analysis = tracker.analyzePortfolio(positions, correlations);

            assertThat(analysis.highlyCorrelatedPairs()).contains("A-B");
        }

        @Test
        @DisplayName("should recommend reducing exposure for critical correlation")
        void shouldRecommendReducingExposureForCriticalCorrelation() {
            List<CorrelationTracker.PositionInfo> positions = List.of(
                    new CorrelationTracker.PositionInfo("A", 0.20, "Tech"),
                    new CorrelationTracker.PositionInfo("B", 0.20, "Tech")
            );

            Map<String, Double> correlations = Map.of(
                    "A-B", 0.90 // Critical
            );

            var analysis = tracker.analyzePortfolio(positions, correlations);

            assertThat(analysis.shouldReduceExposure()).isTrue();
            assertThat(analysis.concentrationWarnings())
                    .anyMatch(w -> w.contains("Critical correlation"));
        }

        @Test
        @DisplayName("should calculate average pairwise correlation")
        void shouldCalculateAveragePairwiseCorrelation() {
            List<CorrelationTracker.PositionInfo> positions = List.of(
                    new CorrelationTracker.PositionInfo("A", 0.20, "Tech"),
                    new CorrelationTracker.PositionInfo("B", 0.20, "Tech"),
                    new CorrelationTracker.PositionInfo("C", 0.20, "Finance")
            );

            Map<String, Double> correlations = Map.of(
                    "A-B", 0.80,
                    "A-C", 0.30,
                    "B-C", 0.20
            );

            var analysis = tracker.analyzePortfolio(positions, correlations);

            // Average of 0.80, 0.30, 0.20 = 0.433
            assertThat(analysis.avgPairwiseCorrelation()).isCloseTo(0.433, within(0.01));
            assertThat(analysis.maxPairwiseCorrelation()).isEqualTo(0.80);
        }
    }

    @Nested
    @DisplayName("Correlation Scaling")
    class CorrelationScaling {

        @Test
        @DisplayName("should return 1.0 for low correlation")
        void shouldReturnOneForLowCorrelation() {
            double scale = tracker.calculateCorrelationScaling(0.3);
            assertThat(scale).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should scale down for high correlation")
        void shouldScaleDownForHighCorrelation() {
            double scale = tracker.calculateCorrelationScaling(0.75);
            assertThat(scale).isLessThan(1.0);
            assertThat(scale).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("should return 0.5 for critical correlation")
        void shouldReturnHalfForCriticalCorrelation() {
            double scale = tracker.calculateCorrelationScaling(0.90);
            assertThat(scale).isEqualTo(0.5);
        }

        @Test
        @DisplayName("should handle negative correlation")
        void shouldHandleNegativeCorrelation() {
            // Negative correlation reduces risk, but we use absolute value
            double scale = tracker.calculateCorrelationScaling(-0.90);
            assertThat(scale).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Concentration Limit Checks")
    class ConcentrationLimitChecks {

        @Test
        @DisplayName("should warn on excessive position weight")
        void shouldWarnOnExcessivePositionWeight() {
            List<String> warnings = tracker.checkConcentrationLimits(
                    "NEW", 0.30, List.of()
            );

            assertThat(warnings).anyMatch(w -> w.contains("exceeds max"));
        }

        @Test
        @DisplayName("should pass valid position weight")
        void shouldPassValidPositionWeight() {
            List<String> warnings = tracker.checkConcentrationLimits(
                    "NEW", 0.15, List.of()
            );

            assertThat(warnings).isEmpty();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("should return correct thresholds")
        void shouldReturnCorrectThresholds() {
            assertThat(tracker.getHighCorrelationThreshold()).isEqualTo(0.7);
            assertThat(tracker.getCriticalCorrelationThreshold()).isEqualTo(0.85);
            assertThat(tracker.getMaxSinglePositionPct()).isEqualTo(0.25);
        }
    }

    @Nested
    @DisplayName("Correlation Estimate Validation")
    class CorrelationEstimateValidation {

        @Test
        @DisplayName("should report valid estimate")
        void shouldReportValidEstimate() {
            var estimate = new CorrelationTracker.CorrelationEstimate(
                    "A", "B", 0.75, 60, System.currentTimeMillis(),
                    CorrelationTracker.CorrelationLevel.HIGH
            );

            assertThat(estimate.isValid()).isTrue();
        }

        @Test
        @DisplayName("should report invalid after 24 hours")
        void shouldReportInvalidAfter24Hours() {
            var estimate = new CorrelationTracker.CorrelationEstimate(
                    "A", "B", 0.75, 60,
                    System.currentTimeMillis() - 25 * 60 * 60 * 1000,
                    CorrelationTracker.CorrelationLevel.HIGH
            );

            assertThat(estimate.isValid()).isFalse();
        }
    }

    // Helper methods
    private List<MarketData> createMarketDataWithPrices(List<Double> prices) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(prices.size());

        for (int i = 0; i < prices.size(); i++) {
            double price = prices.get(i);
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .timestamp(baseTime.plusDays(i))
                    .open(price)
                    .high(price * 1.01)
                    .low(price * 0.99)
                    .close(price)
                    .volume(1000000L)
                    .build();
            history.add(data);
        }

        return history;
    }

    private List<MarketData> createCorrelatedHistory(String symbol, int days, double dailyReturn) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);
        double price = 100.0;

        for (int i = 0; i < days; i++) {
            double returnWithNoise = dailyReturn * (1 + (Math.random() - 0.5) * 0.3);
            price = price * (1 + returnWithNoise);

            MarketData data = MarketData.builder()
                    .symbol(symbol)
                    .timestamp(baseTime.plusDays(i))
                    .open(price)
                    .high(price * 1.005)
                    .low(price * 0.995)
                    .close(price)
                    .volume(1000000L)
                    .build();
            history.add(data);
        }

        return history;
    }
}
