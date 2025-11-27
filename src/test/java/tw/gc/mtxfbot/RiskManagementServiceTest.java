package tw.gc.mtxfbot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RiskManagementServiceTest {

    private ObjectMapper objectMapper;
    private RiskManagementService riskManagementService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        riskManagementService = new RiskManagementService(objectMapper);
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
    void loadEarningsBlackoutDates_whenFileNotExists_shouldNotThrow() {
        // Should handle missing file gracefully
        assertDoesNotThrow(() -> riskManagementService.loadEarningsBlackoutDates());
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
}
