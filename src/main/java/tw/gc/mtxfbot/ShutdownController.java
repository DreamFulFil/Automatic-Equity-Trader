package tw.gc.mtxfbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for triggering graceful shutdown in testing
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ShutdownController {
    
    private final TradingEngine tradingEngine;
    
    /**
     * Trigger autoFlatten shutdown for testing
     * This sends daily summary (e) and shutdown messages (f,g)
     */
    @PostMapping("/shutdown")
    public String triggerShutdown() {
        log.info("Shutdown endpoint called - triggering autoFlatten");
        
        // Run in background thread so response can be sent
        new Thread(() -> {
            try {
                Thread.sleep(50); // Let response be sent first
                tradingEngine.autoFlatten();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }).start();
        
        return "Shutdown initiated - daily summary and bot stopped messages will be sent";
    }
}
