package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StockSettings;

@Repository
public interface StockSettingsRepository extends JpaRepository<StockSettings, Long> {

    // Since we expect only one record, we can use findFirst or similar
    default StockSettings findFirst() {
        return findAll().stream().findFirst().orElse(null);
    }
}