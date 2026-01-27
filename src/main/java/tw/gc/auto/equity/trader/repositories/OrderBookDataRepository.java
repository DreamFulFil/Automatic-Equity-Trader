package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.OrderBookData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for OrderBookData entity operations.
 * 
 * <h3>Query Methods:</h3>
 * <ul>
 *   <li>Latest order book by symbol</li>
 *   <li>Historical order book for time range</li>
 *   <li>Order flow imbalance queries</li>
 *   <li>Spread analysis queries</li>
 * </ul>
 * 
 * @since 2026-01-27 - Phase 5 Data Improvement Plan
 */
@Repository
public interface OrderBookDataRepository extends JpaRepository<OrderBookData, Long> {

    /**
     * Get the most recent order book snapshot for a symbol.
     * 
     * @param symbol Stock/futures symbol
     * @return Latest order book data
     */
    Optional<OrderBookData> findFirstBySymbolOrderByTimestampDesc(String symbol);

    /**
     * Get order book history for a symbol within a time range.
     * 
     * @param symbol Stock/futures symbol
     * @param start Start timestamp (inclusive)
     * @param end End timestamp (inclusive)
     * @return List of order book snapshots
     */
    @Query("SELECT o FROM OrderBookData o WHERE o.symbol = :symbol " +
           "AND o.timestamp >= :start AND o.timestamp <= :end " +
           "ORDER BY o.timestamp ASC")
    List<OrderBookData> findBySymbolAndDateRange(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Get recent order book snapshots for a symbol.
     * 
     * @param symbol Stock/futures symbol
     * @param since Minimum timestamp
     * @return List of recent snapshots
     */
    @Query("SELECT o FROM OrderBookData o WHERE o.symbol = :symbol " +
           "AND o.timestamp >= :since ORDER BY o.timestamp DESC")
    List<OrderBookData> findRecentBySymbol(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since);

    /**
     * Get snapshots with high buy pressure (imbalance > threshold).
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Minimum imbalance ratio
     * @param since Minimum timestamp
     * @return List of snapshots with buy pressure
     */
    @Query("SELECT o FROM OrderBookData o WHERE o.symbol = :symbol " +
           "AND o.imbalance > :threshold AND o.timestamp >= :since " +
           "ORDER BY o.timestamp DESC")
    List<OrderBookData> findWithBuyPressure(
            @Param("symbol") String symbol,
            @Param("threshold") double threshold,
            @Param("since") LocalDateTime since);

    /**
     * Get snapshots with high sell pressure (imbalance < -threshold).
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Maximum imbalance ratio (negative)
     * @param since Minimum timestamp
     * @return List of snapshots with sell pressure
     */
    @Query("SELECT o FROM OrderBookData o WHERE o.symbol = :symbol " +
           "AND o.imbalance < :threshold AND o.timestamp >= :since " +
           "ORDER BY o.timestamp DESC")
    List<OrderBookData> findWithSellPressure(
            @Param("symbol") String symbol,
            @Param("threshold") double threshold,
            @Param("since") LocalDateTime since);

    /**
     * Get snapshots with wide spread (spreadBps > threshold).
     * 
     * @param symbol Stock/futures symbol
     * @param minSpreadBps Minimum spread in basis points
     * @param since Minimum timestamp
     * @return List of snapshots with wide spread
     */
    @Query("SELECT o FROM OrderBookData o WHERE o.symbol = :symbol " +
           "AND o.spreadBps > :minSpreadBps AND o.timestamp >= :since " +
           "ORDER BY o.timestamp DESC")
    List<OrderBookData> findWithWideSpread(
            @Param("symbol") String symbol,
            @Param("minSpreadBps") double minSpreadBps,
            @Param("since") LocalDateTime since);

    /**
     * Calculate average spread over a time period.
     * 
     * @param symbol Stock/futures symbol
     * @param since Minimum timestamp
     * @return Average spread in basis points
     */
    @Query("SELECT AVG(o.spreadBps) FROM OrderBookData o " +
           "WHERE o.symbol = :symbol AND o.timestamp >= :since")
    Double calculateAverageSpread(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since);

    /**
     * Calculate average imbalance over a time period.
     * 
     * @param symbol Stock/futures symbol
     * @param since Minimum timestamp
     * @return Average imbalance ratio
     */
    @Query("SELECT AVG(o.imbalance) FROM OrderBookData o " +
           "WHERE o.symbol = :symbol AND o.timestamp >= :since")
    Double calculateAverageImbalance(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since);

    /**
     * Count snapshots with significant imbalance (magnitude > threshold).
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Imbalance magnitude threshold
     * @param since Minimum timestamp
     * @return Count of snapshots
     */
    @Query("SELECT COUNT(o) FROM OrderBookData o WHERE o.symbol = :symbol " +
           "AND (o.imbalance > :threshold OR o.imbalance < -:threshold) " +
           "AND o.timestamp >= :since")
    long countSignificantImbalance(
            @Param("symbol") String symbol,
            @Param("threshold") double threshold,
            @Param("since") LocalDateTime since);

    /**
     * Get total bid and ask volumes for a symbol in a period.
     * 
     * @param symbol Stock/futures symbol
     * @param since Minimum timestamp
     * @return Array containing [total bid volume, total ask volume]
     */
    @Query("SELECT SUM(o.totalBidVolume), SUM(o.totalAskVolume) FROM OrderBookData o " +
           "WHERE o.symbol = :symbol AND o.timestamp >= :since")
    Object[] getTotalVolumes(
            @Param("symbol") String symbol,
            @Param("since") LocalDateTime since);

    /**
     * Delete old order book data to manage storage.
     * 
     * @param before Delete data before this timestamp
     * @return Number of records deleted
     */
    long deleteByTimestampBefore(LocalDateTime before);

    /**
     * Check if any order book data exists for a symbol.
     * 
     * @param symbol Stock/futures symbol
     * @return true if data exists
     */
    boolean existsBySymbol(String symbol);
}
