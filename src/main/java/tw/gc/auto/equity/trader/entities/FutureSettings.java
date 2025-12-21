package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "future_settings")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FutureSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Futures contract code (e.g., MTX, TXF)
     */
    @Column(nullable = false, unique = true)
    private String contractCode;

    /**
     * Number of contracts to trade per order
     */
    @Column(nullable = false)
    private Integer contractsPerTrade;

    /**
     * Maximum number of open contracts allowed
     */
    @Column(nullable = false)
    private Integer maxOpenContracts;

    /**
     * Leverage ratio for futures trading
     */
    @Column(nullable = false)
    private Double leverage;

    /**
     * Minimum margin required per contract (TWD)
     */
    @Column(nullable = false)
    private Double minMarginPerContract;

    /**
     * Order type for execution (e.g., MARKET, LIMIT)
     */
    @Column(nullable = false)
    private String orderType;

    /**
     * Time-in-force for orders (e.g., GTC, IOC, FOK)
     */
    @Column(nullable = false)
    private String timeInForce;

    /**
     * Stop-loss amount per contract (TWD)
     */
    @Column(nullable = false)
    private Double stopLoss;

    /**
     * Take-profit amount per contract (TWD)
     */
    @Column(nullable = false)
    private Double takeProfit;

    /**
     * Daily loss limit for futures trading (TWD)
     */
    @Column(nullable = false)
    private Double dailyLossLimit;

    /**
     * Weekly loss limit for futures trading (TWD)
     */
    @Column(nullable = false)
    private Double weeklyLossLimit;

    /**
     * Commission fee per contract
     */
    @Column(nullable = false)
    private Double commissionPerContract;

    /**
     * Estimated slippage per contract
     */
    @Column(nullable = false)
    private Double estimatedSlippage;

    /**
     * Whether to auto-roll expiring contracts
     */
    @Column(nullable = false)
    private Boolean autoRollover;

    /**
     * Whether hedging or spread trading is allowed
     */
    @Column(nullable = false)
    private Boolean allowHedging;

    /**
     * Trading session (e.g., DAY, NIGHT, CONTINUOUS)
     */
    @Column(nullable = false)
    private String tradingSession;

    /**
     * Description or notes for this settings profile
     */
    @Column(length = 500)
    private String description;

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
