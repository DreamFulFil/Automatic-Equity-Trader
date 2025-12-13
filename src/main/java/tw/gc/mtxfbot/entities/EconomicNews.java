package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * External Economic and Market News
 * Stores news headlines and articles for LLM analysis and impact scoring
 */
@Entity
@Table(name = "economic_news", indexes = {
    @Index(name = "idx_news_timestamp", columnList = "published_at"),
    @Index(name = "idx_news_source", columnList = "source"),
    @Index(name = "idx_news_sentiment", columnList = "sentiment_score")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicNews {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * When news was published
     */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
    
    /**
     * When news was fetched into system
     */
    @Column(name = "fetched_at", nullable = false)
    @Builder.Default
    private LocalDateTime fetchedAt = LocalDateTime.now();
    
    /**
     * News source (e.g., "Reuters", "Bloomberg", "Yahoo Finance")
     */
    @Column(name = "source", nullable = false, length = 100)
    private String source;
    
    /**
     * News headline
     */
    @Column(name = "headline", nullable = false, columnDefinition = "TEXT")
    private String headline;
    
    /**
     * Full article content (if available)
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    /**
     * URL to original article
     */
    @Column(name = "url", length = 500)
    private String url;
    
    /**
     * News category (e.g., "MARKET", "EARNINGS", "ECONOMIC", "GEOPOLITICAL")
     */
    @Column(name = "category", length = 50)
    private String category;
    
    /**
     * Sentiment score from LLM (-1.0 to +1.0, negative=bearish, positive=bullish)
     */
    @Column(name = "sentiment_score")
    private Double sentimentScore;
    
    /**
     * Market impact score from LLM (0.0 to 1.0)
     */
    @Column(name = "impact_score")
    private Double impactScore;
    
    /**
     * Affected symbols/sectors (comma-separated)
     */
    @Column(name = "affected_symbols", length = 200)
    private String affectedSymbols;
    
    /**
     * LLM-generated explanation of impact
     */
    @Column(name = "llm_explanation", columnDefinition = "TEXT")
    private String llmExplanation;
    
    /**
     * Whether LLM analysis was completed
     */
    @Column(name = "analyzed")
    @Builder.Default
    private boolean analyzed = false;
    
    /**
     * ID of associated LLM insight record
     */
    @Column(name = "llm_insight_id")
    private Long llmInsightId;
    
    /**
     * Whether this news triggered a veto
     */
    @Column(name = "triggered_veto")
    @Builder.Default
    private boolean triggeredVeto = false;
    
    /**
     * News language (e.g., "zh-TW", "en-US")
     */
    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "zh-TW";
    
    /**
     * Raw metadata JSON from news source
     */
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}
