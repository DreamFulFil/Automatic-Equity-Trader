package tw.gc.auto.equity.trader.services;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.VetoEvent;
import tw.gc.auto.equity.trader.repositories.BarRepository;
import tw.gc.auto.equity.trader.repositories.VetoEventRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Veto Analytics Service
 * 
 * Tracks vetoed trades and calculates their hypothetical outcomes to:
 * 1. Calculate "missed profit" from false vetoes
 * 2. Tune veto thresholds based on historical data
 * 3. Identify patterns in veto decisions that hurt profitability
 * 
 * Phase 5.3 Implementation
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VetoAnalyticsService {
    
    private final VetoEventRepository vetoEventRepository;
    private final BarRepository barRepository;
    private final TelegramService telegramService;
    
    // Configuration
    private static final int HOLD_PERIOD_MINUTES = 60;  // Hypothetical hold period for missed trades
    private static final double SLIPPAGE_RATE = 0.0015; // 0.15% slippage assumption
    private static final int ANALYSIS_DAYS = 30;        // Days of history to analyze
    
    /**
     * Vetoed trade record for analysis
     */
    @Data
    @Builder
    public static class VetoedTradeRecord {
        private Long vetoId;
        private LocalDateTime vetoTime;
        private String symbol;
        private String direction;          // LONG or SHORT
        private int proposedShares;
        private double vetoPrice;          // Price at veto time
        private String vetoReason;
        private Double riskScore;
        
        // Hypothetical outcome (filled in by analysis)
        private Double exitPrice;
        private Double hypotheticalPnl;
        private Double hypotheticalReturn;
        private boolean wouldHaveBeenProfitable;
    }
    
    /**
     * Veto analytics report
     */
    @Data
    @Builder
    public static class VetoAnalyticsReport {
        private LocalDateTime reportTime;
        private int totalVetoes;
        private int analyzedVetoes;
        private int profitableVetoes;       // Would have lost money → correct veto
        private int unprofitableVetoes;     // Would have made money → missed opportunity
        private double totalMissedProfit;
        private double totalAvoidedLoss;
        private double netMissedValue;
        private double falseVetoRate;       // % of vetoes that would have been profitable
        private Map<String, VetoTypeStats> statsByVetoType;
        private Map<String, VetoTypeStats> statsBySymbol;
        private List<VetoedTradeRecord> topMissedOpportunities;
        private List<VetoedTradeRecord> topAvoidedLosses;
        private double optimalThresholdSuggestion;
    }
    
    /**
     * Stats per veto type or category
     */
    @Data
    @Builder
    public static class VetoTypeStats {
        private String category;
        private int count;
        private int profitableVetoes;
        private int unprofitableVetoes;
        private double totalMissedProfit;
        private double totalAvoidedLoss;
        private double avgRiskScore;
        private double falseVetoRate;
    }
    
    /**
     * Analyze vetoed trades and calculate hypothetical outcomes
     * Called weekly or on-demand
     */
    @Scheduled(cron = "0 0 18 * * FRI")  // Every Friday at 6 PM
    public VetoAnalyticsReport generateWeeklyAnalytics() {
        return generateAnalyticsReport(LocalDateTime.now().minusDays(7), LocalDateTime.now());
    }
    
    /**
     * Generate analytics report for a specific date range
     */
    public VetoAnalyticsReport generateAnalyticsReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("📊 Generating veto analytics report from {} to {}", startDate, endDate);
        
        // Get all vetoes in the date range
        List<VetoEvent> vetoes = vetoEventRepository.findByTimestampBetween(startDate, endDate);
        
        if (vetoes.isEmpty()) {
            log.info("No vetoes found in the specified date range");
            return VetoAnalyticsReport.builder()
                .reportTime(LocalDateTime.now())
                .totalVetoes(0)
                .analyzedVetoes(0)
                .build();
        }
        
        // Analyze each veto
        List<VetoedTradeRecord> analyzedTrades = new ArrayList<>();
        for (VetoEvent veto : vetoes) {
            if (veto.getSymbol() == null || veto.getSymbol().isEmpty()) {
                continue; // Skip market-wide vetoes
            }
            
            VetoedTradeRecord record = analyzeVetoedTrade(veto);
            if (record != null) {
                analyzedTrades.add(record);
            }
        }
        
        // Calculate aggregate statistics
        return buildAnalyticsReport(vetoes.size(), analyzedTrades);
    }
    
    /**
     * Analyze a single vetoed trade to determine hypothetical outcome
     */
    private VetoedTradeRecord analyzeVetoedTrade(VetoEvent veto) {
        try {
            String symbol = veto.getSymbol();
            LocalDateTime vetoTime = veto.getTimestamp();
            
            // Get price at veto time - use bars around that time
            List<Bar> barsBeforeVeto = barRepository.findBySymbolAndTimeframeAndTimestampBetween(
                symbol, "1m", vetoTime.minusMinutes(5), vetoTime);
            if (barsBeforeVeto.isEmpty()) {
                return null;
            }
            double vetoPrice = barsBeforeVeto.get(barsBeforeVeto.size() - 1).getClose();
            
            // Get price after hold period
            LocalDateTime exitTime = vetoTime.plusMinutes(HOLD_PERIOD_MINUTES);
            // Ensure exit time is within trading hours (09:00-13:30)
            LocalTime exitLocalTime = exitTime.toLocalTime();
            if (exitLocalTime.isBefore(LocalTime.of(9, 0)) || exitLocalTime.isAfter(LocalTime.of(13, 30))) {
                exitTime = vetoTime.plusDays(1).withHour(10).withMinute(0);
            }
            
            List<Bar> barsAfterVeto = barRepository.findBySymbolAndTimeframeAndTimestampBetween(
                symbol, "1m", exitTime, exitTime.plusMinutes(5));
            if (barsAfterVeto.isEmpty()) {
                return null;
            }
            double exitPrice = barsAfterVeto.get(0).getClose();
            
            // Determine direction from metadata or default to LONG
            String direction = extractDirection(veto);
            int proposedShares = extractShares(veto);
            
            // Calculate hypothetical P&L
            double grossReturn = direction.equals("SHORT") 
                ? (vetoPrice - exitPrice) / vetoPrice 
                : (exitPrice - vetoPrice) / vetoPrice;
            double netReturn = grossReturn - SLIPPAGE_RATE;
            double hypotheticalPnl = vetoPrice * proposedShares * netReturn;
            
            return VetoedTradeRecord.builder()
                .vetoId(veto.getId())
                .vetoTime(vetoTime)
                .symbol(symbol)
                .direction(direction)
                .proposedShares(proposedShares)
                .vetoPrice(vetoPrice)
                .vetoReason(veto.getRationale())
                .riskScore(veto.getConfidenceScore())
                .exitPrice(exitPrice)
                .hypotheticalPnl(hypotheticalPnl)
                .hypotheticalReturn(netReturn * 100)
                .wouldHaveBeenProfitable(hypotheticalPnl > 0)
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to analyze veto {}: {}", veto.getId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract trade direction from veto metadata
     */
    private String extractDirection(VetoEvent veto) {
        String metadata = veto.getMetadataJson();
        if (metadata != null) {
            if (metadata.contains("\"direction\":\"SHORT\"")) {
                return "SHORT";
            }
        }
        return "LONG"; // Default
    }
    
    /**
     * Extract proposed shares from veto metadata
     */
    private int extractShares(VetoEvent veto) {
        String metadata = veto.getMetadataJson();
        if (metadata != null) {
            try {
                int idx = metadata.indexOf("\"shares\":");
                if (idx >= 0) {
                    String rest = metadata.substring(idx + 9).trim();
                    StringBuilder sb = new StringBuilder();
                    for (char c : rest.toCharArray()) {
                        if (Character.isDigit(c)) {
                            sb.append(c);
                        } else {
                            break;
                        }
                    }
                    if (!sb.isEmpty()) {
                        return Integer.parseInt(sb.toString());
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        return 50; // Default assumption
    }
    
    /**
     * Build the analytics report from analyzed trades
     */
    private VetoAnalyticsReport buildAnalyticsReport(int totalVetoes, List<VetoedTradeRecord> trades) {
        int profitableVetoes = 0;
        int unprofitableVetoes = 0;
        double totalMissedProfit = 0;
        double totalAvoidedLoss = 0;
        
        Map<String, List<VetoedTradeRecord>> byVetoType = new HashMap<>();
        Map<String, List<VetoedTradeRecord>> bySymbol = new HashMap<>();
        
        for (VetoedTradeRecord trade : trades) {
            if (trade.isWouldHaveBeenProfitable()) {
                unprofitableVetoes++;
                totalMissedProfit += trade.getHypotheticalPnl();
            } else {
                profitableVetoes++;
                totalAvoidedLoss += Math.abs(trade.getHypotheticalPnl());
            }
            
            // Group by veto reason category
            String vetoCategory = categorizeVetoReason(trade.getVetoReason());
            byVetoType.computeIfAbsent(vetoCategory, k -> new ArrayList<>()).add(trade);
            
            // Group by symbol
            bySymbol.computeIfAbsent(trade.getSymbol(), k -> new ArrayList<>()).add(trade);
        }
        
        // Calculate stats by veto type
        Map<String, VetoTypeStats> statsByVetoType = byVetoType.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> buildVetoTypeStats(e.getKey(), e.getValue())
            ));
        
        // Calculate stats by symbol
        Map<String, VetoTypeStats> statsBySymbol = bySymbol.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> buildVetoTypeStats(e.getKey(), e.getValue())
            ));
        
        // Find top missed opportunities and top avoided losses
        List<VetoedTradeRecord> topMissed = trades.stream()
            .filter(VetoedTradeRecord::isWouldHaveBeenProfitable)
            .sorted((a, b) -> Double.compare(b.getHypotheticalPnl(), a.getHypotheticalPnl()))
            .limit(5)
            .toList();
        
        List<VetoedTradeRecord> topAvoided = trades.stream()
            .filter(t -> !t.isWouldHaveBeenProfitable())
            .sorted((a, b) -> Double.compare(Math.abs(b.getHypotheticalPnl()), Math.abs(a.getHypotheticalPnl())))
            .limit(5)
            .toList();
        
        // Calculate false veto rate
        double falseVetoRate = trades.isEmpty() ? 0 : 
            (double) unprofitableVetoes / trades.size() * 100;
        
        // Calculate optimal threshold suggestion
        double optimalThreshold = calculateOptimalThreshold(trades);
        
        VetoAnalyticsReport report = VetoAnalyticsReport.builder()
            .reportTime(LocalDateTime.now())
            .totalVetoes(totalVetoes)
            .analyzedVetoes(trades.size())
            .profitableVetoes(profitableVetoes)
            .unprofitableVetoes(unprofitableVetoes)
            .totalMissedProfit(totalMissedProfit)
            .totalAvoidedLoss(totalAvoidedLoss)
            .netMissedValue(totalMissedProfit - totalAvoidedLoss)
            .falseVetoRate(falseVetoRate)
            .statsByVetoType(statsByVetoType)
            .statsBySymbol(statsBySymbol)
            .topMissedOpportunities(topMissed)
            .topAvoidedLosses(topAvoided)
            .optimalThresholdSuggestion(optimalThreshold)
            .build();
        
        // Log summary
        log.info("📊 Veto Analytics Summary: {} total vetoes, {} analyzed", totalVetoes, trades.size());
        log.info("   - Correct vetoes (avoided loss): {} (${:.2f})", profitableVetoes, totalAvoidedLoss);
        log.info("   - False vetoes (missed profit): {} (${:.2f})", unprofitableVetoes, totalMissedProfit);
        log.info("   - False veto rate: {:.1f}%", falseVetoRate);
        log.info("   - Net missed value: ${:.2f}", report.getNetMissedValue());
        log.info("   - Suggested optimal threshold: {:.1f}", optimalThreshold);
        
        return report;
    }
    
    /**
     * Build stats for a specific veto category
     */
    private VetoTypeStats buildVetoTypeStats(String category, List<VetoedTradeRecord> trades) {
        int profitable = 0;
        int unprofitable = 0;
        double missedProfit = 0;
        double avoidedLoss = 0;
        double totalRiskScore = 0;
        int scoreCount = 0;
        
        for (VetoedTradeRecord trade : trades) {
            if (trade.isWouldHaveBeenProfitable()) {
                unprofitable++;
                missedProfit += trade.getHypotheticalPnl();
            } else {
                profitable++;
                avoidedLoss += Math.abs(trade.getHypotheticalPnl());
            }
            if (trade.getRiskScore() != null) {
                totalRiskScore += trade.getRiskScore();
                scoreCount++;
            }
        }
        
        return VetoTypeStats.builder()
            .category(category)
            .count(trades.size())
            .profitableVetoes(profitable)
            .unprofitableVetoes(unprofitable)
            .totalMissedProfit(missedProfit)
            .totalAvoidedLoss(avoidedLoss)
            .avgRiskScore(scoreCount > 0 ? totalRiskScore / scoreCount : 0)
            .falseVetoRate(trades.isEmpty() ? 0 : (double) unprofitable / trades.size() * 100)
            .build();
    }
    
    /**
     * Categorize veto reason into high-level categories
     */
    private String categorizeVetoReason(String reason) {
        if (reason == null) {
            return "UNKNOWN";
        }
        String lower = reason.toLowerCase();
        
        if (lower.contains("drawdown") || lower.contains("loss limit")) {
            return "DRAWDOWN";
        }
        if (lower.contains("news") || lower.contains("sentiment")) {
            return "NEWS";
        }
        if (lower.contains("volatility") || lower.contains("choppy")) {
            return "VOLATILITY";
        }
        if (lower.contains("streak") || lower.contains("losing")) {
            return "STREAK";
        }
        if (lower.contains("size") || lower.contains("frequency") || lower.contains("shares")) {
            return "SIZE/FREQUENCY";
        }
        if (lower.contains("session") || lower.contains("timing")) {
            return "TIMING";
        }
        if (lower.contains("strategy") || lower.contains("new")) {
            return "STRATEGY_MATURITY";
        }
        
        return "OTHER";
    }
    
    /**
     * Calculate optimal veto threshold based on historical data
     * Uses binary search to find threshold that maximizes net value
     */
    private double calculateOptimalThreshold(List<VetoedTradeRecord> trades) {
        if (trades.isEmpty()) {
            return 70.0; // Default
        }
        
        // Filter trades with risk scores
        List<VetoedTradeRecord> tradesWithScores = trades.stream()
            .filter(t -> t.getRiskScore() != null)
            .toList();
        
        if (tradesWithScores.isEmpty()) {
            return 70.0; // Default
        }
        
        double bestThreshold = 70.0;
        double bestNetValue = Double.NEGATIVE_INFINITY;
        
        // Test thresholds from 50 to 90
        for (int threshold = 50; threshold <= 90; threshold += 5) {
            double netValue = 0;
            for (VetoedTradeRecord trade : tradesWithScores) {
                boolean wouldVeto = trade.getRiskScore() >= threshold;
                if (wouldVeto) {
                    // We vetoed - gain avoided loss or lose missed profit
                    netValue += trade.isWouldHaveBeenProfitable() 
                        ? -trade.getHypotheticalPnl()   // Missed profit (negative)
                        : Math.abs(trade.getHypotheticalPnl());  // Avoided loss (positive)
                } else {
                    // We would have traded - gain profit or lose on bad trade
                    netValue += trade.getHypotheticalPnl(); // Could be positive or negative
                }
            }
            
            if (netValue > bestNetValue) {
                bestNetValue = netValue;
                bestThreshold = threshold;
            }
        }
        
        return bestThreshold;
    }
    
    /**
     * Send weekly veto analytics via Telegram
     */
    public void sendVetoAnalyticsAlert(VetoAnalyticsReport report) {
        if (report.getAnalyzedVetoes() == 0) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Weekly Veto Analytics Report\n\n");
        sb.append(String.format("Total Vetoes: %d\n", report.getTotalVetoes()));
        sb.append(String.format("Analyzed: %d\n\n", report.getAnalyzedVetoes()));
        
        sb.append("✅ Correct Vetoes (avoided loss): ")
          .append(report.getProfitableVetoes())
          .append(String.format(" ($%.0f saved)\n", report.getTotalAvoidedLoss()));
        
        sb.append("❌ False Vetoes (missed profit): ")
          .append(report.getUnprofitableVetoes())
          .append(String.format(" ($%.0f missed)\n\n", report.getTotalMissedProfit()));
        
        sb.append(String.format("False Veto Rate: %.1f%%\n", report.getFalseVetoRate()));
        sb.append(String.format("Net Impact: $%.0f\n\n", report.getNetMissedValue()));
        
        if (!report.getTopMissedOpportunities().isEmpty()) {
            sb.append("Top Missed Opportunities:\n");
            for (VetoedTradeRecord trade : report.getTopMissedOpportunities().subList(
                    0, Math.min(3, report.getTopMissedOpportunities().size()))) {
                sb.append(String.format("  • %s: $%.0f\n", trade.getSymbol(), trade.getHypotheticalPnl()));
            }
            sb.append("\n");
        }
        
        if (report.getOptimalThresholdSuggestion() != 70.0) {
            sb.append(String.format("💡 Suggested threshold adjustment: %.0f\n", 
                report.getOptimalThresholdSuggestion()));
        }
        
        try {
            telegramService.sendMessage(sb.toString());
        } catch (Exception e) {
            log.warn("Failed to send veto analytics alert: {}", e.getMessage());
        }
    }
    
    /**
     * Get recent false veto rate for monitoring
     */
    public double getRecentFalseVetoRate(int days) {
        VetoAnalyticsReport report = generateAnalyticsReport(
            LocalDateTime.now().minusDays(days), 
            LocalDateTime.now()
        );
        return report.getFalseVetoRate();
    }
    
    /**
     * Check if veto thresholds should be adjusted based on recent performance
     */
    public Optional<Double> suggestThresholdAdjustment() {
        VetoAnalyticsReport report = generateAnalyticsReport(
            LocalDateTime.now().minusDays(ANALYSIS_DAYS),
            LocalDateTime.now()
        );
        
        if (report.getAnalyzedVetoes() < 10) {
            return Optional.empty(); // Not enough data
        }
        
        double currentThreshold = 70.0;
        double suggested = report.getOptimalThresholdSuggestion();
        
        // Only suggest if difference is significant (>5 points)
        if (Math.abs(suggested - currentThreshold) > 5) {
            return Optional.of(suggested);
        }
        
        return Optional.empty();
    }
}
