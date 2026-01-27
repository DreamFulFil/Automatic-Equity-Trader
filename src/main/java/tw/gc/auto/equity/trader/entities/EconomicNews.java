package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * EconomicNews Entity - News articles with sentiment analysis for trading strategies.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>NewsSentimentStrategy</b>: Aggregate sentiment for position decisions</li>
 *   <li><b>NewsRevisionMomentumStrategy</b>: Track sentiment momentum</li>
 *   <li><b>Event-Driven Trading</b>: React to high-impact news</li>
 * </ul>
 * 
 * <h3>Data Source:</h3>
 * News fetched from Yahoo Finance, Google News RSS, or CNYES via Python bridge.
 * Sentiment scored using LLM (Ollama) or rule-based analysis.
 * 
 * @see NewsService for news fetching and sentiment scoring
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@Entity
@Table(name = "economic_news", indexes = {
    @Index(name = "idx_news_symbol", columnList = "primary_symbol"),
    @Index(name = "idx_news_timestamp", columnList = "published_at"),
    @Index(name = "idx_news_sentiment", columnList = "sentiment_score"),
    @Index(name = "idx_news_symbol_time", columnList = "primary_symbol, published_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Article Metadata ==========

    /**
     * News headline/title
     */
    @Column(length = 500, nullable = false)
    private String headline;

    /**
     * Brief summary or first paragraph of the article
     */
    @Column(length = 2000)
    private String summary;

    /**
     * URL to the full article
     */
    @Column(length = 1000)
    private String url;

    /**
     * News source (e.g., "Yahoo Finance", "CNYES", "Reuters")
     */
    @Column(length = 100)
    private String source;

    /**
     * Author of the article (if available)
     */
    @Column(length = 200)
    private String author;

    /**
     * When the article was published
     */
    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    /**
     * When we fetched/stored this article
     */
    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    // ========== Symbol Association ==========

    /**
     * Primary stock symbol this news is about
     */
    @Column(name = "primary_symbol", length = 20)
    private String primarySymbol;

    /**
     * Additional related symbols (stored as comma-separated values)
     * e.g., "2330.TW,2454.TW,2317.TW"
     */
    @Column(name = "related_symbols", length = 500)
    private String relatedSymbols;

    // ========== Sentiment Analysis ==========

    /**
     * Sentiment score: -1.0 (very negative) to +1.0 (very positive)
     * 0.0 = neutral
     */
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    /**
     * Confidence in the sentiment analysis (0.0 to 1.0)
     */
    @Column(name = "sentiment_confidence")
    private Double sentimentConfidence;

    /**
     * Method used for sentiment scoring
     * e.g., "ollama", "vader", "finbert", "rule-based"
     */
    @Column(name = "sentiment_method", length = 50)
    @Builder.Default
    private String sentimentMethod = "ollama";

    /**
     * Raw sentiment label from LLM (e.g., "POSITIVE", "NEGATIVE", "NEUTRAL")
     */
    @Column(name = "sentiment_label", length = 20)
    private String sentimentLabel;

    // ========== News Classification ==========

    /**
     * News category
     * e.g., "earnings", "merger", "product", "lawsuit", "macro", "analyst"
     */
    @Column(length = 50)
    private String category;

    /**
     * Impact level: "high", "medium", "low"
     * Based on historical volatility correlation with similar news
     */
    @Column(name = "impact_level", length = 20)
    @Builder.Default
    private String impactLevel = "medium";

    /**
     * Whether this news is about an earnings announcement
     */
    @Column(name = "is_earnings_related")
    @Builder.Default
    private Boolean isEarningsRelated = false;

    /**
     * Whether this is breaking/urgent news
     */
    @Column(name = "is_breaking")
    @Builder.Default
    private Boolean isBreaking = false;

    // ========== Trading Response ==========

    /**
     * Whether we've already acted on this news
     */
    @Column(name = "is_processed")
    @Builder.Default
    private Boolean isProcessed = false;

    /**
     * Timestamp when a trade was triggered by this news (if any)
     */
    @Column(name = "trade_triggered_at")
    private OffsetDateTime tradeTriggeredAt;

    // ========== Lifecycle Hooks ==========

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = OffsetDateTime.now();
        }
    }

    // ========== Utility Methods ==========

    /**
     * Get related symbols as a list.
     */
    public List<String> getRelatedSymbolsList() {
        if (relatedSymbols == null || relatedSymbols.isBlank()) {
            return List.of();
        }
        return List.of(relatedSymbols.split(","));
    }

    /**
     * Set related symbols from a list.
     */
    public void setRelatedSymbolsList(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            this.relatedSymbols = null;
        } else {
            this.relatedSymbols = String.join(",", symbols);
        }
    }

    /**
     * Check if this news is bullish (positive sentiment above threshold).
     */
    public boolean isBullish(double threshold) {
        return sentimentScore != null && sentimentScore >= threshold;
    }

    /**
     * Check if this news is bearish (negative sentiment below threshold).
     */
    public boolean isBearish(double threshold) {
        return sentimentScore != null && sentimentScore <= -threshold;
    }

    /**
     * Check if sentiment analysis is confident enough to act on.
     */
    public boolean isConfident(double minConfidence) {
        return sentimentConfidence != null && sentimentConfidence >= minConfidence;
    }

    /**
     * Get age of news in minutes.
     */
    public long getAgeMinutes() {
        if (publishedAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.Duration.between(publishedAt, OffsetDateTime.now()).toMinutes();
    }

    /**
     * Check if this news is recent (within specified minutes).
     */
    public boolean isRecent(int maxMinutes) {
        return getAgeMinutes() <= maxMinutes;
    }
}
