package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StrategyPerformanceRepository extends JpaRepository<StrategyPerformance, Long> {
    
    /**
     * Find all performance records for a strategy
     */
    List<StrategyPerformance> findByStrategyNameOrderByPeriodEndDesc(String strategyName);
    
    /**
     * Find performance records for a strategy in a specific mode
     */
    List<StrategyPerformance> findByStrategyNameAndPerformanceModeOrderByPeriodEndDesc(
        String strategyName, 
        StrategyPerformance.PerformanceMode mode
    );
    
    /**
     * Find all performance records within a time period
     */
    List<StrategyPerformance> findByPeriodEndBetweenOrderBySharpeRatioDesc(
        LocalDateTime start, 
        LocalDateTime end
    );
    
    /**
     * Find best performing strategies by Sharpe ratio
     */
    @Query("SELECT sp FROM StrategyPerformance sp WHERE sp.periodEnd >= :sinceDate " +
           "ORDER BY sp.sharpeRatio DESC")
    List<StrategyPerformance> findTopPerformersBySharpeRatio(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Find latest performance record for each strategy
     */
    @Query("SELECT sp FROM StrategyPerformance sp WHERE sp.id IN " +
           "(SELECT MAX(sp2.id) FROM StrategyPerformance sp2 GROUP BY sp2.strategyName) " +
           "ORDER BY sp.sharpeRatio DESC")
    List<StrategyPerformance> findLatestPerformanceForAllStrategies();
}
