package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Risk management settings stored in database.
 * Allows dynamic configuration of risk parameters.
 */
@Entity
@Table(name = "risk_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Maximum position size (contracts/shares) */
    @Column(name = "max_position", nullable = false)
    @Builder.Default
    private int maxPosition = 1;

    /** Daily loss limit in TWD - emergency shutdown threshold */
    @Column(name = "daily_loss_limit", nullable = false)
    @Builder.Default
    private int dailyLossLimit = 1500;

    /** Weekly loss limit in TWD - triggers pause until next Monday */
    @Column(name = "weekly_loss_limit", nullable = false)
    @Builder.Default
    private int weeklyLossLimit = 7000;

    /** Max minutes to hold a position before forced exit */
    @Column(name = "max_hold_minutes", nullable = false)
    @Builder.Default
    private int maxHoldMinutes = 45;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;
}