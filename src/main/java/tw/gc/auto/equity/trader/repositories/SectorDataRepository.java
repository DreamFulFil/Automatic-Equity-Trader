package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.SectorData;

import java.util.Optional;

@Repository
public interface SectorDataRepository extends JpaRepository<SectorData, Long> {

    Optional<SectorData> findBySymbol(String symbol);
}
