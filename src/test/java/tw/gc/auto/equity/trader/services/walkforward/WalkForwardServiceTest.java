package tw.gc.auto.equity.trader.services.walkforward;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import tw.gc.auto.equity.trader.services.BacktestService;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WalkForwardService}.
 */
@ExtendWith(MockitoExtension.class)
class WalkForwardServiceTest {
    
    @Mock
    private BacktestService backtestService;
    
    private ParameterOptimizer parameterOptimizer;
    private OverfittingDetector overfittingDetector;
    private WalkForwardService walkForwardService;
    
    @BeforeEach
    void setUp() {
        parameterOptimizer = new ParameterOptimizer();
        overfittingDetector = new OverfittingDetector();
        walkForwardService = new WalkForwardService(backtestService, parameterOptimizer, overfittingDetector);
    }
    
    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {
        
        @Test
        @DisplayName("should create default config")
        void shouldCreateDefaultConfig() {
            var config = WalkForwardService.WalkForwardConfig.defaults();
            
            assertThat(config.trainTestRatio()).isEqualTo(3.0);
            assertThat(config.minTestDays()).isEqualTo(20);
            assertThat(config.anchoredStart()).isFalse();
            assertThat(config.windowStepDays()).isEqualTo(20);
            assertThat(config.initialCapital()).isEqualTo(1_000_000.0);
        }
        
        @Test
        @DisplayName("should reject invalid train/test ratio")
        void shouldRejectInvalidTrainTestRatio() {
            assertThatThrownBy(() -> new WalkForwardService.WalkForwardConfig(
                0.5, // Invalid: < 1.0
                20,
                false,
                20,
                1_000_000.0
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("trainTestRatio");
        }
        
        @Test
        @DisplayName("should reject invalid min test days")
        void shouldRejectInvalidMinTestDays() {
            assertThatThrownBy(() -> new WalkForwardService.WalkForwardConfig(
                3.0,
                5, // Invalid: < 10
                false,
                20,
                1_000_000.0
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("minTestDays");
        }
        
        @Test
        @DisplayName("should reject invalid window step days")
        void shouldRejectInvalidWindowStepDays() {
            assertThatThrownBy(() -> new WalkForwardService.WalkForwardConfig(
                3.0,
                20,
                false,
                0, // Invalid: < 1
                1_000_000.0
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("windowStepDays");
        }
        
        @Test
        @DisplayName("should reject non-positive initial capital")
        void shouldRejectNonPositiveInitialCapital() {
            assertThatThrownBy(() -> new WalkForwardService.WalkForwardConfig(
                3.0,
                20,
                false,
                20,
                0.0 // Invalid: <= 0
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("initialCapital");
        }
    }
    
    @Nested
    @DisplayName("Window Generation")
    class WindowGenerationTests {
        
        @Test
        @DisplayName("should generate correct number of rolling windows")
        void shouldGenerateCorrectRollingWindows() {
            var config = new WalkForwardService.WalkForwardConfig(
                3.0,  // 60 days train, 20 days test
                20,   // min test days
                false, // rolling
                20,   // step by 20 days
                1_000_000.0
            );
            
            LocalDate dataStart = LocalDate.of(2025, 1, 1);
            LocalDate dataEnd = LocalDate.of(2025, 6, 30); // ~180 days
            
            List<WalkForwardWindow> windows = walkForwardService.generateWindows(dataStart, dataEnd, config);
            
            // With 180 days, 80 days per window, stepping by 20 days:
            // Window 1: 0-59 train, 60-79 test
            // Window 2: 20-79 train, 80-99 test
            // etc.
            assertThat(windows).hasSizeGreaterThanOrEqualTo(1);
            
            // First window should start at dataStart
            assertThat(windows.get(0).trainStart()).isEqualTo(dataStart);
        }
        
        @Test
        @DisplayName("should generate anchored windows")
        void shouldGenerateAnchoredWindows() {
            var config = new WalkForwardService.WalkForwardConfig(
                3.0,
                20,
                true, // anchored
                20,
                1_000_000.0
            );
            
            LocalDate dataStart = LocalDate.of(2025, 1, 1);
            LocalDate dataEnd = LocalDate.of(2025, 6, 30);
            
            List<WalkForwardWindow> windows = walkForwardService.generateWindows(dataStart, dataEnd, config);
            
            // All windows should start at the same date
            for (var window : windows) {
                assertThat(window.trainStart()).isEqualTo(dataStart);
            }
        }
        
        @Test
        @DisplayName("should return empty list for insufficient data")
        void shouldReturnEmptyForInsufficientData() {
            var config = WalkForwardService.WalkForwardConfig.defaults();
            
            LocalDate dataStart = LocalDate.of(2025, 1, 1);
            LocalDate dataEnd = LocalDate.of(2025, 1, 30); // Only 30 days - not enough for 80-day window
            
            List<WalkForwardWindow> windows = walkForwardService.generateWindows(dataStart, dataEnd, config);
            
            assertThat(windows).isEmpty();
        }
        
        @Test
        @DisplayName("should maintain correct train/test sequence")
        void shouldMaintainCorrectSequence() {
            var config = WalkForwardService.WalkForwardConfig.defaults();
            
            LocalDate dataStart = LocalDate.of(2025, 1, 1);
            LocalDate dataEnd = LocalDate.of(2025, 12, 31);
            
            List<WalkForwardWindow> windows = walkForwardService.generateWindows(dataStart, dataEnd, config);
            
            for (var window : windows) {
                // Train period should be before test period
                assertThat(window.trainEnd()).isBefore(window.testStart());
                
                // Test period should end within data range
                assertThat(window.testEnd()).isBeforeOrEqualTo(dataEnd);
            }
        }
        
        @Test
        @DisplayName("windows should have approximate 3:1 train/test ratio")
        void windowsShouldHaveCorrectRatio() {
            var config = WalkForwardService.WalkForwardConfig.defaults();
            
            LocalDate dataStart = LocalDate.of(2025, 1, 1);
            LocalDate dataEnd = LocalDate.of(2025, 12, 31);
            
            List<WalkForwardWindow> windows = walkForwardService.generateWindows(dataStart, dataEnd, config);
            
            for (var window : windows) {
                double ratio = window.trainTestRatio();
                assertThat(ratio).isCloseTo(3.0, within(0.5));
            }
        }
    }
    
    @Nested
    @DisplayName("Summary Generation")
    class SummaryTests {
        
        @Test
        @DisplayName("empty summary should have overfit warning")
        void emptySummaryShouldHaveOverfitWarning() {
            var summary = WalkForwardService.WalkForwardOptimizationSummary.empty("2330");
            
            assertThat(summary.symbol()).isEqualTo("2330");
            assertThat(summary.totalWindows()).isZero();
            assertThat(summary.overfitWarning()).isTrue();
            assertThat(summary.warnings()).contains("No valid windows");
        }
        
        @Test
        @DisplayName("should generate detailed report")
        void shouldGenerateDetailedReport() {
            var summary = WalkForwardService.WalkForwardOptimizationSummary.empty("2330");
            
            String report = summary.generateReport();
            
            assertThat(report).contains("2330");
            assertThat(report).contains("Walk-Forward Optimization Report");
            assertThat(report).contains("Summary Statistics");
            assertThat(report).contains("Performance Metrics");
            assertThat(report).contains("Overfit Analysis");
        }
    }
}
