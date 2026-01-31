package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.AnnualReportMetadata;
import tw.gc.auto.equity.trader.repositories.AnnualReportMetadataRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Annual Report Analysis Job
 * 
 * Phase 7: Annual Report RAG Integration
 * - Scheduled job to download reports for watchlist stocks
 * - Index reports in FAISS via Python bridge
 * - Cache summaries in database
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnnualReportAnalysisJob {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final AnnualReportMetadataRepository reportMetadataRepository;
    private final TelegramService telegramService;
    
    // Default Taiwan tickers for annual report analysis
    private static final List<String> DEFAULT_TICKERS = List.of(
        "2330", // TSMC
        "2454", // MediaTek
        "2317", // Hon Hai
        "2303", // UMC
        "3711", // ASE
        "2412", // Chunghwa Telecom
        "2882", // Cathay Financial
        "2881", // Fubon Financial
        "2886", // Mega Financial
        "2002"  // China Steel
    );
    
    /**
     * Monthly job to download and index annual reports
     * Runs on 15th of each month at 03:00 AM
     */
    @Scheduled(cron = "0 0 3 15 * *", zone = "Asia/Taipei")
    @Transactional
    public void monthlyReportDownloadAndIndex() {
        log.info("📄 Starting monthly annual report download and indexing...");
        
        int currentYear = LocalDateTime.now().getYear();
        int reportYear = currentYear - 1; // Last year's reports
        
        List<ReportProcessingResult> results = new ArrayList<>();
        
        for (String ticker : DEFAULT_TICKERS) {
            try {
                ReportProcessingResult result = downloadAndIndexReport(ticker, reportYear);
                results.add(result);
                
                // Rate limiting
                Thread.sleep(5000);
            } catch (Exception e) {
                log.error("Failed to process report for {}: {}", ticker, e.getMessage());
                results.add(ReportProcessingResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error(e.getMessage())
                    .build());
            }
        }
        
        // Send summary report
        String summary = generateSummaryReport(results);
        telegramService.sendMessage(summary);
        
        log.info("✅ Monthly report processing complete: {} success, {} failed",
            results.stream().filter(r -> r.success).count(),
            results.stream().filter(r -> !r.success).count());
    }
    
    /**
     * Download and index a single report
     */
    @Transactional
    public ReportProcessingResult downloadAndIndexReport(String ticker, int reportYear) {
        log.info("Processing annual report for {} (year {})", ticker, reportYear);
        
        try {
            // Check if report already exists
            var existing = reportMetadataRepository.findByTickerAndReportYear(ticker, reportYear);
            if (existing.isPresent() && existing.get().getIndexedAt() != null) {
                log.info("Report already indexed for {} ({})", ticker, reportYear);
                return ReportProcessingResult.builder()
                    .ticker(ticker)
                    .reportYear(reportYear)
                    .success(true)
                    .message("Already indexed")
                    .build();
            }
            
            // Step 1: Download report via Python bridge
            Map<String, Object> downloadResult = downloadReport(ticker, reportYear);
            
            if (!"success".equals(downloadResult.get("status"))) {
                return ReportProcessingResult.builder()
                    .ticker(ticker)
                    .reportYear(reportYear)
                    .success(false)
                    .error("Download failed: " + downloadResult.get("error"))
                    .build();
            }
            
            // Step 2: Index report in FAISS
            Map<String, Object> indexResult = indexReport(ticker, reportYear);
            
            if (!"success".equals(indexResult.get("status"))) {
                return ReportProcessingResult.builder()
                    .ticker(ticker)
                    .reportYear(reportYear)
                    .success(false)
                    .error("Indexing failed: " + indexResult.get("error"))
                    .build();
            }
            
            // Step 3: Generate summary
            Map<String, Object> summaryResult = generateSummary(ticker, reportYear);
            
            // Step 4: Save metadata to database
            AnnualReportMetadata metadata = AnnualReportMetadata.builder()
                .ticker(ticker)
                .reportYear(reportYear)
                .rocYear((Integer) downloadResult.get("roc_year"))
                .reportType((String) downloadResult.get("report_type"))
                .filePath((String) downloadResult.get("file_path"))
                .indexPath((String) indexResult.get("index_path"))
                .chunkCount((Integer) indexResult.get("chunk_count"))
                .summary(summaryResult != null ? (String) summaryResult.get("summary") : null)
                .downloadedAt(LocalDateTime.parse((String) downloadResult.get("timestamp")))
                .indexedAt(LocalDateTime.now())
                .summarizedAt(summaryResult != null ? LocalDateTime.now() : null)
                .build();
            
            reportMetadataRepository.save(metadata);
            
            log.info("✅ Successfully processed report for {} ({})", ticker, reportYear);
            return ReportProcessingResult.builder()
                .ticker(ticker)
                .reportYear(reportYear)
                .success(true)
                .message("Download, index, and summary complete")
                .build();
            
        } catch (Exception e) {
            log.error("Error processing report for {} ({}): {}", ticker, reportYear, e.getMessage());
            return ReportProcessingResult.builder()
                .ticker(ticker)
                .reportYear(reportYear)
                .success(false)
                .error(e.getMessage())
                .build();
        }
    }
    
    private Map<String, Object> downloadReport(String ticker, int reportYear) {
        String url = tradingProperties.getBridge().getUrl() + "/reports/shareholders/annual";
        
        Map<String, Object> request = Map.of(
            "ticker", ticker,
            "report_year", reportYear,
            "report_type", "F04",
            "force", false
        );
        
        try {
            String response = restTemplate.postForObject(url, request, String.class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.error("Failed to download report for {}: {}", ticker, e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }
    
    private Map<String, Object> indexReport(String ticker, int reportYear) {
        String url = tradingProperties.getBridge().getUrl() + "/reports/shareholders/annual/rag/index";
        
        Map<String, Object> request = Map.of(
            "ticker", ticker,
            "report_year", reportYear,
            "report_type", "F04",
            "force", false
        );
        
        try {
            String response = restTemplate.postForObject(url, request, String.class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.error("Failed to index report for {}: {}", ticker, e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }
    
    private Map<String, Object> generateSummary(String ticker, int reportYear) {
        String url = tradingProperties.getBridge().getUrl() + "/reports/shareholders/annual/summary";
        
        Map<String, Object> request = Map.of(
            "ticker", ticker,
            "report_year", reportYear,
            "report_type", "F04",
            "force", false
        );
        
        try {
            String response = restTemplate.postForObject(url, request, String.class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.warn("Failed to generate summary for {}: {}", ticker, e.getMessage());
            return null;
        }
    }
    
    private String generateSummaryReport(List<ReportProcessingResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 MONTHLY ANNUAL REPORT PROCESSING\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        long success = results.stream().filter(r -> r.success).count();
        long failed = results.stream().filter(r -> !r.success).count();
        
        sb.append(String.format("✅ Success: %d\n", success));
        sb.append(String.format("❌ Failed: %d\n\n", failed));
        
        if (failed > 0) {
            sb.append("Failed Reports:\n");
            results.stream()
                .filter(r -> !r.success)
                .forEach(r -> sb.append(String.format("  • %s (%d): %s\n", 
                    r.ticker, r.reportYear, r.error)));
        }
        
        return sb.toString();
    }
    
    /**
     * Get tickers for annual report analysis (can be configured)
     */
    public List<String> getWatchlistTickers() {
        return new ArrayList<>(DEFAULT_TICKERS);
    }
    
    @lombok.Builder
    @lombok.Data
    private static class ReportProcessingResult {
        private String ticker;
        private Integer reportYear;
        private boolean success;
        private String message;
        private String error;
    }
}
