package tw.gc.auto.equity.trader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * BridgeManager - Manages the Python bridge process lifecycle.
 * Handles starting, stopping, and restarting the FastAPI bridge.
 */
@Service
@Slf4j
public class BridgeManager {

    @Value("${bridge.python-executable:python3}")
    private String pythonExecutable;

    @Value("${bridge.script-path:python/bridge.py}")
    private String scriptPath;

    @Value("${bridge.working-dir:.}")
    private String workingDir;

    private Process bridgeProcess;
    private boolean isRunning = false;

    /**
     * Start the Python bridge process
     */
    public synchronized void startBridge(String jasyptPassword) {
        if (isRunning) {
            log.info("Bridge is already running");
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(pythonExecutable, scriptPath);
            pb.directory(new File(workingDir));
            pb.environment().put("JASYPT_PASSWORD", jasyptPassword);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File("/tmp/bridge.log")));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(new File("/tmp/bridge.log")));

            log.info("Starting Python bridge: {} {}", pythonExecutable, scriptPath);
            bridgeProcess = pb.start();
            isRunning = true;

            // Wait a bit for startup
            Thread.sleep(2000);

            if (isBridgeHealthy()) {
                log.info("✅ Bridge started successfully (PID: {})", bridgeProcess.pid());
            } else {
                log.error("❌ Bridge failed to start properly");
                stopBridge();
            }

        } catch (IOException | InterruptedException e) {
            log.error("Failed to start bridge", e);
            isRunning = false;
        }
    }

    /**
     * Stop the Python bridge process
     */
    public synchronized void stopBridge() {
        if (!isRunning || bridgeProcess == null) {
            return;
        }

        try {
            log.info("Stopping Python bridge (PID: {})", bridgeProcess.pid());
            bridgeProcess.destroy();

            // Wait for graceful shutdown
            if (!bridgeProcess.waitFor(10, TimeUnit.SECONDS)) {
                log.warn("Bridge didn't shutdown gracefully, forcing...");
                bridgeProcess.destroyForcibly();
            }

            isRunning = false;
            log.info("✅ Bridge stopped");

        } catch (InterruptedException e) {
            log.error("Error stopping bridge", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Restart the Python bridge process
     */
    public synchronized void restartBridge(String jasyptPassword) {
        log.info("🔄 Restarting Python bridge...");
        stopBridge();
        // Small delay before restart
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        startBridge(jasyptPassword);
    }

    /**
     * Check if bridge is healthy by calling health endpoint
     */
    public boolean isBridgeHealthy() {
        try {
            RestTemplate rt = new RestTemplate();
            String response = rt.getForObject("http://localhost:8888/health", String.class);
            return response != null && response.contains("ok");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get bridge process PID
     */
    public long getBridgePid() {
        return bridgeProcess != null ? bridgeProcess.pid() : -1;
    }
}