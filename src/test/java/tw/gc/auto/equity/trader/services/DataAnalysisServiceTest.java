package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.EventRepository;
import tw.gc.auto.equity.trader.repositories.SignalRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataAnalysisServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private SignalRepository signalRepository;

    @Mock
    private EventRepository eventRepository;

    private DataAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new DataAnalysisService(tradeRepository, signalRepository, eventRepository);
    }

    @Test
    void getTradingMetrics_shouldCalculateCorrectMetrics() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.LIVE;

        when(tradeRepository.countTotalTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(100L);
        when(tradeRepository.countWinningTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(60L);
        when(tradeRepository.sumPnLSince(mode, since)).thenReturn(25000.0);
        when(tradeRepository.maxDrawdownSince(mode, since)).thenReturn(-5000.0);

        Map<String, Object> metrics = service.getTradingMetrics(mode, since);

        assertThat(metrics).containsEntry("totalTrades", 100L);
        assertThat(metrics).containsEntry("winningTrades", 60L);
        assertThat(metrics).containsEntry("winRate", 0.6);
        assertThat(metrics).containsEntry("totalPnL", 25000.0);
        assertThat(metrics).containsEntry("maxDrawdown", -5000.0);
        assertThat(metrics).containsKey("sharpeRatio");
    }

    @Test
    void getTradingMetrics_withZeroTrades_shouldReturnZeroWinRate() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.SIMULATION;

        when(tradeRepository.countTotalTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(0L);
        when(tradeRepository.countWinningTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(0L);
        when(tradeRepository.sumPnLSince(mode, since)).thenReturn(0.0);
        when(tradeRepository.maxDrawdownSince(mode, since)).thenReturn(null);

        Map<String, Object> metrics = service.getTradingMetrics(mode, since);

        assertThat(metrics).containsEntry("winRate", 0.0);
        assertThat(metrics).containsEntry("maxDrawdown", 0.0);
    }

    @Test
    void getSignalMetrics_shouldCalculateSignalStatistics() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);

        when(signalRepository.countLongSignalsSince(since)).thenReturn(45L);
        when(signalRepository.countShortSignalsSince(since)).thenReturn(30L);
        when(signalRepository.averageConfidenceSince(since)).thenReturn(0.75);

        Map<String, Object> metrics = service.getSignalMetrics(since);

        assertThat(metrics).containsEntry("longSignals", 45L);
        assertThat(metrics).containsEntry("shortSignals", 30L);
        assertThat(metrics).containsEntry("totalSignals", 75L);
        assertThat(metrics).containsEntry("averageConfidence", 0.75);
    }

    @Test
    void getSignalMetrics_withNullConfidence_shouldDefaultToZero() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);

        when(signalRepository.countLongSignalsSince(since)).thenReturn(10L);
        when(signalRepository.countShortSignalsSince(since)).thenReturn(5L);
        when(signalRepository.averageConfidenceSince(since)).thenReturn(null);

        Map<String, Object> metrics = service.getSignalMetrics(since);

        assertThat(metrics).containsEntry("averageConfidence", 0.0);
    }

    @Test
    void getSystemHealthMetrics_shouldAggregateSystemEvents() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        when(eventRepository.countErrorsSince(since)).thenReturn(5L);
        when(eventRepository.countSlowApiCallsSince(5000, since)).thenReturn(12L);
        when(eventRepository.countTelegramCommandsSince(since)).thenReturn(50L);
        when(eventRepository.countNewsVetosSince(since)).thenReturn(3L);

        Map<String, Object> metrics = service.getSystemHealthMetrics(since);

        assertThat(metrics).containsEntry("errorCount", 5L);
        assertThat(metrics).containsEntry("slowApiCalls", 12L);
        assertThat(metrics).containsEntry("telegramCommands", 50L);
        assertThat(metrics).containsEntry("newsVetos", 3L);
    }

    @Test
    void getProfitStreaks_shouldCalculateWinAndLossStreaks() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.LIVE;

        List<Trade> trades = new ArrayList<>();
        trades.add(createTrade(100.0));  // win
        trades.add(createTrade(50.0));   // win
        trades.add(createTrade(75.0));   // win
        trades.add(createTrade(-30.0));  // loss
        trades.add(createTrade(-20.0));  // loss
        trades.add(createTrade(40.0));   // win

        when(tradeRepository.findByModeAndTimestampBetween(eq(mode), eq(since), any(LocalDateTime.class)))
                .thenReturn(trades);

        Map<String, Object> streaks = service.getProfitStreaks(mode, since);

        assertThat(streaks).containsEntry("longestWinStreak", 3);
        assertThat(streaks).containsEntry("longestLossStreak", 2);
        assertThat(streaks).containsEntry("currentStreak", 1);
    }

    @Test
    void getProfitStreaks_withAllWins_shouldShowPositiveCurrentStreak() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.LIVE;

        List<Trade> trades = new ArrayList<>();
        trades.add(createTrade(100.0));
        trades.add(createTrade(50.0));
        trades.add(createTrade(75.0));

        when(tradeRepository.findByModeAndTimestampBetween(eq(mode), eq(since), any(LocalDateTime.class)))
                .thenReturn(trades);

        Map<String, Object> streaks = service.getProfitStreaks(mode, since);

        assertThat(streaks).containsEntry("currentStreak", 3);
        assertThat(streaks).containsEntry("longestWinStreak", 3);
        assertThat(streaks).containsEntry("longestLossStreak", 0);
    }

    @Test
    void getProfitStreaks_withAllLosses_shouldShowNegativeCurrentStreak() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.LIVE;

        List<Trade> trades = new ArrayList<>();
        trades.add(createTrade(-100.0));
        trades.add(createTrade(-50.0));

        when(tradeRepository.findByModeAndTimestampBetween(eq(mode), eq(since), any(LocalDateTime.class)))
                .thenReturn(trades);

        Map<String, Object> streaks = service.getProfitStreaks(mode, since);

        assertThat(streaks).containsEntry("currentStreak", -2);
        assertThat(streaks).containsEntry("longestLossStreak", 2);
    }

    @Test
    void getProfitStreaks_withNoTrades_shouldReturnZeros() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.SIMULATION;

        when(tradeRepository.findByModeAndTimestampBetween(eq(mode), eq(since), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        Map<String, Object> streaks = service.getProfitStreaks(mode, since);

        assertThat(streaks).containsEntry("currentStreak", 0);
        assertThat(streaks).containsEntry("longestWinStreak", 0);
        assertThat(streaks).containsEntry("longestLossStreak", 0);
    }

    @Test
    void getDashboardData_shouldAggregateAllMetrics() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Trade.TradingMode mode = Trade.TradingMode.LIVE;

        when(tradeRepository.countTotalTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(100L);
        when(tradeRepository.countWinningTrades(mode, Trade.TradeStatus.CLOSED)).thenReturn(60L);
        when(tradeRepository.sumPnLSince(mode, since)).thenReturn(25000.0);
        when(tradeRepository.maxDrawdownSince(mode, since)).thenReturn(-5000.0);
        when(tradeRepository.findByModeAndTimestampBetween(eq(mode), eq(since), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        when(signalRepository.countLongSignalsSince(since)).thenReturn(45L);
        when(signalRepository.countShortSignalsSince(since)).thenReturn(30L);
        when(signalRepository.averageConfidenceSince(since)).thenReturn(0.75);

        when(eventRepository.countErrorsSince(since)).thenReturn(5L);
        when(eventRepository.countSlowApiCallsSince(5000, since)).thenReturn(12L);
        when(eventRepository.countTelegramCommandsSince(since)).thenReturn(50L);
        when(eventRepository.countNewsVetosSince(since)).thenReturn(3L);

        Map<String, Object> dashboard = service.getDashboardData(mode, since);

        assertThat(dashboard).containsKeys("tradingMetrics", "signalMetrics", "systemHealth", "profitStreaks");
    }

    private Trade createTrade(double pnl) {
        Trade trade = new Trade();
        trade.setRealizedPnL(pnl);
        return trade;
    }
}
