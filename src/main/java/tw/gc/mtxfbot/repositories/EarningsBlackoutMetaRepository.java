package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.gc.mtxfbot.entities.EarningsBlackoutMeta;

import java.util.Optional;

public interface EarningsBlackoutMetaRepository extends JpaRepository<EarningsBlackoutMeta, Long> {

    @EntityGraph(attributePaths = {"dates", "tickersChecked"})
    Optional<EarningsBlackoutMeta> findFirstByOrderByLastUpdatedDesc();
}
