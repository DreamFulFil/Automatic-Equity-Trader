package tw.gc.auto.equity.trader.services.walkforward;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StrategyParameterDefinition}.
 */
class StrategyParameterDefinitionTest {
    
    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {
        
        @Test
        @DisplayName("should create valid parameter definition")
        void shouldCreateValidParameterDefinition() {
            var param = new StrategyParameterDefinition("period", 10.0, 30.0, 5.0, 20.0);
            
            assertThat(param.name()).isEqualTo("period");
            assertThat(param.minValue()).isEqualTo(10.0);
            assertThat(param.maxValue()).isEqualTo(30.0);
            assertThat(param.step()).isEqualTo(5.0);
            assertThat(param.defaultValue()).isEqualTo(20.0);
        }
        
        @Test
        @DisplayName("should create integer parameter using factory method")
        void shouldCreateIntegerParameter() {
            var param = StrategyParameterDefinition.ofInt("period", 7, 21, 2, 14);
            
            assertThat(param.name()).isEqualTo("period");
            assertThat(param.minValue()).isEqualTo(7.0);
            assertThat(param.maxValue()).isEqualTo(21.0);
            assertThat(param.step()).isEqualTo(2.0);
            assertThat(param.defaultValue()).isEqualTo(14.0);
        }
        
        @Test
        @DisplayName("should create double parameter using factory method")
        void shouldCreateDoubleParameter() {
            var param = StrategyParameterDefinition.ofDouble("oversold", 20.0, 40.0, 5.0, 30.0);
            
            assertThat(param.name()).isEqualTo("oversold");
            assertThat(param.minValue()).isEqualTo(20.0);
            assertThat(param.maxValue()).isEqualTo(40.0);
        }
        
        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> new StrategyParameterDefinition(null, 10.0, 30.0, 5.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        }
        
        @Test
        @DisplayName("should reject blank name")
        void shouldRejectBlankName() {
            assertThatThrownBy(() -> new StrategyParameterDefinition("  ", 10.0, 30.0, 5.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        }
        
        @Test
        @DisplayName("should reject minValue greater than maxValue")
        void shouldRejectInvalidRange() {
            assertThatThrownBy(() -> new StrategyParameterDefinition("param", 30.0, 10.0, 5.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minValue");
        }
        
        @Test
        @DisplayName("should reject zero step")
        void shouldRejectZeroStep() {
            assertThatThrownBy(() -> new StrategyParameterDefinition("param", 10.0, 30.0, 0.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
        }
        
        @Test
        @DisplayName("should reject negative step")
        void shouldRejectNegativeStep() {
            assertThatThrownBy(() -> new StrategyParameterDefinition("param", 10.0, 30.0, -1.0, 20.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step");
        }
        
        @Test
        @DisplayName("should reject default below min")
        void shouldRejectDefaultBelowMin() {
            assertThatThrownBy(() -> new StrategyParameterDefinition("param", 10.0, 30.0, 5.0, 5.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultValue");
        }
        
        @Test
        @DisplayName("should reject default above max")
        void shouldRejectDefaultAboveMax() {
            assertThatThrownBy(() -> new StrategyParameterDefinition("param", 10.0, 30.0, 5.0, 35.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultValue");
        }
        
        @Test
        @DisplayName("should accept default equal to min")
        void shouldAcceptDefaultEqualToMin() {
            var param = new StrategyParameterDefinition("param", 10.0, 30.0, 5.0, 10.0);
            assertThat(param.defaultValue()).isEqualTo(10.0);
        }
        
        @Test
        @DisplayName("should accept default equal to max")
        void shouldAcceptDefaultEqualToMax() {
            var param = new StrategyParameterDefinition("param", 10.0, 30.0, 5.0, 30.0);
            assertThat(param.defaultValue()).isEqualTo(30.0);
        }
    }
    
    @Nested
    @DisplayName("Grid Size Calculation")
    class GridSizeTests {
        
        @ParameterizedTest
        @CsvSource({
            "10.0, 30.0, 5.0, 5",   // (30-10)/5 + 1 = 5 values: 10, 15, 20, 25, 30
            "10.0, 30.0, 10.0, 3",  // (30-10)/10 + 1 = 3 values: 10, 20, 30
            "10.0, 30.0, 1.0, 21",  // (30-10)/1 + 1 = 21 values
            "10.0, 10.0, 5.0, 1",   // same min/max = 1 value
            "0.0, 1.0, 0.1, 11"     // fractional step
        })
        @DisplayName("should calculate correct grid size")
        void shouldCalculateCorrectGridSize(double min, double max, double step, int expectedSize) {
            var param = new StrategyParameterDefinition("test", min, max, step, min);
            assertThat(param.gridSize()).isEqualTo(expectedSize);
        }
    }
    
    @Nested
    @DisplayName("Value At Index")
    class ValueAtIndexTests {
        
        @Test
        @DisplayName("should return correct values at each index")
        void shouldReturnCorrectValuesAtIndex() {
            var param = new StrategyParameterDefinition("test", 10.0, 30.0, 5.0, 20.0);
            
            assertThat(param.valueAt(0)).isEqualTo(10.0);
            assertThat(param.valueAt(1)).isEqualTo(15.0);
            assertThat(param.valueAt(2)).isEqualTo(20.0);
            assertThat(param.valueAt(3)).isEqualTo(25.0);
            assertThat(param.valueAt(4)).isEqualTo(30.0);
        }
        
        @Test
        @DisplayName("should throw for negative index")
        void shouldThrowForNegativeIndex() {
            var param = new StrategyParameterDefinition("test", 10.0, 30.0, 5.0, 20.0);
            
            assertThatThrownBy(() -> param.valueAt(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        }
        
        @Test
        @DisplayName("should throw for index out of range")
        void shouldThrowForIndexOutOfRange() {
            var param = new StrategyParameterDefinition("test", 10.0, 30.0, 5.0, 20.0);
            
            assertThatThrownBy(() -> param.valueAt(5))
                .isInstanceOf(IndexOutOfBoundsException.class);
        }
        
        @Test
        @DisplayName("should not exceed max value even with rounding")
        void shouldNotExceedMaxValue() {
            // Edge case: step doesn't divide evenly
            var param = new StrategyParameterDefinition("test", 10.0, 33.0, 5.0, 10.0);
            // Grid: 10, 15, 20, 25, 30 (5 values, last should be capped at 33 if step overshoots)
            
            int size = param.gridSize();
            double lastValue = param.valueAt(size - 1);
            assertThat(lastValue).isLessThanOrEqualTo(33.0);
        }
    }
    
    @Nested
    @DisplayName("All Values Generation")
    class AllValuesTests {
        
        @Test
        @DisplayName("should generate all values in grid")
        void shouldGenerateAllValues() {
            var param = new StrategyParameterDefinition("test", 10.0, 30.0, 5.0, 20.0);
            double[] values = param.allValues();
            
            assertThat(values).containsExactly(10.0, 15.0, 20.0, 25.0, 30.0);
        }
        
        @Test
        @DisplayName("should generate single value for same min/max")
        void shouldGenerateSingleValueForSameMinMax() {
            var param = new StrategyParameterDefinition("test", 20.0, 20.0, 5.0, 20.0);
            double[] values = param.allValues();
            
            assertThat(values).containsExactly(20.0);
        }
        
        @Test
        @DisplayName("should handle fractional steps")
        void shouldHandleFractionalSteps() {
            var param = new StrategyParameterDefinition("test", 0.0, 0.5, 0.1, 0.2);
            double[] values = param.allValues();
            
            assertThat(values).hasSize(6);
            assertThat(values[0]).isCloseTo(0.0, within(0.001));
            assertThat(values[5]).isCloseTo(0.5, within(0.001));
        }
    }
}
