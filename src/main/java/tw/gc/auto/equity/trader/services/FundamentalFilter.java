package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.AnnualReportMetadata;
import tw.gc.auto.equity.trader.repositories.AnnualReportMetadataRepository;

import java.util.Optional;

/**
 * Fundamental Filter Service
 * 
 * Phase 7: Annual Report RAG Integration
 * - Filter out stocks with poor fundamentals
 * - Avoid high P/E, high debt, declining revenue
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FundamentalFilter {
    
    private final AnnualReportMetadataRepository reportMetadataRepository;
    
    // Thresholds for filtering
    private static final double MAX_PE_RATIO = 50.0;
    private static final double MAX_DEBT_TO_EQUITY = 2.0;
    private static final double MIN_REVENUE_GROWTH_PCT = -5.0;
    private static final double MIN_FUNDAMENTAL_SCORE = 30.0;
    
    /**
     * Check if a stock passes fundamental filters
     * Returns true if stock is acceptable for trading
     */
    public boolean passesFilter(String ticker) {
        FilterResult result = evaluateStock(ticker);
        return result.passed;
    }
    
    /**
     * Evaluate stock against fundamental filters with detailed result
     */
    public FilterResult evaluateStock(String ticker) {
        Optional<AnnualReportMetadata> report = 
            reportMetadataRepository.findFirstByTickerOrderByReportYearDesc(ticker);
        
        if (report.isEmpty()) {
            // No fundamental data - allow trading but with caution flag
            return FilterResult.builder()
                .ticker(ticker)
                .passed(true)
                .reason("No fundamental data available")
                .caution(true)
                .build();
        }
        
        AnnualReportMetadata metadata = report.get();
        
        // Check P/E ratio
        if (metadata.getPeRatio() != null && metadata.getPeRatio() > MAX_PE_RATIO) {
            log.warn("Stock {} failed P/E filter: {} > {}", ticker, metadata.getPeRatio(), MAX_PE_RATIO);
            return FilterResult.builder()
                .ticker(ticker)
                .passed(false)
                .reason(String.format("P/E ratio too high: %.2f (max: %.2f)", 
                    metadata.getPeRatio(), MAX_PE_RATIO))
                .build();
        }
        
        // Check debt-to-equity
        if (metadata.getDebtToEquity() != null && metadata.getDebtToEquity() > MAX_DEBT_TO_EQUITY) {
            log.warn("Stock {} failed debt filter: {} > {}", ticker, metadata.getDebtToEquity(), MAX_DEBT_TO_EQUITY);
            return FilterResult.builder()
                .ticker(ticker)
                .passed(false)
                .reason(String.format("Debt-to-equity too high: %.2f (max: %.2f)", 
                    metadata.getDebtToEquity(), MAX_DEBT_TO_EQUITY))
                .build();
        }
        
        // Check revenue growth
        if (metadata.getRevenueGrowthPct() != null && 
            metadata.getRevenueGrowthPct() < MIN_REVENUE_GROWTH_PCT) {
            log.warn("Stock {} failed revenue growth filter: {}% < {}%", 
                ticker, metadata.getRevenueGrowthPct(), MIN_REVENUE_GROWTH_PCT);
            return FilterResult.builder()
                .ticker(ticker)
                .passed(false)
                .reason(String.format("Revenue declining: %.2f%% (min: %.2f%%)", 
                    metadata.getRevenueGrowthPct(), MIN_REVENUE_GROWTH_PCT))
                .build();
        }
        
        // Check fundamental score
        if (metadata.getFundamentalScore() != null && 
            metadata.getFundamentalScore() < MIN_FUNDAMENTAL_SCORE) {
            log.warn("Stock {} failed fundamental score filter: {} < {}", 
                ticker, metadata.getFundamentalScore(), MIN_FUNDAMENTAL_SCORE);
            return FilterResult.builder()
                .ticker(ticker)
                .passed(false)
                .reason(String.format("Fundamental score too low: %.2f (min: %.2f)", 
                    metadata.getFundamentalScore(), MIN_FUNDAMENTAL_SCORE))
                .build();
        }
        
        // All filters passed
        return FilterResult.builder()
            .ticker(ticker)
            .passed(true)
            .reason("All fundamental filters passed")
            .caution(false)
            .build();
    }
    
    /**
     * Get filter thresholds for documentation/display
     */
    public FilterThresholds getThresholds() {
        return FilterThresholds.builder()
            .maxPeRatio(MAX_PE_RATIO)
            .maxDebtToEquity(MAX_DEBT_TO_EQUITY)
            .minRevenueGrowthPct(MIN_REVENUE_GROWTH_PCT)
            .minFundamentalScore(MIN_FUNDAMENTAL_SCORE)
            .build();
    }
    
    /**
     * Filter result
     */
    @lombok.Builder
    @lombok.Data
    public static class FilterResult {
        private String ticker;
        private boolean passed;
        private String reason;
        @lombok.Builder.Default
        private boolean caution = false;
    }
    
    /**
     * Filter thresholds
     */
    @lombok.Builder
    @lombok.Data
    public static class FilterThresholds {
        private double maxPeRatio;
        private double maxDebtToEquity;
        private double minRevenueGrowthPct;
        private double minFundamentalScore;
    }
}
