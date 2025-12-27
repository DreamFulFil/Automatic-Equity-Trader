package tw.gc.mtxfbot.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.repositories.EventRepository;
import tw.gc.mtxfbot.repositories.SignalRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DataAnalysisService - Provides analytics and metrics for backtesting and performance analysis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataAnalysisService {

    private final TradeRepository tradeRepository;
    private final SignalRepository signalRepository;
    private final EventRepository eventRepository;

    /**
     * Get trading performance metrics for a time period
     */
    public Map<String, Object> getTradingMetrics(Trade.TradingMode mode, LocalDateTime since) {
        Map<String, Object> metrics = new HashMap<>();

        long totalTrades = tradeRepository.countTotalTrades(mode, Trade.TradeStatus.CLOSED);
        long winningTrades = tradeRepository.countWinningTrades(mode, Trade.TradeStatus.CLOSED);
        double totalPnL = tradeRepository.sumPnLSince(mode, since);
        Double maxDrawdown = tradeRepository.maxDrawdownSince(mode, since);

        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;

        metrics.put("totalTrades", totalTrades);
        metrics.put("winningTrades", winningTrades);
        metrics.put("winRate", winRate);
        metrics.put("totalPnL", totalPnL);
        metrics.put("maxDrawdown", maxDrawdown != null ? maxDrawdown : 0.0);

        // Calculate Sharpe ratio (simplified - would need daily returns)
        metrics.put("sharpeRatio", calculateSharpeRatio(totalPnL, totalTrades));

        return metrics;
    }

    /**
     * Get signal quality metrics
     */
    public Map<String, Object> getSignalMetrics(LocalDateTime since) {
        Map<String, Object> metrics = new HashMap<>();

        long longSignals = signalRepository.countLongSignalsSince(since);
        long shortSignals = signalRepository.countShortSignalsSince(since);
        Double avgConfidence = signalRepository.averageConfidenceSince(since);

        metrics.put("longSignals", longSignals);
        metrics.put("shortSignals", shortSignals);
        metrics.put("totalSignals", longSignals + shortSignals);
        metrics.put("averageConfidence", avgConfidence != null ? avgConfidence : 0.0);

        return metrics;
    }

    /**
     * Get system health metrics
     */
    public Map<String, Object> getSystemHealthMetrics(LocalDateTime since) {
        Map<String, Object> metrics = new HashMap<>();

        long errors = eventRepository.countErrorsSince(since);
        long slowApiCalls = eventRepository.countSlowApiCallsSince(5000, since); // >5s
        long telegramCommands = eventRepository.countTelegramCommandsSince(since);
        long newsVetos = eventRepository.countNewsVetosSince(since);

        metrics.put("errorCount", errors);
        metrics.put("slowApiCalls", slowApiCalls);
        metrics.put("telegramCommands", telegramCommands);
        metrics.put("newsVetos", newsVetos);

        return metrics;
    }

    /**
     * Get profit streaks analysis
     */
    public Map<String, Object> getProfitStreaks(Trade.TradingMode mode, LocalDateTime since) {
        Map<String, Object> streaks = new HashMap<>();

        var trades = tradeRepository.findByModeAndTimestampBetween(mode, since, LocalDateTime.now());

        int currentStreak = 0;
        int longestWinStreak = 0;
        int longestLossStreak = 0;
        int tempWinStreak = 0;
        int tempLossStreak = 0;

        for (Trade trade : trades) {
            if (trade.getRealizedPnL() != null) {
                boolean isWin = trade.getRealizedPnL() > 0;

                if (isWin) {
                    tempWinStreak++;
                    tempLossStreak = 0;
                    longestWinStreak = Math.max(longestWinStreak, tempWinStreak);
                    currentStreak = tempWinStreak;
                } else {
                    tempLossStreak++;
                    tempWinStreak = 0;
                    longestLossStreak = Math.max(longestLossStreak, tempLossStreak);
                    currentStreak = -tempLossStreak;
                }
            }
        }

        streaks.put("currentStreak", currentStreak);
        streaks.put("longestWinStreak", longestWinStreak);
        streaks.put("longestLossStreak", longestLossStreak);

        return streaks;
    }

    /**
     * Simplified Sharpe ratio calculation
     * In reality, this should use daily returns over a period
     */
    private double calculateSharpeRatio(double totalPnL, long totalTrades) {
        if (totalTrades == 0) return 0.0;

        // Simplified: assume 252 trading days per year
        double annualReturn = (totalPnL / 100000) * 252; // Assuming $100k starting capital
        double volatility = Math.sqrt(totalTrades) * 0.02; // Simplified volatility estimate

        return volatility > 0 ? annualReturn / volatility : 0.0;
    }

    /**
     * Get comprehensive dashboard data
     */
    public Map<String, Object> getDashboardData(Trade.TradingMode mode, LocalDateTime since) {
        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("tradingMetrics", getTradingMetrics(mode, since));
        dashboard.put("signalMetrics", getSignalMetrics(since));
        dashboard.put("systemHealth", getSystemHealthMetrics(since));
        dashboard.put("profitStreaks", getProfitStreaks(mode, since));

        return dashboard;
    }
}