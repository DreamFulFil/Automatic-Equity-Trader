package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.AnnualReportMetadata;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnnualReportMetadataRepository extends JpaRepository<AnnualReportMetadata, Long> {
    
    /**
     * Find report by ticker and year
     */
    Optional<AnnualReportMetadata> findByTickerAndReportYear(String ticker, Integer reportYear);
    
    /**
     * Find latest report for a ticker
     */
    Optional<AnnualReportMetadata> findFirstByTickerOrderByReportYearDesc(String ticker);
    
    /**
     * Find all reports for a ticker
     */
    List<AnnualReportMetadata> findByTickerOrderByReportYearDesc(String ticker);
    
    /**
     * Find reports that haven't been indexed yet
     */
    @Query("SELECT a FROM AnnualReportMetadata a WHERE a.indexedAt IS NULL")
    List<AnnualReportMetadata> findUnindexedReports();
    
    /**
     * Find reports that haven't been summarized yet
     */
    @Query("SELECT a FROM AnnualReportMetadata a WHERE a.summarizedAt IS NULL AND a.indexedAt IS NOT NULL")
    List<AnnualReportMetadata> findUnsummarizedReports();
    
    /**
     * Find reports with positive fundamental scores
     */
    @Query("SELECT a FROM AnnualReportMetadata a WHERE a.fundamentalScore >= :minScore " +
           "ORDER BY a.fundamentalScore DESC")
    List<AnnualReportMetadata> findByFundamentalScoreGreaterThanEqual(@Param("minScore") Double minScore);
    
    /**
     * Find reports downloaded after a certain date
     */
    List<AnnualReportMetadata> findByDownloadedAtAfter(LocalDateTime since);
}
