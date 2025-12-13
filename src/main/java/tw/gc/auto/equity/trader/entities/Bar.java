package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OHLCV Bar Data
 * Aggregated price/volume bars for technical analysis and backtesting
 * Supports multiple timeframes (1min, 3min, 5min, 15min, 1hour, 1day)
 */
@Entity
@Table(name = "bar", indexes = {
    @Index(name = "idx_bar_timestamp", columnList = "timestamp"),
    @Index(name = "idx_bar_symbol", columnList = "symbol"),
    @Index(name = "idx_bar_symbol_timeframe_time", columnList = "symbol,timeframe,timestamp"),
    @Index(name = "idx_bar_symbol_timeframe", columnList = "symbol,timeframe")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bar {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Bar timestamp (opening time of bar)
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * Trading symbol (e.g., "2454.TW", "AUTO_EQUITY_TRADER")
     */
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    /**
     * Market identifier (e.g., "TSE", "TAIFEX")
     */
    @Column(name = "market", length = 20)
    private String market;
    
    /**
     * Timeframe (e.g., "1min", "3min", "5min", "15min", "1hour", "1day")
     */
    @Column(name = "timeframe", nullable = false, length = 10)
    private String timeframe;
    
    /**
     * Opening price
     */
    @Column(name = "open", nullable = false)
    private Double open;
    
    /**
     * Highest price
     */
    @Column(name = "high", nullable = false)
    private Double high;
    
    /**
     * Lowest price
     */
    @Column(name = "low", nullable = false)
    private Double low;
    
    /**
     * Closing price
     */
    @Column(name = "close", nullable = false)
    private Double close;
    
    /**
     * Total volume traded
     */
    @Column(name = "volume", nullable = false)
    private Long volume;
    
    /**
     * Number of trades
     */
    @Column(name = "trade_count")
    private Integer tradeCount;
    
    /**
     * Volume-weighted average price
     */
    @Column(name = "vwap")
    private Double vwap;
    
    /**
     * Buy volume (if available)
     */
    @Column(name = "buy_volume")
    private Long buyVolume;
    
    /**
     * Sell volume (if available)
     */
    @Column(name = "sell_volume")
    private Long sellVolume;
    
    /**
     * Whether this bar is complete/closed
     */
    @Column(name = "is_complete")
    @Builder.Default
    private boolean isComplete = true;
}
