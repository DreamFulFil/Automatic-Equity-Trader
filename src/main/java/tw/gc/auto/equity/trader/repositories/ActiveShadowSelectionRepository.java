package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.ActiveShadowSelection;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActiveShadowSelectionRepository extends JpaRepository<ActiveShadowSelection, Long> {
    
    Optional<ActiveShadowSelection> findByRankPosition(Integer rankPosition);
    
    Optional<ActiveShadowSelection> findByIsActiveTrue();
    
    List<ActiveShadowSelection> findByIsActiveFalseOrderByRankPosition();
    
    List<ActiveShadowSelection> findAllByOrderByRankPosition();
}
