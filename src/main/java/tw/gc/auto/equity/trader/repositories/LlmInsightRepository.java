package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.LlmInsight;
import tw.gc.auto.equity.trader.entities.LlmInsight.InsightType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for LLM-generated insights
 * Supports querying historical LLM outputs for analytics and auditing
 */
@Repository
public interface LlmInsightRepository extends JpaRepository<LlmInsight, Long> {
    
    /**
     * Find all insights of a specific type
     */
    List<LlmInsight> findByInsightType(InsightType insightType);
    
    /**
     * Find all insights for a specific symbol
     */
    List<LlmInsight> findBySymbol(String symbol);
    
    /**
     * Find all insights from a specific source
     */
    List<LlmInsight> findBySource(String source);
    
    /**
     * Find insights within a time range
     */
    List<LlmInsight> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * Find recent insights of a specific type
     */
    List<LlmInsight> findTop10ByInsightTypeOrderByTimestampDesc(InsightType insightType);
    
    /**
     * Find insights related to a specific trade
     */
    List<LlmInsight> findByTradeId(Long tradeId);
    
    /**
     * Find insights related to a specific signal
     */
    List<LlmInsight> findBySignalId(Long signalId);
    
    /**
     * Find insights related to a specific event
     */
    List<LlmInsight> findByEventId(Long eventId);
    
    /**
     * Find successful insights only
     */
    List<LlmInsight> findBySuccessTrue();
    
    /**
     * Find failed insights for debugging
     */
    List<LlmInsight> findBySuccessFalse();
    
    /**
     * Find insights with confidence above threshold
     */
    @Query("SELECT li FROM LlmInsight li WHERE li.confidenceScore >= :threshold ORDER BY li.timestamp DESC")
    List<LlmInsight> findHighConfidenceInsights(@Param("threshold") double threshold);
    
    /**
     * Get latest insight of specific type for symbol
     */
    Optional<LlmInsight> findFirstByInsightTypeAndSymbolOrderByTimestampDesc(
        InsightType insightType, String symbol);
    
    /**
     * Count insights by type
     */
    @Query("SELECT li.insightType, COUNT(li) FROM LlmInsight li GROUP BY li.insightType")
    List<Object[]> countByInsightType();
    
    /**
     * Get average processing time by insight type
     */
    @Query("SELECT li.insightType, AVG(li.processingTimeMs) FROM LlmInsight li WHERE li.success = true GROUP BY li.insightType")
    List<Object[]> averageProcessingTimeByType();
    
    /**
     * Find insights by recommendation
     */
    List<LlmInsight> findByRecommendation(String recommendation);
}
