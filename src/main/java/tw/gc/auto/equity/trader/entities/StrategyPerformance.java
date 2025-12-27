package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * StrategyPerformance Entity - Performance metrics for strategy evaluation.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Backtesting</b>: BACKTEST mode stores historical simulation results</li>
 *   <li><b>Shadow Mode</b>: SHADOW mode tracks parallel live simulation metrics</li>
 *   <li><b>Main Trading</b>: MAIN mode captures active strategy performance</li>
 *   <li><b>Auto-Selection</b>: Data source for {@link AutoStrategySelector} decisions</li>
 *   <li><b>Drawdown Monitor</b>: MDD tracking for emergency strategy switching</li>
 * </ul>
 * 
 * <h3>Key Metrics:</h3>
 * <ul>
 *   <li>Sharpe Ratio: Risk-adjusted return (target > 1.0)</li>
 *   <li>Max Drawdown: Largest peak-to-trough decline (target < 20%)</li>
 *   <li>Win Rate: Percentage of profitable trades (target > 50%)</li>
 *   <li>Profit Factor: Gross profit / Gross loss (target > 1.5)</li>
 * </ul>
 * 
 * @see AutoStrategySelector for strategy selection logic
 * @see DrawdownMonitorService for MDD-based switching
 */
@Entity
@Table(name = "strategy_performance", indexes = {
    @Index(name = "idx_strategy_performance_name", columnList = "strategy_name"),
    @Index(name = "idx_strategy_performance_mode", columnList = "performance_mode"),
    @Index(name = "idx_strategy_performance_period", columnList = "period_start, period_end")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyPerformance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Strategy name
     */
    @Column(name = "strategy_name", nullable = false, length = 100)
    private String strategyName;
    
    /**
     * Performance mode: BACKTEST, SHADOW, MAIN
     */
    @Column(name = "performance_mode", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PerformanceMode performanceMode;
    
    /**
     * Symbol traded (if applicable)
     */
    @Column(name = "symbol", length = 50)
    private String symbol;
    
    /**
     * Period start date
     */
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;
    
    /**
     * Period end date
     */
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;
    
    /**
     * Total number of trades
     */
    @Column(name = "total_trades")
    @Builder.Default
    private int totalTrades = 0;
    
    /**
     * Number of winning trades
     */
    @Column(name = "winning_trades")
    @Builder.Default
    private int winningTrades = 0;
    
    /**
     * Win rate percentage
     */
    @Column(name = "win_rate_pct")
    private Double winRatePct;
    
    /**
     * Total P&L
     */
    @Column(name = "total_pnl")
    @Builder.Default
    private double totalPnl = 0.0;
    
    /**
     * Sharpe Ratio (risk-adjusted return)
     */
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;
    
    /**
     * Maximum Drawdown (%)
     */
    @Column(name = "max_drawdown_pct")
    private Double maxDrawdownPct;
    
    /**
     * Total Return (%)
     */
    @Column(name = "total_return_pct")
    private Double totalReturnPct;
    
    /**
     * Average trade P&L
     */
    @Column(name = "avg_trade_pnl")
    private Double avgTradePnl;
    
    /**
     * Average winning trade
     */
    @Column(name = "avg_win")
    private Double avgWin;
    
    /**
     * Average losing trade
     */
    @Column(name = "avg_loss")
    private Double avgLoss;
    
    /**
     * Profit factor (gross profit / gross loss)
     */
    @Column(name = "profit_factor")
    private Double profitFactor;
    
    /**
     * Parameters used for this performance period (JSON)
     */
    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;
    
    /**
     * When this performance record was calculated
     */
    @Column(name = "calculated_at", nullable = false)
    @Builder.Default
    private LocalDateTime calculatedAt = LocalDateTime.now();
    
    /**
     * Performance mode types
     */
    public enum PerformanceMode {
        /** Historical backtesting */
        BACKTEST,
        
        /** Shadow mode (parallel simulation) */
        SHADOW,
        
        /** Main strategy (active trading) */
        MAIN
    }
}
