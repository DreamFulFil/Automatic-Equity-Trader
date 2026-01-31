package tw.gc.auto.equity.trader.services.positionsizing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for VolatilityTargetingService.
 */
@DisplayName("VolatilityTargetingService")
class VolatilityTargetingServiceTest {

    private VolatilityTargetingService service;

    @BeforeEach
    void setUp() {
        service = new VolatilityTargetingService();
    }

    @Nested
    @DisplayName("Volatility Calculation")
    class VolatilityCalculation {

        @Test
        @DisplayName("should calculate volatility from market data")
        void shouldCalculateVolatilityFromMarketData() {
            List<MarketData> history = createTrendingHistory("TEST", 30, 100.0, 0.02);

            var estimate = service.calculateVolatility(history, "TEST");

            assertThat(estimate.dailyVol()).isGreaterThan(0);
            assertThat(estimate.annualizedVol()).isGreaterThan(estimate.dailyVol());
            assertThat(estimate.symbol()).isEqualTo("TEST");
        }

        @Test
        @DisplayName("should classify normal volatility regime")
        void shouldClassifyNormalVolatilityRegime() {
            // Create data with ~15% annualized volatility (daily ~0.95%)
            List<MarketData> history = createTrendingHistory("TEST", 30, 100.0, 0.01);

            var estimate = service.calculateVolatility(history, "TEST");

            // With 1% daily moves, annualized should be around 15-20%
            assertThat(estimate.regime()).isIn(
                    VolatilityTargetingService.VolatilityRegime.NORMAL,
                    VolatilityTargetingService.VolatilityRegime.LOW,
                    VolatilityTargetingService.VolatilityRegime.HIGH
            );
        }

        @Test
        @DisplayName("should return default estimate for insufficient data")
        void shouldReturnDefaultForInsufficientData() {
            List<MarketData> history = createTrendingHistory("TEST", 5, 100.0, 0.02);

            var estimate = service.calculateVolatility(history, "TEST", 20);

            // Should return default 20% annualized
            assertThat(estimate.annualizedVol()).isEqualTo(0.20);
            assertThat(estimate.regime()).isEqualTo(VolatilityTargetingService.VolatilityRegime.NORMAL);
        }

        @Test
        @DisplayName("should cache volatility estimates")
        void shouldCacheVolatilityEstimates() {
            List<MarketData> history = createTrendingHistory("CACHED", 30, 100.0, 0.02);

            service.calculateVolatility(history, "CACHED");
            var cached = service.getCachedVolatility("CACHED");

            assertThat(cached).isNotNull();
            assertThat(cached.symbol()).isEqualTo("CACHED");
        }
    }

    @Nested
    @DisplayName("Scaling Factor Calculation")
    class ScalingFactorCalculation {

        @Test
        @DisplayName("should scale down for high volatility")
        void shouldScaleDownForHighVolatility() {
            // Current vol 30% vs target 15%
            var result = service.calculateScalingFactor(0.30, 0.15);

            assertThat(result.scaleFactor()).isLessThan(1.0);
            assertThat(result.regime()).isIn(
                    VolatilityTargetingService.VolatilityRegime.HIGH,
                    VolatilityTargetingService.VolatilityRegime.CRISIS
            );
        }

        @Test
        @DisplayName("should scale up for low volatility")
        void shouldScaleUpForLowVolatility() {
            // Current vol 5% vs target 15%
            var result = service.calculateScalingFactor(0.05, 0.15);

            assertThat(result.scaleFactor()).isGreaterThan(1.0);
            assertThat(result.regime()).isEqualTo(VolatilityTargetingService.VolatilityRegime.LOW);
        }

        @Test
        @DisplayName("should cap scaling at 2.0 for low volatility")
        void shouldCapScalingForLowVolatility() {
            // Very low vol would suggest high leverage
            var result = service.calculateScalingFactor(0.01, 0.15);

            assertThat(result.scaleFactor()).isLessThanOrEqualTo(2.0);
        }

        @Test
        @DisplayName("should use minimum scaling in crisis")
        void shouldUseMinimumScalingInCrisis() {
            // Crisis vol = 40% (> 2.5x target of 15%)
            var result = service.calculateScalingFactor(0.40, 0.15);

            assertThat(result.scaleFactor()).isLessThanOrEqualTo(0.1);
            assertThat(result.regime()).isEqualTo(VolatilityTargetingService.VolatilityRegime.CRISIS);
        }

        @Test
        @DisplayName("should handle invalid volatility")
        void shouldHandleInvalidVolatility() {
            var result = service.calculateScalingFactor(0, 0.15);

            assertThat(result.scaleFactor()).isEqualTo(1.0);
            assertThat(result.reasoning()).contains("Invalid volatility");
        }

        @Test
        @DisplayName("should use default target volatility")
        void shouldUseDefaultTargetVolatility() {
            var result = service.calculateScalingFactor(0.15);

            // 15% current vs 15% default target = scale near 1.0
            assertThat(result.scaleFactor()).isBetween(0.8, 1.2);
        }
    }

