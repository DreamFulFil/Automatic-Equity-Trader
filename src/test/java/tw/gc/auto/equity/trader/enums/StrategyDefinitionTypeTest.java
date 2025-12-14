package tw.gc.auto.equity.trader.enums;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.strategy.StrategyType;

import static org.junit.jupiter.api.Assertions.*;

class StrategyDefinitionTypeTest {

    @Test
    void fromClassName_withValidDCA_shouldReturnDCA() {
        assertEquals(StrategyDefinitionType.DCA, StrategyDefinitionType.fromClassName("DCA"));
    }

    @Test
    void fromClassName_withValidMovingAverageCrossover_shouldReturnCorrect() {
        assertEquals(StrategyDefinitionType.MOVING_AVERAGE_CROSSOVER, 
            StrategyDefinitionType.fromClassName("MovingAverageCrossover"));
    }

    @Test
    void fromClassName_caseInsensitive_shouldWork() {
        assertEquals(StrategyDefinitionType.DCA, StrategyDefinitionType.fromClassName("dca"));
        assertEquals(StrategyDefinitionType.RSI, StrategyDefinitionType.fromClassName("rsi"));
    }

    @Test
    void fromClassName_withInvalidName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, 
            () -> StrategyDefinitionType.fromClassName("InvalidStrategy"));
    }

    @Test
    void fromDisplayName_withValidName_shouldReturnCorrect() {
        assertEquals(StrategyDefinitionType.DCA, 
            StrategyDefinitionType.fromDisplayName("Dollar Cost Averaging"));
    }

    @Test
    void fromDisplayName_withInvalidName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, 
            () -> StrategyDefinitionType.fromDisplayName("Invalid Display Name"));
    }

    @Test
    void getType_shouldReturnCorrectStrategyType() {
        assertEquals(StrategyType.LONG_TERM, StrategyDefinitionType.DCA.getType());
        assertEquals(StrategyType.SHORT_TERM, StrategyDefinitionType.RSI.getType());
        assertEquals(StrategyType.INTRADAY, StrategyDefinitionType.VWAP_EXECUTION.getType());
    }

    @Test
    void supportsStock_shouldReturnCorrectValue() {
        assertTrue(StrategyDefinitionType.DCA.supportsStock());
        assertTrue(StrategyDefinitionType.MOVING_AVERAGE_CROSSOVER.supportsStock());
    }

    @Test
    void supportsFutures_shouldReturnCorrectValue() {
        assertTrue(StrategyDefinitionType.MOVING_AVERAGE_CROSSOVER.supportsFutures());
        assertTrue(StrategyDefinitionType.RSI.supportsFutures());
    }

    @Test
    void isCompatibleWith_stockMode_shouldAllowStockStrategies() {
        assertTrue(StrategyDefinitionType.DCA.isCompatibleWith(TradingModeType.TW_STOCK));
        assertTrue(StrategyDefinitionType.MOVING_AVERAGE_CROSSOVER.isCompatibleWith(TradingModeType.TW_STOCK));
    }

    @Test
    void isCompatibleWith_futuresMode_shouldAllowFuturesStrategies() {
        assertTrue(StrategyDefinitionType.MOVING_AVERAGE_CROSSOVER.isCompatibleWith(TradingModeType.TW_FUTURE));
        assertTrue(StrategyDefinitionType.RSI.isCompatibleWith(TradingModeType.TW_FUTURE));
    }

    @Test
    void isCompatibleWith_stockAndFuturesMode_shouldAllowCompatibleStrategies() {
        assertTrue(StrategyDefinitionType.DCA.isCompatibleWith(TradingModeType.TW_STOCK_AND_FUTURE));
        assertTrue(StrategyDefinitionType.MOVING_AVERAGE_CROSSOVER.isCompatibleWith(TradingModeType.TW_STOCK_AND_FUTURE));
    }

    @Test
    void allStrategies_shouldHaveUniqueClassNames() {
        long uniqueCount = java.util.Arrays.stream(StrategyDefinitionType.values())
            .map(StrategyDefinitionType::getClassName)
            .distinct()
            .count();
        
        assertEquals(StrategyDefinitionType.values().length, uniqueCount, 
            "All strategies should have unique class names");
    }

    @Test
    void allStrategies_shouldHaveUniqueDisplayNames() {
        long uniqueCount = java.util.Arrays.stream(StrategyDefinitionType.values())
            .map(StrategyDefinitionType::getDisplayName)
            .distinct()
            .count();
        
        assertEquals(StrategyDefinitionType.values().length, uniqueCount, 
            "All strategies should have unique display names");
    }

    @Test
    void getClassName_shouldNotBeNullOrEmpty() {
        for (StrategyDefinitionType strategy : StrategyDefinitionType.values()) {
            assertNotNull(strategy.getClassName());
            assertFalse(strategy.getClassName().isEmpty());
        }
    }

    @Test
    void getDisplayName_shouldNotBeNullOrEmpty() {
        for (StrategyDefinitionType strategy : StrategyDefinitionType.values()) {
            assertNotNull(strategy.getDisplayName());
            assertFalse(strategy.getDisplayName().isEmpty());
        }
    }
}
