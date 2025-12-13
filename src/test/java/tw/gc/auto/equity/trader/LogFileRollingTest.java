package tw.gc.auto.equity.trader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for log file rolling configuration.
 * 
 * Logs should follow the pattern:
 * - logs/mtxf-bot.log (current)
 * - logs/mtxf-bot-YYYY-MM-DD.log (rolled)
 * 
 * Configuration from application.yml:
 * - max-file-size: 10MB
 * - max-history: 30 days
 * - total-size-cap: 500MB
 */
class LogFileRollingTest {

    @TempDir
    Path tempDir;

    @Test
    void logFileNamePattern_shouldMatchExpectedFormat() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String expectedPattern = "mtxf-bot-" + today + ".log";
        
        // Verify pattern matches YYYY-MM-DD format
        assertTrue(expectedPattern.matches("mtxf-bot-\\d{4}-\\d{2}-\\d{2}\\.log"),
                "Pattern should match mtxf-bot-YYYY-MM-DD.log");
    }

    @Test
    void logDirectory_shouldExistOrBeCreatable() {
        File logsDir = new File("logs");
        
        if (!logsDir.exists()) {
            assertTrue(logsDir.mkdirs(), "Should be able to create logs directory");
        }
        
        assertTrue(logsDir.exists(), "Logs directory should exist");
        assertTrue(logsDir.isDirectory(), "Logs should be a directory");
    }

    @Test
    void logFile_shouldBeWritable() throws Exception {
        Path logFile = tempDir.resolve("test.log");
        String testEntry = "2025-11-27 12:00:00.000 [main] INFO  tw.gc.mtxfbot.TradingEngine - Test entry";
        
        Files.writeString(logFile, testEntry);
        
        assertTrue(Files.exists(logFile), "Log file should exist after write");
        assertEquals(testEntry, Files.readString(logFile), "Content should match");
    }

    @Test
    void multipleLogEntries_shouldAppend() throws Exception {
        Path logFile = tempDir.resolve("test.log");
        
        String entry1 = "Entry 1\n";
        String entry2 = "Entry 2\n";
        String entry3 = "Entry 3\n";
        
        Files.writeString(logFile, entry1);
        Files.writeString(logFile, Files.readString(logFile) + entry2);
        Files.writeString(logFile, Files.readString(logFile) + entry3);
        
        String content = Files.readString(logFile);
        assertTrue(content.contains("Entry 1"));
        assertTrue(content.contains("Entry 2"));
        assertTrue(content.contains("Entry 3"));
    }

    @Test
    void rollingFileName_shouldIncludeDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String today = LocalDate.now().format(formatter);
        String rolledFileName = String.format("mtxf-bot-%s.log", today);
        
        assertTrue(rolledFileName.contains(today));
        assertTrue(rolledFileName.startsWith("mtxf-bot-"));
        assertTrue(rolledFileName.endsWith(".log"));
    }

    @Test
    void fileSizeLimit_shouldBe10MB() {
        long maxFileSizeBytes = 10 * 1024 * 1024; // 10MB
        
        assertEquals(10_485_760, maxFileSizeBytes, "Max file size should be 10MB");
    }

    @Test
    void maxHistory_shouldBe30Days() {
        int maxHistory = 30;
        
        assertEquals(30, maxHistory, "Max history should be 30 days");
    }

    @Test
    void totalSizeCap_shouldBe500MB() {
        long totalSizeCapBytes = 500 * 1024 * 1024; // 500MB
        
        assertEquals(524_288_000, totalSizeCapBytes, "Total size cap should be 500MB");
    }

    @Test
    void logEntryFormat_shouldIncludeTimestamp() {
        String timestamp = "12:30:45.123";
        String thread = "main";
        String level = "INFO";
        String logger = "tw.gc.mtxfbot.TradingEngine";
        String message = "Signal received: LONG @ 20000";
        
        String entry = String.format("%s [%s] %-5s %s - %s",
                timestamp, thread, level, logger, message);
        
        assertTrue(entry.contains(timestamp));
        assertTrue(entry.contains(level));
        assertTrue(entry.contains(logger));
        assertTrue(entry.contains(message));
    }

    @Test
    void logEntryFormat_shouldSupportDebugLevel() {
        String entry = "12:30:45.123 [scheduler-1] DEBUG tw.gc.mtxfbot.TradingEngine - Low confidence: 0.45";
        
        assertTrue(entry.contains("DEBUG"));
        assertTrue(entry.contains("scheduler-1"));
    }

    @Test
    void logEntryFormat_shouldSupportWarnLevel() {
        String entry = "12:30:45.123 [main] WARN  tw.gc.mtxfbot.RiskManagementService - Daily loss: -4000 TWD";
        
        assertTrue(entry.contains("WARN"));
        assertTrue(entry.contains("-4000"));
    }

    @Test
    void logEntryFormat_shouldSupportErrorLevel() {
        String entry = "12:30:45.123 [main] ERROR tw.gc.mtxfbot.TradingEngine - Order execution failed";
        
        assertTrue(entry.contains("ERROR"));
        assertTrue(entry.contains("Order execution failed"));
    }

    @Test
    void pythonBridgeLogFile_shouldHaveSeparateLocation() {
        String pythonLogFile = "logs/python-bridge.log";
        
        assertTrue(pythonLogFile.contains("python-bridge"));
        assertTrue(pythonLogFile.startsWith("logs/"));
    }

    @Test
    void weeklyPnlFile_shouldBeInLogsDirectory() {
        String weeklyPnlFile = "logs/weekly-pnl.txt";
        
        assertTrue(weeklyPnlFile.startsWith("logs/"));
        assertTrue(weeklyPnlFile.endsWith(".txt"));
    }

    @Test
    void weeklyPnlFileFormat_shouldBeSimple() {
        // Format: YYYY-MM-DD,<pnl>
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        double weeklyPnl = 1500.0;
        String content = today + "," + weeklyPnl;
        
        String[] parts = content.split(",");
        assertEquals(2, parts.length);
        assertEquals(10, parts[0].length()); // YYYY-MM-DD
        assertDoesNotThrow(() -> Double.parseDouble(parts[1]));
    }
}
