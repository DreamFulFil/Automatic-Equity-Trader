package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Strategy-Stock Performance Mapping
 */
@Repository
public interface StrategyStockMappingRepository extends JpaRepository<StrategyStockMapping, Long> {
    
    /**
     * Find mapping by symbol and strategy
     */
    Optional<StrategyStockMapping> findBySymbolAndStrategyName(String symbol, String strategyName);
    
    /**
     * Get all mappings for a specific stock
     */
    List<StrategyStockMapping> findBySymbolOrderBySharpeRatioDesc(String symbol);
    
    /**
     * Get best performing strategy for a stock
     */
    @Query("SELECT m FROM StrategyStockMapping m WHERE m.symbol = :symbol AND m.recommended = true")
    Optional<StrategyStockMapping> findRecommendedForStock(@Param("symbol") String symbol);
    
    /**
     * Get all stocks that work well with a specific strategy
     */
    List<StrategyStockMapping> findByStrategyNameOrderBySharpeRatioDesc(String strategyName);
    
    /**
     * Get top N best performing strategy-stock combinations
     */
    @Query("SELECT m FROM StrategyStockMapping m ORDER BY m.sharpeRatio DESC")
    List<StrategyStockMapping> findTopPerformingCombinations();
    
    /**
     * Get low-risk combinations for risk-averse users
     */
    @Query("SELECT m FROM StrategyStockMapping m WHERE m.riskLevel = 'LOW' ORDER BY m.sharpeRatio DESC")
    List<StrategyStockMapping> findLowRiskCombinations();
    
    /**
     * Get combinations with steady returns (good Sharpe, low drawdown)
     */
    @Query("SELECT m FROM StrategyStockMapping m WHERE m.sharpeRatio > 1.0 AND ABS(m.maxDrawdownPct) < 10 ORDER BY m.totalReturnPct DESC")
    List<StrategyStockMapping> findSteadyReturnCombinations();
}
