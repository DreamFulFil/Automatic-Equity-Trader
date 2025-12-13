package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Veto Event with Full Provenance
 * Records all trading vetoes (system, manual, LLM) with complete rationale
 * and traceability for post-trade forensics and compliance
 */
@Entity
@Table(name = "veto_event", indexes = {
    @Index(name = "idx_veto_timestamp", columnList = "timestamp"),
    @Index(name = "idx_veto_source", columnList = "source"),
    @Index(name = "idx_veto_symbol", columnList = "symbol"),
    @Index(name = "idx_veto_active", columnList = "is_active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VetoEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * When veto was triggered
     */
    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * Source of veto
     */
    @Column(name = "source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VetoSource source;
    
    /**
     * Symbol this veto applies to (null for market-wide veto)
     */
    @Column(name = "symbol", length = 20)
    private String symbol;
    
    /**
     * Veto type/reason category
     */
    @Column(name = "veto_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private VetoType vetoType;
    
    /**
     * Complete rationale for veto
     */
    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;
    
    /**
     * Confidence score (if applicable, e.g., from LLM)
     */
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    /**
     * Whether veto is currently active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;
    
    /**
     * When veto expires (null for manual override required)
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    /**
     * When veto was deactivated
     */
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
    
    /**
     * Who/what deactivated the veto
     */
    @Column(name = "deactivated_by", length = 100)
    private String deactivatedBy;
    
    /**
     * ID of associated LLM insight (if LLM-generated veto)
     */
    @Column(name = "llm_insight_id")
    private Long llmInsightId;
    
    /**
     * ID of associated news item (if news-triggered veto)
     */
    @Column(name = "economic_news_id")
    private Long economicNewsId;
    
    /**
     * ID of associated signal (if signal-triggered veto)
     */
    @Column(name = "signal_id")
    private Long signalId;
    
    /**
     * Impact on trading (e.g., "BLOCK_ALL_TRADES", "REDUCE_POSITION_SIZE")
     */
    @Column(name = "impact", length = 50)
    private String impact;
    
    /**
     * Additional metadata JSON
     */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    
    /**
     * Source of veto decision
     */
    public enum VetoSource {
        /** System-generated veto (risk limits, blackouts) */
        SYSTEM,
        
        /** Manual override by human operator */
        MANUAL,
        
        /** LLM-recommended veto */
        LLM,
        
        /** Hybrid: LLM recommendation + manual confirmation */
        HYBRID
    }
    
    /**
     * Type/category of veto
     */
    public enum VetoType {
        /** News-based veto (negative sentiment) */
        NEWS_NEGATIVE,
        
        /** Risk limit hit (daily/weekly loss) */
        RISK_LIMIT,
        
        /** Earnings blackout */
        EARNINGS_BLACKOUT,
        
        /** Market volatility too high */
        HIGH_VOLATILITY,
        
        /** Low liquidity */
        LOW_LIQUIDITY,
        
        /** Technical issue */
        TECHNICAL_ISSUE,
        
        /** Manual user pause */
        USER_PAUSE,
        
        /** Weekend/holiday */
        MARKET_CLOSED,
        
        /** Position limit reached */
        POSITION_LIMIT,
        
        /** Correlation analysis warning */
        CORRELATION_WARNING,
        
        /** Circuit breaker */
        CIRCUIT_BREAKER,
        
        /** Other */
        OTHER
    }
}
