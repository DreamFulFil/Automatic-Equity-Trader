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
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.MarketRegime;
import tw.gc.auto.equity.trader.services.regime.MarketRegimeService.RegimeAnalysis;
import tw.gc.auto.equity.trader.services.regime.RegimeTransitionService.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for RegimeTransitionService.
 * Tests transition detection, confirmation, and scaling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegimeTransitionService Tests")
class RegimeTransitionServiceTest {

    @Mock
    private MarketRegimeService marketRegimeService;

    private RegimeTransitionService service;

    private static final String TEST_SYMBOL = "2330.TW";

    @BeforeEach
    void setUp() {
        service = new RegimeTransitionService(marketRegimeService);
    }

    @Nested
    @DisplayName("Transition Detection Tests")
    class TransitionDetectionTests {

        @Test
        @DisplayName("Should detect potential transition on regime change")
        void shouldDetectPotentialTransition() {
            // Build up history with RANGING regime
            for (int i = 0; i < 5; i++) {
                RegimeAnalysis ranging = createAnalysis(MarketRegime.RANGING, 0.7);
                service.processRegimeUpdate(TEST_SYMBOL, ranging);
            }
            
            // Now transition to TRENDING_UP
            RegimeAnalysis trendingUp = createAnalysis(MarketRegime.TRENDING_UP, 0.8);
            TransitionEvent event = service.processRegimeUpdate(TEST_SYMBOL, trendingUp);
            
            assertThat(event).isNotNull();
            assertThat(event.eventType()).isEqualTo(TransitionEventType.SIGNAL_DETECTED);
            assertThat(event.fromRegime()).isEqualTo(MarketRegime.RANGING);
            assertThat(event.toRegime()).isEqualTo(MarketRegime.TRENDING_UP);
        }

        @Test
        @DisplayName("Should ignore low confidence signals")
        void shouldIgnoreLowConfidenceSignals() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                RegimeAnalysis ranging = createAnalysis(MarketRegime.RANGING, 0.7);
                service.processRegimeUpdate(TEST_SYMBOL, ranging);
            }
            
            // Low confidence transition signal
            RegimeAnalysis lowConfidence = createAnalysis(MarketRegime.TRENDING_UP, 0.5);
            TransitionEvent event = service.processRegimeUpdate(TEST_SYMBOL, lowConfidence);
            
