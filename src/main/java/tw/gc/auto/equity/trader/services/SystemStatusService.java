package tw.gc.auto.equity.trader.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * System Status Service
 * 
 * Thread-safe status management for long-running operations.
 * Tracks whether historical data download and backtesting are in progress.
 * 
 * Operations tracked:
 * - Historical data download (downloadHistoricalDataForMultipleStocks)
 * - Backtesting (runParallelizedBacktest)
 */
@Service
@Slf4j
public class SystemStatusService {

    /**
     * Atomic flag indicating if historical data download is in progress.
     */
    private final AtomicBoolean historyDownloadRunning = new AtomicBoolean(false);

    /**
     * Atomic flag indicating if backtesting is in progress.
     */
    private final AtomicBoolean backtestRunning = new AtomicBoolean(false);

    /**
     * Timestamp when history download started (0 if not running).
     */
    private final AtomicLong historyDownloadStartTime = new AtomicLong(0);

    /**
     * Timestamp when backtest started (0 if not running).
     */
    private final AtomicLong backtestStartTime = new AtomicLong(0);

    /**
     * Check if any long-running operation is in progress.
     * 
     * @return true if history download or backtest is running
     */
    public boolean isOperationRunning() {
        return historyDownloadRunning.get() || backtestRunning.get();
    }

    /**
     * Check if historical data download is in progress.
     * 
     * @return true if download is running
     */
    public boolean isHistoryDownloadRunning() {
        return historyDownloadRunning.get();
    }

    /**
     * Check if backtesting is in progress.
     * 
     * @return true if backtest is running
     */
    public boolean isBacktestRunning() {
        return backtestRunning.get();
    }

    /**
     * Mark historical data download as started.
     * Thread-safe using atomic compareAndSet.
     * 
     * @return true if status was updated, false if already running
     */
    public boolean startHistoryDownload() {
        boolean started = historyDownloadRunning.compareAndSet(false, true);
        if (started) {
            historyDownloadStartTime.set(System.currentTimeMillis());
            log.info("📥 Historical data download started");
        } else {
            log.warn("⚠️ Historical data download already in progress");
        }
        return started;
    }

    /**
     * Mark historical data download as completed.
     * Thread-safe using atomic set.
     */
    public void completeHistoryDownload() {
        historyDownloadRunning.set(false);
        long duration = System.currentTimeMillis() - historyDownloadStartTime.get();
        historyDownloadStartTime.set(0);
        log.info("✅ Historical data download completed (took {}ms)", duration);
    }

    /**
     * Mark backtesting as started.
     * Thread-safe using atomic compareAndSet.
     * 
     * @return true if status was updated, false if already running
     */
    public boolean startBacktest() {
        boolean started = backtestRunning.compareAndSet(false, true);
        if (started) {
            backtestStartTime.set(System.currentTimeMillis());
            log.info("🧪 Backtesting started");
        } else {
            log.warn("⚠️ Backtesting already in progress");
        }
        return started;
    }

    /**
     * Mark backtesting as completed.
     * Thread-safe using atomic set.
     */
    public void completeBacktest() {
        backtestRunning.set(false);
        long duration = System.currentTimeMillis() - backtestStartTime.get();
        backtestStartTime.set(0);
        log.info("✅ Backtesting completed (took {}ms)", duration);
    }

    /**
     * Get historical data download start time.
     * 
     * @return epoch milliseconds when download started, 0 if not running
     */
    public long getHistoryDownloadStartTime() {
        return historyDownloadStartTime.get();
    }

    /**
     * Get backtest start time.
     * 
     * @return epoch milliseconds when backtest started, 0 if not running
     */
    public long getBacktestStartTime() {
        return backtestStartTime.get();
    }
}
