package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveStockServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ActiveStockService activeStockService;

    @BeforeEach
    void setUp() {
        activeStockService = new ActiveStockService(jdbcTemplate);
    }

    @Test
    void getActiveStock_whenConfigExists_shouldReturnSymbol() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("current_active_stock")))
            .thenReturn("2454.TW");

        String result = activeStockService.getActiveStock();

        assertEquals("2454.TW", result);
        verify(jdbcTemplate).queryForObject(anyString(), eq(String.class), eq("current_active_stock"));
    }

    @Test
    void getActiveStock_whenConfigNotExists_shouldReturnDefault() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("current_active_stock")))
            .thenThrow(new EmptyResultDataAccessException(1));

        String result = activeStockService.getActiveStock();

        assertEquals("2454.TW", result); // Default MediaTek
    }

    @Test
    void getActiveStock_whenDatabaseError_shouldReturnDefault() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("current_active_stock")))
            .thenThrow(new RuntimeException("Database error"));

        String result = activeStockService.getActiveStock();

        assertEquals("2454.TW", result); // Default MediaTek
    }

    @Test
    void setActiveStock_whenValidSymbol_shouldUpdateDatabase() {
        activeStockService.setActiveStock("2330.TW");

        verify(jdbcTemplate).update(
            contains("INSERT INTO system_config"),
            eq("current_active_stock"),
            eq("2330.TW")
        );
    }

    @Test
    void setActiveStock_whenNullSymbol_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            activeStockService.setActiveStock(null);
        });

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void setActiveStock_whenEmptySymbol_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> {
            activeStockService.setActiveStock("  ");
        });

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void setActiveStock_whenDatabaseError_shouldThrowRuntimeException() {
        when(jdbcTemplate.update(anyString(), any(), any()))
            .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> {
            activeStockService.setActiveStock("2330.TW");
        });
    }

    @Test
    void getActiveSymbol_whenStockMode_shouldReturnActiveStock() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("current_active_stock")))
            .thenReturn("2454.TW");

        String result = activeStockService.getActiveSymbol("stock");

        assertEquals("2454.TW", result);
    }

    @Test
    void getActiveSymbol_whenFuturesMode_shouldReturnFuturesSymbol() {
        String result = activeStockService.getActiveSymbol("futures");

        assertEquals("AUTO_EQUITY_TRADER", result);
        verifyNoInteractions(jdbcTemplate); // Should not query database for futures
    }

    @Test
    void getActiveSymbol_whenStockModeAndDatabaseError_shouldReturnDefault() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("current_active_stock")))
            .thenThrow(new RuntimeException("Database error"));

        String result = activeStockService.getActiveSymbol("stock");

        assertEquals("2454.TW", result); // Default MediaTek
    }
}
