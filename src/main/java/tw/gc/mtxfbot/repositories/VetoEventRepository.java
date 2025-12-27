package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.mtxfbot.entities.VetoEvent;
import tw.gc.mtxfbot.entities.VetoEvent.VetoSource;
import tw.gc.mtxfbot.entities.VetoEvent.VetoType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for veto events
 */
@Repository
public interface VetoEventRepository extends JpaRepository<VetoEvent, Long> {
    
    List<VetoEvent> findByIsActiveTrue();
    
    List<VetoEvent> findBySymbolAndIsActiveTrue(String symbol);
    
    List<VetoEvent> findBySource(VetoSource source);
    
    List<VetoEvent> findByVetoType(VetoType vetoType);
    
    List<VetoEvent> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT v FROM VetoEvent v WHERE v.isActive = true " +
           "AND (v.expiresAt IS NULL OR v.expiresAt > :now)")
    List<VetoEvent> findCurrentlyActiveVetoes(@Param("now") LocalDateTime now);
    
    @Query("SELECT v FROM VetoEvent v WHERE v.symbol = :symbol " +
           "AND v.isActive = true AND (v.expiresAt IS NULL OR v.expiresAt > :now)")
    Optional<VetoEvent> findActiveVetoForSymbol(
        @Param("symbol") String symbol,
        @Param("now") LocalDateTime now);
    
    @Query("SELECT v.vetoType, COUNT(v) FROM VetoEvent v GROUP BY v.vetoType")
    List<Object[]> countByVetoType();
    
    @Query("SELECT v.source, COUNT(v) FROM VetoEvent v GROUP BY v.source")
    List<Object[]> countBySource();
}
