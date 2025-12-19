package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * StockUniverse Entity - Tracks ~50 selected stocks for automated trading.
 * 
 * Selection criteria includes:
 * - Liquidity (daily volume > threshold)
 * - Market cap (large/mid cap)
 * - Sector diversification
 * - Historical data availability
 */
@Entity
@Table(name = "stock_universe", indexes = {
    @Index(name = "idx_universe_symbol", columnList = "symbol", unique = true),
    @Index(name = "idx_universe_enabled", columnList = "enabled"),
    @Index(name = "idx_universe_selection_score", columnList = "selection_score DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockUniverse {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "symbol", nullable = false, unique = true, length = 20)
    private String symbol;
    
    @Column(name = "stock_name", nullable = false, length = 200)
    private String stockName;
    
    @Column(name = "sector", length = 100)
    private String sector;
    
    @Column(name = "market_cap")
    private Double marketCap;
    
    @Column(name = "avg_daily_volume")
    private Long avgDailyVolume;
    
    @Column(name = "selection_reason", columnDefinition = "TEXT")
    private String selectionReason;
    
    @Column(name = "selection_score")
    private Double selectionScore;
    
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
