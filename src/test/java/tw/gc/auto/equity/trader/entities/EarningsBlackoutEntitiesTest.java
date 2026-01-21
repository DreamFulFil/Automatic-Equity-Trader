package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class EarningsBlackoutEntitiesTest {

    @Test
    void earningsBlackoutDate_equals_hashCode_coverIdAndDateBranches() {
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().blackoutDate(LocalDate.of(2026, 1, 1)).build();
        EarningsBlackoutDate b = EarningsBlackoutDate.builder().blackoutDate(LocalDate.of(2026, 1, 1)).build();
        EarningsBlackoutDate c = EarningsBlackoutDate.builder().blackoutDate(LocalDate.of(2026, 1, 2)).build();

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());

        a.setId(1L);
        b.setId(1L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setId(2L);
        assertNotEquals(a, b);

        // cover null blackoutDate branch
        EarningsBlackoutDate d = EarningsBlackoutDate.builder().blackoutDate(null).build();
        EarningsBlackoutDate e = EarningsBlackoutDate.builder().blackoutDate(null).build();
        assertEquals(d, e);
        assertEquals(d.hashCode(), e.hashCode());
    }

    @Test
    void earningsBlackoutDate_equals_sameObjectReturnsTrue() {
        // Covers line 49: return true when this == o
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().blackoutDate(LocalDate.of(2026, 1, 1)).build();
        assertEquals(a, a);
    }

    @Test
    void earningsBlackoutDate_equals_nonInstanceReturnsFalse() {
        // Covers line 52: return false when o is not EarningsBlackoutDate
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().blackoutDate(LocalDate.of(2026, 1, 1)).build();
        assertNotEquals(a, "not a blackout date");
        assertNotEquals(a, 123);
        assertNotEquals(a, null);
    }

    @Test
    void earningsBlackoutDate_equals_oneIdNullFallsBackToDate() {
        // Covers lines 54-55, 57: when one id is null, fallback to blackoutDate comparison
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().id(1L).blackoutDate(LocalDate.of(2026, 1, 1)).build();
        EarningsBlackoutDate b = EarningsBlackoutDate.builder().id(null).blackoutDate(LocalDate.of(2026, 1, 1)).build();
        
        // a.id != null, b.id == null -> uses blackoutDate comparison
        assertEquals(a, b);
        
        // Reverse: b.id == null, a.id != null
        EarningsBlackoutDate c = EarningsBlackoutDate.builder().id(null).blackoutDate(LocalDate.of(2026, 1, 2)).build();
        assertNotEquals(a, c);
    }

    @Test
    void earningsBlackoutDate_equals_nullBlackoutDateWithNonNull() {
        // Covers line 57: blackoutDate null vs non-null
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().blackoutDate(null).build();
        EarningsBlackoutDate b = EarningsBlackoutDate.builder().blackoutDate(LocalDate.of(2026, 1, 1)).build();
        assertNotEquals(a, b);
    }

    @Test
    void earningsBlackoutDate_hashCode_withIdReturnsIdHashCode() {
        // Covers line 63: id.hashCode()
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().id(42L).blackoutDate(LocalDate.of(2026, 1, 1)).build();
        assertEquals(Long.valueOf(42L).hashCode(), a.hashCode());
    }

    @Test
    void earningsBlackoutDate_hashCode_withNullIdUsesBlackoutDate() {
        // Covers line 65: blackoutDate.hashCode() when id is null
        LocalDate date = LocalDate.of(2026, 1, 1);
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().id(null).blackoutDate(date).build();
        assertEquals(date.hashCode(), a.hashCode());
    }

    @Test
    void earningsBlackoutDate_hashCode_withNullIdAndNullDateReturnsZero() {
        // Covers line 65: returns 0 when both id and blackoutDate are null
        EarningsBlackoutDate a = EarningsBlackoutDate.builder().id(null).blackoutDate(null).build();
        assertEquals(0, a.hashCode());
    }

    @Test
    void earningsBlackoutMeta_equals_hashCode_coverIdAndLastUpdatedBranches() {
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().build();
        EarningsBlackoutMeta b = EarningsBlackoutMeta.builder().build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        a.setId(1L);
        b.setId(1L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setId(2L);
        assertNotEquals(a, b);

        // cover null lastUpdated branch
        a.setId(null);
        b.setId(null);
        a.setLastUpdated(null);
        b.setLastUpdated(null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void earningsBlackoutMeta_equals_sameObjectReturnsTrue() {
        // Covers line 89: return true when this == o (but actually line 86)
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().build();
        assertEquals(a, a);
    }

    @Test
    void earningsBlackoutMeta_equals_nonInstanceReturnsFalse() {
        // Covers line 89: return false when o is not EarningsBlackoutMeta
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().build();
        assertNotEquals(a, "not a meta");
        assertNotEquals(a, 123);
        assertNotEquals(a, null);
    }

    @Test
    void earningsBlackoutMeta_equals_oneIdNullFallsBackToLastUpdated() {
        // Covers lines 91-92, 94: when one id is null, fallback to lastUpdated comparison
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().id(1L).lastUpdated(now).build();
        EarningsBlackoutMeta b = EarningsBlackoutMeta.builder().id(null).lastUpdated(now).build();
        
        // a.id != null, b.id == null -> uses lastUpdated comparison
        assertEquals(a, b);
        
        // Reverse: different lastUpdated
        EarningsBlackoutMeta c = EarningsBlackoutMeta.builder().id(null).lastUpdated(now.plusDays(1)).build();
        assertNotEquals(a, c);
    }

    @Test
    void earningsBlackoutMeta_equals_nullLastUpdatedWithNonNull() {
        // Covers line 94: lastUpdated null vs non-null
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().lastUpdated(null).build();
        EarningsBlackoutMeta b = EarningsBlackoutMeta.builder().lastUpdated(java.time.OffsetDateTime.now()).build();
        assertNotEquals(a, b);
    }

    @Test
    void earningsBlackoutMeta_hashCode_withIdReturnsIdHashCode() {
        // Covers line 100: id.hashCode()
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().id(42L).build();
        assertEquals(Long.valueOf(42L).hashCode(), a.hashCode());
    }

    @Test
    void earningsBlackoutMeta_hashCode_withNullIdUsesLastUpdated() {
        // Covers line 102: lastUpdated.hashCode() when id is null
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().id(null).lastUpdated(now).build();
        assertEquals(now.hashCode(), a.hashCode());
    }

    @Test
    void earningsBlackoutMeta_hashCode_withNullIdAndNullLastUpdatedReturnsZero() {
        // Covers line 102: returns 0 when both id and lastUpdated are null
        EarningsBlackoutMeta a = EarningsBlackoutMeta.builder().id(null).lastUpdated(null).build();
        assertEquals(0, a.hashCode());
    }
}
