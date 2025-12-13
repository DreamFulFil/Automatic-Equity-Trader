package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.strategy.StrategyType;

import static org.junit.jupiter.api.Assertions.*;

class StrategyEnumTest {

    @Test
    void fromClassName_withValidDCA_shouldReturnDCA() {
        assertEquals(StrategyEnum.DCA, StrategyEnum.fromClassName("DCA"));
    }

    @Test
    void fromClassName_withValidMovingAverageCrossover_shouldReturnCorrect() {
        assertEquals(StrategyEnum.MOVING_AVERAGE_CROSSOVER, 
            StrategyEnum.fromClassName("MovingAverageCrossover"));
    }

    @Test
    void fromClassName_caseInsensitive_shouldWork() {
        assertEquals(StrategyEnum.DCA, StrategyEnum.fromClassName("dca"));
        assertEquals(StrategyEnum.RSI, StrategyEnum.fromClassName("rsi"));
    }

    @Test
    void fromClassName_withInvalidName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, 
            () -> StrategyEnum.fromClassName("InvalidStrategy"));
    }

    @Test
    void fromDisplayName_withValidName_shouldReturnCorrect() {
        assertEquals(StrategyEnum.DCA, 
            StrategyEnum.fromDisplayName("Dollar Cost Averaging"));
    }

    @Test
    void fromDisplayName_withInvalidName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, 
            () -> StrategyEnum.fromDisplayName("Invalid Display Name"));
    }

    @Test
    void getType_shouldReturnCorrectStrategyType() {
        assertEquals(StrategyType.LONG_TERM, StrategyEnum.DCA.getType());
        assertEquals(StrategyType.SHORT_TERM, StrategyEnum.RSI.getType());
        assertEquals(StrategyType.INTRADAY, StrategyEnum.VWAP_EXECUTION.getType());
    }

    @Test
    void supportsStock_shouldReturnCorrectValue() {
        assertTrue(StrategyEnum.DCA.supportsStock());
        assertTrue(StrategyEnum.MOVING_AVERAGE_CROSSOVER.supportsStock());
    }

    @Test
    void supportsFutures_shouldReturnCorrectValue() {
        assertTrue(StrategyEnum.MOVING_AVERAGE_CROSSOVER.supportsFutures());
        assertTrue(StrategyEnum.RSI.supportsFutures());
    }

    @Test
    void isCompatibleWith_stockMode_shouldAllowStockStrategies() {
        assertTrue(StrategyEnum.DCA.isCompatibleWith(TradingModeEnum.TW_STOCK));
        assertTrue(StrategyEnum.MOVING_AVERAGE_CROSSOVER.isCompatibleWith(TradingModeEnum.TW_STOCK));
    }

    @Test
    void isCompatibleWith_futuresMode_shouldAllowFuturesStrategies() {
        assertTrue(StrategyEnum.MOVING_AVERAGE_CROSSOVER.isCompatibleWith(TradingModeEnum.TW_FUTURE));
        assertTrue(StrategyEnum.RSI.isCompatibleWith(TradingModeEnum.TW_FUTURE));
    }

    @Test
    void isCompatibleWith_stockAndFuturesMode_shouldAllowCompatibleStrategies() {
        assertTrue(StrategyEnum.DCA.isCompatibleWith(TradingModeEnum.TW_STOCK_AND_FUTURE));
        assertTrue(StrategyEnum.MOVING_AVERAGE_CROSSOVER.isCompatibleWith(TradingModeEnum.TW_STOCK_AND_FUTURE));
    }

    @Test
    void allStrategies_shouldHaveUniqueClassNames() {
        long uniqueCount = java.util.Arrays.stream(StrategyEnum.values())
            .map(StrategyEnum::getClassName)
            .distinct()
            .count();
        
        assertEquals(StrategyEnum.values().length, uniqueCount, 
            "All strategies should have unique class names");
    }

    @Test
    void allStrategies_shouldHaveUniqueDisplayNames() {
        long uniqueCount = java.util.Arrays.stream(StrategyEnum.values())
            .map(StrategyEnum::getDisplayName)
            .distinct()
            .count();
        
        assertEquals(StrategyEnum.values().length, uniqueCount, 
            "All strategies should have unique display names");
    }

    @Test
    void getClassName_shouldNotBeNullOrEmpty() {
        for (StrategyEnum strategy : StrategyEnum.values()) {
            assertNotNull(strategy.getClassName());
            assertFalse(strategy.getClassName().isEmpty());
        }
    }

    @Test
    void getDisplayName_shouldNotBeNullOrEmpty() {
        for (StrategyEnum strategy : StrategyEnum.values()) {
            assertNotNull(strategy.getDisplayName());
            assertFalse(strategy.getDisplayName().isEmpty());
        }
    }
}
