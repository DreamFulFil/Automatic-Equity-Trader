package tw.gc.auto.equity.trader.controllers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.gc.auto.equity.trader.services.SystemStatusService;

/**
 * Status Controller
 * Provides status endpoint for tracking long-running operations.
 * 
 * Reports whether historical data download and/or backtesting are in progress.
 * Uses atomic state management via SystemStatusService for thread-safe operations.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class StatusController {

    private final SystemStatusService systemStatusService;

    /**
     * Get operation status.
     * 
     * @return OperationStatus with download and backtest running flags
     */
    @GetMapping("/status")
    public OperationStatus getStatus() {
        boolean downloadRunning = systemStatusService.isHistoryDownloadRunning();
        boolean backtestRunning = systemStatusService.isBacktestRunning();
        boolean anyOperationRunning = downloadRunning || backtestRunning;
        
        log.debug("Status check: download={}, backtest={}", downloadRunning, backtestRunning);
        
        return new OperationStatus(
            anyOperationRunning,
            downloadRunning,
            backtestRunning,
            systemStatusService.getHistoryDownloadStartTime(),
            systemStatusService.getBacktestStartTime()
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationStatus {
        private boolean operationRunning;
        private boolean historyDownloadRunning;
        private boolean backtestRunning;
        private long historyDownloadStartTime;
        private long backtestStartTime;
    }
}
