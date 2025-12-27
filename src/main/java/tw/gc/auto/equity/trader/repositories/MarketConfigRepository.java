package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.MarketConfig;

import java.util.List;
import java.util.Optional;

/**
 * Repository for market configuration
 */
@Repository
public interface MarketConfigRepository extends JpaRepository<MarketConfig, Long> {
    
    Optional<MarketConfig> findByMarketCode(String marketCode);
    
    List<MarketConfig> findByEnabledTrue();
    
    List<MarketConfig> findByRegion(String region);
    
    List<MarketConfig> findByDataProvider(String dataProvider);
}
