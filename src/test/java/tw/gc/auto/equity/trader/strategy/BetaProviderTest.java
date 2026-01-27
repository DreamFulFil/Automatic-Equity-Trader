package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.services.BetaCalculationService.BetaCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for BetaProvider functional interface.
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@DisplayName("BetaProvider Unit Tests")
class BetaProviderTest {

    @Nested
    @DisplayName("noOp() factory method")
    class NoOpTests {

        @Test
        void noOpShouldReturnEmpty() {
            BetaProvider provider = BetaProvider.noOp();
            
            Optional<Double> beta = provider.getBeta("2330.TW");
            
            assertThat(beta).isEmpty();
        }

        @Test
        void noOpShouldReturnDefaultValue() {
            BetaProvider provider = BetaProvider.noOp();
            
            double beta = provider.getBetaOrDefault("2330.TW", 1.0);
            
            assertThat(beta).isEqualTo(1.0);
        }

        @Test
        void noOpShouldReturnFalseForLowBeta() {
            BetaProvider provider = BetaProvider.noOp();
            
            assertThat(provider.isLowBeta("2330.TW", 0.8)).isFalse();
        }

        @Test
        void noOpShouldReturnFalseForHighBeta() {
            BetaProvider provider = BetaProvider.noOp();
            
            assertThat(provider.isHighBeta("2330.TW", 1.2)).isFalse();
        }

