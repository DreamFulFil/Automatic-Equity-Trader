package tw.gc.auto.equity.trader.services.positionsizing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PositionRiskManager.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PositionRiskManager")
class PositionRiskManagerTest {

    @Mock
    private PositionSizingService positionSizingService;

    @Mock
    private VolatilityTargetingService volatilityTargetingService;

    @Mock
    private CorrelationTracker correlationTracker;

    private PositionRiskManager manager;

    @BeforeEach
    void setUp() {
        manager = new PositionRiskManager(positionSizingService, volatilityTargetingService, correlationTracker);
    }

    @Nested
    @DisplayName("Position Calculation")
    class PositionCalculation {

        @Test
        @DisplayName("should calculate position with all adjustments")
        void shouldCalculatePositionWithAllAdjustments() {
            // Setup mocks
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    100, 100.0, 100000.0, "HALF_KELLY", "Base calculation"
            );
            when(positionSizingService.calculateHalfKelly(any()))
                    .thenReturn(baseResult);

            var volEstimate = new VolatilityTargetingService.VolatilityEstimate(
                    "TEST", 0.01, 0.20, 20, System.currentTimeMillis(),
                    VolatilityTargetingService.VolatilityRegime.NORMAL
            );
            when(volatilityTargetingService.calculateVolatility(anyList(), anyString()))
                    .thenReturn(volEstimate);

            var volScaling = new VolatilityTargetingService.ScalingResult(
                    0.8, VolatilityTargetingService.VolatilityRegime.HIGH, "High volatility"
            );
            when(volatilityTargetingService.calculateScalingFactor(anyDouble()))
                    .thenReturn(volScaling);

            when(correlationTracker.calculateAverageCorrelationWithPortfolio(anyString(), anySet()))
                    .thenReturn(0.3);
            when(correlationTracker.calculateCorrelationScaling(anyDouble()))
                    .thenReturn(1.0);
            when(correlationTracker.checkConcentrationLimits(anyString(), anyDouble(), anyList()))
                    .thenReturn(List.of());

            // Create request
            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .winRate(0.6)
                    .avgWin(100)
                    .avgLoss(50)
                    .priceHistory(createMarketDataHistory(30))
                    .existingPositions(List.of())
                    .build();

            // Execute
            var result = manager.calculatePosition(request);

            // Verify
            assertThat(result.recommendedShares()).isGreaterThan(0);
            assertThat(result.approved()).isTrue();
            assertThat(result.volatilityScale()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("should reduce position for high volatility")
        void shouldReducePositionForHighVolatility() {
            // Setup with high volatility
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    100, 100.0, 100000.0, "FIXED_RISK", "Base"
            );
            when(positionSizingService.calculateFixedRisk(any()))
                    .thenReturn(baseResult);

            var volScaling = new VolatilityTargetingService.ScalingResult(
                    0.3, VolatilityTargetingService.VolatilityRegime.CRISIS, "Crisis"
            );
            when(volatilityTargetingService.calculateVolatility(anyList(), anyString()))
                    .thenReturn(new VolatilityTargetingService.VolatilityEstimate(
                            "TEST", 0.03, 0.45, 20, System.currentTimeMillis(),
                            VolatilityTargetingService.VolatilityRegime.CRISIS
                    ));
            when(volatilityTargetingService.calculateScalingFactor(anyDouble()))
                    .thenReturn(volScaling);

            when(correlationTracker.calculateAverageCorrelationWithPortfolio(anyString(), anySet()))
                    .thenReturn(0.0);
            when(correlationTracker.calculateCorrelationScaling(anyDouble()))
                    .thenReturn(1.0);
            when(correlationTracker.checkConcentrationLimits(anyString(), anyDouble(), anyList()))
                    .thenReturn(List.of());

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .riskPerTradePct(2.0)
                    .priceHistory(createMarketDataHistory(30))
                    .build();

            var result = manager.calculatePosition(request);

            // Position should be significantly reduced
            assertThat(result.recommendedShares()).isLessThan(100);
            assertThat(result.volatilityScale()).isLessThan(0.5);
            assertThat(result.warnings()).anyMatch(w -> w.contains("High volatility"));
        }

        @Test
        @DisplayName("should reduce position for high correlation")
        void shouldReducePositionForHighCorrelation() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    100, 100.0, 100000.0, "FIXED_RISK", "Base"
            );
            when(positionSizingService.calculateFixedRisk(any()))
                    .thenReturn(baseResult);

            // No volatility adjustment
            when(volatilityTargetingService.calculateScalingFactor(anyDouble()))
                    .thenReturn(new VolatilityTargetingService.ScalingResult(
                            1.0, VolatilityTargetingService.VolatilityRegime.NORMAL, "Normal"
                    ));

            // High correlation with existing portfolio
            when(correlationTracker.calculateAverageCorrelationWithPortfolio(anyString(), anySet()))
                    .thenReturn(0.85);
            when(correlationTracker.calculateCorrelationScaling(0.85))
                    .thenReturn(0.5); // 50% reduction
            when(correlationTracker.checkConcentrationLimits(anyString(), anyDouble(), anyList()))
                    .thenReturn(List.of("High correlation warning"));

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .riskPerTradePct(2.0)
                    .existingPositions(List.of(
                            new CorrelationTracker.PositionInfo("EXISTING", 0.20, "Tech")
                    ))
                    .build();

