package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ActiveShadowSelection Entity - The final selection table.
 * 
 * Exactly 11 rows:
 * - Row 1 (rank 1): Active stock + strategy
 * - Rows 2-11 (rank 2-11): Shadow stock + strategy pairs
 * 
 * Updated automatically whenever selection logic runs.
 * Source attribution tracks whether selection came from BACKTEST, FRONTTEST, or COMBINATION.
 */
@Entity
@Table(name = "active_shadow_selection", indexes = {
    @Index(name = "idx_selection_rank", columnList = "rank_position", unique = true),
    @Index(name = "idx_selection_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveShadowSelection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "rank_position", nullable = false, unique = true)
    private Integer rankPosition;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "stock_name", nullable = false, length = 200)
    private String stockName;
    
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    @Column(name = "source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SelectionSource source;
    
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    @Column(name = "composite_score")
    private Double compositeScore;
    
    @Column(name = "selected_at")
    @Builder.Default
    private LocalDateTime selectedAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    public enum SelectionSource {
        BACKTEST,
        FRONTTEST,
        COMBINATION
    }
}
