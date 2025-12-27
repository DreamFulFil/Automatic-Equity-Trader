package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tick/Level-2 Quote Data
 * Stores real-time bid/ask quotes for intraday and long-term analysis
 */
@Entity
@Table(name = "quote", indexes = {
    @Index(name = "idx_quote_timestamp", columnList = "timestamp"),
    @Index(name = "idx_quote_symbol", columnList = "symbol"),
    @Index(name = "idx_quote_symbol_time", columnList = "symbol,timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Timestamp of quote
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
     * Best bid price
     */
    @Column(name = "bid_price")
    private Double bidPrice;
    
    /**
     * Best ask price
     */
    @Column(name = "ask_price")
    private Double askPrice;
    
    /**
     * Bid volume
     */
    @Column(name = "bid_volume")
    private Integer bidVolume;
    
    /**
     * Ask volume
     */
    @Column(name = "ask_volume")
    private Integer askVolume;
    
    /**
     * Mid price (bid+ask)/2
     */
    @Column(name = "mid_price")
    private Double midPrice;
    
    /**
     * Spread (ask-bid)
     */
    @Column(name = "spread")
    private Double spread;
    
    /**
     * Total bid depth (sum of all bid levels)
     */
    @Column(name = "total_bid_volume")
    private Integer totalBidVolume;
    
    /**
     * Total ask depth (sum of all ask levels)
     */
    @Column(name = "total_ask_volume")
    private Integer totalAskVolume;
    
    /**
     * Last trade price
     */
    @Column(name = "last_price")
    private Double lastPrice;
    
    /**
     * Last trade volume
     */
    @Column(name = "last_volume")
    private Integer lastVolume;
}