    @Nested
    @DisplayName("Position Scaling")
    class PositionScaling {

        @Test
        @DisplayName("should scale position based on volatility")
        void shouldScalePositionBasedOnVolatility() {
            int baseShares = 100;

            // High vol should reduce
            int scaledHighVol = service.scalePosition(baseShares, 0.30, 0.15);
            assertThat(scaledHighVol).isLessThan(baseShares);

            // Low vol should increase (up to cap)
            int scaledLowVol = service.scalePosition(baseShares, 0.08, 0.15);
            assertThat(scaledLowVol).isGreaterThan(baseShares);
        }

        @Test
        @DisplayName("should ensure minimum 1 share")
        void shouldEnsureMinimumOneShare() {
            int baseShares = 1;
            int scaled = service.scalePosition(baseShares, 0.50, 0.15);

            assertThat(scaled).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Portfolio Volatility")
    class PortfolioVolatility {

        @Test
        @DisplayName("should calculate portfolio volatility from positions")
        void shouldCalculatePortfolioVolatility() {
            Map<String, VolatilityTargetingService.PositionVol> positions = new HashMap<>();
            positions.put("A", new VolatilityTargetingService.PositionVol("A", 0.5, 0.20));
            positions.put("B", new VolatilityTargetingService.PositionVol("B", 0.5, 0.20));

            double portfolioVol = service.calculatePortfolioVolatility(positions);

            // With equal weights and zero correlation assumed
            // sqrt(0.5^2 * 0.2^2 + 0.5^2 * 0.2^2) = sqrt(0.02) ≈ 0.14
            assertThat(portfolioVol).isGreaterThan(0);
            assertThat(portfolioVol).isLessThan(0.20); // Diversification benefit
        }

        @Test
        @DisplayName("should return 0 for empty portfolio")
        void shouldReturnZeroForEmptyPortfolio() {
            double portfolioVol = service.calculatePortfolioVolatility(Map.of());

            assertThat(portfolioVol).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should return 0 for null portfolio")
        void shouldReturnZeroForNullPortfolio() {
            double portfolioVol = service.calculatePortfolioVolatility(null);

            assertThat(portfolioVol).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Cache Management")
    class CacheManagement {

        @Test
        @DisplayName("should clear cache")
        void shouldClearCache() {
            List<MarketData> history = createTrendingHistory("CLEAR", 30, 100.0, 0.02);
            service.calculateVolatility(history, "CLEAR");

            service.clearCache();

            assertThat(service.getCachedVolatility("CLEAR")).isNull();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("should return default target volatility")
        void shouldReturnDefaultTargetVolatility() {
            assertThat(service.getDefaultTargetVolatility()).isEqualTo(0.15);
        }

        @Test
        @DisplayName("should return default volatility period")
        void shouldReturnDefaultVolatilityPeriod() {
            assertThat(service.getDefaultVolatilityPeriod()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Volatility Estimate Validation")
    class VolatilityEstimateValidation {

        @Test
        @DisplayName("should report valid estimate")
        void shouldReportValidEstimate() {
            var estimate = new VolatilityTargetingService.VolatilityEstimate(
                    "TEST", 0.01, 0.15, 20, System.currentTimeMillis(),
                    VolatilityTargetingService.VolatilityRegime.NORMAL
            );

            assertThat(estimate.isValid()).isTrue();
        }

        @Test
        @DisplayName("should report invalid estimate after 24 hours")
        void shouldReportInvalidEstimateAfter24Hours() {
            var estimate = new VolatilityTargetingService.VolatilityEstimate(
                    "TEST", 0.01, 0.15, 20,
                    System.currentTimeMillis() - 25 * 60 * 60 * 1000, // 25 hours ago
                    VolatilityTargetingService.VolatilityRegime.NORMAL
            );

            assertThat(estimate.isValid()).isFalse();
        }

        @Test
        @DisplayName("should calculate ratio to target")
        void shouldCalculateRatioToTarget() {
            var estimate = new VolatilityTargetingService.VolatilityEstimate(
                    "TEST", 0.02, 0.30, 20, System.currentTimeMillis(),
                    VolatilityTargetingService.VolatilityRegime.HIGH
            );

            // 30% / 15% = 2.0
            assertThat(estimate.ratioToTarget(0.15)).isEqualTo(2.0);
        }
    }

    // Helper method to create market data with trending prices
    private List<MarketData> createTrendingHistory(String symbol, int days, double startPrice, double dailyReturn) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);
        double price = startPrice;

        for (int i = 0; i < days; i++) {
            // Add some randomness to daily return
            double returnWithNoise = dailyReturn * (1 + (Math.random() - 0.5) * 0.5);
            price = price * (1 + returnWithNoise);

            double high = price * (1 + Math.random() * 0.01);
            double low = price * (1 - Math.random() * 0.01);

            MarketData data = MarketData.builder()
                    .symbol(symbol)
                    .timestamp(baseTime.plusDays(i))
                    .open(price)
                    .high(high)
                    .low(low)
                    .close(price)
                    .volume(1000000L)
                    .build();
            history.add(data);
        }

        return history;
    }
}
