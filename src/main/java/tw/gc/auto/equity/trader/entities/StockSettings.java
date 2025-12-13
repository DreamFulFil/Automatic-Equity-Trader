package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Stock trading settings stored in database.
 * Allows dynamic configuration of stock trading parameters.
 */
@Entity
@Table(name = "stock_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Base shares for stock mode (70 shares ≈77k TWD at NT$1,100/share MediaTek) */
    @Column(name = "shares", nullable = false)
    @Builder.Default
    private int shares = 70;

    /** Additional shares per 20k equity above 80k base */
    @Column(name = "share_increment", nullable = false)
    @Builder.Default
    private int shareIncrement = 27;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Version
    private Long version;
}