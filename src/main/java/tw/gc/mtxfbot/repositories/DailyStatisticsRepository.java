package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.mtxfbot.entities.DailyStatistics;
import tw.gc.mtxfbot.entities.Trade.TradingMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStatisticsRepository extends JpaRepository<DailyStatistics, Long> {

    Optional<DailyStatistics> findByTradeDateAndSymbol(LocalDate tradeDate, String symbol);

    List<DailyStatistics> findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
            String symbol, LocalDate start, LocalDate end);

    List<DailyStatistics> findBySymbolOrderByTradeDateDesc(String symbol);

    @Query("SELECT d FROM DailyStatistics d WHERE d.symbol = :symbol ORDER BY d.tradeDate DESC LIMIT :limit")
    List<DailyStatistics> findRecentBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);

    @Query("SELECT SUM(d.realizedPnL) FROM DailyStatistics d WHERE d.symbol = :symbol " +
           "AND d.tradeDate >= :since")
    Double sumPnLSince(@Param("symbol") String symbol, @Param("since") LocalDate since);

    @Query("SELECT AVG(d.winRate) FROM DailyStatistics d WHERE d.symbol = :symbol " +
           "AND d.tradeDate >= :since AND d.totalTrades > 0")
    Double avgWinRateSince(@Param("symbol") String symbol, @Param("since") LocalDate since);

    @Query("SELECT SUM(d.totalTrades) FROM DailyStatistics d WHERE d.symbol = :symbol " +
           "AND d.tradeDate >= :since")
    Long sumTradesSince(@Param("symbol") String symbol, @Param("since") LocalDate since);

    @Query("SELECT MAX(d.maxDrawdown) FROM DailyStatistics d WHERE d.symbol = :symbol " +
           "AND d.tradeDate >= :since")
    Double maxDrawdownSince(@Param("symbol") String symbol, @Param("since") LocalDate since);

    @Query("SELECT d FROM DailyStatistics d WHERE d.symbol = :symbol AND d.tradingMode = :mode " +
           "ORDER BY d.tradeDate DESC")
    List<DailyStatistics> findBySymbolAndMode(@Param("symbol") String symbol, @Param("mode") TradingMode mode);

    @Query("SELECT COUNT(d) FROM DailyStatistics d WHERE d.symbol = :symbol AND d.realizedPnL > 0 " +
           "AND d.tradeDate >= :since")
    long countProfitableDays(@Param("symbol") String symbol, @Param("since") LocalDate since);

    @Query("SELECT COUNT(d) FROM DailyStatistics d WHERE d.symbol = :symbol AND d.tradeDate >= :since")
    long countTradingDays(@Param("symbol") String symbol, @Param("since") LocalDate since);

    @Query("SELECT d FROM DailyStatistics d WHERE d.llamaInsight IS NULL AND d.totalTrades > 0 " +
           "ORDER BY d.tradeDate DESC")
    List<DailyStatistics> findDaysWithoutInsight();
}
