package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.gc.mtxfbot.entities.EarningsBlackoutDate;
import tw.gc.mtxfbot.entities.EarningsBlackoutMeta;

import java.time.LocalDate;
import java.util.List;

public interface EarningsBlackoutDateRepository extends JpaRepository<EarningsBlackoutDate, Long> {
    List<EarningsBlackoutDate> findByMeta(EarningsBlackoutMeta meta);
    List<EarningsBlackoutDate> findByMetaId(Long metaId);
    boolean existsByMetaAndBlackoutDate(EarningsBlackoutMeta meta, LocalDate date);
}
