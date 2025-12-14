package tw.gc.auto.equity.trader.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradingModeTypeTest {

    @Test
    void fromCode_withValidStockCode_shouldReturnTWStock() {
        assertEquals(TradingModeType.TW_STOCK, TradingModeType.fromCode("stock"));
    }

    @Test
    void fromCode_withValidFuturesCode_shouldReturnTWFuture() {
        assertEquals(TradingModeType.TW_FUTURE, TradingModeType.fromCode("futures"));
    }

    @Test
    void fromCode_withValidStockAndFuturesCode_shouldReturnTWStockAndFuture() {
        assertEquals(TradingModeType.TW_STOCK_AND_FUTURE, TradingModeType.fromCode("stock_and_futures"));
    }

    @Test
    void fromCode_withInvalidCode_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> TradingModeType.fromCode("invalid"));
    }

    @Test
    void fromCode_caseInsensitive_shouldWork() {
        assertEquals(TradingModeType.TW_STOCK, TradingModeType.fromCode("STOCK"));
        assertEquals(TradingModeType.TW_FUTURE, TradingModeType.fromCode("FUTURES"));
    }

    @Test
    void includesStock_withTWStock_shouldReturnTrue() {
        assertTrue(TradingModeType.TW_STOCK.includesStock());
    }

    @Test
    void includesStock_withTWFuture_shouldReturnFalse() {
        assertFalse(TradingModeType.TW_FUTURE.includesStock());
    }

    @Test
    void includesStock_withTWStockAndFuture_shouldReturnTrue() {
        assertTrue(TradingModeType.TW_STOCK_AND_FUTURE.includesStock());
    }

    @Test
    void includesFutures_withTWFuture_shouldReturnTrue() {
        assertTrue(TradingModeType.TW_FUTURE.includesFutures());
    }

    @Test
    void includesFutures_withTWStock_shouldReturnFalse() {
        assertFalse(TradingModeType.TW_STOCK.includesFutures());
    }

    @Test
    void includesFutures_withTWStockAndFuture_shouldReturnTrue() {
        assertTrue(TradingModeType.TW_STOCK_AND_FUTURE.includesFutures());
    }

    @Test
    void getCode_shouldReturnCorrectCode() {
        assertEquals("stock", TradingModeType.TW_STOCK.getCode());
        assertEquals("futures", TradingModeType.TW_FUTURE.getCode());
        assertEquals("stock_and_futures", TradingModeType.TW_STOCK_AND_FUTURE.getCode());
    }

    @Test
    void getDescription_shouldReturnNonEmptyDescription() {
        assertNotNull(TradingModeType.TW_STOCK.getDescription());
        assertFalse(TradingModeType.TW_STOCK.getDescription().isEmpty());
        assertNotNull(TradingModeType.TW_FUTURE.getDescription());
        assertFalse(TradingModeType.TW_FUTURE.getDescription().isEmpty());
    }
}
