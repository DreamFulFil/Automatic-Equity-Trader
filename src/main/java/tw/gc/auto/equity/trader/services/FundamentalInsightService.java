package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.AnnualReportMetadata;
import tw.gc.auto.equity.trader.entities.QuarterlyReportMetadata;
import tw.gc.auto.equity.trader.repositories.AnnualReportMetadataRepository;
import tw.gc.auto.equity.trader.repositories.QuarterlyReportMetadataRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fundamental Insight Service
 * 
 * Phase 7: Annual Report RAG Integration
 * - Query RAG for specific questions about annual reports
 * - Score stocks based on report sentiment
 * - Provide fundamental insights for trading decisions
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FundamentalInsightService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final AnnualReportMetadataRepository reportMetadataRepository;
    private final QuarterlyReportMetadataRepository quarterlyReportMetadataRepository;

    private static final int QUARTERLY_FRESHNESS_DAYS = 120;
    
    /**
     * Query annual report for specific insights
     */
    public Map<String, Object> queryReport(String ticker, String question) {
        return queryReport(ticker, question, null);
    }
    
    /**
     * Query annual report for specific insights with year
     */
    public Map<String, Object> queryReport(String ticker, String question, Integer reportYear) {
        String url = tradingProperties.getBridge().getUrl() + "/reports/shareholders/annual/rag/query";
        
        Map<String, Object> request = new HashMap<>();
        request.put("ticker", ticker);
        request.put("question", question);
        if (reportYear != null) {
            request.put("report_year", reportYear);
        }
        request.put("report_type", "F04");
        request.put("top_k", 4);
        request.put("force", false);
        
        try {
            String response = restTemplate.postForObject(url, request, String.class);
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.error("Failed to query report for {}: {}", ticker, e.getMessage());
            return Map.of("status", "error", "error", e.getMessage());
        }
    }
    
    /**
     * Get comprehensive fundamental insights for a stock
     */
    public FundamentalInsight getFundamentalInsights(String ticker) {
        FundamentalInsight insight = FundamentalInsight.builder()
            .ticker(ticker)
            .build();
        
        // Get latest report metadata
        Optional<AnnualReportMetadata> latestReport = 
            reportMetadataRepository.findFirstByTickerOrderByReportYearDesc(ticker);
        
        if (latestReport.isEmpty()) {
            log.debug("No annual report found for {}", ticker);
            insight.setAvailable(false);
            insight.setMessage("No annual report available");
            return insight;
        }
        
        AnnualReportMetadata report = latestReport.get();
        insight.setAvailable(true);
        insight.setReportYear(report.getReportYear());
        insight.setSummary(report.getSummary());
        insight.setFundamentalScore(report.getFundamentalScore());
        insight.setPeRatio(report.getPeRatio());
        insight.setRevenueGrowthPct(report.getRevenueGrowthPct());
        insight.setDebtToEquity(report.getDebtToEquity());

        Optional<QuarterlyReportMetadata> latestQuarterly =
            quarterlyReportMetadataRepository.findFirstByTickerOrderByReportYearDescReportQuarterDesc(ticker);
        latestQuarterly.ifPresent(quarterly -> {
            insight.setQuarterlyReportYear(quarterly.getReportYear());
            insight.setQuarterlyReportQuarter(quarterly.getReportQuarter());
            insight.setQuarterlySummary(quarterly.getSummary());
        });
        
        // Query for key insights
        Map<String, Object> risksResult = queryReport(ticker, "What are the main business risks mentioned?");
        if ("success".equals(risksResult.get("status"))) {
            insight.setMainRisks((String) risksResult.get("answer"));
        }
        
        Map<String, Object> revenueResult = queryReport(ticker, "What is the revenue growth outlook?");
        if ("success".equals(revenueResult.get("status"))) {
            insight.setRevenueOutlook((String) revenueResult.get("answer"));
        }
        
        Map<String, Object> managementResult = queryReport(ticker, 
            "What does management say about market conditions?");
        if ("success".equals(managementResult.get("status"))) {
            insight.setManagementView((String) managementResult.get("answer"));
        }
        
        // Calculate overall recommendation
        insight.setRecommendation(calculateRecommendation(insight));
        
        return insight;
    }
    
    /**
     * Score stock based on fundamental insights (0-100)
     */
    public double scoreFundamentals(String ticker) {
        Optional<AnnualReportMetadata> report = 
            reportMetadataRepository.findFirstByTickerOrderByReportYearDesc(ticker);
        
        if (report.isEmpty()) {
            return 50.0; // Neutral score if no data
        }
        
        AnnualReportMetadata metadata = report.get();
        double score = 50.0; // Base score
        
        // Adjust based on fundamental score from RAG
        if (metadata.getFundamentalScore() != null) {
            score = metadata.getFundamentalScore();
        }
        
        // Adjust based on P/E ratio
        if (metadata.getPeRatio() != null) {
            if (metadata.getPeRatio() < 15) {
                score += 10; // Undervalued
            } else if (metadata.getPeRatio() > 30) {
                score -= 10; // Overvalued
            }
        }
        
        // Adjust based on revenue growth
        if (metadata.getRevenueGrowthPct() != null) {
            if (metadata.getRevenueGrowthPct() > 10) {
                score += 15; // Strong growth
            } else if (metadata.getRevenueGrowthPct() < 0) {
                score -= 20; // Declining revenue
            }
        }
        
        // Adjust based on debt-to-equity
        if (metadata.getDebtToEquity() != null) {
            if (metadata.getDebtToEquity() > 2.0) {
                score -= 15; // High debt
            } else if (metadata.getDebtToEquity() < 0.5) {
                score += 10; // Low debt
            }
        }

        Optional<QuarterlyReportMetadata> quarterly =
            quarterlyReportMetadataRepository.findFirstByTickerOrderByReportYearDescReportQuarterDesc(ticker);
        if (quarterly.isPresent() && quarterly.get().getDownloadedAt() != null) {
            LocalDateTime downloadedAt = quarterly.get().getDownloadedAt();
            if (downloadedAt.isAfter(LocalDateTime.now().minusDays(QUARTERLY_FRESHNESS_DAYS))) {
                score += 5; // Fresh quarterly report adds confidence
            }
        }
        
        // Clamp to 0-100
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Get fundamental scores for multiple tickers
     */
    public Map<String, Double> batchScoreFundamentals(List<String> tickers) {
        Map<String, Double> scores = new HashMap<>();
        for (String ticker : tickers) {
            scores.put(ticker, scoreFundamentals(ticker));
        }
        return scores;
    }
    
    private String calculateRecommendation(FundamentalInsight insight) {
        if (!insight.isAvailable()) {
            return "NEUTRAL - No fundamental data available";
        }
        
        double score = insight.getFundamentalScore() != null ? insight.getFundamentalScore() : 50.0;
        
        // Check for red flags
        if (insight.getPeRatio() != null && insight.getPeRatio() > 50) {
            return "AVOID - Extremely high P/E ratio (>50)";
        }
        
        if (insight.getDebtToEquity() != null && insight.getDebtToEquity() > 2.0) {
            return "CAUTION - High debt-to-equity ratio (>2.0)";
        }
        
        if (insight.getRevenueGrowthPct() != null && insight.getRevenueGrowthPct() < -5) {
            return "CAUTION - Declining revenue (< -5%)";
        }
        
        // Positive recommendations
        if (score >= 75) {
            return "STRONG BUY - Excellent fundamentals";
        } else if (score >= 60) {
            return "BUY - Good fundamentals";
        } else if (score >= 45) {
            return "HOLD - Neutral fundamentals";
        } else if (score >= 30) {
            return "SELL - Weak fundamentals";
        } else {
            return "STRONG SELL - Poor fundamentals";
        }
    }
    
    /**
     * Fundamental insight result
     */
    @lombok.Builder
    @lombok.Data
    public static class FundamentalInsight {
        private String ticker;
        private boolean available;
        private String message;
        private Integer reportYear;
        private String summary;
        private Double fundamentalScore;
        private Double peRatio;
        private Double revenueGrowthPct;
        private Double debtToEquity;
        private Integer quarterlyReportYear;
        private Integer quarterlyReportQuarter;
        private String quarterlySummary;
        private String mainRisks;
        private String revenueOutlook;
        private String managementView;
        private String recommendation;
    }
}
