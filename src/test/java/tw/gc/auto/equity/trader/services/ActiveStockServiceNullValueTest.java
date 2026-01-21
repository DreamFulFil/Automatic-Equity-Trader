package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveStockServiceNullValueTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ActiveStockService activeStockService;

    @BeforeEach
    void setUp() {
        activeStockService = new ActiveStockService(jdbcTemplate);
    }

    @Test
    void getActiveStock_whenQueryReturnsNull_shouldReturnDefaultStock() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("current_active_stock")))
                .thenReturn(null);

        assertEquals("2454.TW", activeStockService.getActiveStock());
    }
}
