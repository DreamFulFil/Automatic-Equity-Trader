package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Strategy-Stock Performance Mapping
 * 
 * Tracks which strategies work best for which stocks.
 * Helps users understand optimal strategy-stock combinations.
 */
@Entity
@Table(name = "strategy_stock_mapping", indexes = {
    @Index(name = "idx_mapping_symbol", columnList = "symbol"),
    @Index(name = "idx_mapping_strategy", columnList = "strategy_name"),
    @Index(name = "idx_mapping_performance", columnList = "sharpe_ratio DESC"),
    @Index(name = "idx_mapping_composite", columnList = "symbol, strategy_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyStockMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Stock symbol (e.g., "2330.TW")
     */
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    /**
     * Stock display name
     */
    @Column(name = "stock_name", length = 100)
    private String stockName;
    
    /**
     * Strategy name
     */
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    /**
     * Performance score (Sharpe ratio)
     */
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    /**
     * Total return percentage
     */
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    /**
     * Win rate percentage
     */
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    /**
     * Maximum drawdown percentage
     */
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    /**
     * Total number of trades
     */
    @Column(name = "total_trades")
    private Integer totalTrades;
    
    /**
     * Average profit per trade
     */
    @Column(name = "avg_profit_per_trade")
    private Double avgProfitPerTrade;
    
    /**
     * Risk level assessment
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;
    
    /**
     * Is this the recommended strategy for this stock?
     */
    @Column(name = "is_recommended")
    @Builder.Default
    private boolean recommended = false;
    
    /**
     * Backtesting period start
     */
    @Column(name = "backtest_start")
    private LocalDateTime backtestStart;
    
    /**
     * Backtesting period end
     */
    @Column(name = "backtest_end")
    private LocalDateTime backtestEnd;
    
    /**
     * Last updated timestamp
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * AI-generated insights about this combination
     */
    @Column(name = "ai_insights", columnDefinition = "TEXT")
    private String aiInsights;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        
        // Auto-calculate risk level based on metrics
        if (maxDrawdownPct != null && sharpeRatio != null) {
            if (Math.abs(maxDrawdownPct) > 15 || sharpeRatio < 0.5) {
                this.riskLevel = "HIGH";
            } else if (Math.abs(maxDrawdownPct) > 10 || sharpeRatio < 1.0) {
                this.riskLevel = "MEDIUM";
            } else {
                this.riskLevel = "LOW";
            }
        }
    }
}
