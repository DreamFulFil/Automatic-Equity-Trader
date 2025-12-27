package tw.gc.mtxfbot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.mtxfbot.entities.Quote;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for tick/quote data
 */
@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {
    
    List<Quote> findBySymbol(String symbol);
    
    List<Quote> findBySymbolAndTimestampBetween(
        String symbol, LocalDateTime start, LocalDateTime end);
    
    Optional<Quote> findFirstBySymbolOrderByTimestampDesc(String symbol);
    
    @Query("SELECT q FROM Quote q WHERE q.symbol = :symbol AND q.timestamp >= :since ORDER BY q.timestamp DESC")
    List<Quote> findRecentQuotes(
        @Param("symbol") String symbol, 
        @Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(q.spread) FROM Quote q WHERE q.symbol = :symbol AND q.timestamp >= :since")
    Double getAverageSpread(
        @Param("symbol") String symbol, 
        @Param("since") LocalDateTime since);
}