            var result = manager.calculatePosition(request);

            assertThat(result.correlationScale()).isEqualTo(0.5);
            assertThat(result.warnings()).anyMatch(w -> w.contains("correlation"));
        }

        @Test
        @DisplayName("should cap position at concentration limits")
        void shouldCapPositionAtConcentrationLimits() {
            // Request large position that exceeds limits
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    500, 100.0, 100000.0, "KELLY", "Base"
            );
            when(positionSizingService.calculateKelly(any()))
                    .thenReturn(baseResult);

            when(volatilityTargetingService.calculateScalingFactor(anyDouble()))
                    .thenReturn(new VolatilityTargetingService.ScalingResult(
                            1.0, VolatilityTargetingService.VolatilityRegime.NORMAL, "Normal"
                    ));

            when(correlationTracker.calculateAverageCorrelationWithPortfolio(anyString(), anySet()))
                    .thenReturn(0.0);
            when(correlationTracker.calculateCorrelationScaling(anyDouble()))
                    .thenReturn(1.0);
            when(correlationTracker.checkConcentrationLimits(anyString(), anyDouble(), anyList()))
                    .thenReturn(List.of());

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .winRate(0.9)
                    .avgWin(500)
                    .avgLoss(50)
                    .preferredMethod(PositionRiskManager.SizingMethod.KELLY)
                    .build();

            var result = manager.calculatePosition(request);

            // Max is 10% of equity / price = 10000 / 100 = 100 shares
            assertThat(result.recommendedShares()).isLessThanOrEqualTo(100);
            assertThat(result.maxAllowedShares()).isEqualTo(100);
        }

        @Test
        @DisplayName("should not approve when exceeding critical limits")
        void shouldNotApproveWhenExceedingCriticalLimits() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    100, 100.0, 100000.0, "FIXED_RISK", "Base"
            );
            when(positionSizingService.calculateFixedRisk(any()))
                    .thenReturn(baseResult);

            when(volatilityTargetingService.calculateScalingFactor(anyDouble()))
                    .thenReturn(new VolatilityTargetingService.ScalingResult(
                            1.0, VolatilityTargetingService.VolatilityRegime.NORMAL, "Normal"
                    ));

            when(correlationTracker.calculateAverageCorrelationWithPortfolio(anyString(), anySet()))
                    .thenReturn(0.0);
            when(correlationTracker.calculateCorrelationScaling(anyDouble()))
                    .thenReturn(1.0);
            when(correlationTracker.checkConcentrationLimits(anyString(), anyDouble(), anyList()))
                    .thenReturn(List.of("Critical: Position exceeds max 25%"));

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .riskPerTradePct(2.0)
                    .build();

            var result = manager.calculatePosition(request);

            assertThat(result.approved()).isFalse();
        }
    }

    @Nested
    @DisplayName("Method Selection")
    class MethodSelection {

        @Test
        @DisplayName("should use Half-Kelly when trade stats available")
        void shouldUseHalfKellyWhenTradeStatsAvailable() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    50, 100.0, 100000.0, "HALF_KELLY", "Half Kelly"
            );
            when(positionSizingService.calculateHalfKelly(any()))
                    .thenReturn(baseResult);

            setupDefaultMocks();

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .winRate(0.6)
                    .avgWin(100)
                    .avgLoss(50)
                    .build();

            var result = manager.calculatePosition(request);

            assertThat(result.methodUsed()).isEqualTo(PositionRiskManager.SizingMethod.HALF_KELLY);
            verify(positionSizingService).calculateHalfKelly(any());
        }

        @Test
        @DisplayName("should use ATR when only volatility available")
        void shouldUseAtrWhenOnlyVolatilityAvailable() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    50, 100.0, 100000.0, "ATR", "ATR-based"
            );
            when(positionSizingService.calculateAtrBased(any()))
                    .thenReturn(baseResult);

            setupDefaultMocks();

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .atr(5.0)
                    .riskPerTradePct(2.0)
                    .build();

            var result = manager.calculatePosition(request);

            assertThat(result.methodUsed()).isEqualTo(PositionRiskManager.SizingMethod.ATR);
            verify(positionSizingService).calculateAtrBased(any());
        }

        @Test
        @DisplayName("should use Fixed Risk as fallback")
        void shouldUseFixedRiskAsFallback() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    20, 100.0, 100000.0, "FIXED_RISK", "Fixed risk"
            );
            when(positionSizingService.calculateFixedRisk(any()))
                    .thenReturn(baseResult);

            setupDefaultMocks();

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .riskPerTradePct(2.0)
                    .build();

            var result = manager.calculatePosition(request);

            assertThat(result.methodUsed()).isEqualTo(PositionRiskManager.SizingMethod.FIXED_RISK);
        }

        @Test
        @DisplayName("should respect preferred method")
        void shouldRespectPreferredMethod() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    100, 100.0, 100000.0, "KELLY", "Kelly"
            );
            when(positionSizingService.calculateKelly(any()))
                    .thenReturn(baseResult);

            setupDefaultMocks();

            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .winRate(0.6)
                    .avgWin(100)
                    .avgLoss(50)
                    .preferredMethod(PositionRiskManager.SizingMethod.KELLY)
                    .build();

            var result = manager.calculatePosition(request);

            assertThat(result.methodUsed()).isEqualTo(PositionRiskManager.SizingMethod.KELLY);
            verify(positionSizingService).calculateKelly(any());
        }
    }

    @Nested
    @DisplayName("Quick Approval Check")
    class QuickApprovalCheck {

        @Test
        @DisplayName("should approve valid position")
        void shouldApproveValidPosition() {
            boolean approved = manager.wouldApprove("TEST", 50, 100.0, 100000.0);

            // 50 * 100 = 5000 = 5% of equity
            assertThat(approved).isTrue();
        }

        @Test
        @DisplayName("should reject position exceeding concentration")
        void shouldRejectPositionExceedingConcentration() {
            boolean approved = manager.wouldApprove("TEST", 300, 100.0, 100000.0);

            // 300 * 100 = 30000 = 30% of equity > 25% max
            assertThat(approved).isFalse();
        }

        @Test
        @DisplayName("should reject position exceeding per-trade risk")
        void shouldRejectPositionExceedingPerTradeRisk() {
            boolean approved = manager.wouldApprove("TEST", 150, 100.0, 100000.0);

            // 150 * 100 = 15000 = 15% of equity > 10% per-trade max
            assertThat(approved).isFalse();
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("should calculate max position size")
        void shouldCalculateMaxPositionSize() {
            // Max is min(25%, 10%) = 10%
            // 10% of 100k = 10k / 100 = 100 shares
            int maxShares = manager.getMaxPositionSize(100.0, 100000.0);

            assertThat(maxShares).isEqualTo(100);
        }

        @Test
        @DisplayName("should calculate simple position")
        void shouldCalculateSimplePosition() {
            var baseResult = PositionSizingService.PositionSizeResult.of(
                    20, 100.0, 100000.0, "FIXED_RISK", "2% risk"
            );
            when(positionSizingService.calculateFixedRisk(any()))
                    .thenReturn(baseResult);

            setupDefaultMocks();

            int shares = manager.calculateSimplePosition(100000.0, 100.0, 2.0);

            assertThat(shares).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Request Builder")
    class RequestBuilder {

        @Test
        @DisplayName("should build valid request")
        void shouldBuildValidRequest() {
            var request = PositionRiskManager.PositionRequest.builder()
                    .symbol("TEST")
                    .stockPrice(100.0)
                    .equity(100000.0)
                    .winRate(0.6)
                    .avgWin(100)
                    .avgLoss(50)
                    .atr(5.0)
                    .riskPerTradePct(2.0)
                    .sector("Tech")
                    .preferredMethod(PositionRiskManager.SizingMethod.HALF_KELLY)
                    .build();

            assertThat(request.symbol()).isEqualTo("TEST");
            assertThat(request.equity()).isEqualTo(100000.0);
            assertThat(request.preferredMethod()).isEqualTo(PositionRiskManager.SizingMethod.HALF_KELLY);
        }

        @Test
        @DisplayName("should reject null symbol")
        void shouldRejectNullSymbol() {
            assertThatThrownBy(() -> 
                    PositionRiskManager.PositionRequest.builder()
                            .stockPrice(100.0)
                            .equity(100000.0)
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject invalid stock price")
        void shouldRejectInvalidStockPrice() {
            assertThatThrownBy(() ->
                    PositionRiskManager.PositionRequest.builder()
                            .symbol("TEST")
                            .stockPrice(0)
                            .equity(100000.0)
                            .build()
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should reject invalid equity")
        void shouldRejectInvalidEquity() {
            assertThatThrownBy(() ->
                    PositionRiskManager.PositionRequest.builder()
                            .symbol("TEST")
                            .stockPrice(100.0)
                            .equity(-1000.0)
                            .build()
            ).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // Helper methods
    private void setupDefaultMocks() {
        when(volatilityTargetingService.calculateScalingFactor(anyDouble()))
                .thenReturn(new VolatilityTargetingService.ScalingResult(
                        1.0, VolatilityTargetingService.VolatilityRegime.NORMAL, "Normal"
                ));

        when(correlationTracker.calculateAverageCorrelationWithPortfolio(anyString(), anySet()))
                .thenReturn(0.0);
        when(correlationTracker.calculateCorrelationScaling(anyDouble()))
                .thenReturn(1.0);
        when(correlationTracker.checkConcentrationLimits(anyString(), anyDouble(), anyList()))
                .thenReturn(List.of());
    }

    private List<MarketData> createMarketDataHistory(int days) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);
        double price = 100.0;

        for (int i = 0; i < days; i++) {
            price = price * (1 + (Math.random() - 0.5) * 0.02);

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
}
