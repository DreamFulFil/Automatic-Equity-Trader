package tw.gc.auto.equity.trader.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import tw.gc.auto.equity.trader.services.HistoryDataService;

import java.io.IOException;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Slf4j
public class HistoryDataController {

    private final HistoryDataService historyDataService;

    @PostMapping("/download")
    public HistoryDataService.DownloadResult downloadHistory(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "10") int years) {

        log.info("📥 REST: Downloading history for {} ({}y)", symbol, years);

        try {
            return historyDataService.downloadHistoricalData(symbol, years);
        } catch (IOException e) {
            log.error("Failed to download history for {}", symbol, e);
            throw new RuntimeException("Failed to download history: " + e.getMessage());
        }
    }
}