        @Test
        void noOpShouldReturnUnknownCategory() {
            BetaProvider provider = BetaProvider.noOp();
            
            assertThat(provider.categorize("2330.TW")).isEqualTo(BetaCategory.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("withFixedBeta() factory method")
    class WithFixedBetaTests {

        @Test
        void shouldReturnFixedBetaForAnySymbol() {
            BetaProvider provider = BetaProvider.withFixedBeta(1.2);
            
            assertThat(provider.getBeta("2330.TW")).hasValue(1.2);
            assertThat(provider.getBeta("2454.TW")).hasValue(1.2);
            assertThat(provider.getBeta("ANY.TW")).hasValue(1.2);
        }

        @Test
        void shouldDetectHighBetaWithFixedValue() {
            BetaProvider provider = BetaProvider.withFixedBeta(1.5);
            
            assertThat(provider.isHighBeta("2330.TW", 1.2)).isTrue();
            assertThat(provider.isLowBeta("2330.TW", 0.8)).isFalse();
        }

        @Test
        void shouldDetectLowBetaWithFixedValue() {
            BetaProvider provider = BetaProvider.withFixedBeta(0.6);
            
            assertThat(provider.isLowBeta("2330.TW", 0.8)).isTrue();
            assertThat(provider.isHighBeta("2330.TW", 1.2)).isFalse();
        }

        @Test
        void shouldCategorizeCorrectly() {
            assertThat(BetaProvider.withFixedBeta(0.5).categorize("X"))
                    .isEqualTo(BetaCategory.LOW_BETA);
            assertThat(BetaProvider.withFixedBeta(1.0).categorize("X"))
                    .isEqualTo(BetaCategory.NEUTRAL);
            assertThat(BetaProvider.withFixedBeta(1.5).categorize("X"))
                    .isEqualTo(BetaCategory.HIGH_BETA);
        }
    }

    @Nested
    @DisplayName("fromMap() factory method")
    class FromMapTests {

        @Test
        void shouldReturnBetaFromMap() {
            Map<String, Double> betaMap = new HashMap<>();
            betaMap.put("2330.TW", 1.3);
            betaMap.put("2454.TW", 1.8);
            betaMap.put("2412.TW", 0.5);
            
            BetaProvider provider = BetaProvider.fromMap(betaMap);
            
            assertThat(provider.getBeta("2330.TW")).hasValue(1.3);
            assertThat(provider.getBeta("2454.TW")).hasValue(1.8);
            assertThat(provider.getBeta("2412.TW")).hasValue(0.5);
        }

        @Test
        void shouldReturnEmptyForUnknownSymbol() {
            Map<String, Double> betaMap = new HashMap<>();
            betaMap.put("2330.TW", 1.3);
            
            BetaProvider provider = BetaProvider.fromMap(betaMap);
            
            assertThat(provider.getBeta("UNKNOWN")).isEmpty();
        }

        @Test
        void shouldClassifyCorrectlyFromMap() {
            Map<String, Double> betaMap = new HashMap<>();
            betaMap.put("LOW", 0.5);
            betaMap.put("NEUTRAL", 1.0);
            betaMap.put("HIGH", 1.5);
            
            BetaProvider provider = BetaProvider.fromMap(betaMap);
            
            assertThat(provider.isLowBeta("LOW", 0.8)).isTrue();
            assertThat(provider.isHighBeta("HIGH", 1.2)).isTrue();
            assertThat(provider.isLowBeta("NEUTRAL", 0.8)).isFalse();
            assertThat(provider.isHighBeta("NEUTRAL", 1.2)).isFalse();
        }
    }

    @Nested
    @DisplayName("withDefault() factory method")
    class WithDefaultTests {

        @Test
        void shouldReturnBetaFromDelegateWhenPresent() {
            Map<String, Double> betaMap = new HashMap<>();
            betaMap.put("2330.TW", 1.3);
            BetaProvider delegate = BetaProvider.fromMap(betaMap);
            
            BetaProvider provider = BetaProvider.withDefault(delegate, 1.0);
            
            assertThat(provider.getBeta("2330.TW")).hasValue(1.3);
        }

        @Test
        void shouldReturnDefaultWhenNotInDelegate() {
            Map<String, Double> betaMap = new HashMap<>();
            betaMap.put("2330.TW", 1.3);
            BetaProvider delegate = BetaProvider.fromMap(betaMap);
            
            BetaProvider provider = BetaProvider.withDefault(delegate, 1.0);
            
            assertThat(provider.getBeta("UNKNOWN")).hasValue(1.0);
        }
    }

    @Nested
    @DisplayName("getBetaOrDefault()")
    class GetBetaOrDefaultTests {

        @Test
        void shouldReturnBetaWhenPresent() {
            BetaProvider provider = BetaProvider.withFixedBeta(1.5);
            
            double beta = provider.getBetaOrDefault("2330.TW", 1.0);
            
            assertThat(beta).isEqualTo(1.5);
        }

        @Test
        void shouldReturnDefaultWhenEmpty() {
            BetaProvider provider = BetaProvider.noOp();
            
            double beta = provider.getBetaOrDefault("2330.TW", 1.0);
            
            assertThat(beta).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("compareBeta()")
    class CompareBetaTests {

        @Test
        void shouldCompareCorrectly() {
            Map<String, Double> betaMap = new HashMap<>();
            betaMap.put("LOW", 0.5);
            betaMap.put("HIGH", 1.5);
            
            BetaProvider provider = BetaProvider.fromMap(betaMap);
            
            assertThat(provider.compareBeta("LOW", "HIGH")).isNegative();
            assertThat(provider.compareBeta("HIGH", "LOW")).isPositive();
            assertThat(provider.compareBeta("LOW", "LOW")).isZero();
        }

        @Test
        void shouldHandleEmptyBetas() {
            BetaProvider provider = BetaProvider.noOp();
            
            int result = provider.compareBeta("A", "B");
            
            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("calculateExpectedReturn() - CAPM")
    class CalculateExpectedReturnTests {

        @Test
        void shouldCalculateCAPMReturn() {
            BetaProvider provider = BetaProvider.withFixedBeta(1.2);
            
            // Risk-free rate: 2%, Market return: 10%
            Double expectedReturn = provider.calculateExpectedReturn("2330.TW", 0.10, 0.02);
            
            // Expected = Rf + Beta * (Rm - Rf) = 0.02 + 1.2 * (0.10 - 0.02) = 0.116
            assertThat(expectedReturn).isCloseTo(0.116, within(0.0001));
        }

        @Test
        void shouldReturnNullWhenBetaIsEmpty() {
            BetaProvider provider = BetaProvider.noOp();
            
            Double expectedReturn = provider.calculateExpectedReturn("2330.TW", 0.10, 0.02);
            
            assertThat(expectedReturn).isNull();
        }
    }

    @Nested
    @DisplayName("getPositionSizeMultiplier()")
    class PositionSizeMultiplierTests {

        @Test
        void shouldReturnInverseBetaWithinBounds() {
            BetaProvider provider = BetaProvider.withFixedBeta(2.0);
            
            double multiplier = provider.getPositionSizeMultiplier("2330.TW", 1.0);
            
            // 1 / 2.0 = 0.5
            assertThat(multiplier).isEqualTo(0.5);
        }

        @Test
        void shouldCapMultiplierAtTwo() {
            BetaProvider provider = BetaProvider.withFixedBeta(0.3);
            
            double multiplier = provider.getPositionSizeMultiplier("2330.TW", 1.0);
            
            // 1 / 0.3 = 3.33, but capped at 2.0
            assertThat(multiplier).isEqualTo(2.0);
        }

        @Test
        void shouldReturnOneWhenBetaIsOne() {
            BetaProvider provider = BetaProvider.withFixedBeta(1.0);
            
            double multiplier = provider.getPositionSizeMultiplier("2330.TW", 1.0);
            
            assertThat(multiplier).isEqualTo(1.0);
        }

        @Test
        void shouldReturnOneWhenBetaIsEmpty() {
            BetaProvider provider = BetaProvider.noOp();
            
            double multiplier = provider.getPositionSizeMultiplier("2330.TW", 1.0);
            
            assertThat(multiplier).isEqualTo(1.0);
        }

        @Test
        void shouldReturnZeroForNegativeBeta() {
            BetaProvider provider = BetaProvider.withFixedBeta(-0.5);
            
            double multiplier = provider.getPositionSizeMultiplier("2330.TW", 1.0);
            
            assertThat(multiplier).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Custom implementation")
    class CustomImplementationTests {

        @Test
        void shouldAllowCustomImplementation() {
            // Custom provider that returns beta based on symbol suffix
            BetaProvider provider = symbol -> {
                if (symbol.endsWith(".TW")) {
                    return Optional.of(1.2);
                }
                return Optional.of(0.8);
            };
            
            assertThat(provider.getBeta("2330.TW")).hasValue(1.2);
            assertThat(provider.getBeta("AAPL")).hasValue(0.8);
        }

        @Test
        void defaultMethodsShouldDelegateToGetBeta() {
            BetaProvider provider = symbol -> {
                if ("HIGH".equals(symbol)) return Optional.of(1.5);
                if ("LOW".equals(symbol)) return Optional.of(0.5);
                return Optional.of(1.0);
            };
            
            // Test all default methods work with custom implementation
            assertThat(provider.isHighBeta("HIGH", 1.2)).isTrue();
            assertThat(provider.isLowBeta("LOW", 0.8)).isTrue();
            assertThat(provider.categorize("HIGH")).isEqualTo(BetaCategory.HIGH_BETA);
            assertThat(provider.categorize("LOW")).isEqualTo(BetaCategory.LOW_BETA);
            assertThat(provider.categorize("NEUTRAL")).isEqualTo(BetaCategory.NEUTRAL);
            assertThat(provider.getBetaOrDefault("X", 2.0)).isEqualTo(1.0);
        }

        @Test
        void applyShouldDelegateToBeta() {
            BetaProvider provider = BetaProvider.withFixedBeta(1.3);
            
            // Function interface apply method
            Optional<Double> result = provider.apply("2330.TW");
            
            assertThat(result).hasValue(1.3);
        }
    }
}
