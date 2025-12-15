package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.ActiveStrategyConfig;

import java.util.Optional;

@Repository
public interface ActiveStrategyConfigRepository extends JpaRepository<ActiveStrategyConfig, Long> {
    
    /**
     * Find the current active strategy configuration.
     * There should only be one record.
     */
    Optional<ActiveStrategyConfig> findFirstByOrderByIdAsc();
}
