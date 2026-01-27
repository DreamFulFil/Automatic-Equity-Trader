package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.EconomicEvent.EventType;
import tw.gc.auto.equity.trader.entities.EconomicEvent.ImpactLevel;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EconomicEvent entity.
 */
@DisplayName("EconomicEvent Entity Tests")
class EconomicEventTest {

    private EconomicEvent event;

    @BeforeEach
    void setUp() {
        event = EconomicEvent.builder()
            .id(1L)
            .eventType(EventType.ECONOMIC_RELEASE)
            .eventName("GDP Growth Rate")
            .eventCode("TW_GDP_Q4_2026")
            .eventDate(LocalDate.of(2026, 1, 30))
            .country("TW")
            .currency("TWD")
            .forecastValue(3.5)
            .previousValue(3.2)
            .impactLevel(ImpactLevel.HIGH)
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    @Nested
    @DisplayName("Basic Property Tests")
    class BasicPropertyTests {

        @Test
        @DisplayName("Should create event with all fields")
        void shouldCreateEventWithAllFields() {
            assertThat(event.getId()).isEqualTo(1L);
            assertThat(event.getEventType()).isEqualTo(EventType.ECONOMIC_RELEASE);
            assertThat(event.getEventName()).isEqualTo("GDP Growth Rate");
            assertThat(event.getEventCode()).isEqualTo("TW_GDP_Q4_2026");
            assertThat(event.getEventDate()).isEqualTo(LocalDate.of(2026, 1, 30));
            assertThat(event.getCountry()).isEqualTo("TW");
            assertThat(event.getCurrency()).isEqualTo("TWD");
            assertThat(event.getForecastValue()).isEqualTo(3.5);
            assertThat(event.getPreviousValue()).isEqualTo(3.2);
            assertThat(event.getImpactLevel()).isEqualTo(ImpactLevel.HIGH);
        }

        @Test
        @DisplayName("Should handle null actual value before release")
        void shouldHandleNullActualValue() {
            assertThat(event.getActualValue()).isNull();
            assertThat(event.isReleased()).isFalse();
        }
    }

    @Nested
    @DisplayName("Holiday Tests")
    class HolidayTests {

        @Test
        @DisplayName("Should identify market holiday by type")
        void shouldIdentifyMarketHolidayByType() {
            EconomicEvent holiday = EconomicEvent.builder()
                .eventType(EventType.MARKET_HOLIDAY)
                .eventName("New Year's Day")
                .eventDate(LocalDate.of(2026, 1, 1))
                .country("TW")
                .impactLevel(ImpactLevel.HOLIDAY)
                .updatedAt(OffsetDateTime.now())
                .build();

            assertThat(holiday.isMarketHoliday()).isTrue();
        }

        @Test
        @DisplayName("Should identify market holiday by impact level")
        void shouldIdentifyMarketHolidayByImpactLevel() {
            EconomicEvent holiday = EconomicEvent.builder()
                .eventType(EventType.OTHER)
                .eventName("Special Holiday")
                .eventDate(LocalDate.of(2026, 1, 2))
                .country("TW")
                .impactLevel(ImpactLevel.HOLIDAY)
                .updatedAt(OffsetDateTime.now())
                .build();

            assertThat(holiday.isMarketHoliday()).isTrue();
        }

        @Test
        @DisplayName("Should not identify non-holiday as holiday")
        void shouldNotIdentifyNonHolidayAsHoliday() {
            assertThat(event.isMarketHoliday()).isFalse();
        }
    }

    @Nested
    @DisplayName("Impact Level Tests")
    class ImpactLevelTests {

        @Test
        @DisplayName("Should identify high impact events")
        void shouldIdentifyHighImpact() {
            assertThat(event.isHighImpact()).isTrue();
        }

        @Test
        @DisplayName("Should not identify medium impact as high")
        void shouldNotIdentifyMediumAsHigh() {
            event.setImpactLevel(ImpactLevel.MEDIUM);
            assertThat(event.isHighImpact()).isFalse();
        }

        @Test
        @DisplayName("Should not identify low impact as high")
        void shouldNotIdentifyLowAsHigh() {
            event.setImpactLevel(ImpactLevel.LOW);
            assertThat(event.isHighImpact()).isFalse();
        }
    }

    @Nested
    @DisplayName("Date Tests")
    class DateTests {

        @Test
        @DisplayName("Should identify future events")
        void shouldIdentifyFutureEvents() {
            event.setEventDate(LocalDate.now().plusDays(10));
            assertThat(event.isFuture()).isTrue();
        }

        @Test
        @DisplayName("Should identify past events")
        void shouldIdentifyPastEvents() {
            event.setEventDate(LocalDate.now().minusDays(10));
            assertThat(event.isFuture()).isFalse();
        }

        @Test
        @DisplayName("Should identify today's events")
        void shouldIdentifyTodaysEvents() {
            event.setEventDate(LocalDate.now());
            assertThat(event.isToday()).isTrue();
        }

        @Test
        @DisplayName("Should check events within days")
        void shouldCheckEventsWithinDays() {
            event.setEventDate(LocalDate.now().plusDays(3));
            assertThat(event.isWithinDays(5)).isTrue();
            assertThat(event.isWithinDays(2)).isFalse();
        }

        @Test
        @DisplayName("Should calculate days until event")
        void shouldCalculateDaysUntil() {
            event.setEventDate(LocalDate.now().plusDays(5));
            assertThat(event.getDaysUntil()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should handle past events in days until")
        void shouldHandlePastEventsInDaysUntil() {
            event.setEventDate(LocalDate.now().minusDays(3));
            assertThat(event.getDaysUntil()).isEqualTo(-3);
        }
    }

    @Nested
    @DisplayName("Surprise Factor Tests")
    class SurpriseFactorTests {

        @Test
        @DisplayName("Should calculate positive surprise")
        void shouldCalculatePositiveSurprise() {
            event.setActualValue(4.0);
            event.setForecastValue(3.5);

            Double surprise = event.getSurpriseFactor();
            assertThat(surprise).isNotNull();
            assertThat(surprise).isGreaterThan(0);
            assertThat(event.isPositiveSurprise()).isTrue();
            assertThat(event.isNegativeSurprise()).isFalse();
        }

        @Test
        @DisplayName("Should calculate negative surprise")
        void shouldCalculateNegativeSurprise() {
            event.setActualValue(3.0);
            event.setForecastValue(3.5);

            Double surprise = event.getSurpriseFactor();
            assertThat(surprise).isNotNull();
            assertThat(surprise).isLessThan(0);
            assertThat(event.isNegativeSurprise()).isTrue();
            assertThat(event.isPositiveSurprise()).isFalse();
        }

        @Test
        @DisplayName("Should return null surprise when not released")
        void shouldReturnNullSurpriseWhenNotReleased() {
            assertThat(event.getSurpriseFactor()).isNull();
        }

        @Test
        @DisplayName("Should handle zero forecast")
        void shouldHandleZeroForecast() {
            event.setActualValue(1.0);
            event.setForecastValue(0.0);
            assertThat(event.getSurpriseFactor()).isNull();
        }
    }

    @Nested
    @DisplayName("Change from Previous Tests")
    class ChangeFromPreviousTests {

        @Test
        @DisplayName("Should calculate change from previous")
        void shouldCalculateChangeFromPrevious() {
            event.setActualValue(4.0);
            event.setPreviousValue(3.2);

            Double change = event.getChangeFromPrevious();
            assertThat(change).isNotNull();
            assertThat(change).isCloseTo(0.25, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("Should return null when no actual value")
        void shouldReturnNullWhenNoActual() {
            assertThat(event.getChangeFromPrevious()).isNull();
        }

        @Test
        @DisplayName("Should handle negative previous value")
        void shouldHandleNegativePreviousValue() {
            event.setActualValue(-2.0);
            event.setPreviousValue(-3.0);

            Double change = event.getChangeFromPrevious();
            assertThat(change).isNotNull();
        }
    }

    @Nested
    @DisplayName("Sector Tests")
    class SectorTests {

        @Test
        @DisplayName("Should identify affected sectors")
        void shouldIdentifyAffectedSectors() {
            event.setAffectedSectors("FINANCE,TECH,BANKING");

            assertThat(event.affectsSector("FINANCE")).isTrue();
            assertThat(event.affectsSector("finance")).isTrue(); // Case insensitive
            assertThat(event.affectsSector("TECH")).isTrue();
            assertThat(event.affectsSector("RETAIL")).isFalse();
        }

        @Test
        @DisplayName("Should handle null affected sectors")
        void shouldHandleNullAffectedSectors() {
            event.setAffectedSectors(null);
            assertThat(event.affectsSector("FINANCE")).isFalse();
        }

        @Test
        @DisplayName("Should handle empty affected sectors")
        void shouldHandleEmptyAffectedSectors() {
            event.setAffectedSectors("");
            assertThat(event.affectsSector("FINANCE")).isFalse();
        }
    }

    @Nested
    @DisplayName("Country Tests")
    class CountryTests {

        @Test
        @DisplayName("Should identify Taiwan events")
        void shouldIdentifyTaiwanEvents() {
            assertThat(event.isTaiwanEvent()).isTrue();
        }

        @Test
        @DisplayName("Should identify US events")
        void shouldIdentifyUSEvents() {
            event.setCountry("US");
            assertThat(event.isUSEvent()).isTrue();
            assertThat(event.isTaiwanEvent()).isFalse();
        }
    }

    @Nested
    @DisplayName("Event Type Tests")
    class EventTypeTests {

        @Test
        @DisplayName("Should identify futures expiration")
        void shouldIdentifyFuturesExpiration() {
            event.setEventType(EventType.FUTURES_EXPIRATION);
            assertThat(event.isFuturesExpiration()).isTrue();
        }

        @Test
        @DisplayName("Should identify interest rate decision")
        void shouldIdentifyInterestRateDecision() {
            event.setEventType(EventType.CENTRAL_BANK);
            event.setEventName("Federal Reserve Interest Rate Decision");
            assertThat(event.isInterestRateDecision()).isTrue();
        }

        @Test
        @DisplayName("Should not identify non-rate central bank event")
        void shouldNotIdentifyNonRateCentralBankEvent() {
            event.setEventType(EventType.CENTRAL_BANK);
            event.setEventName("Federal Reserve Meeting Minutes");
            assertThat(event.isInterestRateDecision()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal by event code")
        void shouldBeEqualByEventCode() {
            EconomicEvent event1 = EconomicEvent.builder()
                .eventCode("TW_GDP_Q4_2026")
                .eventType(EventType.ECONOMIC_RELEASE)
                .eventDate(LocalDate.of(2026, 1, 30))
                .country("TW")
                .impactLevel(ImpactLevel.HIGH)
                .eventName("GDP")
                .updatedAt(OffsetDateTime.now())
                .build();

            EconomicEvent event2 = EconomicEvent.builder()
                .eventCode("TW_GDP_Q4_2026")
                .eventType(EventType.ECONOMIC_RELEASE)
                .eventDate(LocalDate.of(2026, 1, 31)) // Different date
                .country("TW")
                .impactLevel(ImpactLevel.MEDIUM) // Different impact
                .eventName("GDP Growth")
                .updatedAt(OffsetDateTime.now())
                .build();

            assertThat(event1).isEqualTo(event2);
            assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
        }

        @Test
        @DisplayName("Should be equal by composite key when no event code")
        void shouldBeEqualByCompositeKey() {
            EconomicEvent event1 = EconomicEvent.builder()
                .eventType(EventType.MARKET_HOLIDAY)
                .eventName("New Year")
                .eventDate(LocalDate.of(2026, 1, 1))
                .country("TW")
                .impactLevel(ImpactLevel.HOLIDAY)
                .updatedAt(OffsetDateTime.now())
                .build();

            EconomicEvent event2 = EconomicEvent.builder()
                .eventType(EventType.MARKET_HOLIDAY)
                .eventName("New Year")
                .eventDate(LocalDate.of(2026, 1, 1))
                .country("TW")
                .impactLevel(ImpactLevel.HOLIDAY)
                .updatedAt(OffsetDateTime.now())
                .build();

            assertThat(event1).isEqualTo(event2);
        }
    }

    @Nested
    @DisplayName("toString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should produce readable string representation")
        void shouldProduceReadableString() {
            String str = event.toString();

            assertThat(str).contains("ECONOMIC_RELEASE");
            assertThat(str).contains("GDP Growth Rate");
            assertThat(str).contains("TW");
            assertThat(str).contains("HIGH");
        }
    }

    @Nested
    @DisplayName("Enum Tests")
    class EnumTests {

        @Test
        @DisplayName("Should have all event types")
        void shouldHaveAllEventTypes() {
            assertThat(EventType.values()).hasSize(10);
            assertThat(EventType.valueOf("ECONOMIC_RELEASE")).isEqualTo(EventType.ECONOMIC_RELEASE);
            assertThat(EventType.valueOf("CENTRAL_BANK")).isEqualTo(EventType.CENTRAL_BANK);
            assertThat(EventType.valueOf("FUTURES_EXPIRATION")).isEqualTo(EventType.FUTURES_EXPIRATION);
            assertThat(EventType.valueOf("MARKET_HOLIDAY")).isEqualTo(EventType.MARKET_HOLIDAY);
        }

        @Test
        @DisplayName("Should have all impact levels")
        void shouldHaveAllImpactLevels() {
            assertThat(ImpactLevel.values()).hasSize(4);
            assertThat(ImpactLevel.valueOf("HIGH")).isEqualTo(ImpactLevel.HIGH);
            assertThat(ImpactLevel.valueOf("MEDIUM")).isEqualTo(ImpactLevel.MEDIUM);
            assertThat(ImpactLevel.valueOf("LOW")).isEqualTo(ImpactLevel.LOW);
            assertThat(ImpactLevel.valueOf("HOLIDAY")).isEqualTo(ImpactLevel.HOLIDAY);
        }
    }
}
