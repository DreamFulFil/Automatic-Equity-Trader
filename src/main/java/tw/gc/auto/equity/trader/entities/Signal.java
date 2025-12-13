package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Signal entity for logging all trading signals.
 * Used for backtesting, signal analysis, and performance tracking.
 */
@Entity
@Table(name = "signals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignalDirection direction;

    @Column(nullable = false)
    private double confidence; // 0.0 to 1.0

    @Column(name = "current_price", nullable = false)
    private double currentPrice;

    @Column(name = "exit_signal")
    private Boolean exitSignal; // true if this is an exit signal

    @Column(name = "market_data", columnDefinition = "TEXT")
    private String marketData; // JSON string with RSI, volume, etc.

    @Column(length = 50)
    private String symbol; // e.g., "2454.TW", "AUTO_EQUITY_TRADER"

    @Column(length = 200)
    private String reason; // Signal generation reason

    @Column(name = "news_veto")
    private Boolean newsVeto; // true if news veto was active

    @Column(name = "news_score")
    private Double newsScore; // News sentiment score if available

    public enum SignalDirection {
        LONG, SHORT, HOLD
    }
}