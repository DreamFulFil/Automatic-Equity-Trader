package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * BacktestResult Entity - Persistent storage of comprehensive backtest results.
 * 
 * Stores ~50 stocks × 100 strategies = ~50,000 rows per backtest run.
 * 
 * Metrics align with industry-standard backtesting frameworks (e.g., Backtrader, VectorBT):
 * - Time metrics: duration, exposure time
 * - Equity metrics: final, peak, return, buy & hold return
 * - Risk-adjusted metrics: Sharpe, Sortino, Calmar ratios
 * - Drawdown metrics: max/avg drawdown, max/avg drawdown duration
 * - Trade metrics: win rate, best/worst/avg trade, trade durations
 * - Quality metrics: profit factor, expectancy, SQN
 * 
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

    // ========== Configuration ==========
    @Column(name = "initial_capital")
    private Double initialCapital;
    
    @Column(name = "commission_rate")
    private Double commissionRate;
    
    @Column(name = "slippage_rate")
    private Double slippageRate;

    // ========== Time Metrics ==========
    @Column(name = "backtest_period_start")
    private LocalDateTime backtestPeriodStart;
    
    @Column(name = "backtest_period_end")
    private LocalDateTime backtestPeriodEnd;
    
    @Column(name = "duration_days")
    private Integer durationDays;
    
    @Column(name = "exposure_time_pct")
    private Double exposureTimePct;

    // ========== Equity Metrics ==========
    @Column(name = "final_equity")
    private Double finalEquity;
    
    @Column(name = "equity_peak")
    private Double equityPeak;
    
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    @Column(name = "buy_hold_return_pct")
    private Double buyHoldReturnPct;

    // ========== Annualized Metrics ==========
    @Column(name = "annual_return_pct")
    private Double annualReturnPct;
    
    @Column(name = "annual_volatility_pct")
    private Double annualVolatilityPct;

    // ========== Risk-Adjusted Ratios ==========
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    @Column(name = "sortino_ratio")
    private Double sortinoRatio;
    
    @Column(name = "calmar_ratio")
    private Double calmarRatio;

    // ========== Drawdown Metrics ==========
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    @Column(name = "avg_drawdown_pct")
    private Double avgDrawdownPct;
    
    @Column(name = "max_drawdown_duration_days")
    private Integer maxDrawdownDurationDays;
    
    @Column(name = "avg_drawdown_duration_days")
    private Double avgDrawdownDurationDays;

    // ========== Trade Statistics ==========
    @Column(name = "total_trades")
    private Integer totalTrades;
    
    @Column(name = "winning_trades")
    private Integer winningTrades;
    
    @Column(name = "losing_trades")
    private Integer losingTrades;
    
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    @Column(name = "best_trade_pct")
    private Double bestTradePct;
    
    @Column(name = "worst_trade_pct")
    private Double worstTradePct;
    
    @Column(name = "avg_trade_pct")
    private Double avgTradePct;
    
    @Column(name = "avg_profit_per_trade")
    private Double avgProfitPerTrade;

    // ========== Trade Duration ==========
    @Column(name = "max_trade_duration_days")
    private Integer maxTradeDurationDays;
    
    @Column(name = "avg_trade_duration_days")
    private Double avgTradeDurationDays;

    // ========== Quality Metrics ==========
    @Column(name = "profit_factor")
    private Double profitFactor;
    
    @Column(name = "expectancy")
    private Double expectancy;
    
    @Column(name = "sqn")
    private Double sqn;
    
    @Column(name = "data_points")
    private Integer dataPoints;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;
}
