package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.Bar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for OHLCV bar data
 */
@Repository
public interface BarRepository extends JpaRepository<Bar, Long> {
    
    List<Bar> findBySymbolAndTimeframe(String symbol, String timeframe);
    
    List<Bar> findBySymbolAndTimeframeAndTimestampBetween(
        String symbol, String timeframe, LocalDateTime start, LocalDateTime end);
    
    Optional<Bar> findFirstBySymbolAndTimeframeOrderByTimestampDesc(
        String symbol, String timeframe);
    
    @Query("SELECT b FROM Bar b WHERE b.symbol = :symbol AND b.timeframe = :timeframe " +
           "AND b.timestamp >= :since ORDER BY b.timestamp DESC")
    List<Bar> findRecentBars(
        @Param("symbol") String symbol,
        @Param("timeframe") String timeframe,
        @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(b.volume) FROM Bar b WHERE b.symbol = :symbol " +
           "AND b.timeframe = :timeframe AND b.timestamp >= :since")
    Double getAverageVolume(
        @Param("symbol") String symbol,
        @Param("timeframe") String timeframe,
        @Param("since") LocalDateTime since);
    
    List<Bar> findBySymbolAndTimeframeAndIsCompleteTrue(String symbol, String timeframe);
    
    boolean existsBySymbolAndTimestampAndTimeframe(String symbol, LocalDateTime timestamp, String timeframe);
    
    /**
     * Truncate all bar data for clean 10-year backtest ingestion
     */
    @Modifying
    @Query(value = "TRUNCATE TABLE bar RESTART IDENTITY CASCADE", nativeQuery = true)
    void truncateTable();
}
