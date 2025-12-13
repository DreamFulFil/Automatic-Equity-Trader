package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.Signal;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {

    List<Signal> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    List<Signal> findBySymbolAndTimestampBetweenOrderByTimestampDesc(String symbol, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s FROM Signal s WHERE s.confidence >= :minConfidence AND s.timestamp >= :since ORDER BY s.timestamp DESC")
    List<Signal> findHighConfidenceSignalsSince(@Param("minConfidence") double minConfidence, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(s) FROM Signal s WHERE s.direction = 'LONG' AND s.timestamp >= :since")
    long countLongSignalsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(s) FROM Signal s WHERE s.direction = 'SHORT' AND s.timestamp >= :since")
    long countShortSignalsSince(@Param("since") LocalDateTime since);

    @Query("SELECT AVG(s.confidence) FROM Signal s WHERE s.timestamp >= :since")
    Double averageConfidenceSince(@Param("since") LocalDateTime since);
}