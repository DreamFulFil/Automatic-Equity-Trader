package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.DailyStatistics;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ReportingService - Generates daily and weekly performance reports.
 * 
 * Migrated from Python scripts to native Java for direct DB access via JPA/Hibernate.
 * Daily Report: Scheduled for Taiwan market close (13:30 + 30min = 14:00).
 * Weekly Report: Scheduled for the close of the final trading day of the week (Friday 14:00).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportingService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");

    private final EndOfDayStatisticsService endOfDayStatisticsService;
    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final StrategyPerformanceRepository strategyPerformanceRepository;
    private final StrategyStockMappingRepository strategyStockMappingRepository;
    private final LlmService llmService;
    private final RiskManagementService riskManagementService;
    private final ContractScalingService contractScalingService;
    private final StockSettingsService stockSettingsService;
    private final TelegramService telegramService;
    private final TradingStateService tradingStateService;
    private final StrategyManager strategyManager;
    private final ActiveStockService activeStockService;
    private final ActiveStrategyService activeStrategyService;

    /**
     * Daily Report - Scheduled for Taiwan market close (13:30 + 30min = 14:00).
     * Generates comprehensive daily performance report with main vs shadow strategy comparison.
     */
    @Scheduled(cron = "0 0 14 * * MON-FRI", zone = "Asia/Taipei")
    public void calculateDailyStatistics() {
        log.info("📊 Calculating end-of-day statistics...");
        try {
            LocalDate today = LocalDate.now(TAIPEI_ZONE);
            String symbol = getActiveSymbol();
            endOfDayStatisticsService.calculateAndSaveStatisticsForDay(today, symbol);
            
            // Also calculate for all active strategies
            for (IStrategy strategy : strategyManager.getActiveStrategies()) {
                // In a real system, we'd track per-strategy stats separately
                // For now, the main stats cover the aggregate account performance
            }
            
            // Generate LLM Insight
            generateDailyInsight(today, symbol);
            
            // Send daily summary with shadow comparison
            sendDailyPerformanceReport();
        } catch (Exception e) {
            log.error("❌ Failed to calculate daily statistics", e);
        }
    }

    /**
     * Weekly Report - Scheduled for Friday market close (14:00).
     * Generates 7-day trend analysis with consistency scoring.
     */
    @Scheduled(cron = "0 0 14 * * FRI", zone = "Asia/Taipei")
    public void generateWeeklyReport() {
        log.info("📅 Generating weekly performance report...");
        try {
            sendWeeklyPerformanceReport();
        } catch (Exception e) {
            log.error("❌ Failed to generate weekly report", e);
        }
    }
    
    public void generateDailyInsight(LocalDate date, String symbol) {
        try {
            DailyStatistics stats = dailyStatisticsRepository.findByTradeDateAndSymbol(date, symbol)
                .orElseThrow(() -> new RuntimeException("No stats found for " + date));
                
            String prompt = String.format(
                "Analyze these trading statistics for %s on %s:\n" +
                "PnL: %.0f\nTrades: %d\nWin Rate: %.1f%%\n" +
                "Provide a 1-sentence insight on performance and a 1-sentence recommendation.",
                symbol, date, stats.getTotalPnL(), stats.getTotalTrades(), stats.getWinRate());
                
            String insight = llmService.generateInsight(prompt);
            stats.setLlamaInsight(insight);
            stats.setInsightGeneratedAt(LocalDateTime.now(TAIPEI_ZONE));
            dailyStatisticsRepository.save(stats);
            log.info("🧠 Generated LLM Insight: {}", insight);
        } catch (Exception e) {
            log.error("Failed to generate LLM insight", e);
        }
    }

    /**
     * Daily Performance Report - Main vs Shadow comparison with recommendations.
     * Migrated from Python daily_performance_report.py
     */
    public void sendDailyPerformanceReport() {
        double pnl = riskManagementService.getDailyPnL();
        String status = pnl > 0 ? "💰 Profitable" : "📉 Loss";
        String comment = "";
        
        // Fetch insight if available
        String insight = "";
        try {
            LocalDate today = LocalDate.now(TAIPEI_ZONE);
            String symbol = getActiveSymbol();
            insight = dailyStatisticsRepository.findByTradeDateAndSymbol(today, symbol)
                .map(DailyStatistics::getLlamaInsight)
                .orElse("");
            if (!insight.isEmpty()) {
                insight = "\n\n🧠 AI Insight:\n" + insight;
            }
        } catch (Exception e) {
            // ignore
        }
        
        if (pnl > 3000) {
            comment = "\n🚀 EXCEPTIONAL DAY! Let winners run!";
        } else if (pnl > 2000) {
            comment = "\n🎯 Great performance!";
        } else if (pnl > 1000) {
            comment = "\n✅ Solid day!";
        }
        
        String tradingMode = tradingStateService.getTradingMode();
        String modeInfo = "stock".equals(tradingMode) 
            ? String.format("Mode: STOCK\nShares: %d (base %d +%d/20k)",
                stockSettingsService.getBaseStockQuantity(contractScalingService.getLastEquity()),
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Mode: FUTURES\nContracts: %d", contractScalingService.getMaxContracts());

        // Get main strategy performance
        String mainStrategy = activeStrategyService.getActiveStrategyName();
        String currentStock = activeStockService.getActiveStock();

        // Get shadow performances for comparison
        List<StrategyPerformance> shadowPerfs = getShadowPerformances(24);
        String shadowComparison = formatShadowComparison(shadowPerfs);

        // Generate recommendations
        String recommendations = generateDailyRecommendations(mainStrategy, currentStock, shadowPerfs);
        
        telegramService.sendMessage(String.format(
                "📊 DAILY PERFORMANCE REPORT\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📈 Main: %s + %s\n" +
                "%s\n" +
                "Today P&L: %.0f TWD\n" +
                "Week P&L: %.0f TWD\n" +
                "Equity: %.0f TWD\n" +
                "Status: %s%s%s\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "%s\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "💡 RECOMMENDATIONS\n%s\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━",
                currentStock, mainStrategy,
                modeInfo, pnl, riskManagementService.getWeeklyPnL(),
                contractScalingService.getLastEquity(), status, comment, insight,
                shadowComparison, recommendations));
    }

    /**
     * Weekly Performance Report - 7-day trend analysis with consistency scoring.
     * Migrated from Python weekly_performance_report.py
     */
    public void sendWeeklyPerformanceReport() {
        LocalDateTime weekStart = LocalDateTime.now(TAIPEI_ZONE)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekEnd = LocalDateTime.now(TAIPEI_ZONE);

        // Get main strategy weekly aggregate
        String mainStrategy = activeStrategyService.getActiveStrategyName();
        String currentStock = activeStockService.getActiveStock();

        // Get weekly performances
        List<StrategyPerformance> mainPerfs = getMainPerformances(7);
        List<StrategyPerformance> shadowPerfs = getShadowPerformances(7 * 24);

        // Calculate weekly aggregates
        WeeklyAggregate mainAggregate = calculateWeeklyAggregate(mainPerfs, mainStrategy, currentStock);
        List<WeeklyAggregate> shadowAggregates = aggregateShadowPerformances(shadowPerfs);

        // Calculate trends
        String mainTrend = calculateTrend(mainPerfs);

        // Generate weekly recommendations
        String recommendations = generateWeeklyRecommendations(mainAggregate, shadowAggregates);

        StringBuilder report = new StringBuilder();
        report.append("📅 WEEKLY PERFORMANCE REPORT\n");
        report.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
        report.append(String.format("Period: %s to %s\n\n", 
            weekStart.toLocalDate(), weekEnd.toLocalDate()));

        // Main strategy section
        report.append("📈 MAIN STRATEGY\n");
        if (mainAggregate != null) {
            report.append(String.format("Strategy: %s\nSymbol: %s\n", mainAggregate.strategyName, mainAggregate.symbol));
            report.append(String.format("Avg Return: %.2f%%\nAvg Sharpe: %.2f\n", mainAggregate.avgReturnPct, mainAggregate.avgSharpeRatio));
            report.append(String.format("Worst DD: %.2f%%\nWin Rate: %.1f%%\n", mainAggregate.worstDrawdownPct, mainAggregate.avgWinRatePct));
            report.append(String.format("Consistency: %.2f\nTrend: %s\n", mainAggregate.consistencyScore, mainTrend));
        } else {
            report.append("No main strategy data for this week\n");
        }

        // Top shadow strategies
        report.append("\n👥 TOP 5 SHADOW STRATEGIES\n");
        if (!shadowAggregates.isEmpty()) {
            List<WeeklyAggregate> top5 = shadowAggregates.stream()
                .sorted((a, b) -> Double.compare(b.avgSharpeRatio * b.consistencyScore, a.avgSharpeRatio * a.consistencyScore))
                .limit(5)
                .toList();
            for (int i = 0; i < top5.size(); i++) {
                WeeklyAggregate agg = top5.get(i);
                report.append(String.format("%d. %s + %s (Sharpe: %.2f, Cons: %.2f)\n",
                    i + 1, agg.symbol, agg.strategyName, agg.avgSharpeRatio, agg.consistencyScore));
            }
        } else {
            report.append("No shadow data available\n");
        }

        report.append("\n━━━━━━━━━━━━━━━━━━━━━━━━\n");
        report.append("💡 WEEKLY RECOMMENDATIONS\n");
        report.append(recommendations);
        report.append("\n━━━━━━━━━━━━━━━━━━━━━━━━");

        telegramService.sendMessage(report.toString());
    }

    // Legacy method for backward compatibility
    public void sendDailySummary() {
        sendDailyPerformanceReport();
    }

    private List<StrategyPerformance> getMainPerformances(int days) {
        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusDays(days);
        return strategyPerformanceRepository.findAll().stream()
            .filter(p -> p.getPerformanceMode() == StrategyPerformance.PerformanceMode.MAIN)
            .filter(p -> p.getPeriodEnd() != null && p.getPeriodEnd().isAfter(since))
            .toList();
    }

    private List<StrategyPerformance> getShadowPerformances(int hours) {
        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusHours(hours);
        return strategyPerformanceRepository.findAll().stream()
            .filter(p -> p.getPerformanceMode() == StrategyPerformance.PerformanceMode.SHADOW)
            .filter(p -> p.getCalculatedAt() != null && p.getCalculatedAt().isAfter(since))
            .sorted((a, b) -> {
                Double sharpeA = a.getSharpeRatio() != null ? a.getSharpeRatio() : 0.0;
                Double sharpeB = b.getSharpeRatio() != null ? b.getSharpeRatio() : 0.0;
                return Double.compare(sharpeB, sharpeA);
            })
            .toList();
    }

    private String formatShadowComparison(List<StrategyPerformance> shadowPerfs) {
        if (shadowPerfs.isEmpty()) {
            return "👥 No shadow data available";
        }
        StringBuilder sb = new StringBuilder("👥 TOP 5 SHADOW:\n");
        int count = 0;
        for (StrategyPerformance perf : shadowPerfs) {
            if (count >= 5) break;
            sb.append(String.format("%d. %s (Sharpe: %.2f, Ret: %.1f%%)\n",
                count + 1, perf.getStrategyName(),
                perf.getSharpeRatio() != null ? perf.getSharpeRatio() : 0.0,
                perf.getTotalReturnPct() != null ? perf.getTotalReturnPct() : 0.0));
            count++;
        }
        return sb.toString();
    }

    private String generateDailyRecommendations(String mainStrategy, String currentStock, List<StrategyPerformance> shadowPerfs) {
        List<String> recs = new ArrayList<>();
        
        if (shadowPerfs.isEmpty()) {
            return "⚠️ Enable shadow trading for better insights";
        }

        StrategyPerformance bestShadow = shadowPerfs.get(0);
        double bestSharpe = bestShadow.getSharpeRatio() != null ? bestShadow.getSharpeRatio() : 0.0;

        // Find current main strategy performance
        Optional<StrategyPerformance> mainPerf = shadowPerfs.stream()
            .filter(p -> mainStrategy.equals(p.getStrategyName()))
            .findFirst();
        double mainSharpe = mainPerf.map(p -> p.getSharpeRatio() != null ? p.getSharpeRatio() : 0.0).orElse(0.0);

        if (bestSharpe > mainSharpe + 0.3 && !bestShadow.getStrategyName().equals(mainStrategy)) {
            recs.add(String.format("📊 Consider: /set-main-strategy %s (Sharpe +%.2f)", 
                bestShadow.getStrategyName(), bestSharpe - mainSharpe));
        }

        if (bestShadow.getSymbol() != null && !bestShadow.getSymbol().equals(currentStock) && bestSharpe > mainSharpe + 0.5) {
            recs.add(String.format("🔄 Consider: /change-stock %s", bestShadow.getSymbol()));
        }

        if (recs.isEmpty()) {
            recs.add("✅ Current configuration performing well");
        }

        return String.join("\n", recs);
    }

    private String generateWeeklyRecommendations(WeeklyAggregate mainAgg, List<WeeklyAggregate> shadowAggs) {
        List<String> recs = new ArrayList<>();

        if (shadowAggs.isEmpty()) {
            return "⚠️ No shadow data for recommendations";
        }

        WeeklyAggregate bestShadow = shadowAggs.stream()
            .filter(a -> a.consistencyScore > 0.6)
            .max(Comparator.comparingDouble(a -> a.avgSharpeRatio * a.consistencyScore))
            .orElse(null);

        if (bestShadow != null && mainAgg != null) {
            double sharpeImprovement = bestShadow.avgSharpeRatio - mainAgg.avgSharpeRatio;
            if (sharpeImprovement > 0.3) {
                recs.add(String.format("🎯 STRATEGY: /set-main-strategy %s (+%.2f Sharpe)", 
                    bestShadow.strategyName, sharpeImprovement));
            }
            if (!bestShadow.symbol.equals(mainAgg.symbol) && sharpeImprovement > 0.3) {
                recs.add(String.format("🔄 STOCK: /change-stock %s", bestShadow.symbol));
            }
            if (mainAgg.consistencyScore < 0.4) {
                recs.add("⚠️ HIGH VARIANCE: Consider more stable strategy");
            }
        }

        if (recs.isEmpty()) {
            recs.add("✅ Continue with current configuration");
        }

        return String.join("\n", recs);
    }

    private WeeklyAggregate calculateWeeklyAggregate(List<StrategyPerformance> perfs, String strategyName, String symbol) {
        if (perfs.isEmpty()) return null;

        double avgReturn = perfs.stream()
            .filter(p -> p.getTotalReturnPct() != null)
            .mapToDouble(StrategyPerformance::getTotalReturnPct)
            .average().orElse(0.0);

        double avgSharpe = perfs.stream()
            .filter(p -> p.getSharpeRatio() != null)
            .mapToDouble(StrategyPerformance::getSharpeRatio)
            .average().orElse(0.0);

        double worstDD = perfs.stream()
            .filter(p -> p.getMaxDrawdownPct() != null)
            .mapToDouble(StrategyPerformance::getMaxDrawdownPct)
            .min().orElse(0.0);

        double avgWinRate = perfs.stream()
            .filter(p -> p.getWinRatePct() != null)
            .mapToDouble(StrategyPerformance::getWinRatePct)
            .average().orElse(0.0);

        double consistency = calculateConsistencyScore(perfs);

        return new WeeklyAggregate(strategyName, symbol, avgReturn, avgSharpe, worstDD, avgWinRate, consistency, perfs.size());
    }

    private List<WeeklyAggregate> aggregateShadowPerformances(List<StrategyPerformance> shadowPerfs) {
        Map<String, List<StrategyPerformance>> grouped = shadowPerfs.stream()
            .collect(Collectors.groupingBy(p -> p.getStrategyName() + "|" + (p.getSymbol() != null ? p.getSymbol() : "")));

        List<WeeklyAggregate> aggregates = new ArrayList<>();
        for (Map.Entry<String, List<StrategyPerformance>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String strategyName = parts[0];
            String symbol = parts.length > 1 ? parts[1] : "";
            WeeklyAggregate agg = calculateWeeklyAggregate(entry.getValue(), strategyName, symbol);
            if (agg != null) {
                aggregates.add(agg);
            }
        }
        return aggregates;
    }

    private double calculateConsistencyScore(List<StrategyPerformance> perfs) {
        if (perfs.size() < 2) return 0.0;

        List<Double> returns = perfs.stream()
            .filter(p -> p.getTotalReturnPct() != null)
            .map(StrategyPerformance::getTotalReturnPct)
            .toList();

        if (returns.isEmpty()) return 0.0;

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
            .mapToDouble(r -> Math.pow(r - mean, 2))
            .average().orElse(0.0);

        // Lower variance = higher consistency
        return 1.0 / (1.0 + variance / 100.0);
    }

    private String calculateTrend(List<StrategyPerformance> perfs) {
        if (perfs.size() < 3) return "INSUFFICIENT_DATA";

        List<StrategyPerformance> sorted = perfs.stream()
            .filter(p -> p.getPeriodEnd() != null)
            .sorted(Comparator.comparing(StrategyPerformance::getPeriodEnd))
            .toList();

        int mid = sorted.size() / 2;
        double firstHalf = sorted.subList(0, mid).stream()
            .filter(p -> p.getSharpeRatio() != null)
            .mapToDouble(StrategyPerformance::getSharpeRatio)
            .average().orElse(0.0);
        double secondHalf = sorted.subList(mid, sorted.size()).stream()
            .filter(p -> p.getSharpeRatio() != null)
            .mapToDouble(StrategyPerformance::getSharpeRatio)
            .average().orElse(0.0);

        double diff = secondHalf - firstHalf;
        if (diff > 0.2) return "IMPROVING ⬆️";
        if (diff < -0.2) return "DECLINING ⬇️";
        return "STABLE ➡️";
    }

    private String getActiveSymbol() {
        return activeStockService.getActiveSymbol(tradingStateService.getTradingMode());
    }

    // Inner class for weekly aggregation
    private static class WeeklyAggregate {
        String strategyName;
        String symbol;
        double avgReturnPct;
        double avgSharpeRatio;
        double worstDrawdownPct;
        double avgWinRatePct;
        double consistencyScore;
        int dataPoints;

        WeeklyAggregate(String strategyName, String symbol, double avgReturnPct, double avgSharpeRatio,
                       double worstDrawdownPct, double avgWinRatePct, double consistencyScore, int dataPoints) {
            this.strategyName = strategyName;
            this.symbol = symbol;
            this.avgReturnPct = avgReturnPct;
            this.avgSharpeRatio = avgSharpeRatio;
            this.worstDrawdownPct = worstDrawdownPct;
            this.avgWinRatePct = avgWinRatePct;
            this.consistencyScore = consistencyScore;
            this.dataPoints = dataPoints;
        }
    }
}
