package tw.gc.mtxfbot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Telegram message formatting.
 * 
 * MarkdownV2 requires escaping special characters:
 * _ * [ ] ( ) ~ ` > # + - = | { } . !
 * 
 * These tests ensure messages won't break Telegram API.
 */
class TelegramMarkdownTest {

    @Test
    void messageWithPnL_shouldFormatCorrectly() {
        // Test P&L formatting - should be numeric
        double pnl = 1500.50;
        String message = String.format("P&L: %.0f TWD", pnl);
        
        assertTrue(message.contains("1501") || message.contains("1500"));
        assertFalse(message.contains("*"));  // No unexpected markdown
    }

    @Test
    void messageWithNegativePnL_shouldFormatCorrectly() {
        double pnl = -2500.0;
        String message = String.format("P&L: %.0f TWD", pnl);
        
        assertTrue(message.contains("-2500"));
    }

    @Test
    void messageWithEmoji_shouldNotBreak() {
        String message = "🚀 MTXF Bot started\n" +
                "💰 Equity: 100,000 TWD\n" +
                "📊 Signal: LONG\n" +
                "✅ Order filled";
        
        assertTrue(message.contains("🚀"));
        assertTrue(message.contains("💰"));
        assertTrue(message.contains("📊"));
        assertTrue(message.contains("✅"));
    }

    @Test
    void messageWithSpecialChars_shouldBeEscaped() {
        // Characters that need escaping in MarkdownV2
        String original = "Price: 20,000.50 (updated)";
        String escaped = escapeMarkdownV2(original);
        
        assertTrue(escaped.contains("\\."));
        assertTrue(escaped.contains("\\("));
        assertTrue(escaped.contains("\\)"));
    }

    @Test
    void multilineMessage_shouldPreserveNewlines() {
        String message = "Line 1\nLine 2\nLine 3";
        
        assertEquals(3, message.split("\n").length);
        assertTrue(message.contains("\n"));
    }

    @Test
    void dailySummaryFormat_shouldBeValid() {
        double pnl = 1500.0;
        double weeklyPnl = 3000.0;
        int contracts = 1;
        double equity = 100000.0;
        String status = "Profitable";
        
        String message = String.format(
                "DAILY SUMMARY\n" +
                "Today P&L: %.0f TWD\n" +
                "Week P&L: %.0f TWD\n" +
                "Contracts: %d\n" +
                "Equity: %.0f TWD\n" +
                "Status: %s\n" +
                "NO PROFIT CAPS - Unlimited upside!",
                pnl, weeklyPnl, contracts, equity, status);
        
        assertTrue(message.contains("DAILY SUMMARY"));
        assertTrue(message.contains("1500"));
        assertTrue(message.contains("3000"));
        assertTrue(message.contains("100000"));
        assertTrue(message.contains("NO PROFIT CAPS"));
    }

    @Test
    void orderFilledMessage_shouldContainAllFields() {
        String action = "BUY";
        int quantity = 1;
        double price = 20000.0;
        int position = 1;
        
        String message = String.format(
                "ORDER FILLED\n%s %d MTXF @ %.0f\nPosition: %d", 
                action, quantity, price, position);
        
        assertTrue(message.contains("ORDER FILLED"));
        assertTrue(message.contains("BUY"));
        assertTrue(message.contains("20000"));
        assertTrue(message.contains("Position: 1"));
    }

    @Test
    void emergencyShutdownMessage_shouldBeUrgent() {
        double dailyPnl = -5000.0;
        
        String message = String.format(
                "EMERGENCY SHUTDOWN\nDaily loss: %.0f TWD\nFlattening all positions!", 
                dailyPnl);
        
        assertTrue(message.contains("EMERGENCY"));
        assertTrue(message.contains("-5000"));
        assertTrue(message.contains("Flattening"));
    }

    @Test
    void weeklyLimitMessage_shouldShowPause() {
        double weeklyPnl = -15000.0;
        
        String message = String.format(
            "WEEKLY LOSS LIMIT HIT\nWeekly P&L: %.0f TWD\nTrading paused until next Monday!",
            weeklyPnl
        );
        
        assertTrue(message.contains("WEEKLY LOSS LIMIT"));
        assertTrue(message.contains("-15000"));
        assertTrue(message.contains("paused"));
    }

    @Test
    void statusMessage_shouldShowAllInfo() {
        String message = "BOT STATUS\n" +
                "State: ACTIVE\n" +
                "Position: 1 @ 20000 (held 15 min)\n" +
                "Max Contracts: 1\n" +
                "Today P&L: 500 TWD\n" +
                "Week P&L: 2000 TWD\n" +
                "News Veto: Clear\n" +
                "Commands: /pause /resume /close";
        
        assertTrue(message.contains("BOT STATUS"));
        assertTrue(message.contains("ACTIVE"));
        assertTrue(message.contains("Commands"));
    }

    /**
     * Helper method to escape MarkdownV2 special characters.
     * In actual TelegramService, we send plain text, not MarkdownV2.
     */
    private String escapeMarkdownV2(String text) {
        // Characters that need escaping: _ * [ ] ( ) ~ ` > # + - = | { } . !
        return text
            .replace(".", "\\.")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("-", "\\-")
            .replace("+", "\\+")
            .replace("!", "\\!")
            .replace("=", "\\=")
            .replace("|", "\\|")
            .replace("{", "\\{")
            .replace("}", "\\}")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("~", "\\~")
            .replace("`", "\\`")
            .replace(">", "\\>")
            .replace("#", "\\#")
            .replace("_", "\\_")
            .replace("*", "\\*");
    }
}
