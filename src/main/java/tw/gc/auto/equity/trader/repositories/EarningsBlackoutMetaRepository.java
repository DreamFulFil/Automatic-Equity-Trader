package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;

import java.util.Optional;

public interface EarningsBlackoutMetaRepository extends JpaRepository<EarningsBlackoutMeta, Long> {

    @EntityGraph(attributePaths = {"dates", "tickersChecked"})
    Optional<EarningsBlackoutMeta> findFirstByOrderByLastUpdatedDesc();
}
