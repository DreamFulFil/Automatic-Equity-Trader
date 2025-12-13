package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Multi-Market Configuration
 * Supports simultaneous, conflict-free operation across multiple markets
 * 
 * Markets can include:
 * - US equities (NYSE, NASDAQ)
 * - Taiwan equities (TSE, OTC)
 * - Futures (TAIFEX, CME)
 * - Others as configured
 */
@Entity
@Table(name = "market_config", indexes = {
    @Index(name = "idx_market_code", columnList = "market_code", unique = true),
    @Index(name = "idx_market_enabled", columnList = "enabled")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Market code (e.g., "TSE", "TAIFEX", "NYSE", "NASDAQ")
     */
    @Column(name = "market_code", nullable = false, unique = true, length = 20)
    private String marketCode;
    
    /**
     * Market display name
     */
    @Column(name = "market_name", nullable = false, length = 100)
    private String marketName;
    
    /**
     * Market region/country
     */
    @Column(name = "region", length = 50)
    private String region;
    
    /**
     * Market timezone (e.g., "Asia/Taipei", "America/New_York")
     */
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;
    
    /**
     * Trading session start time (market local time)
     */
    @Column(name = "session_start", nullable = false)
    private LocalTime sessionStart;
    
    /**
     * Trading session end time (market local time)
     */
    @Column(name = "session_end", nullable = false)
    private LocalTime sessionEnd;
    
    /**
     * Lunch break start (nullable if no lunch break)
     */
    @Column(name = "lunch_start")
    private LocalTime lunchStart;
    
    /**
     * Lunch break end (nullable if no lunch break)
     */
    @Column(name = "lunch_end")
    private LocalTime lunchEnd;
    
    /**
     * Whether this market is enabled for trading
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
    
    /**
     * Currency code (e.g., "TWD", "USD")
     */
    @Column(name = "currency", length = 10)
    private String currency;
    
    /**
     * Minimum tick size
     */
    @Column(name = "tick_size")
    private Double tickSize;
    
    /**
     * Default contract multiplier (for futures)
     */
    @Column(name = "contract_multiplier")
    private Integer contractMultiplier;
    
    /**
     * Market-specific risk settings reference
     */
    @Column(name = "risk_settings_id")
    private Long riskSettingsId;
    
    /**
     * API endpoint/bridge URL for this market
     */
    @Column(name = "api_endpoint", length = 200)
    private String apiEndpoint;
    
    /**
     * Market-specific configuration JSON
     */
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;
    
    /**
     * Whether market supports pre-market trading
     */
    @Column(name = "supports_premarket")
    @Builder.Default
    private boolean supportsPremarket = false;
    
    /**
     * Whether market supports after-hours trading
     */
    @Column(name = "supports_afterhours")
    @Builder.Default
    private boolean supportsAfterhours = false;
    
    /**
     * Market data provider (e.g., "Shioaji", "IB", "Alpaca")
     */
    @Column(name = "data_provider", length = 50)
    private String dataProvider;
}
