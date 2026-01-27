package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * EconomicEvent Entity - Economic calendar events for trading strategy timing.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Event Risk</b>: Reduce exposure before high-impact economic releases</li>
 *   <li><b>Seasonal Timing</b>: Identify historically strong/weak periods</li>
 *   <li><b>Calendar Spreads</b>: Plan trades around expiration dates</li>
 *   <li><b>Market Holidays</b>: Avoid trading on non-trading days</li>
 * </ul>
 * 
 * <h3>Event Categories:</h3>
 * <ul>
 *   <li><b>ECONOMIC_RELEASE</b>: GDP, CPI, unemployment, etc.</li>
 *   <li><b>CENTRAL_BANK</b>: Interest rate decisions, policy meetings</li>
 *   <li><b>FUTURES_EXPIRATION</b>: Futures and options settlement</li>
 *   <li><b>MARKET_HOLIDAY</b>: Market closures</li>
 *   <li><b>EARNINGS_SEASON</b>: Company earnings reporting periods</li>
 *   <li><b>TAX_DATE</b>: Tax-related deadlines</li>
 * </ul>
 * 
 * <h3>Data Sources:</h3>
 * <ul>
 *   <li>Taiwan market holidays from TWSE calendar</li>
 *   <li>Futures expiration from Taiwan Futures Exchange</li>
 *   <li>Economic releases from investing.com calendar</li>
 * </ul>
 * 
 * @see EconomicCalendarService for event retrieval and scheduling
 * @since 2026-01-26 - Phase 4 Data Improvement Plan
 */
