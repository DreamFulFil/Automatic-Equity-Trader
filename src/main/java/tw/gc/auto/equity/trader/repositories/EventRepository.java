package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.Event;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    List<Event> findByTypeAndTimestampBetweenOrderByTimestampDesc(Event.EventType type, LocalDateTime start, LocalDateTime end);

    List<Event> findBySeverityAndTimestampBetweenOrderByTimestampDesc(Event.EventSeverity severity, LocalDateTime start, LocalDateTime end);

    List<Event> findByCategoryAndTimestampBetweenOrderByTimestampDesc(String category, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.type = 'ERROR' AND e.timestamp >= :since")
    long countErrorsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.type = 'API_CALL' AND e.responseTimeMs > :threshold AND e.timestamp >= :since")
    long countSlowApiCallsSince(@Param("threshold") long threshold, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.category = 'TELEGRAM' AND e.timestamp >= :since")
    long countTelegramCommandsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.type = 'VETO' AND e.timestamp >= :since")
    long countNewsVetosSince(@Param("since") LocalDateTime since);
}