package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.BotSettings;

import java.util.Optional;

@Repository
public interface BotSettingsRepository extends JpaRepository<BotSettings, Long> {
    
    Optional<BotSettings> findByKey(String key);
    
    boolean existsByKey(String key);
}