@Entity
@Table(name = "economic_event", indexes = {
    @Index(name = "idx_event_date", columnList = "event_date"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_event_country", columnList = "country"),
    @Index(name = "idx_event_country_date", columnList = "country, event_date"),
    @Index(name = "idx_event_impact", columnList = "impact_level")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Event Identification ==========

    /**
     * Type of economic event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 30, nullable = false)
    private EventType eventType;

    /**
     * Event name/title (e.g., "GDP Growth Rate", "Fed Interest Rate Decision")
     */
    @Column(name = "event_name", length = 200, nullable = false)
    private String eventName;

    /**
     * Unique event code for deduplication (e.g., "TW_GDP_Q4_2026")
     */
    @Column(name = "event_code", length = 50, unique = true)
    private String eventCode;

    // ========== Timing ==========

    /**
     * Date of the event
     */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /**
     * Time of the event (if known, null for all-day events)
     */
    @Column(name = "event_time")
    private LocalDateTime eventTime;

    /**
     * When this event data was last updated
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ========== Geographic Scope ==========

    /**
     * Country code (ISO 3166-1 alpha-2, e.g., "TW", "US", "CN", "JP")
     */
    @Column(name = "country", length = 2, nullable = false)
    private String country;

    /**
     * Currency affected by this event (ISO 4217, e.g., "TWD", "USD")
     */
    @Column(name = "currency", length = 3)
    private String currency;

    // ========== Event Values ==========

    /**
     * Actual value when released (null before release)
     */
    @Column(name = "actual_value")
    private Double actualValue;

    /**
     * Forecasted/consensus value
     */
    @Column(name = "forecast_value")
    private Double forecastValue;

    /**
     * Previous period's value
     */
    @Column(name = "previous_value")
    private Double previousValue;

    /**
     * Unit of measurement (e.g., "%", "B", "K")
     */
    @Column(name = "value_unit", length = 20)
    private String valueUnit;

    // ========== Impact Assessment ==========

    /**
     * Expected market impact level
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", length = 10, nullable = false)
    private ImpactLevel impactLevel;

    /**
     * Affected market sectors (comma-separated, e.g., "FINANCE,TECH")
     */
    @Column(name = "affected_sectors", length = 200)
    private String affectedSectors;

    /**
     * Additional notes or context
     */
    @Column(name = "notes", length = 500)
    private String notes;

    // ========== Data Source ==========

    /**
     * Source of this event data (e.g., "TWSE", "investing.com", "TAIFEX")
     */
    @Column(name = "data_source", length = 50)
    private String dataSource;

    // ========== Enums ==========

    /**
     * Types of economic events tracked
     */
    public enum EventType {
        /** Economic data releases (GDP, CPI, PMI, etc.) */
        ECONOMIC_RELEASE,
        
        /** Central bank decisions (interest rates, policy) */
        CENTRAL_BANK,
        
        /** Futures/options expiration and settlement */
        FUTURES_EXPIRATION,
        
        /** Market holiday (exchange closed) */
        MARKET_HOLIDAY,
        
        /** Earnings reporting season */
        EARNINGS_SEASON,
        
        /** Tax-related dates (deadlines, refunds) */
        TAX_DATE,
        
        /** Dividend-related dates (ex-dividend, record) */
        DIVIDEND_DATE,
        
        /** IPO or corporate action */
        CORPORATE_ACTION,
        
        /** Political event (elections, policy changes) */
        POLITICAL_EVENT,
        
        /** Other significant event */
        OTHER
    }

    /**
     * Impact levels for event importance
     */
    public enum ImpactLevel {
        /** High impact - expect significant market movement */
        HIGH,
        
        /** Medium impact - moderate volatility expected */
        MEDIUM,
        
        /** Low impact - minimal market effect expected */
        LOW,
        
        /** Holiday - market closed, no trading */
        HOLIDAY
    }

    // ========== Utility Methods ==========

    /**
     * Check if this event is a market holiday (no trading)
     */
    public boolean isMarketHoliday() {
        return eventType == EventType.MARKET_HOLIDAY || impactLevel == ImpactLevel.HOLIDAY;
    }

    /**
     * Check if this event is high impact
     */
    public boolean isHighImpact() {
        return impactLevel == ImpactLevel.HIGH;
    }

    /**
     * Check if the event has been released (has actual value)
     */
    public boolean isReleased() {
        return actualValue != null;
    }

    /**
     * Check if the event is in the future
     */
    public boolean isFuture() {
        return eventDate.isAfter(LocalDate.now());
    }

    /**
     * Check if the event is today
     */
    public boolean isToday() {
        return eventDate.equals(LocalDate.now());
    }

    /**
     * Check if the event is within the next N days
     */
    public boolean isWithinDays(int days) {
        LocalDate now = LocalDate.now();
        return !eventDate.isBefore(now) && !eventDate.isAfter(now.plusDays(days));
    }

    /**
     * Calculate surprise factor (actual vs forecast deviation)
     * @return Surprise as percentage deviation, or null if not released
     */
    public Double getSurpriseFactor() {
        if (actualValue == null || forecastValue == null || forecastValue == 0) {
            return null;
        }
        return (actualValue - forecastValue) / Math.abs(forecastValue);
    }

    /**
     * Get the change from previous value
     * @return Change as percentage, or null if data unavailable
     */
    public Double getChangeFromPrevious() {
        if (actualValue == null || previousValue == null || previousValue == 0) {
            return null;
        }
        return (actualValue - previousValue) / Math.abs(previousValue);
    }

    /**
     * Check if this is a positive surprise (actual > forecast)
     */
    public boolean isPositiveSurprise() {
        Double surprise = getSurpriseFactor();
        return surprise != null && surprise > 0.01; // 1% threshold
    }

    /**
     * Check if this is a negative surprise (actual < forecast)
     */
    public boolean isNegativeSurprise() {
        Double surprise = getSurpriseFactor();
        return surprise != null && surprise < -0.01; // 1% threshold
    }

    /**
     * Check if this event affects a specific sector
     */
    public boolean affectsSector(String sector) {
        if (affectedSectors == null || affectedSectors.isEmpty()) {
            return false;
        }
        return affectedSectors.toUpperCase().contains(sector.toUpperCase());
    }

    /**
     * Check if this is a Taiwan-specific event
     */
    public boolean isTaiwanEvent() {
        return "TW".equals(country);
    }

    /**
     * Check if this is a US event (often affects global markets)
     */
    public boolean isUSEvent() {
        return "US".equals(country);
    }

    /**
     * Get days until this event
     * @return Days until event, negative if in past
     */
    public long getDaysUntil() {
        return eventDate.toEpochDay() - LocalDate.now().toEpochDay();
    }

    /**
     * Check if event is a futures expiration
     */
    public boolean isFuturesExpiration() {
        return eventType == EventType.FUTURES_EXPIRATION;
    }

    /**
     * Check if this is an interest rate decision
     */
    public boolean isInterestRateDecision() {
        return eventType == EventType.CENTRAL_BANK && 
               (eventName.toLowerCase().contains("interest rate") ||
                eventName.toLowerCase().contains("rate decision"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EconomicEvent that = (EconomicEvent) o;
        return Objects.equals(eventCode, that.eventCode) ||
               (Objects.equals(eventType, that.eventType) && 
                Objects.equals(eventDate, that.eventDate) &&
                Objects.equals(country, that.country) &&
                Objects.equals(eventName, that.eventName));
    }

    @Override
    public int hashCode() {
        if (eventCode != null) {
            return Objects.hash(eventCode);
        }
        return Objects.hash(eventType, eventDate, country, eventName);
    }

    @Override
    public String toString() {
        return String.format("EconomicEvent{type=%s, name='%s', date=%s, country=%s, impact=%s}",
            eventType, eventName, eventDate, country, impactLevel);
    }
}
