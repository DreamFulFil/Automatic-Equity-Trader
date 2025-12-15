package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active Stock Service
 * 
 * Manages the currently active stock symbol for trading in stock mode.
 * Provides dynamic stock selection instead of hardcoded symbol.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActiveStockService {
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final String DEFAULT_STOCK = "2454.TW"; // MediaTek as fallback
    private static final String CONFIG_KEY = "CURRENT_ACTIVE_STOCK";
    
    /**
     * Get the currently active stock symbol
     */
    public String getActiveStock() {
        try {
            String symbol = jdbcTemplate.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = ?",
                String.class,
                CONFIG_KEY
            );
            return symbol != null ? symbol : DEFAULT_STOCK;
        } catch (Exception e) {
            log.warn("Failed to get active stock from database, using default: {}", DEFAULT_STOCK);
            return DEFAULT_STOCK;
        }
    }
    
    /**
     * Set the active stock symbol
     */
    @Transactional
    public void setActiveStock(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        
        try {
            // Insert or update
            jdbcTemplate.update(
                "INSERT INTO system_config (config_key, config_value, description) " +
                "VALUES (?, ?, 'Currently active stock symbol for trading in stock mode') " +
                "ON CONFLICT (config_key) DO UPDATE SET config_value = EXCLUDED.config_value",
                CONFIG_KEY, symbol
            );
            
            log.info("Active stock changed to: {}", symbol);
        } catch (Exception e) {
            log.error("Failed to set active stock: {}", e.getMessage());
            throw new RuntimeException("Failed to update active stock", e);
        }
    }
    
    /**
     * Get active symbol based on trading mode
     */
    public String getActiveSymbol(String tradingMode) {
        if ("stock".equals(tradingMode)) {
            return getActiveStock();
        } else {
            return "AUTO_EQUITY_TRADER"; // Futures mode
        }
    }
}
