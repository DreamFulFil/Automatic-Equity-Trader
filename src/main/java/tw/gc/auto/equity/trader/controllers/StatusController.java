package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.gc.auto.equity.trader.services.SystemStatusService;

/**
 * Status Controller
 * Provides a simple boolean status endpoint for system health monitoring.
 * 
 * Uses atomic state management via SystemStatusService for thread-safe operations.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class StatusController {

    private final SystemStatusService systemStatusService;

    /**
     * Get system operational status.
     * 
     * @return true if all systems are operational, false otherwise
     */
    @GetMapping("/status")
    public boolean getStatus() {
        boolean status = systemStatusService.isSystemHealthy();
        log.debug("Status check: {}", status);
        return status;
    }
}
