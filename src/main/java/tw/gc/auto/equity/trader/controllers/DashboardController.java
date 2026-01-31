package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tw.gc.auto.equity.trader.services.dashboard.AlertService;
import tw.gc.auto.equity.trader.services.dashboard.DashboardDataService;

import java.util.List;
import java.util.Map;

/**
 * Dashboard Controller - Phase 9
 * 
 * REST API endpoints for real-time trading dashboard:
 * - GET /api/dashboard/overview - Comprehensive dashboard data
 * - GET /api/dashboard/positions - Current positions with P&L
 * - GET /api/dashboard/strategies - Strategy performance rankings
 * - GET /api/dashboard/trades - Recent trade activity
 * - GET /api/dashboard/equity-curve - Historical equity curve
 * - GET /api/dashboard/risk-metrics - Risk analytics
 * 
 * All endpoints return JSON data suitable for web dashboard consumption.
 */
@RestController
@RequestMapping("/api/dashboard")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Allow dashboard frontend from any origin
public class DashboardController {

    private final DashboardDataService dashboardDataService;
    private final AlertService alertService;

    /**
     * GET /api/dashboard/overview
     * Returns comprehensive dashboard overview with all key metrics
     */
    @GetMapping("/overview")
    public ResponseEntity<DashboardDataService.DashboardOverview> getDashboardOverview() {
        log.info("📊 Dashboard overview requested");
        
        try {
            DashboardDataService.DashboardOverview overview = dashboardDataService.getDashboardOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            log.error("❌ Failed to get dashboard overview: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/positions
     * Returns current positions with unrealized P&L
     */
    @GetMapping("/positions")
    public ResponseEntity<List<DashboardDataService.PositionSummary>> getCurrentPositions() {
        log.debug("📊 Current positions requested");
        
        try {
            List<DashboardDataService.PositionSummary> positions = dashboardDataService.getCurrentPositions();
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            log.error("❌ Failed to get positions: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/strategies?limit=10
     * Returns top performing strategies
     */
    @GetMapping("/strategies")
    public ResponseEntity<List<DashboardDataService.StrategyPerformance>> getTopStrategies(
            @RequestParam(defaultValue = "10") int limit) {
        log.debug("📊 Top strategies requested (limit: {})", limit);
        
        try {
            List<DashboardDataService.StrategyPerformance> strategies = 
                dashboardDataService.getTopStrategies(Math.min(limit, 50)); // Cap at 50
            return ResponseEntity.ok(strategies);
        } catch (Exception e) {
            log.error("❌ Failed to get strategies: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/trades?limit=20
     * Returns recent trade activity
     */
    @GetMapping("/trades")
    public ResponseEntity<List<DashboardDataService.TradeActivity>> getRecentTrades(
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("📊 Recent trades requested (limit: {})", limit);
        
        try {
            List<DashboardDataService.TradeActivity> trades = 
                dashboardDataService.getRecentTrades(Math.min(limit, 100)); // Cap at 100
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            log.error("❌ Failed to get trades: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/equity-curve?days=30
     * Returns equity curve data points
     */
    @GetMapping("/equity-curve")
    public ResponseEntity<List<DashboardDataService.EquityDataPoint>> getEquityCurve(
            @RequestParam(defaultValue = "30") int days) {
        log.debug("📊 Equity curve requested (days: {})", days);
        
        try {
            List<DashboardDataService.EquityDataPoint> equityCurve = 
                dashboardDataService.getEquityCurve(Math.min(days, 365)); // Cap at 1 year
            return ResponseEntity.ok(equityCurve);
        } catch (Exception e) {
            log.error("❌ Failed to get equity curve: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/risk-metrics
     * Returns comprehensive risk analytics
     */
    @GetMapping("/risk-metrics")
    public ResponseEntity<DashboardDataService.RiskMetrics> getRiskMetrics() {
        log.debug("📊 Risk metrics requested");
        
        try {
            DashboardDataService.RiskMetrics metrics = dashboardDataService.getRiskMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("❌ Failed to get risk metrics: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/health
     * Health check endpoint for dashboard
     */
    @GetMapping("/health")
    public ResponseEntity<HealthStatus> getHealthStatus() {
        return ResponseEntity.ok(HealthStatus.builder()
            .status("UP")
            .timestamp(java.time.LocalDateTime.now())
            .build());
    }

    /**
     * GET /api/dashboard/alerts
     * Get alerts with optional filters
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<AlertService.Alert>> getAlerts(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        try {
            log.info("🔔 Alerts requested: limit={}, level={}, type={}, unreadOnly={}", 
                limit, level, type, unreadOnly);
            
            List<AlertService.Alert> alerts;
            
            if (unreadOnly) {
                alerts = alertService.getUnreadAlerts();
            } else if (level != null) {
                alerts = alertService.getAlertsByLevel(AlertService.AlertLevel.valueOf(level));
            } else if (type != null) {
                alerts = alertService.getAlertsByType(AlertService.AlertType.valueOf(type));
            } else {
                alerts = alertService.getRecentAlerts(Math.min(limit, 100));
            }
            
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("❌ Error fetching alerts", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/dashboard/alerts/stats
     * Get alert statistics
     */
    @GetMapping("/alerts/stats")
    public ResponseEntity<AlertService.AlertStatistics> getAlertStatistics() {
        try {
            log.info("📊 Alert statistics requested");
            AlertService.AlertStatistics stats = alertService.getAlertStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("❌ Error fetching alert statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/dashboard/alerts/{alertId}/read
     * Mark alert as read
     */
    @PostMapping("/alerts/{alertId}/read")
    public ResponseEntity<Map<String, Boolean>> markAlertAsRead(@PathVariable String alertId) {
        try {
            log.info("📖 Marking alert as read: {}", alertId);
            boolean success = alertService.markAsRead(alertId);
            return ResponseEntity.ok(Map.of("success", success));
        } catch (Exception e) {
            log.error("❌ Error marking alert as read", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/dashboard/alerts/read-all
     * Mark all alerts as read
     */
    @PostMapping("/alerts/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAlertsAsRead() {
        try {
            log.info("📖 Marking all alerts as read");
            int count = alertService.markAllAsRead();
            return ResponseEntity.ok(Map.of("markedCount", count));
        } catch (Exception e) {
            log.error("❌ Error marking all alerts as read", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health Status DTO
     */
    @lombok.Builder
    @lombok.Value
    private static class HealthStatus {
        String status;
        java.time.LocalDateTime timestamp;
    }
}
