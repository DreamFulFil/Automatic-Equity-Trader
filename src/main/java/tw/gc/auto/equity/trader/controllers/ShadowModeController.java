package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;
import tw.gc.auto.equity.trader.services.ShadowModeStockService;
import tw.gc.auto.equity.trader.services.StrategyManager;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing shadow mode stock configurations
 */
@RestController
@RequestMapping("/api/shadow-mode")
@RequiredArgsConstructor
@Slf4j
public class ShadowModeController {

    private final ShadowModeStockService shadowModeStockService;
    private final StrategyManager strategyManager;

    @GetMapping("/stocks")
    public ResponseEntity<List<ShadowModeStock>> getEnabledStocks() {
        return ResponseEntity.ok(shadowModeStockService.getEnabledStocks());
    }

    @PostMapping("/configure")
    public ResponseEntity<Map<String, String>> configureStocks(@RequestBody List<ShadowModeStockService.ShadowModeStockConfig> configs) {
        try {
            shadowModeStockService.configureStocks(configs);
            
            // Reinitialize strategy manager to pick up new shadow stocks
            // Note: This requires the factory, so we'll need to trigger a full reinitialization
            log.info("✅ Shadow mode stocks configured. Restart recommended to apply changes.");
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Shadow mode stocks configured. Restart the application to apply changes.",
                "stocks_configured", String.valueOf(configs.size())
            ));
        } catch (Exception e) {
            log.error("Failed to configure shadow mode stocks", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<ShadowModeStock> addStock(
            @RequestParam String symbol,
            @RequestParam String stockName,
            @RequestParam String strategyName,
            @RequestParam(required = false) Double expectedReturnPercentage,
            @RequestParam(required = false) Integer rankPosition) {
        
        ShadowModeStock stock = shadowModeStockService.addOrUpdateStock(
            symbol, stockName, strategyName, expectedReturnPercentage, rankPosition
        );
        
        return ResponseEntity.ok(stock);
    }

    @DeleteMapping("/remove")
    public ResponseEntity<Map<String, String>> removeStock(@RequestParam String symbol) {
        try {
            shadowModeStockService.removeStock(symbol);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Stock removed from shadow mode"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
}
