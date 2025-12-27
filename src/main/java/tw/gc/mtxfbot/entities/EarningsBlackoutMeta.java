package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Metadata for earnings blackout date scraping runs.
 * Each successful scrape creates a new meta record with associated dates.
 */
@Entity
@Table(name = "earnings_blackout_meta", indexes = {
    @Index(name = "idx_blackout_meta_last_updated", columnList = "last_updated DESC"),
    @Index(name = "idx_blackout_meta_version", columnList = "version")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsBlackoutMeta {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;
    
    @ElementCollection
    @CollectionTable(name = "earnings_blackout_meta_tickers", joinColumns = @JoinColumn(name = "meta_id"))
    @Column(name = "ticker", nullable = false)
    @Builder.Default
    private Set<String> tickersChecked = new LinkedHashSet<>();
    
    @Column(nullable = false)
    private String source;
    
    @Column(name = "ttl_days", nullable = false)
    @Builder.Default
    private int ttlDays = 7;

    @Version
    @Column(name = "version")
    private Long version;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    @OneToMany(mappedBy = "meta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<EarningsBlackoutDate> dates = new LinkedHashSet<>();
    
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (lastUpdated == null) {
            lastUpdated = now;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
    
    public void addDate(EarningsBlackoutDate date) {
        dates.add(date);
        date.setMeta(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EarningsBlackoutMeta other)) {
            return false;
        }
        if (id != null && other.id != null) {
            return id.equals(other.id);
        }
        return lastUpdated != null ? lastUpdated.equals(other.lastUpdated) : other.lastUpdated == null;
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return id.hashCode();
        }
        return lastUpdated != null ? lastUpdated.hashCode() : 0;
    }
}
