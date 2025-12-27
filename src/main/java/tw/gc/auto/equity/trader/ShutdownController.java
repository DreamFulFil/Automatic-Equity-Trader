package tw.gc.auto.equity.trader;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;

/**
 * REST endpoint for graceful application shutdown
 * 
 * The application now runs indefinitely until explicitly stopped via:
 * - POST /api/shutdown endpoint
 * - External OS signal (SIGTERM/SIGINT)
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ShutdownController {
    
    @NonNull
    private final TradingEngine tradingEngine;
    @NonNull
    private final ShioajiSettingsService shioajiSettingsService;
    @NonNull
    private final ApplicationContext applicationContext;
    
    /**
     * Gracefully shutdown the application
     * Flattens all positions, calculates statistics, and stops the Spring context
     */
    @PostMapping("/shutdown")
    public String triggerShutdown() {
        log.info("🛑 Shutdown endpoint called - initiating graceful shutdown");
        
        // Run in background thread so response can be sent
        new Thread(() -> {
            try {
                Thread.sleep(100); // Let response be sent first
                log.info("🛑 Flattening positions before shutdown...");
                tradingEngine.flattenPosition("Manual shutdown via endpoint");
                
                log.info("🛑 Closing Spring application context...");
                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
            } catch (Exception e) {
                log.error("❌ Error during graceful shutdown", e);
            }
        }).start();
        
        return "Graceful shutdown initiated - flattening positions and stopping application";
    }
    
    /**
     * Get current Shioaji settings for Python bridge
     */
    @GetMapping("/shioaji/settings")
    public ShioajiSettings getShioajiSettings() {
        return shioajiSettingsService.getSettings();
    }
}
