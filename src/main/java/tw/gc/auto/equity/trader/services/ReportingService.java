package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.AppConstants;
import tw.gc.auto.equity.trader.ContractScalingService;
import tw.gc.auto.equity.trader.RiskManagementService;
import tw.gc.auto.equity.trader.StockSettingsService;
import tw.gc.auto.equity.trader.TelegramService;
import tw.gc.auto.equity.trader.entities.DailyStatistics;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportingService {

    private final EndOfDayStatisticsService endOfDayStatisticsService;
    private final DailyStatisticsRepository dailyStatisticsRepository;
    private final LlmService llmService;
    private final RiskManagementService riskManagementService;
    private final ContractScalingService contractScalingService;
    private final StockSettingsService stockSettingsService;
    private final TelegramService telegramService;
    private final TradingStateService tradingStateService;
    private final StrategyManager strategyManager;

    /**
     * Calculate daily statistics at 13:30 (30 minutes after market close).
     * JUSTIFICATION: Generates comprehensive daily reports for shadow-mode strategies.
     * Runs 30 minutes after close to allow EndOfDayStatisticsService to complete first.
     */
    @Scheduled(cron = "0 30 13 * * MON-FRI", zone = AppConstants.SCHEDULER_TIMEZONE)
    public void calculateDailyStatistics() {
        log.info("📊 Calculating end-of-day statistics...");
        try {
            LocalDate today = LocalDate.now(AppConstants.TAIPEI_ZONE);
            String symbol = getActiveSymbol();
            endOfDayStatisticsService.calculateAndSaveStatisticsForDay(today, symbol);
            
            // Also calculate for all active strategies
            for (IStrategy strategy : strategyManager.getActiveStrategies()) {
                // In a real system, we'd track per-strategy stats separately
                // For now, the main stats cover the aggregate account performance
            }
            
            // Generate LLM Insight
            generateDailyInsight(today, symbol);
            
            sendDailySummary();
        } catch (Exception e) {
            log.error("❌ Failed to calculate daily statistics", e);
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
            stats.setInsightGeneratedAt(LocalDateTime.now(AppConstants.TAIPEI_ZONE));
            dailyStatisticsRepository.save(stats);
            log.info("🧠 Generated LLM Insight: {}", insight);
        } catch (Exception e) {
            log.error("Failed to generate LLM insight", e);
        }
    }

    public void sendDailySummary() {
        double pnl = riskManagementService.getDailyPnL();
        String status = pnl > 0 ? "💰 Profitable" : "📉 Loss";
        String comment = "";
        
        // Fetch insight if available
        String insight = "";
        try {
            LocalDate today = LocalDate.now(AppConstants.TAIPEI_ZONE);
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
            comment = "\\n🚀 EXCEPTIONAL DAY! Let winners run!";
        } else if (pnl > 2000) {
            comment = "\\n🎯 Great performance!";
        } else if (pnl > 1000) {
            comment = "\\n✅ Solid day!";
        }
        
        String tradingMode = tradingStateService.getTradingMode();
        String modeInfo = "stock".equals(tradingMode) 
            ? String.format("Mode: STOCK\\nShares: %d (base %d +%d/20k)",
                stockSettingsService.getBaseStockQuantity(contractScalingService.getLastEquity()),
                stockSettingsService.getSettings().getShares(),
                stockSettingsService.getSettings().getShareIncrement())
            : String.format("Mode: FUTURES\\nContracts: %d", contractScalingService.getMaxContracts());
        
        telegramService.sendMessage(String.format(
                "📊 DAILY SUMMARY\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "%s\n" +
                "Today P&L: %.0f TWD\n" +
                "Week P&L: %.0f TWD\n" +
                "Equity: %.0f TWD\n" +
                "Status: %s%s\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "🚀 NO PROFIT CAPS - Unlimited upside!",
                modeInfo, pnl, riskManagementService.getWeeklyPnL(),
                contractScalingService.getLastEquity(), status, comment, insight));
    }

    private String getActiveSymbol() {
        return "stock".equals(tradingStateService.getTradingMode()) ? "2454.TW" : "AUTO_EQUITY_TRADER";
    }
}
