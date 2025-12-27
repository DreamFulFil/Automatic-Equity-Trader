package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import tw.gc.auto.equity.trader.services.DataOperationsService;

import java.util.Map;

/**
 * Data Operations Controller
 * 
 * Provides REST API endpoints for:
 * - Historical data population
 * - Combinatorial backtesting
 * - Strategy auto-selection
 * - Full pipeline execution
 */
@RestController
@RequestMapping("/api/data-ops")
@RequiredArgsConstructor
@Slf4j
public class DataOperationsController {

    private final DataOperationsService dataOperationsService;

    @PostMapping("/populate")
    public Map<String, Object> populateHistoricalData(@RequestParam(defaultValue = "730") int days) {
        log.info("📊 Populating historical data for {} days", days);
        return dataOperationsService.populateHistoricalData(days);
    }

    @PostMapping("/backtest")
    public Map<String, Object> runCombinationalBacktests(
            @RequestParam(defaultValue = "80000") double capital,
            @RequestParam(defaultValue = "730") int days) {
        log.info("🧪 Running combinatorial backtests (capital={}, days={})", capital, days);
        return dataOperationsService.runCombinationalBacktests(capital, days);
    }

    @PostMapping("/select-strategy")
    public Map<String, Object> autoSelectStrategy(
            @RequestParam(defaultValue = "0.5") double minSharpe,
            @RequestParam(defaultValue = "10.0") double minReturn,
            @RequestParam(defaultValue = "50.0") double minWinRate) {
        log.info("🎯 Auto-selecting best strategy (sharpe>={}, return>={}, winRate>={})", 
                minSharpe, minReturn, minWinRate);
        return dataOperationsService.autoSelectBestStrategy(minSharpe, minReturn, minWinRate);
    }

    @PostMapping("/full-pipeline")
    public Map<String, Object> runFullPipeline(@RequestParam(defaultValue = "730") int days) {
        log.info("🚀 Running full data pipeline ({} days)", days);
        return dataOperationsService.runFullPipeline(days);
    }

    @GetMapping("/status")
    public Map<String, Object> getDataStatus() {
        log.debug("📈 Getting data operations status");
        return dataOperationsService.getDataStatus();
    }
}