            assertThat(event).isNull();
        }

        @Test
        @DisplayName("Should trigger crisis immediately without confirmation")
        void shouldTriggerCrisisImmediately() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            // Crisis signal
            RegimeAnalysis crisis = createAnalysis(MarketRegime.CRISIS, 0.95);
            TransitionEvent event = service.processRegimeUpdate(TEST_SYMBOL, crisis);
            
            assertThat(event).isNotNull();
            assertThat(event.eventType()).isEqualTo(TransitionEventType.CRISIS_TRIGGERED);
            assertThat(event.toRegime()).isEqualTo(MarketRegime.CRISIS);
            
            // Should be immediately confirmed
            TransitionState state = service.getTransitionState(TEST_SYMBOL);
            assertThat(state.confirmed()).isTrue();
            assertThat(state.scalingProgress()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Transition Confirmation Tests")
    class TransitionConfirmationTests {

        @Test
        @DisplayName("Should confirm transition after 3 days")
        void shouldConfirmAfterThreeDays() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            // Start transition
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            // Day 2
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            TransitionState state2 = service.getTransitionState(TEST_SYMBOL);
            assertThat(state2.confirmed()).isFalse();
            
            // Day 3 - should confirm
            TransitionEvent event = service.processRegimeUpdate(TEST_SYMBOL, 
                    createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            assertThat(event).isNotNull();
            assertThat(event.eventType()).isEqualTo(TransitionEventType.CONFIRMED);
            
            TransitionState state3 = service.getTransitionState(TEST_SYMBOL);
            assertThat(state3.confirmed()).isTrue();
        }

        @Test
        @DisplayName("Should cancel transition if regime reverts")
        void shouldCancelIfRegimeReverts() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            // Start transition
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            // Revert back to RANGING
            TransitionEvent event = service.processRegimeUpdate(TEST_SYMBOL, 
                    createAnalysis(MarketRegime.RANGING, 0.7));
            
            assertThat(event).isNotNull();
            assertThat(event.eventType()).isEqualTo(TransitionEventType.CANCELLED);
            assertThat(service.getTransitionState(TEST_SYMBOL)).isNull();
        }

        @Test
        @DisplayName("Should track remaining confirmation days")
        void shouldTrackRemainingConfirmationDays() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            // Start transition
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            TransitionState state = service.getTransitionState(TEST_SYMBOL);
            assertThat(state.remainingConfirmationDays()).isEqualTo(2); // 3 - 1 = 2
            assertThat(state.confirmationCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Transition Scaling Tests")
    class TransitionScalingTests {

        @Test
        @DisplayName("Should start with zero scaling for unconfirmed transition")
        void shouldStartWithZeroScalingUnconfirmed() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            // Start transition
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            TransitionState state = service.getTransitionState(TEST_SYMBOL);
            assertThat(state.scalingProgress()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should start scaling after confirmation")
        void shouldStartScalingAfterConfirmation() {
            // Build up history and confirm transition
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            // 3 days of TRENDING_UP to confirm
            for (int i = 0; i < 3; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            }
            
            TransitionState state = service.getTransitionState(TEST_SYMBOL);
            assertThat(state.confirmed()).isTrue();
            assertThat(state.scalingProgress()).isEqualTo(0.2); // 1/5
        }

        @Test
        @DisplayName("Should advance scaling daily")
        void shouldAdvanceScalingDaily() {
            // Build up history and confirm
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            for (int i = 0; i < 3; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            }
            
            double initialProgress = service.getTransitionState(TEST_SYMBOL).scalingProgress();
            
            // Advance scaling
            service.advanceTransitionScaling(TEST_SYMBOL);
            
            TransitionState updated = service.getTransitionState(TEST_SYMBOL);
            assertThat(updated.scalingProgress()).isGreaterThan(initialProgress);
        }

        @Test
        @DisplayName("Should cap scaling at 1.0")
        void shouldCapScalingAtOne() {
            // Build up history and confirm
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            for (int i = 0; i < 3; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            }
            
            // Advance scaling many times
            for (int i = 0; i < 10; i++) {
                service.advanceTransitionScaling(TEST_SYMBOL);
            }
            
            TransitionState state = service.getTransitionState(TEST_SYMBOL);
            assertThat(state.scalingProgress()).isEqualTo(1.0);
            assertThat(state.isComplete()).isTrue();
        }

        @Test
        @DisplayName("Should return 1.0 when no active transition")
        void shouldReturnOneWhenNoTransition() {
            double factor = service.getTransitionScalingFactor(TEST_SYMBOL);
            assertThat(factor).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should blend position sizes during transition")
        void shouldBlendPositionSizesDuringTransition() {
            // Build up history and confirm
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            for (int i = 0; i < 3; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            }
            
            double blended = service.blendPositionSize(TEST_SYMBOL, 1000.0, 500.0);
            
            // With 0.2 scaling: 1000 * 0.8 + 500 * 0.2 = 900
            assertThat(blended).isEqualTo(900.0);
        }
    }

    @Nested
    @DisplayName("Transition State Tests")
    class TransitionStateTests {

        @Test
        @DisplayName("Should track if symbol is in transition")
        void shouldTrackIfInTransition() {
            assertThat(service.isInTransition(TEST_SYMBOL)).isFalse();
            
            // Build up history and start transition
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            assertThat(service.isInTransition(TEST_SYMBOL)).isTrue();
        }

        @Test
        @DisplayName("Should clear transition data for symbol")
        void shouldClearTransitionDataForSymbol() {
            // Build up history
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            
            service.clearTransitionData(TEST_SYMBOL);
            
            assertThat(service.getTransitionState(TEST_SYMBOL)).isNull();
            assertThat(service.isInTransition(TEST_SYMBOL)).isFalse();
        }

        @Test
        @DisplayName("Should clear all transition data")
        void shouldClearAllTransitionData() {
            String symbol2 = "2454.TW";
            
            // Build up history for multiple symbols
            for (int i = 0; i < 5; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
                service.processRegimeUpdate(symbol2, createAnalysis(MarketRegime.TRENDING_UP, 0.8));
            }
            
            service.clearAllTransitionData();
            
            assertThat(service.getRegimeHistory(TEST_SYMBOL, 10)).isEmpty();
            assertThat(service.getRegimeHistory(symbol2, 10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Regime History Tests")
    class RegimeHistoryTests {

        @Test
        @DisplayName("Should maintain regime history")
        void shouldMaintainRegimeHistory() {
            for (int i = 0; i < 10; i++) {
                MarketRegime regime = i % 2 == 0 ? MarketRegime.RANGING : MarketRegime.TRENDING_UP;
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(regime, 0.7));
            }
            
            List<RegimeSnapshot> history = service.getRegimeHistory(TEST_SYMBOL, 5);
            
            assertThat(history).hasSize(5);
        }

        @Test
        @DisplayName("Should limit history size")
        void shouldLimitHistorySize() {
            // Add more than max history
            for (int i = 0; i < 50; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            
            List<RegimeSnapshot> history = service.getRegimeHistory(TEST_SYMBOL, 100);
            
            assertThat(history.size()).isLessThanOrEqualTo(30); // MAX_HISTORY_SIZE
        }

        @Test
        @DisplayName("Should return empty list for unknown symbol")
        void shouldReturnEmptyForUnknownSymbol() {
            List<RegimeSnapshot> history = service.getRegimeHistory("UNKNOWN", 10);
            
            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("Should determine dominant regime")
        void shouldDetermineDominantRegime() {
            // Add mostly RANGING with some TRENDING_UP
            for (int i = 0; i < 10; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.RANGING, 0.7));
            }
            for (int i = 0; i < 3; i++) {
                service.processRegimeUpdate(TEST_SYMBOL, createAnalysis(MarketRegime.TRENDING_UP, 0.7));
            }
            
            MarketRegime dominant = service.getDominantRegime(TEST_SYMBOL, 15);
            
            assertThat(dominant).isEqualTo(MarketRegime.RANGING);
        }

        @Test
        @DisplayName("Should return null for unknown symbol dominant regime")
        void shouldReturnNullForUnknownDominantRegime() {
            MarketRegime dominant = service.getDominantRegime("UNKNOWN", 10);
            assertThat(dominant).isNull();
        }
    }

    @Nested
    @DisplayName("TransitionState Record Tests")
    class TransitionStateRecordTests {

        @Test
        @DisplayName("Should check if transition is complete")
        void shouldCheckIfComplete() {
            TransitionState incomplete = new TransitionState(
                    TEST_SYMBOL, MarketRegime.RANGING, MarketRegime.TRENDING_UP,
                    3, LocalDateTime.now(), 0.5, true
            );
            
            TransitionState complete = new TransitionState(
                    TEST_SYMBOL, MarketRegime.RANGING, MarketRegime.TRENDING_UP,
                    3, LocalDateTime.now(), 1.0, true
            );
            
            assertThat(incomplete.isComplete()).isFalse();
            assertThat(complete.isComplete()).isTrue();
        }

        @Test
        @DisplayName("Should calculate remaining confirmation days")
        void shouldCalculateRemainingDays() {
            TransitionState state = new TransitionState(
                    TEST_SYMBOL, MarketRegime.RANGING, MarketRegime.TRENDING_UP,
                    1, LocalDateTime.now(), 0.0, false
            );
            
            assertThat(state.remainingConfirmationDays()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("TransitionEvent Tests")
    class TransitionEventTests {

        @Test
        @DisplayName("Should have all event types")
        void shouldHaveAllEventTypes() {
            TransitionEventType[] types = TransitionEventType.values();
            
            assertThat(types).contains(
                    TransitionEventType.SIGNAL_DETECTED,
                    TransitionEventType.CONFIRMED,
                    TransitionEventType.CANCELLED,
                    TransitionEventType.COMPLETED,
                    TransitionEventType.CRISIS_TRIGGERED
            );
        }
    }

    @Nested
    @DisplayName("Integration with MarketRegimeService")
    class IntegrationTests {

        @Test
        @DisplayName("Should use MarketRegimeService for analysis")
        void shouldUseMarketRegimeServiceForAnalysis() {
            RegimeAnalysis mockAnalysis = createAnalysis(MarketRegime.TRENDING_UP, 0.8);
            when(marketRegimeService.analyzeRegime(eq(TEST_SYMBOL), any()))
                    .thenReturn(mockAnalysis);
            
            TransitionEvent event = service.updateAndDetectTransition(TEST_SYMBOL, List.of());
            
            verify(marketRegimeService).analyzeRegime(eq(TEST_SYMBOL), any());
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
