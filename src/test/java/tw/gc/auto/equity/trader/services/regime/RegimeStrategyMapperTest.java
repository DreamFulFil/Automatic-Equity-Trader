package tw.gc.auto.equity.trader.services.regime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.MarketRegime;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.RegimeAnalysis;
import tw.gc.auto.equity.trader.services.regime.RegimeStrategyMapper.RegimeAllocation;
import tw.gc.auto.equity.trader.services.regime.RegimeStrategyMapper.StrategyRecommendation;
import tw.gc.auto.equity.trader.strategy.StrategyType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for RegimeStrategyMapper.
 * Tests strategy-to-regime mapping and fitness calculations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegimeStrategyMapper Tests")
class RegimeStrategyMapperTest {

    @Mock
    private MarketRegimeService marketRegimeService;

    private RegimeStrategyMapper mapper;

    private static final String TEST_SYMBOL = "2330.TW";

    @BeforeEach
    void setUp() {
        mapper = new RegimeStrategyMapper(marketRegimeService);
    }

    @Nested
    @DisplayName("Strategy Recommendations by Regime")
    class StrategyRecommendationTests {

        @Test
        @DisplayName("Should recommend momentum strategies for TRENDING_UP")
        void shouldRecommendMomentumForTrendingUp() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.TRENDING_UP, 0.8);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            assertThat(recommendations).isNotEmpty();
            StrategyRecommendation top = recommendations.get(0);
            assertThat(top.strategyName()).isEqualTo(RegimeStrategyMapper.MOMENTUM);
            assertThat(top.fitness()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should recommend defensive strategies for TRENDING_DOWN")
        void shouldRecommendDefensiveForTrendingDown() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.TRENDING_DOWN, 0.75);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            assertThat(recommendations).isNotEmpty();
            StrategyRecommendation top = recommendations.get(0);
            assertThat(top.strategyName()).isEqualTo(RegimeStrategyMapper.DEFENSIVE);
            assertThat(top.fitness()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should recommend mean reversion for RANGING")
        void shouldRecommendMeanReversionForRanging() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.RANGING, 0.7);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            assertThat(recommendations).isNotEmpty();
            StrategyRecommendation top = recommendations.get(0);
            assertThat(top.strategyName()).isEqualTo(RegimeStrategyMapper.MEAN_REVERSION);
        }

        @Test
        @DisplayName("Should recommend defensive/cash for HIGH_VOLATILITY")
        void shouldRecommendDefensiveForHighVolatility() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.HIGH_VOLATILITY, 0.85);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            assertThat(recommendations).isNotEmpty();
            assertThat(recommendations.get(0).strategyName())
                    .isIn(RegimeStrategyMapper.DEFENSIVE, RegimeStrategyMapper.CASH);
        }

        @Test
        @DisplayName("Should recommend cash preservation for CRISIS")
        void shouldRecommendCashForCrisis() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.CRISIS, 0.95);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            assertThat(recommendations).isNotEmpty();
            StrategyRecommendation top = recommendations.get(0);
            assertThat(top.strategyName()).isEqualTo(RegimeStrategyMapper.CASH);
            assertThat(top.fitness()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should sort recommendations by score")
        void shouldSortRecommendationsByScore() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.TRENDING_UP, 0.8);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            for (int i = 0; i < recommendations.size() - 1; i++) {
                assertThat(recommendations.get(i).getScore())
                        .isGreaterThanOrEqualTo(recommendations.get(i + 1).getScore());
            }
        }

        @Test
        @DisplayName("Should penalize momentum in ranging market")
        void shouldPenalizeMomentumInRangingMarket() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.RANGING, 0.7);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            StrategyRecommendation momentum = recommendations.stream()
                    .filter(r -> r.strategyName().equals(RegimeStrategyMapper.MOMENTUM))
                    .findFirst()
                    .orElseThrow();
            
            assertThat(momentum.fitness()).isLessThan(0.5);
        }

        @Test
        @DisplayName("Should penalize mean reversion in uptrend")
        void shouldPenalizeMeanReversionInUptrend() {
            RegimeAnalysis analysis = createAnalysis(MarketRegime.TRENDING_UP, 0.8);
            
            List<StrategyRecommendation> recommendations = mapper.getStrategiesForRegime(analysis);
            
            StrategyRecommendation meanRev = recommendations.stream()
                    .filter(r -> r.strategyName().equals(RegimeStrategyMapper.MEAN_REVERSION))
                    .findFirst()
                    .orElseThrow();
            
            assertThat(meanRev.fitness()).isLessThan(0.5);
        }
    }

    @Nested
    @DisplayName("Strategy Fitness Tests")
    class StrategyFitnessTests {

        @Test
        @DisplayName("Should return fitness from recommendations")
        void shouldReturnFitnessFromRecommendations() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            double fitness = mapper.getStrategyFitness(RegimeStrategyMapper.MOMENTUM, TEST_SYMBOL);
            
            assertThat(fitness).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should return default fitness for unknown strategy")
        void shouldReturnDefaultForUnknownStrategy() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.RANGING, 0.7));
            
            double fitness = mapper.getStrategyFitness("UnknownStrategy", TEST_SYMBOL);
            
            assertThat(fitness).isEqualTo(0.5); // Default
        }

        @Test
        @DisplayName("Should return default recommendations when no cached regime")
        void shouldReturnDefaultWhenNoCachedRegime() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL)).thenReturn(null);
            
            List<StrategyRecommendation> recommendations = mapper.getRecommendedStrategies(TEST_SYMBOL);
            
            assertThat(recommendations).isNotEmpty();
            assertThat(recommendations.get(0).strategyName())
                    .isIn(RegimeStrategyMapper.MEAN_REVERSION, RegimeStrategyMapper.DEFENSIVE);
        }
    }

    @Nested
    @DisplayName("Strategy Blocking Tests")
    class StrategyBlockingTests {

        @Test
        @DisplayName("Should block momentum in crisis")
        void shouldBlockMomentumInCrisis() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.CRISIS, 0.95));
            
            boolean blocked = mapper.shouldBlockStrategy(RegimeStrategyMapper.MOMENTUM, TEST_SYMBOL);
            
            assertThat(blocked).isTrue();
        }

        @Test
        @DisplayName("Should not block cash in crisis")
        void shouldNotBlockCashInCrisis() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.CRISIS, 0.95));
            
            boolean blocked = mapper.shouldBlockStrategy(RegimeStrategyMapper.CASH, TEST_SYMBOL);
            
            assertThat(blocked).isFalse();
        }

        @Test
        @DisplayName("Should block low fitness strategies")
        void shouldBlockLowFitnessStrategies() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.RANGING, 0.7));
            
            // Breakout has 0.25 fitness in ranging market
            boolean blocked = mapper.shouldBlockStrategy(RegimeStrategyMapper.BREAKOUT, TEST_SYMBOL);
            
            assertThat(blocked).isFalse(); // 0.25 > 0.2 threshold
        }
    }

    @Nested
    @DisplayName("Allocation Tests")
    class AllocationTests {

        @Test
        @DisplayName("Should return full equity for TRENDING_UP")
        void shouldReturnFullEquityForTrendingUp() {
            RegimeAllocation allocation = mapper.getAllocationForRegime(MarketRegime.TRENDING_UP);
            
            assertThat(allocation.equityAllocation()).isEqualTo(1.0);
            assertThat(allocation.cashAllocation()).isEqualTo(0.0);
            assertThat(allocation.maxPositionSize()).isEqualTo(0.10);
        }

        @Test
        @DisplayName("Should return reduced equity for TRENDING_DOWN")
        void shouldReturnReducedEquityForTrendingDown() {
            RegimeAllocation allocation = mapper.getAllocationForRegime(MarketRegime.TRENDING_DOWN);
            
            assertThat(allocation.equityAllocation()).isLessThan(1.0);
            assertThat(allocation.cashAllocation()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Should return zero equity for CRISIS")
        void shouldReturnZeroEquityForCrisis() {
            RegimeAllocation allocation = mapper.getAllocationForRegime(MarketRegime.CRISIS);
            
            assertThat(allocation.equityAllocation()).isEqualTo(0.0);
            assertThat(allocation.cashAllocation()).isEqualTo(1.0);
            assertThat(allocation.maxPositionSize()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should get allocation for symbol")
        void shouldGetAllocationForSymbol() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.HIGH_VOLATILITY, 0.85));
            
            RegimeAllocation allocation = mapper.getAllocation(TEST_SYMBOL);
            
            assertThat(allocation.regime()).isEqualTo(MarketRegime.HIGH_VOLATILITY);
            assertThat(allocation.equityAllocation()).isLessThan(1.0);
        }

        @Test
        @DisplayName("Should return conservative default when no cached regime")
        void shouldReturnConservativeDefaultWhenNoCached() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL)).thenReturn(null);
            
            RegimeAllocation allocation = mapper.getAllocation(TEST_SYMBOL);
            
            assertThat(allocation.regime()).isEqualTo(MarketRegime.RANGING);
        }
    }

    @Nested
    @DisplayName("Compatible Regimes Tests")
    class CompatibleRegimesTests {

        @Test
        @DisplayName("Should return compatible regimes for INTRADAY")
        void shouldReturnCompatibleRegimesForIntraday() {
            Set<MarketRegime> compatible = mapper.getCompatibleRegimes(StrategyType.INTRADAY);
            
            assertThat(compatible).contains(MarketRegime.TRENDING_UP, MarketRegime.RANGING);
            assertThat(compatible).doesNotContain(MarketRegime.CRISIS);
        }

        @Test
        @DisplayName("Should return compatible regimes for SWING")
        void shouldReturnCompatibleRegimesForSwing() {
            Set<MarketRegime> compatible = mapper.getCompatibleRegimes(StrategyType.SWING);
            
            assertThat(compatible).contains(
                    MarketRegime.TRENDING_UP, 
                    MarketRegime.RANGING, 
                    MarketRegime.TRENDING_DOWN
            );
        }

        @Test
        @DisplayName("Should return compatible regimes for SHORT_TERM")
        void shouldReturnCompatibleRegimesForShortTerm() {
            Set<MarketRegime> compatible = mapper.getCompatibleRegimes(StrategyType.SHORT_TERM);
            
            assertThat(compatible).contains(MarketRegime.RANGING, MarketRegime.HIGH_VOLATILITY);
        }
    }

    @Nested
    @DisplayName("Position Adjustment Tests")
    class PositionAdjustmentTests {

        @Test
        @DisplayName("Should adjust position size based on regime")
        void shouldAdjustPositionSizeBasedOnRegime() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.CRISIS, 0.95));
            
            double adjusted = mapper.adjustPositionForRegime(TEST_SYMBOL, 10000.0);
            
            assertThat(adjusted).isEqualTo(0.0); // Crisis = 0 scale factor
        }

        @Test
        @DisplayName("Should not adjust when no cached regime")
        void shouldNotAdjustWhenNoCachedRegime() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL)).thenReturn(null);
            
            double adjusted = mapper.adjustPositionForRegime(TEST_SYMBOL, 10000.0);
            
            assertThat(adjusted).isEqualTo(10000.0);
        }

        @Test
        @DisplayName("Should apply scale factor for trending up")
        void shouldApplyScaleFactorForTrendingUp() {
            when(marketRegimeService.getCachedRegime(TEST_SYMBOL))
                    .thenReturn(createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            double adjusted = mapper.adjustPositionForRegime(TEST_SYMBOL, 10000.0);
            
            assertThat(adjusted).isEqualTo(10000.0); // 1.0 scale factor
        }
    }

    @Nested
    @DisplayName("StrategyRecommendation Record Tests")
    class StrategyRecommendationRecordTests {

        @Test
        @DisplayName("Should calculate combined score")
        void shouldCalculateCombinedScore() {
            StrategyRecommendation rec = new StrategyRecommendation(
                    "TestStrategy",
                    StrategyType.SWING,
                    0.8,  // fitness
                    0.75, // confidence
                    MarketRegime.TRENDING_UP,
                    "Test rationale"
            );
            
            assertThat(rec.getScore()).isCloseTo(0.6, within(0.0001)); // 0.8 * 0.75
        }
    }

    // ===== HELPER METHODS =====

    private RegimeAnalysis createAnalysis(MarketRegime regime, double confidence) {
        return new RegimeAnalysis(
                TEST_SYMBOL, regime, confidence, 25.0, 20.0, 15.0,
                15.0, 100.0, 95.0, 0.02, LocalDateTime.now(), "Test rationale"
        );
    }
}
