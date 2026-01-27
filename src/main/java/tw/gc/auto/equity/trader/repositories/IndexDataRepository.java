package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.IndexData;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for IndexData entity operations.
 * 
 * <h3>Primary Use Cases:</h3>
 * <ul>
 *   <li>Fetch latest index values for arbitrage calculations</li>
 *   <li>Historical index data for beta computation</li>
 *   <li>Market trend analysis and timing signals</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@Repository
public interface IndexDataRepository extends JpaRepository<IndexData, Long> {

    // ========== Latest Data Queries ==========

    /**
     * Find the most recent index data for a symbol.
     * 
     * @param indexSymbol Index symbol (e.g., "^TWII")
     * @return Most recent IndexData if available
     */
    Optional<IndexData> findFirstByIndexSymbolOrderByTradeDateDesc(String indexSymbol);

    /**
     * Find index data for a specific date.
     * 
     * @param indexSymbol Index symbol
     * @param tradeDate Trading date
     * @return IndexData for that date if available
     */
    Optional<IndexData> findByIndexSymbolAndTradeDate(String indexSymbol, LocalDate tradeDate);

    // ========== Historical Data Queries ==========

    /**
     * Find index data within a date range (for beta calculation).
     * 
     * @param indexSymbol Index symbol
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of IndexData ordered by date ascending
     */
    @Query("SELECT i FROM IndexData i WHERE i.indexSymbol = :symbol " +
           "AND i.tradeDate >= :startDate AND i.tradeDate <= :endDate " +
           "ORDER BY i.tradeDate ASC")
    List<IndexData> findBySymbolAndDateRange(
        @Param("symbol") String indexSymbol,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find recent N trading days of index data.
     * 
     * @param indexSymbol Index symbol
     * @param limit Number of days to fetch
     * @return List of IndexData ordered by date descending
     */
    @Query(value = "SELECT * FROM index_data WHERE index_symbol = :symbol " +
                   "ORDER BY trade_date DESC LIMIT :limit", 
           nativeQuery = true)
    List<IndexData> findRecentBySymbol(
        @Param("symbol") String indexSymbol,
        @Param("limit") int limit
    );

    // ========== Calculation Queries ==========

    /**
     * Calculate average daily return for an index over a period.
     * 
     * @param indexSymbol Index symbol
     * @param startDate Start date
     * @param endDate End date
     * @return Average daily return
     */
    @Query("SELECT AVG((i.closeValue - i.previousClose) / i.previousClose) FROM IndexData i " +
           "WHERE i.indexSymbol = :symbol AND i.tradeDate >= :startDate AND i.tradeDate <= :endDate " +
           "AND i.previousClose IS NOT NULL AND i.previousClose > 0")
    Double calculateAverageReturn(
        @Param("symbol") String indexSymbol,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Calculate realized volatility for an index.
     * Uses standard deviation of daily returns.
     * 
     * @param indexSymbol Index symbol
     * @param startDate Start date
     * @param endDate End date
     * @return Variance of daily returns (take sqrt for volatility)
     */
    @Query("SELECT COALESCE(STDDEV((i.closeValue - i.previousClose) / i.previousClose), 0) FROM IndexData i " +
           "WHERE i.indexSymbol = :symbol AND i.tradeDate >= :startDate AND i.tradeDate <= :endDate " +
           "AND i.previousClose IS NOT NULL AND i.previousClose > 0")
    Double calculateReturnStdDev(
        @Param("symbol") String indexSymbol,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ========== Existence Checks ==========

    /**
     * Check if data exists for a symbol and date.
     */
    boolean existsByIndexSymbolAndTradeDate(String indexSymbol, LocalDate tradeDate);

    /**
     * Check if any data exists for a symbol.
     */
    boolean existsByIndexSymbol(String indexSymbol);

    // ========== Metadata Queries ==========

    /**
     * Get the earliest date with data for an index.
     */
    @Query("SELECT MIN(i.tradeDate) FROM IndexData i WHERE i.indexSymbol = :symbol")
    LocalDate findEarliestDateBySymbol(@Param("symbol") String indexSymbol);

    /**
     * Get the latest date with data for an index.
     */
    @Query("SELECT MAX(i.tradeDate) FROM IndexData i WHERE i.indexSymbol = :symbol")
    LocalDate findLatestDateBySymbol(@Param("symbol") String indexSymbol);

    /**
     * Count trading days with data for an index.
     */
    long countByIndexSymbol(String indexSymbol);

    /**
     * Find all distinct index symbols in the database.
     */
    @Query("SELECT DISTINCT i.indexSymbol FROM IndexData i ORDER BY i.indexSymbol")
    List<String> findAllIndexSymbols();

    // ========== Cleanup Queries ==========

    /**
     * Delete old data before a certain date.
     * 
     * @param indexSymbol Index symbol
     * @param beforeDate Delete data older than this date
     */
    void deleteByIndexSymbolAndTradeDateBefore(String indexSymbol, LocalDate beforeDate);
}
