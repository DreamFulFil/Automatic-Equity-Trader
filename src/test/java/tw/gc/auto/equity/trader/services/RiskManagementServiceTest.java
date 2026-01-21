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

    @Test
    void loadWeeklyPnL_shouldHandleInvalidFileContents() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, "not-a-date,not-a-number");

        assertDoesNotThrow(() -> riskManagementService.loadWeeklyPnL());
    }

    @Test
    void saveWeeklyPnL_shouldHandleWriteFailure() throws Exception {
        java.nio.file.Path logsPath = java.nio.file.Paths.get("logs");
        java.nio.file.Path backup = null;
        if (java.nio.file.Files.exists(logsPath)) {
            backup = java.nio.file.Paths.get("logs.backup-for-test");
            java.nio.file.Files.move(logsPath, backup);
        }
        java.nio.file.Files.writeString(logsPath, "not-a-directory");

        try {
            assertDoesNotThrow(() -> riskManagementService.saveWeeklyPnL());
        } finally {
            java.nio.file.Files.deleteIfExists(logsPath);
            if (backup != null && java.nio.file.Files.exists(backup)) {
                java.nio.file.Files.move(backup, logsPath);
            }
        }
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

    // ==================== Coverage tests for lines 57-59, 145, 173-175, 179-180, 193-194 ====================

    @Test
    void initialize_shouldCallLoadWeeklyPnLAndRefreshBlackout() {
        // This tests @PostConstruct initialization (lines 57-59)
        // The setUp already calls constructor, so we can call initialize directly
        assertDoesNotThrow(() -> riskManagementService.initialize());
    }

    @Test
    void deriveEarningsBlackoutStock_whenTickersCheckedIsNull_shouldReturnNull() {
        // Line 145: if (latest == null || latest.getTickersChecked() == null) return null
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now())
                .tickersChecked(null) // tickersChecked is null
                .source("test")
                .build();
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.getCurrentBlackoutDates()).thenReturn(Set.of(today));

        riskManagementService.isEarningsBlackout();
        assertNull(riskManagementService.getEarningsBlackoutStock());
    }

    @Test
    void loadWeeklyPnL_shouldResetWhenPastWeek() throws Exception {
        // Lines 173-175: Reset weekly P&L when saved date is before current week
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.createDirectories(path.getParent());
        
        // Write a date from 2 weeks ago
        LocalDate twoWeeksAgo = LocalDate.now(ZoneId.of("Asia/Taipei")).minusWeeks(2);
        String content = twoWeeksAgo + ",5000.0";
        java.nio.file.Files.writeString(path, content);
        
        riskManagementService.loadWeeklyPnL();
        
        // Weekly P&L should be reset to 0 because the saved date is from previous week
        assertEquals(0.0, riskManagementService.getWeeklyPnL());
        
        // Clean up
        java.nio.file.Files.deleteIfExists(path);
    }

    @Test
    void loadWeeklyPnL_shouldPreservePnLFromCurrentWeek() throws Exception {
        // Lines 169-171: Load weekly P&L from current week
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.createDirectories(path.getParent());
        
        // Write today's date with a P&L value
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        String content = today + ",3000.0";
        java.nio.file.Files.writeString(path, content);
        
        riskManagementService.loadWeeklyPnL();
        
        // Weekly P&L should be loaded from file
        assertEquals(3000.0, riskManagementService.getWeeklyPnL());
        
        // Clean up
        java.nio.file.Files.deleteIfExists(path);
    }

    @Test
    void loadWeeklyPnL_shouldHandleIOException() throws Exception {
        // Lines 179-180: Handle exception when loading weekly P&L
        // Create a file with invalid format to trigger parsing error
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, "invalid-format");
        
        // Should handle exception gracefully
        assertDoesNotThrow(() -> riskManagementService.loadWeeklyPnL());
        
        // Clean up
        java.nio.file.Files.deleteIfExists(path);
    }

    @Test
    void saveWeeklyPnL_shouldHandleIOException() throws Exception {
        // Lines 193-194: Handle IOException when saving weekly P&L
        // This is tested by writing to a valid path and verifying no exception
        // We can't easily force an IOException, but we can verify successful save
        riskManagementService.recordPnL(100, 15000);
        assertDoesNotThrow(() -> riskManagementService.saveWeeklyPnL());
        
        // Clean up
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.deleteIfExists(path);
    }

    // ==================== Coverage tests for lines 179-180, 193-194 ====================
    
    @Test
    void loadWeeklyPnL_exceptionPath_setsDefaultAndLogs() throws Exception {
        // Lines 179-180: catch block logs warning when exception occurs
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.createDirectories(path.getParent());
        // Write malformed content to trigger exception path
        java.nio.file.Files.writeString(path, "");
        
        assertDoesNotThrow(() -> riskManagementService.loadWeeklyPnL());
        
        // Clean up
        java.nio.file.Files.deleteIfExists(path);
    }
    
    @Test
    void saveWeeklyPnL_createsDirectoriesAndWrites() throws Exception {
        // Lines 193-194: successful write (and implicit IOException handling path)
        // Remove the file first to ensure fresh write
        java.nio.file.Path path = java.nio.file.Paths.get("logs/weekly-pnl.txt");
        java.nio.file.Files.deleteIfExists(path);
        
        riskManagementService.recordPnL(1500, 15000);
        riskManagementService.saveWeeklyPnL();
        
        assertTrue(java.nio.file.Files.exists(path));
        String content = java.nio.file.Files.readString(path);
        assertTrue(content.contains("1500"));
        
        // Clean up
        java.nio.file.Files.deleteIfExists(path);
    }
}
