package tw.gc.auto.equity.trader.services.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.TelegramService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlertService - Phase 9
 * 
 * Tests cover:
 * - Alert creation and storage
 * - Alert filtering (by type, level, time)
 * - Alert statistics calculation
 * - Telegram integration for critical alerts
 * - Read/unread management
 * - Alert history cleanup
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private TelegramService telegramService;

    @InjectMocks
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        // Reset alert service state by creating new instance
        alertService = new AlertService(telegramService);
    }

    @Test
    void testCreateAlert_StoresAlertSuccessfully() {
        // When
        AlertService.Alert alert = alertService.createAlert(
            AlertService.AlertType.TRADE,
            AlertService.AlertLevel.INFO,
            "Trade Executed",
            "Bought 100 shares of 2330"
        );

        // Then
        assertThat(alert).isNotNull();
        assertThat(alert.getId()).isNotNull();
        assertThat(alert.getType()).isEqualTo(AlertService.AlertType.TRADE);
        assertThat(alert.getLevel()).isEqualTo(AlertService.AlertLevel.INFO);
        assertThat(alert.getTitle()).isEqualTo("Trade Executed");
        assertThat(alert.getMessage()).isEqualTo("Bought 100 shares of 2330");
        assertThat(alert.isRead()).isFalse();
        assertThat(alert.getTimestamp()).isNotNull();
    }

    @Test
    void testCreateAlert_CriticalAlert_SendsTelegram() {
        // When
        alertService.createAlert(
            AlertService.AlertType.RISK,
            AlertService.AlertLevel.CRITICAL,
            "Max Drawdown Reached",
            "Portfolio drawdown: 15%"
        );

        // Then
        verify(telegramService, times(1)).sendMessage(anyString());
    }

    @Test
    void testCreateAlert_WarningAlert_SendsTelegram() {
        // When
        alertService.createAlert(
            AlertService.AlertType.SYSTEM,
            AlertService.AlertLevel.WARNING,
            "Connection Issue",
            "API reconnecting..."
        );

        // Then
        verify(telegramService, times(1)).sendMessage(anyString());
    }

    @Test
    void testCreateAlert_InfoAlert_DoesNotSendTelegram() {
        // When
        alertService.createAlert(
            AlertService.AlertType.TRADE,
            AlertService.AlertLevel.INFO,
            "Trade Complete",
            "Order filled"
        );

        // Then
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void testCreateRiskAlert() {
        // When
        AlertService.Alert alert = alertService.createRiskAlert(
            "Drawdown Warning",
            "Portfolio down 10%",
            AlertService.AlertLevel.WARNING
        );

        // Then
        assertThat(alert.getType()).isEqualTo(AlertService.AlertType.RISK);
        assertThat(alert.getLevel()).isEqualTo(AlertService.AlertLevel.WARNING);
        assertThat(alert.getTitle()).isEqualTo("Drawdown Warning");
    }

    @Test
    void testCreateTradeAlert_LargeProfit() {
        // When
        AlertService.Alert alert = alertService.createTradeAlert("2330", "SELL", 15000);

        // Then
        assertThat(alert.getType()).isEqualTo(AlertService.AlertType.TRADE);
        assertThat(alert.getLevel()).isEqualTo(AlertService.AlertLevel.INFO);
        assertThat(alert.getTitle()).contains("SELL").contains("2330");
        assertThat(alert.getMessage()).contains("15000");
    }

    @Test
    void testCreateTradeAlert_SmallProfit() {
        // When
        AlertService.Alert alert = alertService.createTradeAlert("2330", "BUY", 5000);

        // Then
        assertThat(alert.getLevel()).isEqualTo(AlertService.AlertLevel.LOW);
    }

    @Test
    void testCreateSystemAlert() {
        // When
        AlertService.Alert alert = alertService.createSystemAlert(
            "Database Error",
            "Connection timeout",
            AlertService.AlertLevel.CRITICAL
        );

        // Then
        assertThat(alert.getType()).isEqualTo(AlertService.AlertType.SYSTEM);
        assertThat(alert.getLevel()).isEqualTo(AlertService.AlertLevel.CRITICAL);
    }

    @Test
    void testCreatePerformanceAlert() {
        // When
        AlertService.Alert alert = alertService.createPerformanceAlert(
            "New Equity High",
            "Portfolio reached 1M TWD"
        );

        // Then
        assertThat(alert.getType()).isEqualTo(AlertService.AlertType.PERFORMANCE);
        assertThat(alert.getLevel()).isEqualTo(AlertService.AlertLevel.INFO);
    }

    @Test
    void testGetRecentAlerts_ReturnsLimitedResults() {
        // Given - Create 10 alerts
        for (int i = 0; i < 10; i++) {
            alertService.createAlert(
                AlertService.AlertType.TRADE,
                AlertService.AlertLevel.INFO,
                "Alert " + i,
                "Message " + i
            );
        }

        // When
        List<AlertService.Alert> alerts = alertService.getRecentAlerts(5);

        // Then
        assertThat(alerts).hasSize(5);
    }

    @Test
    void testGetUnreadAlerts_OnlyReturnsUnread() {
        // Given - Create 5 alerts and mark 2 as read
        AlertService.Alert alert1 = alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Alert 1", "Msg 1"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Alert 2", "Msg 2"
        );
        AlertService.Alert alert3 = alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Alert 3", "Msg 3"
        );

        alertService.markAsRead(alert1.getId());
        alertService.markAsRead(alert3.getId());

        // When
        List<AlertService.Alert> unread = alertService.getUnreadAlerts();

        // Then
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0).getTitle()).isEqualTo("Alert 2");
    }

    @Test
    void testGetAlertsByLevel_FiltersCorrectly() {
        // Given - Create alerts with different levels
        alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.CRITICAL, "Critical 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Info 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.SYSTEM, AlertService.AlertLevel.CRITICAL, "Critical 2", "Msg"
        );

        // When
        List<AlertService.Alert> criticalAlerts = 
            alertService.getAlertsByLevel(AlertService.AlertLevel.CRITICAL);

        // Then
        assertThat(criticalAlerts).hasSize(2);
        assertThat(criticalAlerts).allMatch(a -> a.getLevel() == AlertService.AlertLevel.CRITICAL);
    }

    @Test
    void testGetAlertsByType_FiltersCorrectly() {
        // Given - Create alerts with different types
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Trade 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.WARNING, "Risk 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Trade 2", "Msg"
        );

        // When
        List<AlertService.Alert> tradeAlerts = 
            alertService.getAlertsByType(AlertService.AlertType.TRADE);

        // Then
        assertThat(tradeAlerts).hasSize(2);
        assertThat(tradeAlerts).allMatch(a -> a.getType() == AlertService.AlertType.TRADE);
    }

    @Test
    void testGetAlertsSince_FiltersCorrectly() throws InterruptedException {
        // Given - Create alert, wait, record time, create more alerts
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Old Alert", "Msg"
        );
        
        Thread.sleep(100);
        LocalDateTime cutoff = LocalDateTime.now();
        Thread.sleep(100);
        
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "New Alert 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "New Alert 2", "Msg"
        );

        // When
        List<AlertService.Alert> recentAlerts = alertService.getAlertsSince(cutoff);

        // Then
        assertThat(recentAlerts).hasSize(2);
        assertThat(recentAlerts).allMatch(a -> a.getTitle().startsWith("New"));
    }

    @Test
    void testMarkAsRead_UpdatesAlert() {
        // Given
        AlertService.Alert alert = alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Test", "Msg"
        );
        assertThat(alert.isRead()).isFalse();

        // When
        boolean success = alertService.markAsRead(alert.getId());

        // Then
        assertThat(success).isTrue();
        List<AlertService.Alert> allAlerts = alertService.getAllAlerts();
        assertThat(allAlerts.get(0).isRead()).isTrue();
    }

    @Test
    void testMarkAsRead_NonExistentAlert_ReturnsFalse() {
        // When
        boolean success = alertService.markAsRead("non-existent-id");

        // Then
        assertThat(success).isFalse();
    }

    @Test
    void testMarkAllAsRead_UpdatesAllUnread() {
        // Given - Create 5 alerts
        for (int i = 0; i < 5; i++) {
            alertService.createAlert(
                AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Alert " + i, "Msg"
            );
        }

        // When
        int count = alertService.markAllAsRead();

        // Then
        assertThat(count).isEqualTo(5);
        List<AlertService.Alert> unread = alertService.getUnreadAlerts();
        assertThat(unread).isEmpty();
    }

    @Test
    void testGetAlertStatistics_CalculatesCorrectly() {
        // Given - Create alerts with various types and levels
        alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.CRITICAL, "Critical 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.WARNING, "Warning 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Info 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.SYSTEM, AlertService.AlertLevel.INFO, "Info 2", "Msg"
        );

        // Mark 1 as read
        List<AlertService.Alert> allAlerts = alertService.getAllAlerts();
        alertService.markAsRead(allAlerts.get(0).getId());

        // When
        AlertService.AlertStatistics stats = alertService.getAlertStatistics();

        // Then
        assertThat(stats.getTotalAlerts()).isEqualTo(4);
        assertThat(stats.getUnreadAlerts()).isEqualTo(3);
        assertThat(stats.getCriticalAlerts()).isEqualTo(1);
        assertThat(stats.getWarningAlerts()).isEqualTo(1);
        assertThat(stats.getInfoAlerts()).isEqualTo(2);
        assertThat(stats.getAlertsByType()).containsEntry("TRADE", 2);
        assertThat(stats.getAlertsByType()).containsEntry("RISK", 1);
        assertThat(stats.getAlertsByType()).containsEntry("SYSTEM", 1);
    }

    @Test
    void testClearOldAlerts_RemovesOldAlerts() throws InterruptedException {
        // Given - Create old alerts
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Old 1", "Msg"
        );
        alertService.createAlert(
            AlertService.AlertType.TRADE, AlertService.AlertLevel.INFO, "Old 2", "Msg"
        );

        // When - Clear alerts older than 0 days (all should be cleared)
        int removed = alertService.clearOldAlerts(0);

        // Then
        assertThat(removed).isEqualTo(2);
        assertThat(alertService.getAllAlerts()).isEmpty();
    }

    @Test
    void testAlertMaxSize_EnforcesLimit() {
        // Given - Max is 1000, create 1100 alerts
        for (int i = 0; i < 1100; i++) {
            alertService.createAlert(
                AlertService.AlertType.TRADE,
                AlertService.AlertLevel.INFO,
                "Alert " + i,
                "Message"
            );
        }

        // When
        List<AlertService.Alert> allAlerts = alertService.getAllAlerts();

        // Then - Should have max 1000 alerts
        assertThat(allAlerts).hasSizeLessThanOrEqualTo(1000);
    }

    @Test
    void testGetLevelEmoji_ReturnsCorrectEmoji() {
        // Given
        AlertService.Alert critical = alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.CRITICAL, "Test", "Msg"
        );
        AlertService.Alert warning = alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.WARNING, "Test", "Msg"
        );
        AlertService.Alert info = alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.INFO, "Test", "Msg"
        );
        AlertService.Alert low = alertService.createAlert(
            AlertService.AlertType.RISK, AlertService.AlertLevel.LOW, "Test", "Msg"
        );

        // Then
        assertThat(critical.getLevelEmoji()).isEqualTo("🚨");
        assertThat(warning.getLevelEmoji()).isEqualTo("⚠️");
        assertThat(info.getLevelEmoji()).isEqualTo("ℹ️");
        assertThat(low.getLevelEmoji()).isEqualTo("📝");
    }
}
