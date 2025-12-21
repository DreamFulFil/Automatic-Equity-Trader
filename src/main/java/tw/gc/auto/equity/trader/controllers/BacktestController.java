package tw.gc.auto.equity.trader.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.services.BacktestService;
import tw.gc.auto.equity.trader.strategy.IStrategy;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    /**
     * Run parallelized backtest across top 50 stocks with all strategies.
     * 
     * IMPORTANT: This endpoint does NOT download historical data.
     * Data must be downloaded separately via /api/history/download.
     * 
     * Flow:
     * 1. Fetch top 50 Taiwan stocks dynamically from web sources
     * 2. Check which stocks have historical data available
     * 3. Run backtests in parallel across all stocks with data
     * 4. Store results in database
     * 
     * @param initialCapital Starting capital for backtest (default: 80000 TWD)
     * @return Map of stock symbol to strategy results
     */
    @PostMapping("/run")
    public Map<String, Map<String, BacktestService.InMemoryBacktestResult>> runParallelizedBacktest(
            @RequestParam(defaultValue = "80000") double initialCapital) {
        
        log.info("🚀 Running parallelized backtest with initial capital: {} TWD", initialCapital);
        
        List<IStrategy> strategies = backtestService.getAllStrategies();
        
        log.info("📊 Testing {} strategies across stocks with available data", strategies.size());
        
        return backtestService.runParallelizedBacktest(strategies, initialCapital);
    }
}
