package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StockSettings;

import java.util.Optional;

@Repository
public interface StockSettingsRepository extends JpaRepository<StockSettings, Long> {

    // Find latest settings (highest ID)
    Optional<StockSettings> findFirstByOrderByIdDesc();
    
    // Legacy compatibility
    default StockSettings findFirst() {
        return findAll().stream().findFirst().orElse(null);
    }
}