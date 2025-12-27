package tw.gc.mtxfbot.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.entities.*;
import tw.gc.mtxfbot.repositories.*;
import tw.gc.mtxfbot.AppConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EndOfDayStatisticsService - Calculates and stores daily trading statistics.
 * Runs automatically at 13:05 Taiwan time (after trading window closes).
 * Generates AI insights from collected data via Ollama/Llama.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EndOfDayStatisticsService {

    private static final ZoneId TAIPEI_ZONE = AppConstants.TAIPEI_ZONE;

    @NonNull
    private final TradeRepository tradeRepository;
    @NonNull
    private final SignalRepository signalRepository;
    @NonNull
    private final EventRepository eventRepository;
    @NonNull
    private final DailyStatisticsRepository dailyStatisticsRepository;
    @NonNull
    private final MarketDataRepository marketDataRepository;
    @NonNull
    private final RestTemplate restTemplate;

    /**
     * Calculate and store end-of-day statistics.
     * Triggered at 13:05 Taiwan time (5 minutes after market close).
     */
    @Scheduled(cron = "0 5 13 * * MON-FRI", zone = AppConstants.SCHEDULER_TIMEZONE)
    @Transactional
    public void calculateEndOfDayStatistics() {
        log.info("📊 Calculating end-of-day statistics...");

        LocalDate today = LocalDate.now(TAIPEI_ZONE);
        String symbol = System.getProperty("trading.mode", "stock").equals("stock") ? "2454.TW" : "MTXF";

        try {
            DailyStatistics stats = calculateStatisticsForDay(today, symbol);
            dailyStatisticsRepository.save(stats);
            log.info("✅ Daily statistics saved for {} on {}", symbol, today);

            // Generate AI insight asynchronously
            generateInsightAsync(stats);

        } catch (Exception e) {
            log.error("❌ Failed to calculate daily statistics", e);
        }
    }

    /**
     * Calculate and save statistics for a specific day and symbol.
     */
    @Transactional
    public DailyStatistics calculateAndSaveStatisticsForDay(LocalDate date, String symbol) {
        DailyStatistics stats = calculateStatisticsForDay(date, symbol);
        if (stats != null) {
            dailyStatisticsRepository.save(stats);
            log.info("✅ Daily statistics saved for {} on {}", symbol, date);
        }
        return stats;
    }

    DailyStatistics calculateStatisticsForDay(LocalDate date, String symbol) {
        LocalDateTime dayStart = date.atTime(LocalTime.of(11, 30));
        LocalDateTime dayEnd = date.atTime(LocalTime.of(13, 0));

        // Get trades for the day
        List<Trade> trades = tradeRepository.findByTimestampBetween(dayStart, dayEnd)
                .stream()
                .filter(t -> symbol.equals(t.getSymbol()))
                .collect(Collectors.toList());

        // Get signals for the day
        List<Signal> signals = signalRepository.findByTimestampBetweenOrderByTimestampDesc(dayStart, dayEnd)
                .stream()
                .filter(s -> symbol.equals(s.getSymbol()))
                .collect(Collectors.toList());

        // Calculate trade statistics
        List<Trade> closedTrades = trades.stream()
                .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED)
                .collect(Collectors.toList());

        int totalTrades = closedTrades.size();
        int winningTrades = (int) closedTrades.stream()
                .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0)
                .count();
        int losingTrades = totalTrades - winningTrades;
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades * 100 : 0;

        double realizedPnL = closedTrades.stream()
                .filter(t -> t.getRealizedPnL() != null)
                .mapToDouble(Trade::getRealizedPnL)
                .sum();

        double grossProfit = closedTrades.stream()
                .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0)
                .mapToDouble(Trade::getRealizedPnL)
                .sum();

        double grossLoss = Math.abs(closedTrades.stream()
                .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() < 0)
                .mapToDouble(Trade::getRealizedPnL)
                .sum());

        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? Double.MAX_VALUE : 0);

        double avgTradePnL = totalTrades > 0 ? realizedPnL / totalTrades : 0;

        double avgWinnerPnL = winningTrades > 0 ? 
                closedTrades.stream()
                        .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0)
                        .mapToDouble(Trade::getRealizedPnL)
                        .average()
                        .orElse(0) : 0;

        double avgLoserPnL = losingTrades > 0 ?
                closedTrades.stream()
                        .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() < 0)
                        .mapToDouble(Trade::getRealizedPnL)
                        .average()
                        .orElse(0) : 0;

        // Calculate max drawdown (simplified - largest single trade loss)
        double maxDrawdown = closedTrades.stream()
                .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() < 0)
                .mapToDouble(t -> Math.abs(t.getRealizedPnL()))
                .max()
                .orElse(0);

        double maxProfit = closedTrades.stream()
                .filter(t -> t.getRealizedPnL() != null && t.getRealizedPnL() > 0)
                .mapToDouble(Trade::getRealizedPnL)
                .max()
                .orElse(0);

        // Calculate hold time statistics
        double avgHoldMinutes = closedTrades.stream()
                .filter(t -> t.getHoldDurationMinutes() != null)
                .mapToInt(Trade::getHoldDurationMinutes)
                .average()
                .orElse(0);

        int maxHoldMinutes = closedTrades.stream()
                .filter(t -> t.getHoldDurationMinutes() != null)
                .mapToInt(Trade::getHoldDurationMinutes)
                .max()
                .orElse(0);

        int minHoldMinutes = closedTrades.stream()
                .filter(t -> t.getHoldDurationMinutes() != null)
                .mapToInt(Trade::getHoldDurationMinutes)
                .min()
                .orElse(0);

        int timeInMarket = closedTrades.stream()
                .filter(t -> t.getHoldDurationMinutes() != null)
                .mapToInt(Trade::getHoldDurationMinutes)
                .sum();

        // Calculate signal statistics
        int signalsGenerated = signals.size();
        int signalsLong = (int) signals.stream()
                .filter(s -> s.getDirection() == Signal.SignalDirection.LONG)
                .count();
        int signalsShort = (int) signals.stream()
                .filter(s -> s.getDirection() == Signal.SignalDirection.SHORT)
                .count();
        int signalsActedOn = totalTrades;

        double avgSignalConfidence = signals.stream()
                .mapToDouble(Signal::getConfidence)
                .average()
                .orElse(0);

        int newsVetoCount = (int) signals.stream()
                .filter(s -> Boolean.TRUE.equals(s.getNewsVeto()))
                .count();

        // Get cumulative statistics
        Double cumulativePnL = dailyStatisticsRepository.sumPnLSince(symbol, date.minusDays(365));
        if (cumulativePnL == null) cumulativePnL = 0.0;
        cumulativePnL += realizedPnL;

        Long cumulativeTradesLong = dailyStatisticsRepository.sumTradesSince(symbol, date.minusDays(365));
        int cumulativeTrades = (cumulativeTradesLong != null ? cumulativeTradesLong.intValue() : 0) + totalTrades;

        // Calculate streaks
        List<DailyStatistics> recentDays = dailyStatisticsRepository.findRecentBySymbol(symbol, 30);
        int consecutiveWins = calculateConsecutiveWins(recentDays, realizedPnL > 0);
        int consecutiveLosses = calculateConsecutiveLosses(recentDays, realizedPnL < 0);

        // Get trading mode
        Trade.TradingMode tradingMode = closedTrades.isEmpty() ? Trade.TradingMode.SIMULATION :
                closedTrades.get(0).getMode();

        return DailyStatistics.builder()
                .tradeDate(date)
                .calculatedAt(LocalDateTime.now(TAIPEI_ZONE))
                .symbol(symbol)
                .totalTrades(totalTrades)
                .winningTrades(winningTrades)
                .losingTrades(losingTrades)
                .winRate(winRate)
                .realizedPnL(realizedPnL)
                .totalPnL(realizedPnL)
                .maxDrawdown(maxDrawdown)
                .maxProfit(maxProfit)
                .avgTradePnL(avgTradePnL)
                .avgWinnerPnL(avgWinnerPnL)
                .avgLoserPnL(avgLoserPnL)
                .profitFactor(profitFactor)
                .signalsGenerated(signalsGenerated)
                .signalsLong(signalsLong)
                .signalsShort(signalsShort)
                .signalsActedOn(signalsActedOn)
                .avgSignalConfidence(avgSignalConfidence)
                .newsVetoCount(newsVetoCount)
                .avgHoldMinutes(avgHoldMinutes)
                .maxHoldMinutes(maxHoldMinutes)
                .minHoldMinutes(minHoldMinutes)
                .timeInMarketMinutes(timeInMarket)
                .cumulativePnL(cumulativePnL)
                .cumulativeTrades(cumulativeTrades)
                .consecutiveWins(consecutiveWins)
                .consecutiveLosses(consecutiveLosses)
                .tradingMode(tradingMode)
                .build();
    }

    private int calculateConsecutiveWins(List<DailyStatistics> recentDays, boolean todayIsWin) {
        if (!todayIsWin) return 0;
        int streak = 1;
        for (DailyStatistics day : recentDays) {
            if (day.getRealizedPnL() != null && day.getRealizedPnL() > 0) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateConsecutiveLosses(List<DailyStatistics> recentDays, boolean todayIsLoss) {
        if (!todayIsLoss) return 0;
        int streak = 1;
        for (DailyStatistics day : recentDays) {
            if (day.getRealizedPnL() != null && day.getRealizedPnL() < 0) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Generate AI insight for daily statistics using Ollama/Llama.
     */
    private void generateInsightAsync(DailyStatistics stats) {
        try {
            String prompt = buildInsightPrompt(stats);

            Map<String, Object> request = Map.of(
                    "model", "llama3.1:8b-instruct-q5_K_M",
                    "prompt", prompt,
                    "stream", false,
                    "options", Map.of("temperature", 0.5)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "http://localhost:11434/api/generate",
                    request,
                    Map.class
            );

            if (response != null && response.containsKey("response")) {
                String insight = (String) response.get("response");
                stats.setLlamaInsight(insight.trim());
                stats.setInsightGeneratedAt(LocalDateTime.now(TAIPEI_ZONE));
                dailyStatisticsRepository.save(stats);
                log.info("✨ AI insight generated for {}", stats.getTradeDate());
            }

        } catch (Exception e) {
            log.warn("⚠️ Failed to generate AI insight: {}", e.getMessage());
        }
    }

    private String buildInsightPrompt(DailyStatistics stats) {
        return String.format("""
            You are a professional trading analyst. Analyze today's trading performance and provide a brief, actionable insight.
            
            Trading Statistics for %s:
            - Symbol: %s
            - Total Trades: %d (Wins: %d, Losses: %d)
            - Win Rate: %.1f%%
            - Total P&L: %.0f TWD
            - Max Drawdown: %.0f TWD
            - Profit Factor: %.2f
            - Avg Hold Time: %.1f minutes
            - Signals Generated: %d (Long: %d, Short: %d)
            - News Vetos: %d
            - Cumulative P&L: %.0f TWD
            - Consecutive Wins: %d
            - Consecutive Losses: %d
            
            Provide a 2-3 sentence insight focusing on:
            1. Performance assessment (good/bad/neutral day)
            2. Key observation (what worked or didn't)
            3. One actionable suggestion for tomorrow
            
            Be concise and professional. Use numbers when relevant.
            """,
                stats.getTradeDate(),
                stats.getSymbol(),
                stats.getTotalTrades(),
                stats.getWinningTrades(),
                stats.getLosingTrades(),
                stats.getWinRate(),
                stats.getRealizedPnL(),
                stats.getMaxDrawdown(),
                stats.getProfitFactor(),
                stats.getAvgHoldMinutes(),
                stats.getSignalsGenerated(),
                stats.getSignalsLong(),
                stats.getSignalsShort(),
                stats.getNewsVetoCount(),
                stats.getCumulativePnL(),
                stats.getConsecutiveWins(),
                stats.getConsecutiveLosses()
        );
    }

    /**
     * Get statistics summary for a date range.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatisticsSummary(String symbol, LocalDate start, LocalDate end) {
        List<DailyStatistics> stats = dailyStatisticsRepository
                .findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(symbol, start, end);

        double totalPnL = stats.stream()
                .filter(s -> s.getRealizedPnL() != null)
                .mapToDouble(DailyStatistics::getRealizedPnL)
                .sum();

        int totalTrades = stats.stream()
                .filter(s -> s.getTotalTrades() != null)
                .mapToInt(DailyStatistics::getTotalTrades)
                .sum();

        double avgWinRate = stats.stream()
                .filter(s -> s.getWinRate() != null && s.getTotalTrades() != null && s.getTotalTrades() > 0)
                .mapToDouble(DailyStatistics::getWinRate)
                .average()
                .orElse(0);

        long profitableDays = stats.stream()
                .filter(s -> s.getRealizedPnL() != null && s.getRealizedPnL() > 0)
                .count();

        return Map.of(
                "symbol", symbol,
                "startDate", start.toString(),
                "endDate", end.toString(),
                "tradingDays", stats.size(),
                "profitableDays", profitableDays,
                "totalPnL", totalPnL,
                "totalTrades", totalTrades,
                "avgWinRate", avgWinRate,
                "avgDailyPnL", stats.isEmpty() ? 0 : totalPnL / stats.size()
        );
    }
}
