package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.BacktestRanking;

import java.util.List;
import java.util.Optional;

@Repository
public interface BacktestRankingRepository extends JpaRepository<BacktestRanking, Long> {
    
    List<BacktestRanking> findByBacktestRunIdOrderByRankPosition(String backtestRunId);
    
    Optional<BacktestRanking> findByBacktestRunIdAndRankPosition(String backtestRunId, Integer rankPosition);
    
    void deleteByBacktestRunId(String backtestRunId);
}
