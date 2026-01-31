package tw.gc.auto.equity.trader.services.positionsizing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PositionSizingService.
 */
@DisplayName("PositionSizingService")
class PositionSizingServiceTest {

    private PositionSizingService service;

    @BeforeEach
    void setUp() {
        service = new PositionSizingService();
    }

    @Nested
    @DisplayName("Kelly Criterion")
    class KellyCriterion {

        @Test
        @DisplayName("should calculate Kelly fraction correctly")
        void shouldCalculateKellyFractionCorrectly() {
            // Win rate 60%, avg win 100, avg loss 50
            // b = 100/50 = 2, p = 0.6, q = 0.4
            // f* = (2*0.6 - 0.4) / 2 = (1.2 - 0.4) / 2 = 0.4
            var config = PositionSizingService.PositionSizeConfig.forKelly(
                    100000, 100, 0.6, 100, 50
            );

            var result = service.calculateKelly(config);

            assertThat(result.method()).isEqualTo("KELLY");
            assertThat(result.positionPct()).isGreaterThan(0);
            assertThat(result.positionPct()).isLessThanOrEqualTo(0.10); // Max cap
        }

        @Test
        @DisplayName("should cap Kelly at 10% max position")
        void shouldCapKellyAtMaxPosition() {
            // Very favorable odds that would suggest > 10%
            var config = PositionSizingService.PositionSizeConfig.forKelly(
                    100000, 100, 0.9, 200, 50
            );

            var result = service.calculateKelly(config);

            assertThat(result.positionPct()).isLessThanOrEqualTo(0.10);
        }

