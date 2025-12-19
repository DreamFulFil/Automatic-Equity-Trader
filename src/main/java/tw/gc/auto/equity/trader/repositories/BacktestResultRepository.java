package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.BacktestResult;

import java.util.List;

@Repository
public interface BacktestResultRepository extends JpaRepository<BacktestResult, Long> {
    
    List<BacktestResult> findByBacktestRunIdOrderBySharpeRatioDesc(String backtestRunId);
    
    List<BacktestResult> findBySymbolAndStrategyName(String symbol, String strategyName);
    
    @Query("SELECT DISTINCT br.backtestRunId FROM BacktestResult br ORDER BY br.createdAt DESC")
    List<String> findDistinctBacktestRunIds();
    
    @Query("SELECT br FROM BacktestResult br WHERE br.backtestRunId = :runId " +
           "ORDER BY (br.sharpeRatio * 0.4 + br.totalReturnPct * 0.3 + br.winRatePct * 0.2 - br.maxDrawdownPct * 0.1) DESC")
    List<BacktestResult> findTopPerformers(@Param("runId") String runId);
    
    long countByBacktestRunId(String backtestRunId);
    
    void deleteByBacktestRunId(String backtestRunId);
}
