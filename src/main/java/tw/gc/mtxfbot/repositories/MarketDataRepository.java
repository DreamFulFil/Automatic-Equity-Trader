package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.entities.MarketData.Timeframe;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    List<MarketData> findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            String symbol, Timeframe timeframe, LocalDateTime start, LocalDateTime end);

    List<MarketData> findBySymbolAndTimeframeOrderByTimestampDesc(String symbol, Timeframe timeframe);

    Optional<MarketData> findFirstBySymbolAndTimeframeOrderByTimestampDesc(String symbol, Timeframe timeframe);

    @Query("SELECT m FROM MarketData m WHERE m.symbol = :symbol AND m.timeframe = :timeframe " +
           "ORDER BY m.timestamp DESC LIMIT :limit")
    List<MarketData> findRecentBySymbolAndTimeframe(
            @Param("symbol") String symbol, 
            @Param("timeframe") Timeframe timeframe, 
            @Param("limit") int limit);

    @Query("SELECT AVG(m.close) FROM MarketData m WHERE m.symbol = :symbol AND m.timeframe = :timeframe " +
           "AND m.timestamp >= :since")
    Double averageCloseSince(@Param("symbol") String symbol, @Param("timeframe") Timeframe timeframe,
                             @Param("since") LocalDateTime since);

    @Query("SELECT AVG(m.volume) FROM MarketData m WHERE m.symbol = :symbol AND m.timeframe = :timeframe " +
           "AND m.timestamp >= :since")
    Double averageVolumeSince(@Param("symbol") String symbol, @Param("timeframe") Timeframe timeframe,
                              @Param("since") LocalDateTime since);

    @Query("SELECT MAX(m.high) FROM MarketData m WHERE m.symbol = :symbol AND m.timeframe = :timeframe " +
           "AND m.timestamp >= :since")
    Double maxHighSince(@Param("symbol") String symbol, @Param("timeframe") Timeframe timeframe,
                        @Param("since") LocalDateTime since);

    @Query("SELECT MIN(m.low) FROM MarketData m WHERE m.symbol = :symbol AND m.timeframe = :timeframe " +
           "AND m.timestamp >= :since")
    Double minLowSince(@Param("symbol") String symbol, @Param("timeframe") Timeframe timeframe,
                       @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(m) FROM MarketData m WHERE m.symbol = :symbol AND m.timeframe = :timeframe " +
           "AND m.timestamp >= :since")
    long countSince(@Param("symbol") String symbol, @Param("timeframe") Timeframe timeframe,
                    @Param("since") LocalDateTime since);

    void deleteByTimestampBefore(LocalDateTime before);
}