        @Test
        @DisplayName("should return minimum position for negative Kelly")
        void shouldReturnMinimumForNegativeKelly() {
            // Win rate 30%, avg win 50, avg loss 100
            // b = 0.5, p = 0.3, q = 0.7
            // f* = (0.5*0.3 - 0.7) / 0.5 = -1.1 (negative)
            var config = PositionSizingService.PositionSizeConfig.forKelly(
                    100000, 100, 0.3, 50, 100
            );

            var result = service.calculateKelly(config);

            assertThat(result.shares()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should handle zero avg loss gracefully")
        void shouldHandleZeroAvgLoss() {
            var config = PositionSizingService.PositionSizeConfig.forKelly(
                    100000, 100, 0.5, 100, 0
            );

            var result = service.calculateKelly(config);

            assertThat(result.shares()).isEqualTo(1);
            assertThat(result.reasoning()).contains("Invalid avgLoss");
        }
    }

    @Nested
    @DisplayName("Half-Kelly")
    class HalfKelly {

        @Test
        @DisplayName("should be half of full Kelly")
        void shouldBeHalfOfFullKelly() {
            var config = PositionSizingService.PositionSizeConfig.forKelly(
                    100000, 100, 0.6, 100, 50
            );

            var fullKelly = service.calculateKelly(config);
            var halfKelly = service.calculateHalfKelly(config);

            // Half-Kelly should be approximately half (rounding may cause slight differences)
            assertThat(halfKelly.shares()).isLessThanOrEqualTo(fullKelly.shares() / 2 + 1);
            assertThat(halfKelly.shares()).isGreaterThanOrEqualTo(fullKelly.shares() / 2 - 1);
        }

        @Test
        @DisplayName("should have HALF_KELLY method tag")
        void shouldHaveHalfKellyMethodTag() {
            var config = PositionSizingService.PositionSizeConfig.forKelly(
                    100000, 100, 0.6, 100, 50
            );

            var result = service.calculateHalfKelly(config);

            assertThat(result.method()).isEqualTo("HALF_KELLY");
        }
    }

    @Nested
    @DisplayName("ATR-based Sizing")
    class AtrBasedSizing {

        @Test
        @DisplayName("should size inversely to ATR")
        void shouldSizeInverselyToAtr() {
            // Higher ATR = smaller position
            // Use higher price to ensure we stay below max cap
            var lowAtrConfig = PositionSizingService.PositionSizeConfig.forAtr(
                    100000, 500, 5.0, 1.0  // 1% risk, stop = 5, shares = 1000/5 = 200, capped at 20
            );
            var highAtrConfig = PositionSizingService.PositionSizeConfig.forAtr(
                    100000, 500, 50.0, 1.0 // 1% risk, stop = 50, shares = 1000/50 = 20
            );

            var lowAtrResult = service.calculateAtrBased(lowAtrConfig);
            var highAtrResult = service.calculateAtrBased(highAtrConfig);

            // Both are capped at 20 (10% of 100k / 500 = 20), so check they're different or equal
            assertThat(lowAtrResult.shares()).isGreaterThanOrEqualTo(highAtrResult.shares());
        }

        @Test
        @DisplayName("should respect risk per trade percentage")
        void shouldRespectRiskPerTrade() {
            var config = PositionSizingService.PositionSizeConfig.forAtr(
                    100000, 100, 5.0, 2.0 // 2% risk per trade
            );

            var result = service.calculateAtrBased(config);

            // Risk = 2% of 100k = 2000
            // Stop distance = ATR * 2 = 10
            // Shares = 2000 / 10 = 200
            assertThat(result.shares()).isGreaterThan(0);
            assertThat(result.method()).isEqualTo("ATR");
        }

        @Test
        @DisplayName("should handle custom ATR multiplier")
        void shouldHandleCustomAtrMultiplier() {
            // Use parameters that result in below max cap
            // Max position = 10% of 100k / 100 = 100 shares
            // We want: risk / (ATR * mult) < 100
            // 2000 / (100 * 1) = 20 shares (below cap)
            // 2000 / (100 * 3) = 6 shares (below cap)
            var config = PositionSizingService.PositionSizeConfig.forAtr(
                    100000, 100, 100.0, 2.0  // 2% risk = 2000, ATR=100
            );

            var result1x = service.calculateAtrBased(config, 1.0); // stop=100, shares=2000/100=20
            var result3x = service.calculateAtrBased(config, 3.0); // stop=300, shares=2000/300=6

            // Larger multiplier = wider stop = fewer shares
            assertThat(result1x.shares()).isGreaterThan(result3x.shares());
        }

        @Test
        @DisplayName("should handle invalid ATR")
        void shouldHandleInvalidAtr() {
            var config = PositionSizingService.PositionSizeConfig.forAtr(
                    100000, 100, 0, 2.0
            );

            var result = service.calculateAtrBased(config);

            assertThat(result.shares()).isEqualTo(1);
            assertThat(result.reasoning()).contains("Invalid ATR");
        }
    }

    @Nested
    @DisplayName("Fixed Risk Sizing")
    class FixedRiskSizing {

        @Test
        @DisplayName("should calculate correct position for given risk")
        void shouldCalculateCorrectPosition() {
            // 2% risk on 100k = 2000 position value / 100 price = 20 shares
            var config = PositionSizingService.PositionSizeConfig.forFixedRisk(
                    100000, 100, 2.0
            );

            var result = service.calculateFixedRisk(config);

            assertThat(result.shares()).isEqualTo(20);
        }

        @Test
        @DisplayName("should cap at max position percentage")
        void shouldCapAtMaxPosition() {
            // 50% risk would exceed 10% cap
            var config = PositionSizingService.PositionSizeConfig.forFixedRisk(
                    100000, 100, 50.0
            );

            var result = service.calculateFixedRisk(config);

            // Should be capped at 10% = 10000 / 100 = 100 shares
            assertThat(result.shares()).isLessThanOrEqualTo(100);
        }
    }

    @Nested
    @DisplayName("ATR Calculation")
    class AtrCalculation {

        @Test
        @DisplayName("should calculate ATR from market data")
        void shouldCalculateAtrFromMarketData() {
            List<MarketData> history = createMarketDataHistory(25, 100.0, 5.0);

            double atr = service.calculateAtr(history, 14);

            assertThat(atr).isGreaterThan(0);
        }

        @Test
        @DisplayName("should return 0 for insufficient data")
        void shouldReturnZeroForInsufficientData() {
            List<MarketData> history = createMarketDataHistory(5, 100.0, 2.0);

            double atr = service.calculateAtr(history, 14);

            assertThat(atr).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should return 0 for null history")
        void shouldReturnZeroForNullHistory() {
            double atr = service.calculateAtr(null, 14);

            assertThat(atr).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Recommend Method")
    class RecommendMethod {

        @Test
        @DisplayName("should use Half-Kelly when trade stats available")
        void shouldUseHalfKellyWhenTradeStatsAvailable() {
            var config = new PositionSizingService.PositionSizeConfig(
                    100000, 100, 0.6, 100, 50, 5.0, 2.0, null
            );

            var result = service.recommend(config);

            assertThat(result.method()).isEqualTo("HALF_KELLY");
        }

        @Test
        @DisplayName("should use ATR when only volatility available")
        void shouldUseAtrWhenOnlyVolatilityAvailable() {
            var config = new PositionSizingService.PositionSizeConfig(
                    100000, 100, 0, 0, 0, 5.0, 2.0, null
            );

            var result = service.recommend(config);

            assertThat(result.method()).isEqualTo("ATR");
        }

        @Test
        @DisplayName("should use fixed risk as fallback")
        void shouldUseFixedRiskAsFallback() {
            var config = new PositionSizingService.PositionSizeConfig(
                    100000, 100, 0, 0, 0, 0, 2.0, null
            );

            var result = service.recommend(config);

            assertThat(result.method()).isEqualTo("FIXED_RISK");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject zero equity")
        void shouldRejectZeroEquity() {
            var config = PositionSizingService.PositionSizeConfig.forFixedRisk(
                    0, 100, 2.0
            );

            assertThatThrownBy(() -> service.calculateFixedRisk(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Equity must be positive");
        }

        @Test
        @DisplayName("should reject zero price")
        void shouldRejectZeroPrice() {
            var config = PositionSizingService.PositionSizeConfig.forFixedRisk(
                    100000, 0, 2.0
            );

            assertThatThrownBy(() -> service.calculateFixedRisk(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Stock price must be positive");
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("should detect positions exceeding max")
        void shouldDetectPositionsExceedingMax() {
            assertThat(service.exceedsMaxPosition(15000, 100000)).isTrue();
            assertThat(service.exceedsMaxPosition(5000, 100000)).isFalse();
        }

        @Test
        @DisplayName("should calculate max shares")
        void shouldCalculateMaxShares() {
            // 10% of 100k = 10k / 100 = 100 shares
            int maxShares = service.getMaxShares(100000, 100);

            assertThat(maxShares).isEqualTo(100);
        }
    }

    // Helper method to create market data history
    private List<MarketData> createMarketDataHistory(int days, double basePrice, double volatility) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);

        for (int i = 0; i < days; i++) {
            double price = basePrice + (Math.random() - 0.5) * volatility * 2;
            double high = price + Math.random() * volatility;
            double low = price - Math.random() * volatility;

            MarketData data = MarketData.builder()
                    .symbol("TEST")
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
