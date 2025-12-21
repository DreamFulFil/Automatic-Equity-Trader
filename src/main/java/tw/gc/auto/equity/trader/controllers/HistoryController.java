package tw.gc.auto.equity.trader.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.services.HistoryDataService;

/**
 * Controller for historical data operations.
 * Separated from backtest operations for clear separation of concerns.
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryController {

    private final HistoryDataService historyDataService;

    /**
     * Download historical data for multiple stocks.
     * This is a long-running operation that downloads data from Yahoo Finance.
     * 
     * @param symbols List of stock symbols (default: top 50 Taiwan stocks)
     * @param years Number of years of history to download (default: 10)
     * @return Map of symbol to download results
     */
    @PostMapping("/download")
    public Map<String, HistoryDataService.DownloadResult> downloadHistoricalData(
            @RequestParam(required = false) List<String> symbols,
            @RequestParam(defaultValue = "10") int years) {
        
        if (symbols == null || symbols.isEmpty()) {
            symbols = getDefaultStockList();
        }
        
        log.info("📥 Starting historical data download for {} stocks, {} years each", 
            symbols.size(), years);
        
        return historyDataService.downloadHistoricalDataForMultipleStocks(symbols, years);
    }

    /**
     * Download historical data for a single stock.
     * 
     * @param symbol Stock symbol (e.g., "2330.TW")
     * @param years Number of years of history (default: 10)
     * @return Download result
     */
    @PostMapping("/download/{symbol}")
    public HistoryDataService.DownloadResult downloadSingleStock(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int years) throws Exception {
        
        log.info("📥 Downloading {} years of historical data for {}", years, symbol);
        
        return historyDataService.downloadHistoricalData(symbol, years);
    }

    /**
     * Reset truncation flag to allow fresh data download.
     * Call this before downloading if you want to clear existing data.
     */
    @PostMapping("/reset-truncation")
    public Map<String, String> resetTruncation() {
        historyDataService.resetTruncationFlag();
        log.info("🔄 Truncation flag reset - next download will clear existing data");
        return Map.of("status", "success", "message", "Truncation flag reset");
    }

    /**
     * Default stock list for Taiwan market (top 50).
     */
    private List<String> getDefaultStockList() {
        return List.of(
            // Technology leaders
            "2330.TW", "2454.TW", "2317.TW", "2382.TW", "2308.TW", 
            "2303.TW", "2357.TW", "3008.TW", "2344.TW", "2345.TW",
            "2347.TW", "2353.TW", "3711.TW", "2356.TW", "2377.TW",
            "2379.TW", "2408.TW", "3034.TW", "2301.TW", "2327.TW",
            // Financial
            "2881.TW", "2882.TW", "2886.TW", "2891.TW", "2892.TW",
            "2884.TW", "2883.TW", "2885.TW", "5880.TW", "2887.TW",
            // Materials & Manufacturing
            "1303.TW", "1301.TW", "2002.TW", "1216.TW", "2409.TW",
            "2801.TW", "2912.TW", "2207.TW", "2609.TW", "2603.TW",
            // Consumer & Retail
            "2610.TW", "2615.TW", "9910.TW", "2412.TW", "3045.TW",
            // Additional diversification
            "6505.TW", "2498.TW", "2395.TW", "3037.TW", "4938.TW"
        );
    }
}
