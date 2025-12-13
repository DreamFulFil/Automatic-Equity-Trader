package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DailyStatistics entity for storing end-of-day trading statistics.
 * Calculated by Java application at market close (13:00 Taiwan time).
 * Used for performance analysis, insights generation, and chart data.
 */
@Entity
@Table(name = "daily_statistics", indexes = {
    @Index(name = "idx_daily_stats_date", columnList = "trade_date"),
    @Index(name = "idx_daily_stats_symbol", columnList = "symbol")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private LocalDateTime calculatedAt;

    @Column(length = 20, nullable = false)
    private String symbol; // e.g., "2454.TW", "AUTO_EQUITY_TRADER"

    @Column(name = "strategy_name", length = 100)
    private String strategyName; // Name of the strategy for these stats

    // ========== PRICE STATISTICS ==========
    @Column(name = "open_price")
    private Double openPrice;

    @Column(name = "close_price")
    private Double closePrice;

    @Column(name = "high_price")
    private Double highPrice;

    @Column(name = "low_price")
    private Double lowPrice;

    @Column(name = "price_change")
    private Double priceChange; // Close - Open

    @Column(name = "price_change_pct")
    private Double priceChangePct; // (Close - Open) / Open * 100

    @Column(name = "daily_range")
    private Double dailyRange; // High - Low

    @Column(name = "daily_range_pct")
    private Double dailyRangePct; // (High - Low) / Open * 100

    // ========== VOLUME STATISTICS ==========
    @Column(name = "total_volume")
    private Long totalVolume;

    @Column(name = "average_volume")
    private Double averageVolume;

    @Column(name = "volume_vs_avg")
    private Double volumeVsAvg; // Today's volume / 20-day average

    // ========== TRADING PERFORMANCE ==========
    @Column(name = "total_trades")
    private Integer totalTrades;

    @Column(name = "winning_trades")
    private Integer winningTrades;

    @Column(name = "losing_trades")
    private Integer losingTrades;

    @Column(name = "win_rate")
    private Double winRate; // Winning / Total * 100

    @Column(name = "realized_pnl")
    private Double realizedPnL; // Sum of all closed trades

    @Column(name = "unrealized_pnl")
    private Double unrealizedPnL; // Open position P&L at close

    @Column(name = "total_pnl")
    private Double totalPnL; // Realized + Unrealized

    @Column(name = "max_drawdown")
    private Double maxDrawdown; // Largest intraday loss

    @Column(name = "max_profit")
    private Double maxProfit; // Largest intraday gain

    @Column(name = "avg_trade_pnl")
    private Double avgTradePnL; // Average P&L per trade

    @Column(name = "avg_winner_pnl")
    private Double avgWinnerPnL;

    @Column(name = "avg_loser_pnl")
    private Double avgLoserPnL;

    @Column(name = "profit_factor")
    private Double profitFactor; // Gross profit / Gross loss

    // ========== SIGNAL STATISTICS ==========
    @Column(name = "signals_generated")
    private Integer signalsGenerated;

    @Column(name = "signals_long")
    private Integer signalsLong;

    @Column(name = "signals_short")
    private Integer signalsShort;

    @Column(name = "signals_acted_on")
    private Integer signalsActedOn; // Signals that resulted in trades

    @Column(name = "avg_signal_confidence")
    private Double avgSignalConfidence;

    @Column(name = "news_veto_count")
    private Integer newsVetoCount;

    // ========== TECHNICAL INDICATORS AT CLOSE ==========
    @Column(name = "rsi_close")
    private Double rsiClose;

    @Column(name = "macd_close")
    private Double macdClose;

    @Column(name = "sma_20_close")
    private Double sma20Close;

    @Column(name = "sma_50_close")
    private Double sma50Close;

    @Column(name = "atr_close")
    private Double atrClose;

    @Column(name = "vwap_close")
    private Double vwapClose;

    // ========== TIME STATISTICS ==========
    @Column(name = "avg_hold_minutes")
    private Double avgHoldMinutes;

    @Column(name = "max_hold_minutes")
    private Integer maxHoldMinutes;

    @Column(name = "min_hold_minutes")
    private Integer minHoldMinutes;

    @Column(name = "time_in_market_minutes")
    private Integer timeInMarketMinutes; // Total minutes with open position

    // ========== RISK METRICS ==========
    @Column(name = "sharpe_ratio")
    private Double sharpeRatio; // Risk-adjusted return

    @Column(name = "sortino_ratio")
    private Double sortinoRatio; // Downside risk-adjusted return

    @Column(name = "calmar_ratio")
    private Double calmarRatio; // Return / Max drawdown

    // ========== CUMULATIVE STATISTICS ==========
    @Column(name = "cumulative_pnl")
    private Double cumulativePnL; // Running total P&L since start

    @Column(name = "cumulative_trades")
    private Integer cumulativeTrades; // Running total trades

    @Column(name = "cumulative_win_rate")
    private Double cumulativeWinRate; // Overall win rate

    @Column(name = "consecutive_wins")
    private Integer consecutiveWins; // Current winning streak

    @Column(name = "consecutive_losses")
    private Integer consecutiveLosses; // Current losing streak

    @Column(name = "equity_high_watermark")
    private Double equityHighWatermark; // Highest equity reached

    // ========== INSIGHTS ==========
    @Column(name = "llama_insight", columnDefinition = "TEXT")
    private String llamaInsight; // AI-generated insight from Llama

    @Column(name = "insight_generated_at")
    private LocalDateTime insightGeneratedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trading_mode")
    private Trade.TradingMode tradingMode;

    @Column(length = 500)
    private String notes; // Manual notes or observations
}
