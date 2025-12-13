package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StrategyConfig;
import tw.gc.auto.equity.trader.entities.StrategyConfig.StrategyType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for strategy configuration
 */
@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {
    
    Optional<StrategyConfig> findByStrategyName(String strategyName);
    
    List<StrategyConfig> findByEnabledTrue();
    
    List<StrategyConfig> findByStrategyType(StrategyType strategyType);
    
    List<StrategyConfig> findByMarketCode(String marketCode);
    
    @Query("SELECT s FROM StrategyConfig s WHERE s.enabled = true ORDER BY s.priority DESC, s.strategyName")
    List<StrategyConfig> findEnabledStrategiesOrderedByPriority();
    
    List<StrategyConfig> findByEnabledTrueAndMarketCode(String marketCode);
}
