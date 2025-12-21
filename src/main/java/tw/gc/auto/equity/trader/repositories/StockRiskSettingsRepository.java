package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;

@Repository
public interface StockRiskSettingsRepository extends JpaRepository<StockRiskSettings, Long> {

    // Since we expect only one record, we can use findFirst or similar
    default StockRiskSettings findFirst() {
        return findAll().stream().findFirst().orElse(null);
    }
}