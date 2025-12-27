package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.entities.Trade.TradingMode;
import tw.gc.mtxfbot.entities.Trade.TradeStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    
    List<Trade> findByModeAndStatusOrderByTimestampDesc(TradingMode mode, TradeStatus status);
    
    List<Trade> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<Trade> findByModeAndTimestampBetween(TradingMode mode, LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.mode = :mode AND t.status = :status AND t.realizedPnL > 0")
    long countWinningTrades(@Param("mode") TradingMode mode, @Param("status") TradeStatus status);
    
    @Query("SELECT COUNT(t) FROM Trade t WHERE t.mode = :mode AND t.status = :status")
    long countTotalTrades(@Param("mode") TradingMode mode, @Param("status") TradeStatus status);
    
    @Query("SELECT COALESCE(SUM(t.realizedPnL), 0) FROM Trade t WHERE t.mode = :mode AND t.timestamp >= :since")
    double sumPnLSince(@Param("mode") TradingMode mode, @Param("since") LocalDateTime since);
    
    @Query("SELECT MAX(ABS(t.realizedPnL)) FROM Trade t WHERE t.mode = :mode AND t.realizedPnL < 0 AND t.timestamp >= :since")
    Double maxDrawdownSince(@Param("mode") TradingMode mode, @Param("since") LocalDateTime since);
    
    /** Get the most recent open trade for closing */
    List<Trade> findByStatusOrderByTimestampDesc(TradeStatus status);

    Optional<Trade> findFirstBySymbolAndStatusOrderByTimestampDesc(String symbol, TradeStatus status);

    Optional<Trade> findFirstBySymbolAndModeAndStatusOrderByTimestampDesc(String symbol, TradingMode mode, TradeStatus status);
}
