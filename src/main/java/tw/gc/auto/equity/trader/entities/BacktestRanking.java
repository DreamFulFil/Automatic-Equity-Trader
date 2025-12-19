package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BacktestRanking Entity - Ranked results derived from backtest runs.
 * 
 * Used for both automatic and manual strategy selection.
 * Rankings are recomputed after each backtest or fronttest run.
 */
@Entity
@Table(name = "backtest_rankings", indexes = {
    @Index(name = "idx_ranking_run_id", columnList = "backtest_run_id"),
    @Index(name = "idx_ranking_rank", columnList = "rank_position"),
    @Index(name = "idx_ranking_composite", columnList = "backtest_run_id, rank_position")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestRanking {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "backtest_run_id", nullable = false, length = 50)
    private String backtestRunId;
    
    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "stock_name", length = 200)
    private String stockName;
    
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    @Column(name = "composite_score", nullable = false)
    private Double compositeScore;
    
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    @Column(name = "total_trades")
    private Integer totalTrades;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
