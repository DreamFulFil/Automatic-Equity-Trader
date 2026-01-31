package tw.gc.auto.equity.trader.services.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.services.TelegramService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Alert Service - Phase 9
 * 
 * Centralized alert management for:
 * - Risk events (drawdown limits, loss limits)
 * - Trade notifications (entries, exits, profit targets)
 * - System status (errors, warnings, info)
 * - Performance milestones (profit goals, new highs)
 * 
 * Provides:
 * - Alert creation and storage
 * - Alert priority levels (CRITICAL, WARNING, INFO)
 * - Alert history and filtering
 * - Integration with Telegram for critical alerts
 * - Dashboard notification center
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final int MAX_ALERTS = 1000; // Keep last 1000 alerts
    
    private final TelegramService telegramService;
    
    // In-memory alert storage (would use database in production)
    private final Queue<Alert> alertQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> alertCountByType = new ConcurrentHashMap<>();
    
    /**
     * Create and broadcast alert
     */
    public Alert createAlert(AlertType type, AlertLevel level, String title, String message) {
        Alert alert = Alert.builder()
            .id(UUID.randomUUID().toString())
            .type(type)
            .level(level)
            .title(title)
            .message(message)
            .timestamp(LocalDateTime.now(TAIPEI_ZONE))
            .read(false)
            .build();
        
        // Store alert
        alertQueue.offer(alert);
        
        // Maintain max size
        while (alertQueue.size() > MAX_ALERTS) {
            alertQueue.poll();
        }
        
        // Track alert counts
        String typeKey = type.name();
        alertCountByType.merge(typeKey, 1, Integer::sum);
        
        // Send to Telegram for critical/warning alerts
        if (level == AlertLevel.CRITICAL || level == AlertLevel.WARNING) {
            String emoji = level == AlertLevel.CRITICAL ? "🚨" : "⚠️";
            telegramService.sendMessage(String.format("%s %s\n%s", emoji, title, message));
        }
        
        log.info("🔔 Alert created: [{}] {} - {}", level, title, message);
        return alert;
    }

    /**
     * Create risk alert (drawdown, loss limits)
     */
    public Alert createRiskAlert(String title, String message, AlertLevel level) {
        return createAlert(AlertType.RISK, level, title, message);
    }

    /**
     * Create trade alert (entry, exit, profit)
     */
    public Alert createTradeAlert(String symbol, String action, double pnl) {
        String title = String.format("Trade: %s %s", action, symbol);
        String message = String.format("P&L: %.0f TWD", pnl);
        AlertLevel level = pnl > 10000 ? AlertLevel.INFO : AlertLevel.LOW;
        
        return createAlert(AlertType.TRADE, level, title, message);
    }

    /**
     * Create system alert (errors, warnings)
     */
    public Alert createSystemAlert(String title, String message, AlertLevel level) {
        return createAlert(AlertType.SYSTEM, level, title, message);
    }

    /**
     * Create performance alert (milestones, achievements)
     */
    public Alert createPerformanceAlert(String title, String message) {
        return createAlert(AlertType.PERFORMANCE, AlertLevel.INFO, title, message);
    }

    /**
     * Get all alerts
     */
    public List<Alert> getAllAlerts() {
        return new ArrayList<>(alertQueue);
    }

    /**
     * Get recent alerts
     */
    public List<Alert> getRecentAlerts(int limit) {
        return alertQueue.stream()
            .sorted(Comparator.comparing(Alert::getTimestamp).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Get unread alerts
     */
    public List<Alert> getUnreadAlerts() {
        return alertQueue.stream()
            .filter(alert -> !alert.isRead())
            .sorted(Comparator.comparing(Alert::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get alerts by level
     */
    public List<Alert> getAlertsByLevel(AlertLevel level) {
        return alertQueue.stream()
            .filter(alert -> alert.getLevel() == level)
            .sorted(Comparator.comparing(Alert::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get alerts by type
     */
    public List<Alert> getAlertsByType(AlertType type) {
        return alertQueue.stream()
            .filter(alert -> alert.getType() == type)
            .sorted(Comparator.comparing(Alert::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get alerts since timestamp
     */
    public List<Alert> getAlertsSince(LocalDateTime since) {
        return alertQueue.stream()
            .filter(alert -> alert.getTimestamp().isAfter(since))
            .sorted(Comparator.comparing(Alert::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Mark alert as read
     */
    public boolean markAsRead(String alertId) {
        return alertQueue.stream()
            .filter(alert -> alert.getId().equals(alertId))
            .findFirst()
            .map(alert -> {
                alert.setRead(true);
                return true;
            })
            .orElse(false);
    }

    /**
     * Mark all alerts as read
     */
    public int markAllAsRead() {
        int count = 0;
        for (Alert alert : alertQueue) {
            if (!alert.isRead()) {
                alert.setRead(true);
                count++;
            }
        }
        log.info("📖 Marked {} alerts as read", count);
        return count;
    }

    /**
     * Get alert statistics
     */
    public AlertStatistics getAlertStatistics() {
        long totalAlerts = alertQueue.size();
        long unreadCount = alertQueue.stream().filter(alert -> !alert.isRead()).count();
        
        Map<AlertLevel, Long> byLevel = alertQueue.stream()
            .collect(Collectors.groupingBy(Alert::getLevel, Collectors.counting()));
        
        Map<AlertType, Long> byType = alertQueue.stream()
            .collect(Collectors.groupingBy(Alert::getType, Collectors.counting()));
        
        return AlertStatistics.builder()
            .totalAlerts((int) totalAlerts)
            .unreadAlerts((int) unreadCount)
            .criticalAlerts(byLevel.getOrDefault(AlertLevel.CRITICAL, 0L).intValue())
            .warningAlerts(byLevel.getOrDefault(AlertLevel.WARNING, 0L).intValue())
            .infoAlerts(byLevel.getOrDefault(AlertLevel.INFO, 0L).intValue())
            .alertsByType(byType.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().name(),
                    e -> e.getValue().intValue()
                )))
            .build();
    }

    /**
     * Clear old alerts (older than specified days)
     */
    public int clearOldAlerts(int days) {
        LocalDateTime cutoff = LocalDateTime.now(TAIPEI_ZONE).minusDays(days);
        int initialSize = alertQueue.size();
        
        alertQueue.removeIf(alert -> alert.getTimestamp().isBefore(cutoff));
        
        int removed = initialSize - alertQueue.size();
        log.info("🧹 Cleared {} alerts older than {} days", removed, days);
        return removed;
    }

    /**
     * Alert Type Enum
     */
    public enum AlertType {
        RISK,           // Risk management events
        TRADE,          // Trade entries/exits
        SYSTEM,         // System errors/warnings
        PERFORMANCE,    // Performance milestones
        STRATEGY        // Strategy events
    }

    /**
     * Alert Level Enum
     */
    public enum AlertLevel {
        CRITICAL,   // Requires immediate attention
        WARNING,    // Important but not urgent
        INFO,       // Informational
        LOW         // Low priority
    }

    /**
     * Alert DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class Alert {
        private String id;
        private AlertType type;
        private AlertLevel level;
        private String title;
        private String message;
        private LocalDateTime timestamp;
        private boolean read;
        
        public String getLevelEmoji() {
            return switch (level) {
                case CRITICAL -> "🚨";
                case WARNING -> "⚠️";
                case INFO -> "ℹ️";
                case LOW -> "📝";
            };
        }
    }

    /**
     * Alert Statistics DTO
     */
    @lombok.Builder
    @lombok.Value
    public static class AlertStatistics {
        int totalAlerts;
        int unreadAlerts;
        int criticalAlerts;
        int warningAlerts;
        int infoAlerts;
        Map<String, Integer> alertsByType;
    }
}
