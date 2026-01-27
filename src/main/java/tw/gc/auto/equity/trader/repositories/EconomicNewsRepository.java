package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.EconomicNews;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Repository for EconomicNews entity operations.
 * 
 * <h3>Key Query Patterns:</h3>
 * <ul>
 *   <li>By symbol and time range for sentiment aggregation</li>
 *   <li>Recent news for real-time trading decisions</li>
 *   <li>High-impact news for event-driven strategies</li>
 *   <li>Unprocessed news for batch processing</li>
 * </ul>
 * 
 * @see EconomicNews
 * @see NewsService
 * @since 2026-01-26 - Phase 2 Data Improvement Plan
 */
@Repository
public interface EconomicNewsRepository extends JpaRepository<EconomicNews, Long> {

    // ========== Symbol-Based Queries ==========

    /**
     * Find all news for a primary symbol, ordered by publication time descending.
     */
    List<EconomicNews> findByPrimarySymbolOrderByPublishedAtDesc(String symbol);

    /**
     * Find news for a symbol within a date range.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.publishedAt >= :start AND n.publishedAt <= :end " +
           "ORDER BY n.publishedAt DESC")
    List<EconomicNews> findBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * Find recent news for a symbol within the last N hours.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.publishedAt >= :since ORDER BY n.publishedAt DESC")
    List<EconomicNews> findRecentBySymbol(
            @Param("symbol") String symbol,
            @Param("since") OffsetDateTime since);

    /**
     * Find news mentioning a symbol in related_symbols (contains search).
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.relatedSymbols LIKE %:symbol% " +
           "AND n.publishedAt >= :since ORDER BY n.publishedAt DESC")
    List<EconomicNews> findByRelatedSymbol(
            @Param("symbol") String symbol,
            @Param("since") OffsetDateTime since);

    // ========== Sentiment-Based Queries ==========

    /**
     * Find high-impact news (strong sentiment above threshold).
     */
    @Query("SELECT n FROM EconomicNews n WHERE ABS(n.sentimentScore) >= :threshold " +
           "AND n.publishedAt >= :since ORDER BY ABS(n.sentimentScore) DESC")
    List<EconomicNews> findHighImpactNews(
            @Param("threshold") double threshold,
            @Param("since") OffsetDateTime since);

    /**
     * Find bullish news for a symbol (positive sentiment).
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.sentimentScore >= :minScore " +
           "AND n.publishedAt >= :since ORDER BY n.sentimentScore DESC")
    List<EconomicNews> findBullishNewsBySymbol(
            @Param("symbol") String symbol,
            @Param("minScore") double minScore,
            @Param("since") OffsetDateTime since);

    /**
     * Find bearish news for a symbol (negative sentiment).
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.sentimentScore <= :maxScore " +
           "AND n.publishedAt >= :since ORDER BY n.sentimentScore ASC")
    List<EconomicNews> findBearishNewsBySymbol(
            @Param("symbol") String symbol,
            @Param("maxScore") double maxScore,
            @Param("since") OffsetDateTime since);

    // ========== Aggregation Queries ==========

    /**
     * Calculate average sentiment for a symbol within a time range.
     */
    @Query("SELECT AVG(n.sentimentScore) FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.publishedAt >= :since AND n.sentimentScore IS NOT NULL")
    Double calculateAverageSentiment(
            @Param("symbol") String symbol,
            @Param("since") OffsetDateTime since);

    /**
     * Count news articles for a symbol within a time range.
     */
    @Query("SELECT COUNT(n) FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.publishedAt >= :since")
    long countNewsBySymbol(
            @Param("symbol") String symbol,
            @Param("since") OffsetDateTime since);

    /**
     * Calculate sentiment momentum (weighted recent sentiment).
     * Returns news ordered by time for client-side momentum calculation.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.publishedAt >= :since AND n.sentimentScore IS NOT NULL " +
           "ORDER BY n.publishedAt DESC")
    List<EconomicNews> findForMomentumCalculation(
            @Param("symbol") String symbol,
            @Param("since") OffsetDateTime since);

    // ========== Processing Status Queries ==========

    /**
     * Find unprocessed news articles.
     */
    List<EconomicNews> findByIsProcessedFalseOrderByPublishedAtAsc();

    /**
     * Find unprocessed news for a specific symbol.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.isProcessed = false ORDER BY n.publishedAt ASC")
    List<EconomicNews> findUnprocessedBySymbol(@Param("symbol") String symbol);

    /**
     * Find breaking news that hasn't been processed.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.isBreaking = true " +
           "AND n.isProcessed = false ORDER BY n.publishedAt DESC")
    List<EconomicNews> findUnprocessedBreakingNews();

    // ========== Category-Based Queries ==========

    /**
     * Find news by category for a symbol.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.category = :category AND n.publishedAt >= :since " +
           "ORDER BY n.publishedAt DESC")
    List<EconomicNews> findBySymbolAndCategory(
            @Param("symbol") String symbol,
            @Param("category") String category,
            @Param("since") OffsetDateTime since);

    /**
     * Find earnings-related news for a symbol.
     */
    @Query("SELECT n FROM EconomicNews n WHERE n.primarySymbol = :symbol " +
           "AND n.isEarningsRelated = true AND n.publishedAt >= :since " +
           "ORDER BY n.publishedAt DESC")
    List<EconomicNews> findEarningsNewsBySymbol(
            @Param("symbol") String symbol,
            @Param("since") OffsetDateTime since);

    // ========== Deduplication Queries ==========

    /**
     * Check if a news article with the same headline exists (for deduplication).
     */
    boolean existsByHeadlineAndPublishedAt(String headline, OffsetDateTime publishedAt);

    /**
     * Check if a news article with the same URL exists.
     */
    boolean existsByUrl(String url);

    // ========== Cleanup Queries ==========

    /**
     * Delete old news articles (for data retention policy).
     */
    @Query("DELETE FROM EconomicNews n WHERE n.publishedAt < :cutoff")
    void deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
