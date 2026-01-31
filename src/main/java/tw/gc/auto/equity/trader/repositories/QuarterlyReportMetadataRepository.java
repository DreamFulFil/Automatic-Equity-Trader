package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.QuarterlyReportMetadata;

import java.util.Optional;
import java.util.List;

@Repository
public interface QuarterlyReportMetadataRepository extends JpaRepository<QuarterlyReportMetadata, Long> {

    Optional<QuarterlyReportMetadata> findByTickerAndReportYearAndReportQuarter(
        String ticker,
        Integer reportYear,
        Integer reportQuarter
    );

    Optional<QuarterlyReportMetadata> findFirstByTickerOrderByReportYearDescReportQuarterDesc(String ticker);

    List<QuarterlyReportMetadata> findByTickerOrderByReportYearDescReportQuarterDesc(String ticker);
}
