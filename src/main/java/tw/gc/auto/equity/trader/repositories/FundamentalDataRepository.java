package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.FundamentalData;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for FundamentalData entity.
 * Provides access to fundamental financial metrics for trading strategies.
 * 
 * @since 2026-01-26 - Phase 1 Data Improvement Plan
 */
@Repository
public interface FundamentalDataRepository extends JpaRepository<FundamentalData, Long> {

    // ========== Basic Queries ==========

    /**
     * Find all fundamental data for a symbol, ordered by report date descending
     */
    List<FundamentalData> findBySymbolOrderByReportDateDesc(String symbol);

    /**
     * Find the most recent fundamental data for a symbol
     */
    Optional<FundamentalData> findFirstBySymbolOrderByReportDateDesc(String symbol);

    /**
     * Find the most recent fundamental data for a symbol fetched after a certain time
     */
    Optional<FundamentalData> findFirstBySymbolAndFetchedAtAfterOrderByReportDateDesc(
            String symbol, OffsetDateTime fetchedAfter);

    /**
     * Find fundamental data for a symbol within a date range
     */
    List<FundamentalData> findBySymbolAndReportDateBetweenOrderByReportDateDesc(
            String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * Find fundamental data by exact symbol and report date
     */
    Optional<FundamentalData> findBySymbolAndReportDate(String symbol, LocalDate reportDate);

    // ========== Bulk Queries ==========

    /**
     * Find latest fundamental data for multiple symbols
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.symbol IN :symbols 
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            """)
    List<FundamentalData> findLatestBySymbols(@Param("symbols") List<String> symbols);

    /**
     * Find all symbols that have fundamental data
     */
    @Query("SELECT DISTINCT f.symbol FROM FundamentalData f ORDER BY f.symbol")
    List<String> findAllSymbols();

    /**
     * Find symbols with stale data (not updated since given time)
     */
    @Query("""
            SELECT DISTINCT f.symbol FROM FundamentalData f 
            WHERE f.fetchedAt < :staleThreshold
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            """)
    List<String> findSymbolsWithStaleData(@Param("staleThreshold") OffsetDateTime staleThreshold);

    // ========== Value Factor Queries ==========

    /**
     * Find stocks with high earnings yield (low P/E)
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.peRatio IS NOT NULL AND f.peRatio > 0 AND f.peRatio < :maxPe
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.peRatio ASC
            """)
    List<FundamentalData> findHighEarningsYield(@Param("maxPe") Double maxPe);

    /**
     * Find stocks with high book-to-market (low P/B, value stocks)
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.pbRatio IS NOT NULL AND f.pbRatio > 0 AND f.pbRatio < :maxPb
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.pbRatio ASC
            """)
    List<FundamentalData> findHighBookToMarket(@Param("maxPb") Double maxPb);

    /**
     * Find stocks with high dividend yield
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.dividendYield IS NOT NULL AND f.dividendYield >= :minYield
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.dividendYield DESC
            """)
    List<FundamentalData> findHighDividendYield(@Param("minYield") Double minYield);

    // ========== Quality Factor Queries ==========

    /**
     * Find high-quality stocks (high ROE, low debt)
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.roe IS NOT NULL AND f.roe >= :minRoe
            AND (f.debtToEquity IS NULL OR f.debtToEquity < :maxDebtToEquity)
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.roe DESC
            """)
    List<FundamentalData> findQualityStocks(
            @Param("minRoe") Double minRoe, 
            @Param("maxDebtToEquity") Double maxDebtToEquity);

    /**
     * Find stocks with high profitability (gross margin)
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.grossMargin IS NOT NULL AND f.grossMargin >= :minMargin
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.grossMargin DESC
            """)
    List<FundamentalData> findHighProfitability(@Param("minMargin") Double minMargin);

    // ========== Distress Factor Queries ==========

    /**
     * Find financially distressed stocks (high debt, low current ratio)
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE (f.debtToEquity IS NOT NULL AND f.debtToEquity > :minDebtToEquity)
            OR (f.currentRatio IS NOT NULL AND f.currentRatio < :maxCurrentRatio)
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            """)
    List<FundamentalData> findDistressedStocks(
            @Param("minDebtToEquity") Double minDebtToEquity,
            @Param("maxCurrentRatio") Double maxCurrentRatio);

    // ========== Growth Queries ==========

    /**
     * Find high growth stocks
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.revenueGrowth IS NOT NULL AND f.revenueGrowth >= :minGrowth
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.revenueGrowth DESC
            """)
    List<FundamentalData> findHighGrowthStocks(@Param("minGrowth") Double minGrowth);

    // ========== Accrual / Earnings Quality Queries ==========

    /**
     * Find stocks with low accruals (high earnings quality)
     */
    @Query("""
            SELECT f FROM FundamentalData f 
            WHERE f.accrualsRatio IS NOT NULL AND f.accrualsRatio < :maxAccruals
            AND f.reportDate = (
                SELECT MAX(f2.reportDate) FROM FundamentalData f2 WHERE f2.symbol = f.symbol
            )
            ORDER BY f.accrualsRatio ASC
            """)
    List<FundamentalData> findLowAccrualStocks(@Param("maxAccruals") Double maxAccruals);

    // ========== Count Queries ==========

    /**
     * Count records for a symbol
     */
    long countBySymbol(String symbol);

    /**
     * Check if fresh data exists for a symbol (within last N hours)
     */
    @Query("""
            SELECT COUNT(f) > 0 FROM FundamentalData f 
            WHERE f.symbol = :symbol AND f.fetchedAt > :threshold
            """)
    boolean existsFreshDataForSymbol(
            @Param("symbol") String symbol, 
            @Param("threshold") OffsetDateTime threshold);

    // ========== Deletion Queries ==========

    /**
     * Delete old records for a symbol (keep only latest N)
     */
    @Query("""
            DELETE FROM FundamentalData f 
            WHERE f.symbol = :symbol 
            AND f.id NOT IN (
                SELECT f2.id FROM FundamentalData f2 
                WHERE f2.symbol = :symbol 
                ORDER BY f2.reportDate DESC 
                LIMIT :keepCount
            )
            """)
    void deleteOldRecordsForSymbol(@Param("symbol") String symbol, @Param("keepCount") int keepCount);
}
