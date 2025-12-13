package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;

@Repository
public interface ShioajiSettingsRepository extends JpaRepository<ShioajiSettings, Long> {

    // Since we expect only one record, we can use findFirst or similar
    default ShioajiSettings findFirst() {
        return findAll().stream().findFirst().orElse(null);
    }
}