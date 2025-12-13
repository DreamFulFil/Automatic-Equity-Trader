package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.EconomicNews;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for economic news
 */
@Repository
public interface EconomicNewsRepository extends JpaRepository<EconomicNews, Long> {
    
    List<EconomicNews> findByPublishedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<EconomicNews> findBySource(String source);
    
    List<EconomicNews> findByCategory(String category);
    
    List<EconomicNews> findByAnalyzedFalse();
    
    List<EconomicNews> findByTriggeredVetoTrue();
    
    @Query("SELECT n FROM EconomicNews n WHERE n.sentimentScore IS NOT NULL " +
           "AND n.sentimentScore < :threshold ORDER BY n.publishedAt DESC")
    List<EconomicNews> findNegativeNews(@Param("threshold") double threshold);
    
    @Query("SELECT n FROM EconomicNews n WHERE n.impactScore >= :threshold " +
           "ORDER BY n.impactScore DESC, n.publishedAt DESC")
    List<EconomicNews> findHighImpactNews(@Param("threshold") double threshold);
    
    @Query("SELECT n FROM EconomicNews n WHERE n.affectedSymbols LIKE %:symbol% " +
           "AND n.publishedAt >= :since ORDER BY n.publishedAt DESC")
    List<EconomicNews> findNewsBySymbol(
        @Param("symbol") String symbol,
        @Param("since") LocalDateTime since);
}
