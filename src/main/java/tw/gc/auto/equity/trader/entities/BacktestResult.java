package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BacktestResult Entity - Persistent storage of backtest results.
 * 
 * Stores ~50 stocks × 100 strategies = ~50,000 rows per backtest run.
 * Essential for:
 * - Strategy selection explainability
 * - Audit trail for decision-making
 * - Cross-run comparison
 * - Performance trending
 */
@Entity
@Table(name = "backtest_results", indexes = {
    @Index(name = "idx_backtest_run_id", columnList = "backtest_run_id"),
    @Index(name = "idx_backtest_symbol", columnList = "symbol"),
    @Index(name = "idx_backtest_strategy", columnList = "strategy_name"),
    @Index(name = "idx_backtest_composite", columnList = "backtest_run_id, symbol, strategy_name"),
    @Index(name = "idx_backtest_performance", columnList = "sharpe_ratio DESC, total_return_pct DESC")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "backtest_run_id", nullable = false, length = 50)
    private String backtestRunId;
    
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;
    
    @Column(name = "stock_name", length = 200)
    private String stockName;
    
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    @Column(name = "initial_capital")
    private Double initialCapital;
    
    @Column(name = "final_equity")
    private Double finalEquity;
    
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    @Column(name = "total_trades")
    private Integer totalTrades;
    
    @Column(name = "winning_trades")
    private Integer winningTrades;
    
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    @Column(name = "avg_profit_per_trade")
    private Double avgProfitPerTrade;
    
    @Column(name = "profit_factor")
    private Double profitFactor;
    
    @Column(name = "backtest_period_start")
    private LocalDateTime backtestPeriodStart;
    
    @Column(name = "backtest_period_end")
    private LocalDateTime backtestPeriodEnd;
    
    @Column(name = "data_points")
    private Integer dataPoints;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;
}
