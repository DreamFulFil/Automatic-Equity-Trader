package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.services.EarningsBlackoutService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskManagementServiceTest {

    @Mock
    private EarningsBlackoutService earningsBlackoutService;

    private RiskManagementService riskManagementService;

    @BeforeEach
    void setUp() {
        riskManagementService = new RiskManagementService(earningsBlackoutService);
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Collections.emptySet());
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.empty());
        when(earningsBlackoutService.isLatestStale()).thenReturn(true);
    }

    // ==================== P&L tracking tests ====================

    @Test
    void getDailyPnL_shouldReturnZeroInitially() {
        assertEquals(0.0, riskManagementService.getDailyPnL());
    }

    @Test
    void getWeeklyPnL_shouldReturnZeroInitially() {
        assertEquals(0.0, riskManagementService.getWeeklyPnL());
    }

    @Test
    void recordPnL_shouldUpdateDailyAndWeekly() {
        riskManagementService.recordPnL(1000, 15000);
        
        assertEquals(1000, riskManagementService.getDailyPnL());
        assertEquals(1000, riskManagementService.getWeeklyPnL());
    }

    @Test
    void recordPnL_shouldAccumulate() {
        riskManagementService.recordPnL(500, 15000);
        riskManagementService.recordPnL(300, 15000);
        
        assertEquals(800, riskManagementService.getDailyPnL());
        assertEquals(800, riskManagementService.getWeeklyPnL());
    }

    @Test
    void recordPnL_withNegativeValue_shouldDecrease() {
        riskManagementService.recordPnL(1000, 15000);
        riskManagementService.recordPnL(-500, 15000);
        
        assertEquals(500, riskManagementService.getDailyPnL());
    }

    @Test
    void resetDailyPnL_shouldResetToZero() {
        riskManagementService.recordPnL(1000, 15000);
        riskManagementService.resetDailyPnL();
        
        assertEquals(0.0, riskManagementService.getDailyPnL());
        // Weekly should remain
        assertEquals(1000, riskManagementService.getWeeklyPnL());
    }

    // ==================== Loss limit tests ====================

    @Test
    void isDailyLimitExceeded_whenBelowLimit_shouldReturnFalse() {
        riskManagementService.recordPnL(-1000, 15000);
        
        assertFalse(riskManagementService.isDailyLimitExceeded(4500));
    }

    @Test
    void isDailyLimitExceeded_whenAtLimit_shouldReturnTrue() {
        riskManagementService.recordPnL(-4500, 15000);
        
        assertTrue(riskManagementService.isDailyLimitExceeded(4500));
    }

    @Test
    void isDailyLimitExceeded_whenAboveLimit_shouldReturnTrue() {
        riskManagementService.recordPnL(-5000, 15000);
        
        assertTrue(riskManagementService.isDailyLimitExceeded(4500));
    }

    @Test
    void isWeeklyLimitHit_shouldBeFalseInitially() {
        assertFalse(riskManagementService.isWeeklyLimitHit());
    }

    @Test
    void checkWeeklyLossLimit_whenHit_shouldSetFlag() {
        riskManagementService.recordPnL(-15000, 15000);
        
        assertTrue(riskManagementService.isWeeklyLimitHit());
    }

    @Test
    void checkWeeklyLossLimit_whenNotHit_shouldNotSetFlag() {
        riskManagementService.recordPnL(-10000, 15000);
        
        assertFalse(riskManagementService.isWeeklyLimitHit());
    }

    // ==================== Earnings blackout tests ====================

    @Test
    void isEarningsBlackout_shouldBeFalseInitially() {
        assertFalse(riskManagementService.isEarningsBlackout());
    }

    @Test
    void getEarningsBlackoutStock_shouldBeNullInitially() {
        assertNull(riskManagementService.getEarningsBlackoutStock());
    }

    @Test
    void isEarningsBlackout_whenDatePresent_shouldReturnTrue() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Set.of(today));

        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")))
            .tickersChecked(new LinkedHashSet<>(Arrays.asList("TSM", "2317.TW")))
                .source("test")
                .build();
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.isDataStale(meta)).thenReturn(false);
        when(earningsBlackoutService.isLatestStale()).thenReturn(false);

        assertTrue(riskManagementService.isEarningsBlackout());
        assertEquals("TSM, 2317.TW", riskManagementService.getEarningsBlackoutStock());
    }

    @Test
    void isEarningsBlackoutDataStale_shouldDelegateToService() {
        when(earningsBlackoutService.isLatestStale()).thenReturn(true);
        assertTrue(riskManagementService.isEarningsBlackoutDataStale());
    }

    // ==================== Weekly P&L persistence tests ====================

    @Test
    void saveWeeklyPnL_shouldNotThrow() {
        riskManagementService.recordPnL(1000, 15000);
        
        // saveWeeklyPnL is called internally by recordPnL
        assertDoesNotThrow(() -> riskManagementService.saveWeeklyPnL());
    }

    @Test
    void loadWeeklyPnL_shouldHandleMissingFile() {
        // Should not throw when file doesn't exist
        assertDoesNotThrow(() -> riskManagementService.loadWeeklyPnL());
    }

    // ==================== Edge case tests ====================

    @Test
    void recordPnL_withZero_shouldNotChange() {
        riskManagementService.recordPnL(1000, 15000);
        riskManagementService.recordPnL(0, 15000);
        
        assertEquals(1000, riskManagementService.getDailyPnL());
    }

    @Test
    void recordPnL_withLargeLoss_shouldTriggerWeeklyLimit() {
        riskManagementService.recordPnL(-20000, 15000);
        
        assertTrue(riskManagementService.isWeeklyLimitHit());
    }

    @Test
    void recordPnL_perSymbol_shouldTrackIsolatedBalances() {
        riskManagementService.recordPnL("AUTO_EQUITY_TRADER", 1000, 15000);
        riskManagementService.recordPnL("2454.TW", -500, 15000);

        assertEquals(1000, riskManagementService.getDailyPnL("AUTO_EQUITY_TRADER"));
        assertEquals(-500, riskManagementService.getDailyPnL("2454.TW"));
        assertEquals(500, riskManagementService.getDailyPnL());
    }

    @Test
    void isDailyLimitExceeded_withPositivePnL_shouldReturnFalse() {
        riskManagementService.recordPnL(5000, 15000);
        
        assertFalse(riskManagementService.isDailyLimitExceeded(4500));
    }

    @Test
    void multipleDaysSimulation_shouldAccumulateWeekly() {
        // Day 1
        riskManagementService.recordPnL(1000, 15000);
        riskManagementService.resetDailyPnL();
        
        // Day 2
        riskManagementService.recordPnL(-500, 15000);
        riskManagementService.resetDailyPnL();
        
        // Day 3
        riskManagementService.recordPnL(800, 15000);
        
        assertEquals(800, riskManagementService.getDailyPnL()); // Only day 3
        assertEquals(1300, riskManagementService.getWeeklyPnL()); // 1000 - 500 + 800
    }

    @Test
    void getWeeklyPnL_perSymbol_shouldReturnCorrectValue() {
        riskManagementService.recordPnL("2330.TW", 1500, 15000);
        riskManagementService.recordPnL("2454.TW", -800, 15000);

        assertEquals(1500, riskManagementService.getWeeklyPnL("2330.TW"));
        assertEquals(-800, riskManagementService.getWeeklyPnL("2454.TW"));
        assertEquals(0.0, riskManagementService.getWeeklyPnL("UNKNOWN"));
    }

    @Test
    void refreshEarningsBlackoutState_withException_shouldNotThrow() {
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenThrow(new RuntimeException("Service error"));

        // Should handle exception gracefully
        assertDoesNotThrow(() -> riskManagementService.isEarningsBlackout());
        assertFalse(riskManagementService.isEarningsBlackout());
    }

    @Test
    void deriveEarningsBlackoutStock_withEmptyTickers_shouldReturnNull() {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now())
                .tickersChecked(new LinkedHashSet<>())
                .source("test")
                .build();
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Collections.emptySet());

        assertNull(riskManagementService.getEarningsBlackoutStock());
    }

    @Test
    void deriveEarningsBlackoutStock_withBlankTickers_shouldFilterThem() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now())
                .tickersChecked(new LinkedHashSet<>(Arrays.asList("TSM", "", "2317.TW", null, "  ")))
                .source("test")
                .build();
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Set.of(today));

        riskManagementService.isEarningsBlackout();
        String result = riskManagementService.getEarningsBlackoutStock();
        assertEquals("TSM, 2317.TW", result);
    }

    @Test
    void deriveEarningsBlackoutStock_withMoreThanThreeTickers_shouldLimitToThree() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now())
                .tickersChecked(new LinkedHashSet<>(Arrays.asList("A", "B", "C", "D", "E")))
                .source("test")
                .build();
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Set.of(today));

        riskManagementService.isEarningsBlackout();
        String result = riskManagementService.getEarningsBlackoutStock();
        assertEquals("A, B, C", result);
    }

    @Test
    void isEarningsBlackout_whenMetaIsNull_shouldReturnFalse() {
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.empty());
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Collections.emptySet());

        assertFalse(riskManagementService.isEarningsBlackout());
        assertNull(riskManagementService.getEarningsBlackoutStock());
    }

    @Test
    void isEarningsBlackout_whenBlackoutDatesEmpty_shouldReturnFalse() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now())
                .tickersChecked(new LinkedHashSet<>(Arrays.asList("TSM")))
                .source("test")
                .build();
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        // Set blackout dates to trigger state update with stock name
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Set.of(today));

        assertTrue(riskManagementService.isEarningsBlackout()); // Should be true when date matches
        assertEquals("TSM", riskManagementService.getEarningsBlackoutStock());
        
        // Now check with different date (not blackout)
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Collections.emptySet());
        assertFalse(riskManagementService.isEarningsBlackout()); // Should be false when dates empty
        // Stock name is cleared when blackout dates are empty
        assertNull(riskManagementService.getEarningsBlackoutStock());
    }
}
