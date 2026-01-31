package tw.gc.auto.equity.trader.services.execution;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ExecutionQualityReport - Generate periodic reports on execution quality.
 * 
 * <h3>Phase 4: Realistic Execution Modeling</h3>
 * <p>Generates weekly reports including:
 * <ul>
 *   <li>Overall slippage statistics</li>
 *   <li>Fill rate analysis</li>
 *   <li>High-slippage stocks identification</li>
 *   <li>Time-of-day patterns</li>
 *   <li>Recommendations for execution improvement</li>
 * </ul>
 * 
 * @see ExecutionAnalyticsService for underlying data
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionQualityReport {

    private final ExecutionAnalyticsService executionAnalyticsService;
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double ACCEPTABLE_SLIPPAGE_BPS = 15.0;  // 0.15%
    private static final double ACCEPTABLE_FILL_RATE = 95.0;     // 95%
    
    /**
     * Generate a weekly execution quality report.
     * Runs every Monday at 8:00 AM.
     */
    @Scheduled(cron = "0 0 8 * * MON")
    public void generateWeeklyReport() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusWeeks(1);
        
        WeeklyReport report = generateReport(startDate, endDate);
        
        log.info("📊 Weekly Execution Quality Report Generated:\n{}", formatReportForLog(report));
    }
    
    /**
     * Generate a report for a specific date range.
     * 
     * @param startDate start of period
     * @param endDate end of period
     * @return the generated report
     */
    public WeeklyReport generateReport(LocalDate startDate, LocalDate endDate) {
        ExecutionAnalyticsService.ExecutionSummary summary = 
            executionAnalyticsService.getSummaryForPeriod(startDate, endDate);
        
        List<ExecutionAnalyticsService.SymbolSlippageStats> highSlippageSymbols = 
            executionAnalyticsService.getHighSlippageSymbols();
        
        Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTime = 
            executionAnalyticsService.getSlippageByTimeBucket();
        
        List<String> recommendations = generateRecommendations(
            summary, highSlippageSymbols, slippageByTime);
        
        return WeeklyReport.builder()
            .startDate(startDate)
            .endDate(endDate)
            .summary(summary)
            .highSlippageSymbols(highSlippageSymbols)
            .slippageByTimeBucket(slippageByTime)
            .recommendations(recommendations)
            .overallGrade(calculateGrade(summary))
            .build();
    }
    
    /**
     * Generate a quick summary for Telegram notification.
     * 
     * @param startDate start of period
     * @param endDate end of period
     * @return formatted summary string
     */
    public String generateTelegramSummary(LocalDate startDate, LocalDate endDate) {
        ExecutionAnalyticsService.ExecutionSummary summary = 
            executionAnalyticsService.getSummaryForPeriod(startDate, endDate);
        
        if (summary.getTotalExecutions() == 0) {
            return "📊 No executions in period " + startDate + " to " + endDate;
        }
        
        String grade = calculateGrade(summary);
        String slippageEmoji = summary.getAverageSlippageBps() <= ACCEPTABLE_SLIPPAGE_BPS ? "✅" : "⚠️";
        String fillEmoji = summary.getFillRate() >= ACCEPTABLE_FILL_RATE ? "✅" : "⚠️";
        
        return String.format("""
            📊 *Execution Quality Report*
            Period: %s to %s
            
            📈 *Summary*
            • Total Executions: %d
            • Symbols Traded: %d
            
            %s *Slippage*
            • Average: %.2f bps (%.3f%%)
            • Max: %.2f bps
            • High Slippage Trades: %d
            
            %s *Fill Rate*
            • Rate: %.1f%%
            
            🎯 *Overall Grade: %s*
            """,
            startDate.format(DATE_FORMAT), endDate.format(DATE_FORMAT),
            summary.getTotalExecutions(),
            summary.getSymbolCount(),
            slippageEmoji,
            summary.getAverageSlippageBps(), summary.getSlippageCostPercent(),
            summary.getMaxSlippageBps(),
            summary.getHighSlippageCount(),
            fillEmoji,
            summary.getFillRate(),
            grade);
    }
    
    /**
     * Get stocks that should be avoided due to poor execution quality.
     * 
     * @return list of symbols to avoid with reasons
     */
    public List<StockAvoidanceRecommendation> getStocksToAvoid() {
        return executionAnalyticsService.getHighSlippageSymbols().stream()
            .filter(s -> s.getAverageSlippageBps() > ACCEPTABLE_SLIPPAGE_BPS * 2) // Very high slippage
            .map(s -> StockAvoidanceRecommendation.builder()
                .symbol(s.getSymbol())
                .reason(buildAvoidanceReason(s))
                .averageSlippageBps(s.getAverageSlippageBps())
                .fillRate(s.getFillRate())
                .sampleCount(s.getSampleCount())
                .build())
            .collect(Collectors.toList());
    }
    
    /**
     * Identify optimal execution times based on historical slippage.
     * 
     * @return ranked list of time buckets from best to worst
     */
    public List<TimeBucketRanking> getOptimalExecutionTimes() {
        Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTime = 
            executionAnalyticsService.getSlippageByTimeBucket();
        
        return slippageByTime.entrySet().stream()
            .map(e -> TimeBucketRanking.builder()
                .timeBucket(e.getKey())
                .averageSlippageBps(e.getValue())
                .isRecommended(e.getValue() <= ACCEPTABLE_SLIPPAGE_BPS)
                .build())
            .sorted(Comparator.comparingDouble(TimeBucketRanking::getAverageSlippageBps))
            .collect(Collectors.toList());
    }
    
    /**
     * Check if execution quality is acceptable for live trading.
     * 
     * @return true if execution quality meets minimum standards
     */
    public boolean isExecutionQualityAcceptable() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusWeeks(4); // 4-week lookback
        
        ExecutionAnalyticsService.ExecutionSummary summary = 
            executionAnalyticsService.getSummaryForPeriod(startDate, endDate);
        
        if (summary.getTotalExecutions() < 20) {
            // Insufficient data - assume acceptable
            return true;
        }
        
        return summary.getAverageSlippageBps() <= ACCEPTABLE_SLIPPAGE_BPS * 1.5 &&
               summary.getFillRate() >= ACCEPTABLE_FILL_RATE * 0.9;
    }
    
    // ==================== Helper Methods ====================
    
    private List<String> generateRecommendations(
            ExecutionAnalyticsService.ExecutionSummary summary,
            List<ExecutionAnalyticsService.SymbolSlippageStats> highSlippageSymbols,
            Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTime) {
        
        List<String> recommendations = new ArrayList<>();
        
        // High overall slippage
        if (summary.getAverageSlippageBps() > ACCEPTABLE_SLIPPAGE_BPS) {
            recommendations.add(String.format(
                "⚠️ Average slippage (%.2f bps) exceeds target (%.2f bps). " +
                "Consider using limit orders or reducing order sizes.",
                summary.getAverageSlippageBps(), ACCEPTABLE_SLIPPAGE_BPS));
        }
        
        // Low fill rate
        if (summary.getFillRate() < ACCEPTABLE_FILL_RATE) {
            recommendations.add(String.format(
                "⚠️ Fill rate (%.1f%%) below target (%.1f%%). " +
                "Consider more aggressive pricing or splitting orders.",
                summary.getFillRate(), ACCEPTABLE_FILL_RATE));
        }
        
        // High-slippage stocks
        if (!highSlippageSymbols.isEmpty()) {
            String symbols = highSlippageSymbols.stream()
                .limit(3)
                .map(ExecutionAnalyticsService.SymbolSlippageStats::getSymbol)
                .collect(Collectors.joining(", "));
            recommendations.add(String.format(
                "⚠️ High slippage stocks identified: %s. Consider avoiding or using smaller sizes.",
                symbols));
        }
        
        // Time-based recommendations
        Optional<Map.Entry<ExecutionAnalyticsService.TimeBucket, Double>> worstTime = 
            slippageByTime.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue));
        
        if (worstTime.isPresent() && worstTime.get().getValue() > ACCEPTABLE_SLIPPAGE_BPS * 1.5) {
            recommendations.add(String.format(
                "⚠️ Avoid trading during %s (%.2f bps avg slippage).",
                worstTime.get().getKey().name().replace("_", " ").toLowerCase(),
                worstTime.get().getValue()));
        }
        
        // Positive feedback
        if (recommendations.isEmpty()) {
            recommendations.add("✅ Execution quality is within acceptable parameters.");
        }
        
        return recommendations;
    }
    
    private String calculateGrade(ExecutionAnalyticsService.ExecutionSummary summary) {
        if (summary.getTotalExecutions() < 10) {
            return "N/A (Insufficient Data)";
        }
        
        double slippageScore = Math.max(0, 100 - (summary.getAverageSlippageBps() / ACCEPTABLE_SLIPPAGE_BPS) * 50);
        double fillScore = summary.getFillRate();
        
        double overallScore = (slippageScore * 0.6) + (fillScore * 0.4);
        
        if (overallScore >= 90) return "A+ (Excellent)";
        if (overallScore >= 80) return "A (Good)";
        if (overallScore >= 70) return "B (Acceptable)";
        if (overallScore >= 60) return "C (Needs Improvement)";
        return "D (Poor)";
    }
    
    private String buildAvoidanceReason(ExecutionAnalyticsService.SymbolSlippageStats stats) {
        StringBuilder reason = new StringBuilder();
        
        if (stats.getAverageSlippageBps() > ACCEPTABLE_SLIPPAGE_BPS * 3) {
            reason.append("Extremely high slippage (").append(String.format("%.1f", stats.getAverageSlippageBps()))
                  .append(" bps). ");
        } else {
            reason.append("High slippage (").append(String.format("%.1f", stats.getAverageSlippageBps()))
                  .append(" bps). ");
        }
        
        if (stats.getFillRate() < 90) {
            reason.append("Low fill rate (").append(String.format("%.1f", stats.getFillRate())).append("%). ");
        }
        
        if (!stats.isConsistent()) {
            reason.append("Inconsistent execution (high variability). ");
        }
        
        return reason.toString().trim();
    }
    
    private String formatReportForLog(WeeklyReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n═══════════════════════════════════════\n");
        sb.append("  WEEKLY EXECUTION QUALITY REPORT\n");
        sb.append("  Period: ").append(report.getStartDate()).append(" to ").append(report.getEndDate()).append("\n");
        sb.append("═══════════════════════════════════════\n");
        
        ExecutionAnalyticsService.ExecutionSummary s = report.getSummary();
        sb.append(String.format("  Total Executions: %d\n", s.getTotalExecutions()));
        sb.append(String.format("  Symbols Traded:   %d\n", s.getSymbolCount()));
        sb.append(String.format("  Avg Slippage:     %.2f bps (%.3f%%)\n", 
            s.getAverageSlippageBps(), s.getSlippageCostPercent()));
        sb.append(String.format("  Max Slippage:     %.2f bps\n", s.getMaxSlippageBps()));
        sb.append(String.format("  Fill Rate:        %.1f%%\n", s.getFillRate()));
        sb.append(String.format("  High Slip Trades: %d\n", s.getHighSlippageCount()));
        sb.append("\n  Grade: ").append(report.getOverallGrade()).append("\n");
        
        if (!report.getRecommendations().isEmpty()) {
            sb.append("\n  Recommendations:\n");
            for (String rec : report.getRecommendations()) {
                sb.append("    • ").append(rec).append("\n");
            }
        }
        
        sb.append("═══════════════════════════════════════");
        return sb.toString();
    }
    
    // ==================== Data Classes ====================
    
    /**
     * Weekly execution quality report.
     */
    @Data
    @Builder
    public static class WeeklyReport {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final ExecutionAnalyticsService.ExecutionSummary summary;
        private final List<ExecutionAnalyticsService.SymbolSlippageStats> highSlippageSymbols;
        private final Map<ExecutionAnalyticsService.TimeBucket, Double> slippageByTimeBucket;
        private final List<String> recommendations;
        private final String overallGrade;
    }
    
    /**
     * Recommendation to avoid a specific stock.
     */
    @Data
    @Builder
    public static class StockAvoidanceRecommendation {
        private final String symbol;
        private final String reason;
        private final double averageSlippageBps;
        private final double fillRate;
        private final int sampleCount;
    }
    
    /**
     * Ranking of a time bucket by execution quality.
     */
    @Data
    @Builder
    public static class TimeBucketRanking {
        private final ExecutionAnalyticsService.TimeBucket timeBucket;
        private final double averageSlippageBps;
        private final boolean isRecommended;
        
        /**
         * Get a human-readable time range for this bucket.
         */
        public String getTimeRange() {
            return switch (timeBucket) {
                case MARKET_OPEN -> "09:00-09:30";
                case MORNING_EARLY -> "09:30-10:30";
                case MORNING_LATE -> "10:30-11:30";
                case MIDDAY -> "11:30-12:30";
                case AFTERNOON_EARLY -> "12:30-13:00";
                case MARKET_CLOSE -> "13:00-13:30";
            };
        }
    }
}
