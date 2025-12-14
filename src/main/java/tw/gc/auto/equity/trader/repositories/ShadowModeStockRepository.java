package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShadowModeStockRepository extends JpaRepository<ShadowModeStock, Long> {
    
    List<ShadowModeStock> findByEnabledTrueOrderByRankPosition();
    
    Optional<ShadowModeStock> findBySymbol(String symbol);
    
    void deleteBySymbol(String symbol);
}
