package tw.gc.auto.equity.trader.entities;

/**
 * Trading mode enumeration for multi-market support.
 * Defines which markets the bot operates in.
 */
public enum TradingModeEnum {
    /**
     * Taiwan stock market only (TSE/OTC)
     * Examples: 2454.TW (MediaTek), 2330.TW (TSMC)
     */
    TW_STOCK("stock", "Taiwan Stock Market"),
    
    /**
     * Taiwan futures market only (TAIFEX)
     * Examples: TXFR1 (Taiwan Index Futures)
     */
    TW_FUTURE("futures", "Taiwan Futures Market"),
    
    /**
     * Both Taiwan stock and futures markets
     * Allows concurrent trading in both markets
     */
    TW_STOCK_AND_FUTURE("stock_and_futures", "Taiwan Stock & Futures Markets");
    
    private final String code;
    private final String description;
    
    TradingModeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Parse from code string (e.g., "stock", "futures")
     */
    public static TradingModeEnum fromCode(String code) {
        for (TradingModeEnum mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        // Legacy compatibility: map old "stock" to TW_STOCK
        if ("stock".equalsIgnoreCase(code)) {
            return TW_STOCK;
        }
        throw new IllegalArgumentException("Unknown trading mode code: " + code);
    }
    
    /**
     * Check if this mode includes stock trading
     */
    public boolean includesStock() {
        return this == TW_STOCK || this == TW_STOCK_AND_FUTURE;
    }
    
    /**
     * Check if this mode includes futures trading
     */
    public boolean includesFutures() {
        return this == TW_FUTURE || this == TW_STOCK_AND_FUTURE;
    }
}
