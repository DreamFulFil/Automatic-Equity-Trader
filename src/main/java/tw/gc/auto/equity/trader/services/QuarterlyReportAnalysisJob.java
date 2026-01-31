package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.QuarterlyReportMetadata;
import tw.gc.auto.equity.trader.repositories.QuarterlyReportMetadataRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quarterly Report Analysis Job
 *
 * Phase 1: Quarterly report integration
 * - Scheduled job to download quarterly reports for watchlist stocks
 * - Cache summaries in database
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QuarterlyReportAnalysisJob {

    private static final List<String> DEFAULT_TICKERS = List.of(
        "2330",
        "2454",
        "2317",
        "2303",
        "3711",
        "2412",
        "2882",
        "2881",
        "2886",
        "2002"
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final QuarterlyReportMetadataRepository quarterlyReportMetadataRepository;
    private final TelegramService telegramService;

    /**
     * Quarterly job to download and summarize reports
     * Runs on the 15th of Jan/Apr/Jul/Oct at 04:00 AM (Taipei)
     */
    @Scheduled(cron = "0 0 4 15 1,4,7,10 *", zone = "Asia/Taipei")
    @Transactional
    public void quarterlyReportDownloadAndSummarize() {
        log.info("📄 Starting quarterly report download and summarization...");

        QuarterInfo quarterInfo = resolvePreviousQuarter(LocalDate.now());
        List<ReportProcessingResult> results = new ArrayList<>();

        for (String ticker : DEFAULT_TICKERS) {
            try {
                ReportProcessingResult result = downloadAndSummarizeReport(
                    ticker,
                    quarterInfo.reportYear(),
                    quarterInfo.reportQuarter()
                );
                results.add(result);
                Thread.sleep(3000);
            } catch (Exception e) {
                log.error("Failed to process quarterly report for {}: {}", ticker, e.getMessage());
                results.add(ReportProcessingResult.builder()
                    .ticker(ticker)
                    .reportYear(quarterInfo.reportYear())
                    .reportQuarter(quarterInfo.reportQuarter())
                    .success(false)
                    .error(e.getMessage())
                    .build());
            }
        }

        String summary = generateSummaryReport(results, quarterInfo);
        telegramService.sendMessage(summary);

        log.info("✅ Quarterly report processing complete: {} success, {} failed",
            results.stream().filter(r -> r.success).count(),
            results.stream().filter(r -> !r.success).count());
    }

    @Transactional
    public ReportProcessingResult downloadAndSummarizeReport(String ticker, int reportYear, int reportQuarter) {
        log.info("Processing quarterly report for {} (Q{} {})", ticker, reportQuarter, reportYear);

        try {
            var existing = quarterlyReportMetadataRepository.findByTickerAndReportYearAndReportQuarter(
                ticker,
                reportYear,
                reportQuarter
            );
            if (existing.isPresent() && existing.get().getSummarizedAt() != null) {
                return ReportProcessingResult.builder()
                    .ticker(ticker)
                    .reportYear(reportYear)
                    .reportQuarter(reportQuarter)
                    .success(true)
                    .message("Already summarized")
                    .build();
            }

            Map<String, Object> downloadResult = downloadReport(ticker, reportYear, reportQuarter);
            if (!"success".equals(downloadResult.get("status"))) {
                return ReportProcessingResult.builder()
                    .ticker(ticker)
                    .reportYear(reportYear)
                    .reportQuarter(reportQuarter)
                    .success(false)
                    .error("Download failed: " + downloadResult.get("error"))
                    .build();
            }

            Map<String, Object> summaryResult = generateSummary(ticker, reportYear, reportQuarter);

            QuarterlyReportMetadata metadata = QuarterlyReportMetadata.builder()
                .ticker(ticker)
                .reportYear(reportYear)
                .reportQuarter(reportQuarter)
                .rocYear((Integer) downloadResult.get("roc_year"))
                .reportType((String) downloadResult.get("report_type"))
                .filePath((String) downloadResult.get("file_path"))
                .summary(summaryResult != null ? (String) summaryResult.get("summary") : null)
                .downloadedAt(LocalDateTime.parse((String) downloadResult.get("timestamp")))
                .summarizedAt(summaryResult != null ? LocalDateTime.now() : null)
                .build();

            quarterlyReportMetadataRepository.save(metadata);

            return ReportProcessingResult.builder()
                .ticker(ticker)
                .reportYear(reportYear)
                .reportQuarter(reportQuarter)
                .success(true)
                .message("Download and summary complete")
                .build();
        } catch (Exception e) {
            log.error("Error processing quarterly report for {}: {}", ticker, e.getMessage());
            return ReportProcessingResult.builder()
                .ticker(ticker)
                .reportYear(reportYear)
                .reportQuarter(reportQuarter)
                .success(false)
                .error(e.getMessage())
                .build();
        }
    }

    private Map<String, Object> downloadReport(String ticker, int reportYear, int reportQuarter) {
        String url = tradingProperties.getBridge().getUrl() + "/reports/financial/quarterly";

        Map<String, Object> request = Map.of(
            "ticker", ticker,
            "report_year", reportYear,
            "report_quarter", reportQuarter,
            "report_type", "F01",
            "force", false
        );

        try {
            String response = restTemplate.postForObject(url, request, String.class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.error("Failed to download quarterly report for {}: {}", ticker, e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }

    private Map<String, Object> generateSummary(String ticker, int reportYear, int reportQuarter) {
        String url = tradingProperties.getBridge().getUrl() + "/reports/financial/quarterly/summary";

        Map<String, Object> request = Map.of(
            "ticker", ticker,
            "report_year", reportYear,
            "report_quarter", reportQuarter,
            "report_type", "F01",
            "force", false
        );

        try {
            String response = restTemplate.postForObject(url, request, String.class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.warn("Failed to generate quarterly summary for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    private QuarterInfo resolvePreviousQuarter(LocalDate date) {
        int quarter = ((date.getMonthValue() - 1) / 3) + 1;
        int reportQuarter = quarter - 1;
        int reportYear = date.getYear();
        if (reportQuarter == 0) {
            reportQuarter = 4;
            reportYear -= 1;
        }
        return new QuarterInfo(reportYear, reportQuarter);
    }

    private String generateSummaryReport(List<ReportProcessingResult> results, QuarterInfo quarterInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 QUARTERLY REPORT PROCESSING\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append(String.format("Period: %d Q%d\n\n", quarterInfo.reportYear(), quarterInfo.reportQuarter()));

        long success = results.stream().filter(r -> r.success).count();
        long failed = results.stream().filter(r -> !r.success).count();

        sb.append(String.format("✅ Success: %d\n", success));
        sb.append(String.format("❌ Failed: %d\n\n", failed));

        if (failed > 0) {
            sb.append("Failed Reports:\n");
            results.stream()
                .filter(r -> !r.success)
                .forEach(r -> sb.append(String.format("  • %s (Q%d %d): %s\n",
                    r.ticker, r.reportQuarter, r.reportYear, r.error)));
        }

        return sb.toString();
    }

    private record QuarterInfo(int reportYear, int reportQuarter) {
    }

    @lombok.Builder
    @lombok.Data
    private static class ReportProcessingResult {
        private String ticker;
        private Integer reportYear;
        private Integer reportQuarter;
        private boolean success;
        private String message;
        private String error;
    }
}
