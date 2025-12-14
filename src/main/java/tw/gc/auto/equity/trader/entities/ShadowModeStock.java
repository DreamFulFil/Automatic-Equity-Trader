package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Shadow Mode Stock Configuration
 * Tracks multiple stocks with their assigned strategies for shadow mode monitoring.
 */
@Entity
@Table(name = "shadow_mode_stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowModeStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    @Column(name = "stock_name", nullable = false)
    private String stockName;

    @Column(name = "strategy_name", nullable = false)
    private String strategyName;

    @Column(name = "expected_return_percentage")
    private Double expectedReturnPercentage;

    @Column(name = "rank_position")
    private Integer rankPosition;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;
}
