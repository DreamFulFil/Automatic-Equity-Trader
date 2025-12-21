package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Centralized risk management settings - single source of truth.
 * All risk parameters configurable via Telegram.
 * Designed for extremely risk-averse trading (80,000 TWD starting capital).
 */
@Entity
@Table(name = "stock_risk_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockRiskSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== POSITION SIZING ==========
    
    /** Maximum shares per trade (Taiwan odd-lot restrictions apply) */
    @Column(name = "max_shares_per_trade", nullable = false)
    @Builder.Default
    private int maxSharesPerTrade = 50;

    // ========== LOSS LIMITS ==========
    
    /** Daily loss limit in TWD - emergency halt if exceeded */
    @Column(name = "daily_loss_limit_twd", nullable = false)
    @Builder.Default
    private int dailyLossLimitTwd = 1000;

    /** Weekly loss limit in TWD - pause trading until next week */
    @Column(name = "weekly_loss_limit_twd", nullable = false)
    @Builder.Default
    private int weeklyLossLimitTwd = 4000;

    /** Per-trade stop loss in TWD */
    @Column(name = "stop_loss_twd_per_trade", nullable = false)
    @Builder.Default
    private int stopLossTwdPerTrade = 800;

    // ========== TRADE FREQUENCY ==========
    
    /** Maximum trades per day */
    @Column(name = "max_daily_trades", nullable = false)
    @Builder.Default
    private int maxDailyTrades = 2;

    /** Minimum hold time before exit (minutes) */
    @Column(name = "min_hold_minutes", nullable = false)
    @Builder.Default
    private int minHoldMinutes = 15;

    /** Maximum hold time before forced exit (minutes) */
    @Column(name = "max_hold_minutes", nullable = false)
    @Builder.Default
    private int maxHoldMinutes = 45;

    // ========== STRATEGY QUALITY FILTERS ==========
    
    /** Minimum Sharpe ratio for strategy eligibility */
    @Column(name = "min_sharpe_ratio", nullable = false)
    @Builder.Default
    private double minSharpeRatio = 1.5;

    /** Minimum win rate (0.0 - 1.0) */
    @Column(name = "min_win_rate", nullable = false)
    @Builder.Default
    private double minWinRate = 0.55;

    /** Maximum drawdown percent (0-100) */
    @Column(name = "max_drawdown_percent", nullable = false)
    @Builder.Default
    private double maxDrawdownPercent = 15.0;

    /** Minimum backtest period in days */
    @Column(name = "strategy_backtest_days", nullable = false)
    @Builder.Default
    private int strategyBacktestDays = 730;

    /** Minimum number of trades in backtest for validity */
    @Column(name = "min_total_trades_in_backtest", nullable = false)
    @Builder.Default
    private int minTotalTradesInBacktest = 150;

    // ========== AI & FILTERS ==========
    
    /** Enable Ollama AI veto on every trade */
    @Column(name = "enable_ai_veto", nullable = false)
    @Builder.Default
    private boolean enableAiVeto = true;

    /** Enable volatility-based trade filtering */
    @Column(name = "enable_volatility_filter", nullable = false)
    @Builder.Default
    private boolean enableVolatilityFilter = true;

    /** Volatility threshold multiplier (1.0 = normal, 1.8 = block if 1.8x normal ATR) */
    @Column(name = "volatility_threshold_multiplier", nullable = false)
    @Builder.Default
    private double volatilityThresholdMultiplier = 1.8;

    // ========== METADATA ==========
    
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;
}