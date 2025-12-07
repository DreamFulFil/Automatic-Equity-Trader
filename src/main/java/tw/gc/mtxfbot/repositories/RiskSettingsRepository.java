package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.mtxfbot.entities.RiskSettings;

@Repository
public interface RiskSettingsRepository extends JpaRepository<RiskSettings, Long> {

    // Since we expect only one record, we can use findFirst or similar
    default RiskSettings findFirst() {
        return findAll().stream().findFirst().orElse(null);
    }
}