package tw.gc.auto.equity.trader.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.gc.auto.equity.trader.entities.EconomicEvent;
import tw.gc.auto.equity.trader.entities.EconomicEvent.EventType;
import tw.gc.auto.equity.trader.entities.EconomicEvent.ImpactLevel;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for EconomicEvent entity operations.
 * 
 * <h3>Query Categories:</h3>
 * <ul>
 *   <li><b>Upcoming Events</b>: Find events within next N days</li>
 *   <li><b>Date Range</b>: Events between two dates</li>
 *   <li><b>By Type</b>: Filter by event category</li>
 *   <li><b>By Country</b>: Filter by geographic scope</li>
 *   <li><b>High Impact</b>: Filter critical market-moving events</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 4 Data Improvement Plan
 */
@Repository
public interface EconomicEventRepository extends JpaRepository<EconomicEvent, Long> {

    // ========== By Event Code ==========

    /**
     * Find event by unique code
     */
    Optional<EconomicEvent> findByEventCode(String eventCode);

    /**
     * Check if event code exists
     */
    boolean existsByEventCode(String eventCode);

    // ========== Upcoming Events ==========

    /**
     * Find upcoming events within the next N days
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventDate >= :today AND e.eventDate <= :endDate ORDER BY e.eventDate ASC, e.impactLevel ASC")
    List<EconomicEvent> findUpcomingEvents(
        @Param("today") LocalDate today,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find upcoming high-impact events
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventDate >= :today AND e.eventDate <= :endDate AND e.impactLevel = 'HIGH' ORDER BY e.eventDate ASC")
    List<EconomicEvent> findUpcomingHighImpactEvents(
        @Param("today") LocalDate today,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find events for today
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventDate = :today ORDER BY e.impactLevel ASC")
    List<EconomicEvent> findTodaysEvents(@Param("today") LocalDate today);

    // ========== By Date Range ==========

    /**
     * Find events within a date range
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventDate >= :startDate AND e.eventDate <= :endDate ORDER BY e.eventDate ASC")
    List<EconomicEvent> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ========== By Country ==========

    /**
     * Find events by country code
     */
    List<EconomicEvent> findByCountryOrderByEventDateAsc(String country);

    /**
     * Find upcoming events by country
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.country = :country AND e.eventDate >= :today ORDER BY e.eventDate ASC")
    List<EconomicEvent> findUpcomingByCountry(
        @Param("country") String country,
        @Param("today") LocalDate today
    );

    /**
     * Find events by country and date range
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.country = :country AND e.eventDate >= :startDate AND e.eventDate <= :endDate ORDER BY e.eventDate ASC")
    List<EconomicEvent> findByCountryAndDateRange(
        @Param("country") String country,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ========== By Event Type ==========

    /**
     * Find events by type
     */
    List<EconomicEvent> findByEventTypeOrderByEventDateAsc(EventType eventType);

    /**
     * Find upcoming events by type
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = :eventType AND e.eventDate >= :today ORDER BY e.eventDate ASC")
    List<EconomicEvent> findUpcomingByType(
        @Param("eventType") EventType eventType,
        @Param("today") LocalDate today
    );

    // ========== Market Holidays ==========

    /**
     * Find market holidays within a date range
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'MARKET_HOLIDAY' AND e.eventDate >= :startDate AND e.eventDate <= :endDate ORDER BY e.eventDate ASC")
    List<EconomicEvent> findMarketHolidays(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find market holidays for a country
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'MARKET_HOLIDAY' AND e.country = :country AND e.eventDate >= :startDate AND e.eventDate <= :endDate ORDER BY e.eventDate ASC")
    List<EconomicEvent> findMarketHolidaysByCountry(
        @Param("country") String country,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Check if a date is a market holiday
     */
    @Query("SELECT COUNT(e) > 0 FROM EconomicEvent e WHERE e.eventType = 'MARKET_HOLIDAY' AND e.country = :country AND e.eventDate = :date")
    boolean isMarketHoliday(
        @Param("country") String country,
        @Param("date") LocalDate date
    );

    // ========== Futures Expiration ==========

    /**
     * Find futures expiration dates
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'FUTURES_EXPIRATION' AND e.eventDate >= :startDate AND e.eventDate <= :endDate ORDER BY e.eventDate ASC")
    List<EconomicEvent> findFuturesExpirations(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find next futures expiration date
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'FUTURES_EXPIRATION' AND e.country = :country AND e.eventDate >= :today ORDER BY e.eventDate ASC LIMIT 1")
    Optional<EconomicEvent> findNextFuturesExpiration(
        @Param("country") String country,
        @Param("today") LocalDate today
    );

    // ========== By Impact Level ==========

    /**
     * Find events by impact level
     */
    List<EconomicEvent> findByImpactLevelOrderByEventDateAsc(ImpactLevel impactLevel);

    /**
     * Find upcoming events by impact level
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.impactLevel = :impactLevel AND e.eventDate >= :today AND e.eventDate <= :endDate ORDER BY e.eventDate ASC")
    List<EconomicEvent> findUpcomingByImpactLevel(
        @Param("impactLevel") ImpactLevel impactLevel,
        @Param("today") LocalDate today,
        @Param("endDate") LocalDate endDate
    );

    // ========== Count Queries ==========

    /**
     * Count upcoming high-impact events
     */
    @Query("SELECT COUNT(e) FROM EconomicEvent e WHERE e.impactLevel = 'HIGH' AND e.eventDate >= :today AND e.eventDate <= :endDate")
    long countUpcomingHighImpactEvents(
        @Param("today") LocalDate today,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Count events by country and date range
     */
    @Query("SELECT COUNT(e) FROM EconomicEvent e WHERE e.country = :country AND e.eventDate >= :startDate AND e.eventDate <= :endDate")
    long countByCountryAndDateRange(
        @Param("country") String country,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // ========== Central Bank Events ==========

    /**
     * Find upcoming central bank events
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'CENTRAL_BANK' AND e.eventDate >= :today ORDER BY e.eventDate ASC")
    List<EconomicEvent> findUpcomingCentralBankEvents(@Param("today") LocalDate today);

    /**
     * Find next interest rate decision
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'CENTRAL_BANK' AND e.country = :country AND e.eventDate >= :today AND LOWER(e.eventName) LIKE '%rate%' ORDER BY e.eventDate ASC LIMIT 1")
    Optional<EconomicEvent> findNextInterestRateDecision(
        @Param("country") String country,
        @Param("today") LocalDate today
    );

    // ========== Economic Releases ==========

    /**
     * Find upcoming economic releases by country
     */
    @Query("SELECT e FROM EconomicEvent e WHERE e.eventType = 'ECONOMIC_RELEASE' AND e.country = :country AND e.eventDate >= :today ORDER BY e.eventDate ASC")
    List<EconomicEvent> findUpcomingEconomicReleases(
        @Param("country") String country,
        @Param("today") LocalDate today
    );

    // ========== Delete/Cleanup ==========

    /**
     * Delete old events (cleanup)
     */
    @Query("DELETE FROM EconomicEvent e WHERE e.eventDate < :cutoffDate")
    void deleteOldEvents(@Param("cutoffDate") LocalDate cutoffDate);
}
