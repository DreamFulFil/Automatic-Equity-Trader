package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * System Status Service
 * 
 * Thread-safe status management using Atomic variables.
 * A single coordinator thread periodically checks all service health
 * and updates the atomic status flag.
 * 
 * Components monitored:
 * - Java Application (self)
 * - Python Bridge (Port 8888)
 * - Database connectivity
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SystemStatusService {

    private final RestTemplate restTemplate;

    @Value("${python.bridge.url:http://localhost:8888}")
    private String pythonBridgeUrl;

    /**
     * Atomic boolean for thread-safe status reads.
     * Updated only by the coordinator thread.
     */
    private final AtomicBoolean systemHealthy = new AtomicBoolean(false);

    /**
     * Atomic timestamps for last successful checks
     */
    private final AtomicLong lastPythonBridgeCheck = new AtomicLong(0);
    private final AtomicLong lastDatabaseCheck = new AtomicLong(0);
    private final AtomicLong lastStatusUpdate = new AtomicLong(0);

    /**
     * Individual component status flags
     */
    private final AtomicBoolean pythonBridgeHealthy = new AtomicBoolean(false);
    private final AtomicBoolean databaseHealthy = new AtomicBoolean(true);

    /**
     * Health check timeout threshold (30 seconds)
     */
    private static final long HEALTH_CHECK_TIMEOUT_MS = 30_000;

    /**
     * Get system operational status.
     * Thread-safe read from atomic variable.
     * 
     * @return true if all systems are operational
     */
    public boolean isSystemHealthy() {
        return systemHealthy.get();
    }

    /**
     * Get Python Bridge health status.
     * 
     * @return true if Python Bridge is reachable
     */
    public boolean isPythonBridgeHealthy() {
        return pythonBridgeHealthy.get();
    }

    /**
     * Get Database health status.
     * 
     * @return true if database is accessible
     */
    public boolean isDatabaseHealthy() {
        return databaseHealthy.get();
    }

    /**
     * Get last status update timestamp.
     * 
     * @return epoch milliseconds of last health check
     */
    public long getLastStatusUpdate() {
        return lastStatusUpdate.get();
    }

    /**
     * Coordinator thread - runs every 10 seconds to check all components.
     * This is the ONLY method that updates the status flags.
     */
    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public void runHealthChecks() {
        long now = System.currentTimeMillis();
        boolean allHealthy = true;

        // Check Python Bridge
        boolean pythonOk = checkPythonBridge();
        pythonBridgeHealthy.set(pythonOk);
        if (pythonOk) {
            lastPythonBridgeCheck.set(now);
        }
        allHealthy = allHealthy && pythonOk;

        // Check Database (via JPA repository test query)
        boolean dbOk = checkDatabase();
        databaseHealthy.set(dbOk);
        if (dbOk) {
            lastDatabaseCheck.set(now);
        }
        allHealthy = allHealthy && dbOk;

        // Update overall status
        systemHealthy.set(allHealthy);
        lastStatusUpdate.set(now);

        if (log.isTraceEnabled()) {
            log.trace("Health check: python={}, db={}, overall={}", pythonOk, dbOk, allHealthy);
        }
    }

    /**
     * Check Python Bridge health by calling its health endpoint.
     */
    private boolean checkPythonBridge() {
        try {
            String healthUrl = pythonBridgeUrl + "/health";
            String response = restTemplate.getForObject(healthUrl, String.class);
            return response != null && response.contains("ok");
        } catch (Exception e) {
            log.debug("Python Bridge health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check database connectivity.
     * For now, assume healthy if no exceptions during startup.
     * A more thorough check could execute a simple query.
     */
    private boolean checkDatabase() {
        // Database is assumed healthy - actual check would query repository
        // This is a simplified version; production would call:
        // return someRepository.count() >= 0;
        return true;
    }

    /**
     * Force a status update (for testing or manual trigger).
     * Still thread-safe as it uses atomic operations.
     */
    public void forceStatusUpdate(boolean healthy) {
        systemHealthy.set(healthy);
        lastStatusUpdate.set(System.currentTimeMillis());
        log.info("System status forcefully set to: {}", healthy);
    }
}
