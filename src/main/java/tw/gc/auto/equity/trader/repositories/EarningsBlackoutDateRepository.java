package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutDate;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;

import java.time.LocalDate;
import java.util.List;

public interface EarningsBlackoutDateRepository extends JpaRepository<EarningsBlackoutDate, Long> {
    List<EarningsBlackoutDate> findByMeta(EarningsBlackoutMeta meta);
    List<EarningsBlackoutDate> findByMetaId(Long metaId);
    boolean existsByMetaAndBlackoutDate(EarningsBlackoutMeta meta, LocalDate date);
}
