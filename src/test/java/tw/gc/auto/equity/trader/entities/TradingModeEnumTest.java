package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingModeEnumTest {

    @Test
    void fromCode_withValidStockCode_shouldReturnTWStock() {
        assertEquals(TradingModeEnum.TW_STOCK, TradingModeEnum.fromCode("stock"));
    }

    @Test
    void fromCode_withValidFuturesCode_shouldReturnTWFuture() {
        assertEquals(TradingModeEnum.TW_FUTURE, TradingModeEnum.fromCode("futures"));
    }

    @Test
    void fromCode_withValidStockAndFuturesCode_shouldReturnTWStockAndFuture() {
        assertEquals(TradingModeEnum.TW_STOCK_AND_FUTURE, TradingModeEnum.fromCode("stock_and_futures"));
    }

    @Test
    void fromCode_withInvalidCode_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> TradingModeEnum.fromCode("invalid"));
    }

    @Test
    void fromCode_caseInsensitive_shouldWork() {
        assertEquals(TradingModeEnum.TW_STOCK, TradingModeEnum.fromCode("STOCK"));
        assertEquals(TradingModeEnum.TW_FUTURE, TradingModeEnum.fromCode("FUTURES"));
    }

    @Test
    void includesStock_withTWStock_shouldReturnTrue() {
        assertTrue(TradingModeEnum.TW_STOCK.includesStock());
    }

    @Test
    void includesStock_withTWFuture_shouldReturnFalse() {
        assertFalse(TradingModeEnum.TW_FUTURE.includesStock());
    }

    @Test
    void includesStock_withTWStockAndFuture_shouldReturnTrue() {
        assertTrue(TradingModeEnum.TW_STOCK_AND_FUTURE.includesStock());
    }

    @Test
    void includesFutures_withTWFuture_shouldReturnTrue() {
        assertTrue(TradingModeEnum.TW_FUTURE.includesFutures());
    }

    @Test
    void includesFutures_withTWStock_shouldReturnFalse() {
        assertFalse(TradingModeEnum.TW_STOCK.includesFutures());
    }

    @Test
    void includesFutures_withTWStockAndFuture_shouldReturnTrue() {
        assertTrue(TradingModeEnum.TW_STOCK_AND_FUTURE.includesFutures());
    }

    @Test
    void getCode_shouldReturnCorrectCode() {
        assertEquals("stock", TradingModeEnum.TW_STOCK.getCode());
        assertEquals("futures", TradingModeEnum.TW_FUTURE.getCode());
        assertEquals("stock_and_futures", TradingModeEnum.TW_STOCK_AND_FUTURE.getCode());
    }

    @Test
    void getDescription_shouldReturnNonEmptyDescription() {
        assertNotNull(TradingModeEnum.TW_STOCK.getDescription());
        assertFalse(TradingModeEnum.TW_STOCK.getDescription().isEmpty());
        assertNotNull(TradingModeEnum.TW_FUTURE.getDescription());
        assertFalse(TradingModeEnum.TW_FUTURE.getDescription().isEmpty());
    }
}
