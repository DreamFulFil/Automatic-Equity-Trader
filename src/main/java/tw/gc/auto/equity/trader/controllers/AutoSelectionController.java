package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.gc.auto.equity.trader.services.AutoStrategySelector;

import java.util.Map;

@RestController
@RequestMapping("/api/auto-selection")
@RequiredArgsConstructor
@Slf4j
public class AutoSelectionController {

    private final AutoStrategySelector autoStrategySelector;

    @PostMapping("/run-now")
    public Map<String, String> runAutoSelection() {
        log.info("🎯 Manual trigger: Running auto-selection NOW");
        
        try {
            // Run main strategy selection
            autoStrategySelector.selectBestStrategyAndStock();
            
            // Run shadow mode selection
            autoStrategySelector.selectShadowModeStrategies();
            
            return Map.of(
                "status", "success",
                "message", "Auto-selection completed successfully"
            );
        } catch (Exception e) {
            log.error("Failed to run auto-selection", e);
            return Map.of(
                "status", "error",
                "message", e.getMessage()
            );
        }
    }
}
