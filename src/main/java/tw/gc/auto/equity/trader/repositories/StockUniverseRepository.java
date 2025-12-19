package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StockUniverse;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockUniverseRepository extends JpaRepository<StockUniverse, Long> {
    
    Optional<StockUniverse> findBySymbol(String symbol);
    
    List<StockUniverse> findByEnabledTrueOrderBySelectionScoreDesc();
    
    long countByEnabledTrue();
}
