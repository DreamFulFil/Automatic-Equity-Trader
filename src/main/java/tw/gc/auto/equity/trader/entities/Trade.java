package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Trade entity for logging all trades (simulation and live).
 * Essential for backtesting, performance analysis, and /golive eligibility checks.
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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_status")
    @Builder.Default
    private TradeStatus status = TradeStatus.OPEN;
    
    public enum TradeAction {
        BUY, SELL
    }
    
    public enum TradingMode {
        SIMULATION, LIVE
    }
    
    public enum TradeStatus {
        OPEN, CLOSED, CANCELLED
    }
}
