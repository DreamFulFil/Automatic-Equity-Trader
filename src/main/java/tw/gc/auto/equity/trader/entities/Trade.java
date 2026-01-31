package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Trade Entity - Core record for all trading activity in the system.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Entry Recording</b>: Captures BUY/SELL actions with entry price, quantity, and strategy</li>
 *   <li><b>Exit Recording</b>: Updates with exit price, realized P&L, and hold duration on close</li>
 *   <li><b>Performance Analysis</b>: Source data for strategy performance calculations</li>
 *   <li><b>/golive Eligibility</b>: Win rate and drawdown calculations for live trading approval</li>
 * </ul>
 * 
 * <h3>Asset Type:</h3>
 * The {@code assetType} column defaults to {@code STOCK} globally. Futures logic is not yet live,
 * but the schema supports future expansion to TAIFEX futures trading.
 * 
 * @see StrategyPerformance for aggregated strategy metrics
 * @see DailyStatistics for daily aggregations
 */
@Entity
@Table(name = "trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeAction action;
    
    @Column(nullable = false)
    private int quantity;
    
    @Column(name = "entry_price", nullable = false)
    private double entryPrice;
    
    @Column(name = "exit_price")
    private Double exitPrice;
    
    @Column(name = "realized_pnl")
    private Double realizedPnL;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TradingMode mode = TradingMode.SIMULATION;
    
    @Column(length = 50)
    private String symbol; // e.g., "2454.TW", "AUTO_EQUITY_TRADER"

    @Column(name = "strategy_name", length = 100)
    private String strategyName; // Name of the strategy that generated this trade
    
    @Column(length = 200)
    private String reason; // Entry/exit reason from signal
    
    @Column(name = "signal_confidence")
    private Double signalConfidence;
    
    @Column(name = "hold_duration_minutes")
    private Integer holdDurationMinutes;

    @Column(name = "executed_slippage")
    private Double executedSlippage;

    @Column(name = "market_regime_at_entry", length = 50)
    private String marketRegimeAtEntry;

    @Column(name = "volatility_at_entry")
    private Double volatilityAtEntry;

    @Column(name = "order_book_spread_at_entry")
    private Double orderBookSpreadAtEntry;

    @Column(name = "alternative_signals_json", columnDefinition = "TEXT")
    private String alternativeSignalsJson;

    @Column(name = "expected_pnl")
    private Double expectedPnl;

    @Column(name = "news_context_json", columnDefinition = "TEXT")
    private String newsContextJson;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_status")
    @Builder.Default
    private TradeStatus status = TradeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    @Builder.Default
    private AssetType assetType = AssetType.STOCK;
    
    public enum TradeAction {
        BUY, SELL
    }
    
    public enum TradingMode {
        SIMULATION, LIVE
    }
    
    public enum TradeStatus {
        OPEN, CLOSED, CANCELLED
    }
    
    public enum AssetType {
        STOCK,
        FUTURE
    }
}
