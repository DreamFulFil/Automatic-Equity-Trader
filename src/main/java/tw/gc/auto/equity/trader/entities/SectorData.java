package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SectorData Entity
 *
 * Stores sector/industry classifications for Taiwan stocks.
 */
@Entity
@Table(name = "sector_data", indexes = {
    @Index(name = "idx_sector_symbol", columnList = "symbol", unique = true),
    @Index(name = "idx_sector_sector", columnList = "sector")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20, unique = true)
    private String symbol;

    @Column(name = "sector", length = 100)
    private String sector;

    @Column(name = "industry", length = 150)
    private String industry;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
