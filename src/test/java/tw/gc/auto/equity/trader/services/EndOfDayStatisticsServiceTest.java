package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.services.TelegramService;
import tw.gc.auto.equity.trader.entities.*;
import tw.gc.auto.equity.trader.repositories.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndOfDayStatisticsServiceTest {

    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private SignalRepository signalRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private DailyStatisticsRepository dailyStatisticsRepository;
    @Mock
    private MarketDataRepository marketDataRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private TelegramService telegramService;
    @Mock
    private ActiveStockService activeStockService;

    @InjectMocks
    private EndOfDayStatisticsService service;

    private LocalDate testDate;
    private String symbol;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.now();
        symbol = "2454.TW";
        System.setProperty("trading.mode", "stock");
    }

    @Test
    void calculateStatisticsForDay_WithNoTrades_ReturnsZeroStats() {
        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Collections.emptyList());
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertNotNull(stats);
        assertEquals(0, stats.getTotalTrades());
        assertEquals(0.0, stats.getWinRate());
        assertEquals(0.0, stats.getRealizedPnL());
    }

    @Test
    void calculateStatisticsForDay_WithWinningTrade_CalculatesCorrectly() {
        Trade winningTrade = Trade.builder()
                .id(1L)
                .timestamp(testDate.atTime(11, 45))
                .action(Trade.TradeAction.BUY)
                .quantity(100)
                .entryPrice(100.0)
                .exitPrice(105.0)
                .realizedPnL(500.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(15)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(List.of(winningTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(1, stats.getTotalTrades());
        assertEquals(1, stats.getWinningTrades());
        assertEquals(0, stats.getLosingTrades());
        assertEquals(100.0, stats.getWinRate());
        assertEquals(500.0, stats.getRealizedPnL());
        assertEquals(500.0, stats.getMaxProfit());
    }

    @Test
    void calculateStatisticsForDay_WithLosingTrade_CalculatesCorrectly() {
        Trade losingTrade = Trade.builder()
                .id(1L)
                .timestamp(testDate.atTime(11, 45))
                .action(Trade.TradeAction.BUY)
                .quantity(100)
                .entryPrice(100.0)
                .exitPrice(95.0)
                .realizedPnL(-500.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(20)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(List.of(losingTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(1, stats.getTotalTrades());
        assertEquals(0, stats.getWinningTrades());
        assertEquals(1, stats.getLosingTrades());
        assertEquals(0.0, stats.getWinRate());
        assertEquals(-500.0, stats.getRealizedPnL());
        assertEquals(500.0, stats.getMaxDrawdown()); // Absolute value
    }

    @Test
    void calculateStatisticsForDay_WithMultipleTrades_CalculatesCorrectly() {
        Trade trade1 = Trade.builder()
                .id(1L)
                .timestamp(testDate.atTime(11, 35))
                .realizedPnL(300.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(10)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade trade2 = Trade.builder()
                .id(2L)
                .timestamp(testDate.atTime(12, 0))
                .realizedPnL(-100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(15)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade trade3 = Trade.builder()
                .id(3L)
                .timestamp(testDate.atTime(12, 30))
                .realizedPnL(200.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(20)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenReturn(Arrays.asList(trade1, trade2, trade3));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(3, stats.getTotalTrades());
        assertEquals(2, stats.getWinningTrades());
        assertEquals(1, stats.getLosingTrades());
        assertEquals(400.0, stats.getRealizedPnL()); // 300 - 100 + 200
        assertEquals(300.0, stats.getMaxProfit());
        assertEquals(100.0, stats.getMaxDrawdown());
    }

    @Test
    void calculateStatisticsForDay_WithSignals_CalculatesSignalStats() {
        Signal signal1 = Signal.builder()
                .id(1L)
                .timestamp(testDate.atTime(11, 35))
                .direction(Signal.SignalDirection.LONG)
                .confidence(0.75)
                .symbol(symbol)
                .newsVeto(false)
                .build();

        Signal signal2 = Signal.builder()
                .id(2L)
                .timestamp(testDate.atTime(12, 0))
                .direction(Signal.SignalDirection.SHORT)
                .confidence(0.65)
                .symbol(symbol)
                .newsVeto(true)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Collections.emptyList());
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any()))
                .thenReturn(Arrays.asList(signal1, signal2));
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(2, stats.getSignalsGenerated());
        assertEquals(1, stats.getSignalsLong());
        assertEquals(1, stats.getSignalsShort());
        assertEquals(1, stats.getNewsVetoCount());
        assertEquals(0.7, stats.getAvgSignalConfidence(), 0.01);
    }

    @Test
    void calculateStatisticsForDay_WithHoldTimes_CalculatesTimeStats() {
        Trade trade1 = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(10)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade trade2 = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(30)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(trade1, trade2));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(20.0, stats.getAvgHoldMinutes());
        assertEquals(30, stats.getMaxHoldMinutes());
        assertEquals(10, stats.getMinHoldMinutes());
        assertEquals(40, stats.getTimeInMarketMinutes()); // 10 + 30
    }

    @Test
    void calculateStatisticsForDay_CalculatesProfitFactor() {
        Trade winner = Trade.builder()
                .realizedPnL(1000.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade loser = Trade.builder()
                .realizedPnL(-500.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(winner, loser));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(2.0, stats.getProfitFactor()); // 1000 / 500
    }

    @Test
    void getStatisticsSummary_ReturnsCorrectSummary() {
        DailyStatistics stat1 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(3)
                .winRate(66.67)
                .realizedPnL(500.0)
                .build();

        DailyStatistics stat2 = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(2)
                .winRate(50.0)
                .realizedPnL(-200.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(stat1, stat2));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        assertEquals(symbol, summary.get("symbol"));
        assertEquals(2, summary.get("tradingDays"));
        assertEquals(1L, summary.get("profitableDays"));
        assertEquals(300.0, summary.get("totalPnL"));
        assertEquals(5, summary.get("totalTrades"));
    }

    @Test
    void calculateStatisticsForDay_FiltersBySymbol() {
        Trade matchingTrade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade otherTrade = Trade.builder()
                .realizedPnL(999.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol("OTHER")
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenReturn(Arrays.asList(matchingTrade, otherTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // Should only count the matching trade
        assertEquals(1, stats.getTotalTrades());
        assertEquals(100.0, stats.getRealizedPnL());
    }

    @Test
    void calculateStatisticsForDay_IgnoresOpenTrades() {
        Trade closedTrade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade openTrade = Trade.builder()
                .status(Trade.TradeStatus.OPEN)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenReturn(Arrays.asList(closedTrade, openTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // Should only count closed trades
        assertEquals(1, stats.getTotalTrades());
    }

    @Test
    void calculateStatisticsForDay_calculatesConsecutiveWins() {
        Trade winningTrade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        DailyStatistics prevWin1 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .realizedPnL(50.0)
                .build();
        
        DailyStatistics prevWin2 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .realizedPnL(75.0)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(winningTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Arrays.asList(prevWin1, prevWin2));

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(3, stats.getConsecutiveWins());
        assertEquals(0, stats.getConsecutiveLosses());
    }

    @Test
    void calculateStatisticsForDay_calculatesConsecutiveLosses() {
        Trade losingTrade = Trade.builder()
                .realizedPnL(-100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        DailyStatistics prevLoss1 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .realizedPnL(-50.0)
                .build();
        
        DailyStatistics prevLoss2 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .realizedPnL(-75.0)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(losingTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Arrays.asList(prevLoss1, prevLoss2));

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(0, stats.getConsecutiveWins());
        assertEquals(3, stats.getConsecutiveLosses());
    }

    @Test
    void calculateStatisticsForDay_streakBreaksOnOppositeResult() {
        Trade losingTrade = Trade.builder()
                .realizedPnL(-100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        DailyStatistics prevLoss = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .realizedPnL(-50.0)
                .build();
        
        DailyStatistics prevWin = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .realizedPnL(75.0) // Win breaks loss streak
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(losingTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Arrays.asList(prevLoss, prevWin));

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(2, stats.getConsecutiveLosses()); // Today + prev day only
    }

    @Test
    void calculateStatisticsForDay_calculatesCumulativeFromPastYear() {
        Trade todayTrade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(todayTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(eq(symbol), any())).thenReturn(5000.0);
        when(dailyStatisticsRepository.sumTradesSince(eq(symbol), any())).thenReturn(50L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(5100.0, stats.getCumulativePnL()); // 5000 + 100
        assertEquals(51, stats.getCumulativeTrades()); // 50 + 1
    }

    @Test
    void calculateStatisticsForDay_handleNullCumulatives() {
        Trade todayTrade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(todayTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(eq(symbol), any())).thenReturn(null);
        when(dailyStatisticsRepository.sumTradesSince(eq(symbol), any())).thenReturn(null);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(100.0, stats.getCumulativePnL()); // Handles null
        assertEquals(1, stats.getCumulativeTrades());
    }

    @Test
    void calculateStatisticsForDay_profitFactorInfinity_whenNoLosses() {
        Trade winner1 = Trade.builder()
                .realizedPnL(1000.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade winner2 = Trade.builder()
                .realizedPnL(500.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(winner1, winner2));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(Double.MAX_VALUE, stats.getProfitFactor());
    }

    @Test
    void calculateStatisticsForDay_profitFactorZero_whenNoWins() {
        Trade loser1 = Trade.builder()
                .realizedPnL(-1000.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade loser2 = Trade.builder()
                .realizedPnL(-500.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(loser1, loser2));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(0.0, stats.getProfitFactor());
    }

    @Test
    void calculateAndSaveStatisticsForDay_savesAndReturnsStats() {
        Trade trade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Arrays.asList(trade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics result = service.calculateAndSaveStatisticsForDay(testDate, symbol);

        assertNotNull(result);
        verify(dailyStatisticsRepository).save(any(DailyStatistics.class));
        assertEquals(100.0, result.getRealizedPnL());
    }

    @Test
    void calculateEndOfDayStatistics_handlesException() {
        when(activeStockService.getActiveSymbol(anyString())).thenReturn(symbol);
        when(tradeRepository.findByTimestampBetween(any(), any())).thenThrow(new RuntimeException("Database error"));

        // Should not throw, just log error
        assertDoesNotThrow(() -> service.calculateEndOfDayStatistics());

        verify(dailyStatisticsRepository, never()).save(any());
    }

    @Test
    void getStatisticsSummary_calculatesCorrectAverages() {
        DailyStatistics stat1 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .symbol(symbol)
                .totalTrades(2)
                .winRate(50.0)
                .realizedPnL(100.0)
                .build();

        DailyStatistics stat2 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(3)
                .winRate(66.67)
                .realizedPnL(200.0)
                .build();

        DailyStatistics stat3 = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(1)
                .winRate(100.0)
                .realizedPnL(-50.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(stat1, stat2, stat3));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        assertEquals(3, summary.get("tradingDays"));
        assertEquals(2L, summary.get("profitableDays"));
        assertEquals(250.0, summary.get("totalPnL")); // 100 + 200 - 50
        assertEquals(6, summary.get("totalTrades")); // 2 + 3 + 1
        assertEquals(72.22, (Double)summary.get("avgWinRate"), 0.1); // (50 + 66.67 + 100) / 3
        assertEquals(83.33, (Double)summary.get("avgDailyPnL"), 0.1); // 250 / 3
    }

    @Test
    void getStatisticsSummary_handlesEmptyResults() {
        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        assertEquals(0, summary.get("tradingDays"));
        assertEquals(0.0, summary.get("totalPnL"));
        assertEquals(0.0, summary.get("avgDailyPnL"));
    }

    @Test
    void calculateStatisticsForDay_signalFiltering_bySymbol() {
        Signal matchingSignal = Signal.builder()
                .timestamp(testDate.atTime(11, 35))
                .direction(Signal.SignalDirection.LONG)
                .confidence(0.75)
                .symbol(symbol)
                .newsVeto(false)
                .build();

        Signal otherSignal = Signal.builder()
                .timestamp(testDate.atTime(12, 0))
                .direction(Signal.SignalDirection.SHORT)
                .confidence(0.65)
                .symbol("OTHER.TW")
                .newsVeto(false)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Collections.emptyList());
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any()))
                .thenReturn(Arrays.asList(matchingSignal, otherSignal));
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(1, stats.getSignalsGenerated()); // Only matching signal
        assertEquals(1, stats.getSignalsLong());
        assertEquals(0, stats.getSignalsShort());
    }

    @Test
    void calculateEndOfDayStatistics_successPath_savesStatsAndGeneratesInsight() {
        when(activeStockService.getActiveSymbol("stock")).thenReturn(symbol);
        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(Collections.emptyList());
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("response", "Test AI insight"));

        service.calculateEndOfDayStatistics();

        verify(dailyStatisticsRepository, times(2)).save(any(DailyStatistics.class));
    }

    @Test
    void calculateStatisticsForDay_handlesNullRealizedPnL() {
        Trade tradeWithPnL = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade tradeWithoutPnL = Trade.builder()
                .realizedPnL(null)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenReturn(Arrays.asList(tradeWithPnL, tradeWithoutPnL));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        assertEquals(100.0, stats.getRealizedPnL());
        assertEquals(1, stats.getWinningTrades());
    }

    @Test
    void generateInsightAsync_successfulResponse_savesInsightAndSendsTelegram() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(5)
                .winningTrades(3)
                .losingTrades(2)
                .winRate(60.0)
                .realizedPnL(500.0)
                .maxDrawdown(200.0)
                .profitFactor(1.5)
                .avgHoldMinutes(15.0)
                .signalsGenerated(10)
                .signalsLong(6)
                .signalsShort(4)
                .newsVetoCount(2)
                .cumulativePnL(5000.0)
                .consecutiveWins(2)
                .consecutiveLosses(0)
                .build();

        Map<String, Object> mockResponse = Map.of("response", "AI generated insight about trading");
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(mockResponse);

        service.generateInsightAsync(stats);

        assertEquals("AI generated insight about trading", stats.getLlamaInsight());
        assertNotNull(stats.getInsightGeneratedAt());
        verify(dailyStatisticsRepository).save(stats);
        verify(telegramService).sendMessage(contains("Daily Trading Summary"));
    }

    @Test
    void generateInsightAsync_nullResponse_doesNotSaveInsight() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(1)
                .winningTrades(1)
                .losingTrades(0)
                .winRate(100.0)
                .realizedPnL(100.0)
                .maxDrawdown(0.0)
                .profitFactor(1.0)
                .avgHoldMinutes(10.0)
                .signalsGenerated(1)
                .signalsLong(1)
                .signalsShort(0)
                .newsVetoCount(0)
                .cumulativePnL(100.0)
                .consecutiveWins(1)
                .consecutiveLosses(0)
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

        service.generateInsightAsync(stats);

        assertNull(stats.getLlamaInsight());
        verify(dailyStatisticsRepository, never()).save(any());
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void generateInsightAsync_responseWithoutKey_doesNotSaveInsight() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(1)
                .winningTrades(1)
                .losingTrades(0)
                .winRate(100.0)
                .realizedPnL(100.0)
                .maxDrawdown(0.0)
                .profitFactor(1.0)
                .avgHoldMinutes(10.0)
                .signalsGenerated(1)
                .signalsLong(1)
                .signalsShort(0)
                .newsVetoCount(0)
                .cumulativePnL(100.0)
                .consecutiveWins(1)
                .consecutiveLosses(0)
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(Map.of("error", "failed"));

        service.generateInsightAsync(stats);

        assertNull(stats.getLlamaInsight());
        verify(dailyStatisticsRepository, never()).save(any());
    }

    @Test
    void generateInsightAsync_restTemplateThrowsException_logsWarning() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(1)
                .winningTrades(1)
                .losingTrades(0)
                .winRate(100.0)
                .realizedPnL(100.0)
                .maxDrawdown(0.0)
                .profitFactor(1.0)
                .avgHoldMinutes(10.0)
                .signalsGenerated(1)
                .signalsLong(1)
                .signalsShort(0)
                .newsVetoCount(0)
                .cumulativePnL(100.0)
                .consecutiveWins(1)
                .consecutiveLosses(0)
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertDoesNotThrow(() -> service.generateInsightAsync(stats));

        verify(dailyStatisticsRepository, never()).save(any());
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void sendTelegramSummary_sendsFormattedMessage() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(5)
                .winningTrades(3)
                .losingTrades(2)
                .winRate(60.0)
                .realizedPnL(500.0)
                .maxDrawdown(200.0)
                .profitFactor(1.5)
                .avgHoldMinutes(15.5)
                .signalsGenerated(10)
                .signalsLong(6)
                .signalsShort(4)
                .newsVetoCount(2)
                .llamaInsight("Strong performance with good risk management")
                .build();

        service.sendTelegramSummary(stats);

        verify(telegramService).sendMessage(argThat(message ->
                message.contains("Daily Trading Summary") &&
                message.contains(symbol) &&
                message.contains("500") &&
                message.contains("Strong performance")
        ));
    }

    @Test
    void buildInsightPrompt_generatesValidPrompt() {
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(5)
                .winningTrades(3)
                .losingTrades(2)
                .winRate(60.0)
                .realizedPnL(500.0)
                .maxDrawdown(200.0)
                .profitFactor(1.5)
                .avgHoldMinutes(15.0)
                .signalsGenerated(10)
                .signalsLong(6)
                .signalsShort(4)
                .newsVetoCount(2)
                .cumulativePnL(5000.0)
                .consecutiveWins(2)
                .consecutiveLosses(0)
                .build();

        String prompt = service.buildInsightPrompt(stats);

        assertTrue(prompt.contains("professional trading analyst"));
        assertTrue(prompt.contains(symbol));
        assertTrue(prompt.contains("Total Trades: 5"));
        assertTrue(prompt.contains("Wins: 3"));
        assertTrue(prompt.contains("Losses: 2"));
        assertTrue(prompt.contains("Win Rate: 60"));
        assertTrue(prompt.contains("500"));
        assertTrue(prompt.contains("Consecutive Wins: 2"));
    }

    @Test
    void getStatisticsSummary_handlesNullFieldsGracefully() {
        DailyStatistics statWithNulls = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(null)
                .winRate(null)
                .realizedPnL(null)
                .build();

        DailyStatistics statWithValues = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(5)
                .winRate(60.0)
                .realizedPnL(500.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(statWithNulls, statWithValues));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        assertEquals(500.0, summary.get("totalPnL"));
        assertEquals(5, summary.get("totalTrades"));
        assertEquals(60.0, summary.get("avgWinRate"));
    }

    @Test
    void getStatisticsSummary_filtersZeroTradeDaysFromWinRate() {
        DailyStatistics dayWithNoTrades = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(0)
                .winRate(0.0)
                .realizedPnL(0.0)
                .build();

        DailyStatistics dayWithTrades = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(5)
                .winRate(80.0)
                .realizedPnL(500.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(dayWithNoTrades, dayWithTrades));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        assertEquals(80.0, summary.get("avgWinRate"));
    }

    @Test
    void calculateEndOfDayStatistics_successPath_savesStatsAndLogsSuccess() {
        // Setup for complete success path through lines 66-68, 71
        when(activeStockService.getActiveSymbol("stock")).thenReturn(symbol);
        
        Trade trade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();
        
        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(List.of(trade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());
        
        // Mock successful AI response for line 71
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("response", "Test insight"));
        
        service.calculateEndOfDayStatistics();
        
        // Verify save was called (line 67)
        verify(dailyStatisticsRepository, atLeastOnce()).save(any(DailyStatistics.class));
    }

    @Test
    void calculateEndOfDayStatistics_exceptionInCalculation_catchesAndLogsError() {
        // Test line 75 - catch block
        when(activeStockService.getActiveSymbol("stock")).thenReturn(symbol);
        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenThrow(new RuntimeException("Database connection lost"));
        
        // Should not throw exception - just log error
        assertDoesNotThrow(() -> service.calculateEndOfDayStatistics());
        
        // Verify save was never called due to exception
        verify(dailyStatisticsRepository, never()).save(any());
    }

    @Test
    void generateInsightAsync_buildsPromptAndSendsRequest() {
        // Test lines 290, 292, 295-296, 300
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(3)
                .winningTrades(2)
                .losingTrades(1)
                .winRate(66.67)
                .realizedPnL(300.0)
                .maxDrawdown(100.0)
                .profitFactor(2.0)
                .avgHoldMinutes(12.5)
                .signalsGenerated(5)
                .signalsLong(3)
                .signalsShort(2)
                .newsVetoCount(1)
                .cumulativePnL(1000.0)
                .consecutiveWins(2)
                .consecutiveLosses(0)
                .build();

        Map<String, Object> mockResponse = Map.of("response", "  Trimmed insight  ");
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(mockResponse);

        service.generateInsightAsync(stats);

        // Verify the insight was trimmed (line 308)
        assertEquals("Trimmed insight", stats.getLlamaInsight());
        assertNotNull(stats.getInsightGeneratedAt());
        verify(dailyStatisticsRepository).save(stats);
        verify(telegramService).sendMessage(anyString());
    }

    @Test
    void generateInsightAsync_responseContainsKey_savesAndSendsTelegram() {
        // Test lines 306-311, 314
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(1)
                .winningTrades(1)
                .losingTrades(0)
                .winRate(100.0)
                .realizedPnL(50.0)
                .maxDrawdown(0.0)
                .profitFactor(Double.MAX_VALUE)
                .avgHoldMinutes(5.0)
                .signalsGenerated(2)
                .signalsLong(2)
                .signalsShort(0)
                .newsVetoCount(0)
                .cumulativePnL(50.0)
                .consecutiveWins(1)
                .consecutiveLosses(0)
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("response", "Generated insight"));

        service.generateInsightAsync(stats);

        assertEquals("Generated insight", stats.getLlamaInsight());
        assertNotNull(stats.getInsightGeneratedAt());
        verify(dailyStatisticsRepository).save(stats);
        verify(telegramService).sendMessage(contains("Daily Trading Summary"));
    }

    @Test
    void generateInsightAsync_exceptionThrown_logsWarningAndContinues() {
        // Test lines 317-320 - exception handling
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(1)
                .winningTrades(0)
                .losingTrades(1)
                .winRate(0.0)
                .realizedPnL(-50.0)
                .maxDrawdown(50.0)
                .profitFactor(0.0)
                .avgHoldMinutes(3.0)
                .signalsGenerated(1)
                .signalsLong(0)
                .signalsShort(1)
                .newsVetoCount(0)
                .cumulativePnL(-50.0)
                .consecutiveWins(0)
                .consecutiveLosses(1)
                .build();

        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Network timeout"));

        // Should not throw
        assertDoesNotThrow(() -> service.generateInsightAsync(stats));
        
        // Verify no save or telegram sent
        verify(dailyStatisticsRepository, never()).save(any());
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void sendTelegramSummary_formatsAllStatsCorrectly() {
        // Test lines 323, 337-346, 349-350
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(10)
                .winningTrades(7)
                .losingTrades(3)
                .winRate(70.0)
                .realizedPnL(1500.0)
                .maxDrawdown(300.0)
                .profitFactor(2.5)
                .avgHoldMinutes(20.0)
                .signalsGenerated(15)
                .signalsLong(8)
                .signalsShort(7)
                .newsVetoCount(3)
                .llamaInsight("Today showed excellent risk management with a strong win rate.")
                .build();

        service.sendTelegramSummary(stats);

        verify(telegramService).sendMessage(argThat(message ->
                message.contains("Daily Trading Summary") &&
                message.contains(testDate.toString()) &&
                message.contains(symbol) &&
                message.contains("1500") &&
                message.contains("10") &&
                message.contains("W:7") &&
                message.contains("L:3") &&
                message.contains("70.0%") &&
                message.contains("2.50") &&
                message.contains("300") &&
                message.contains("15") &&
                message.contains("L:8") &&
                message.contains("S:7") &&
                message.contains("3") &&
                message.contains("20.0") &&
                message.contains("AI Insight") &&
                message.contains("excellent risk management")
        ));
    }

    @Test
    void buildInsightPrompt_includesAllStatisticsFields() {
        // Test lines 353, 377-393
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(8)
                .winningTrades(5)
                .losingTrades(3)
                .winRate(62.5)
                .realizedPnL(800.0)
                .maxDrawdown(250.0)
                .profitFactor(1.8)
                .avgHoldMinutes(18.0)
                .signalsGenerated(12)
                .signalsLong(7)
                .signalsShort(5)
                .newsVetoCount(4)
                .cumulativePnL(10000.0)
                .consecutiveWins(3)
                .consecutiveLosses(0)
                .build();

        String prompt = service.buildInsightPrompt(stats);

        // Verify all statistics are included (lines 377-393)
        assertTrue(prompt.contains(testDate.toString()));
        assertTrue(prompt.contains(symbol));
        assertTrue(prompt.contains("Total Trades: 8"));
        assertTrue(prompt.contains("Wins: 5"));
        assertTrue(prompt.contains("Losses: 3"));
        assertTrue(prompt.contains("Win Rate: 62.5%"));
        assertTrue(prompt.contains("800"));
        assertTrue(prompt.contains("250"));
        assertTrue(prompt.contains("1.80") || prompt.contains("1.8"));
        assertTrue(prompt.contains("18.0"));
        assertTrue(prompt.contains("Signals Generated: 12"));
        assertTrue(prompt.contains("Long: 7"));
        assertTrue(prompt.contains("Short: 5"));
        assertTrue(prompt.contains("News Vetos: 4"));
        assertTrue(prompt.contains("10000"));
        assertTrue(prompt.contains("Consecutive Wins: 3"));
        assertTrue(prompt.contains("Consecutive Losses: 0"));
    }

    @Test
    void getStatisticsSummary_filtersNullRealizedPnL() {
        // Test line 406
        DailyStatistics statWithNullPnL = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(2)
                .winRate(50.0)
                .realizedPnL(null)
                .build();

        DailyStatistics statWithPnL = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(3)
                .winRate(66.67)
                .realizedPnL(300.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(statWithNullPnL, statWithPnL));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        // Only statWithPnL should be counted for totalPnL
        assertEquals(300.0, summary.get("totalPnL"));
    }

    @Test
    void getStatisticsSummary_filtersNullTotalTrades() {
        // Test line 411
        DailyStatistics statWithNullTrades = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(null)
                .winRate(0.0)
                .realizedPnL(0.0)
                .build();

        DailyStatistics statWithTrades = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(4)
                .winRate(75.0)
                .realizedPnL(400.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(statWithNullTrades, statWithTrades));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        assertEquals(4, summary.get("totalTrades"));
    }

    @Test
    void getStatisticsSummary_filtersNullWinRateAndZeroTrades() {
        // Test line 416
        DailyStatistics statWithNullWinRate = DailyStatistics.builder()
                .tradeDate(testDate)
                .symbol(symbol)
                .totalTrades(5)
                .winRate(null)
                .realizedPnL(100.0)
                .build();

        DailyStatistics statWithZeroTrades = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .symbol(symbol)
                .totalTrades(0)
                .winRate(0.0)
                .realizedPnL(0.0)
                .build();

        DailyStatistics statWithValidWinRate = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .symbol(symbol)
                .totalTrades(6)
                .winRate(83.33)
                .realizedPnL(500.0)
                .build();

        when(dailyStatisticsRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
                anyString(), any(), any()))
                .thenReturn(Arrays.asList(statWithNullWinRate, statWithZeroTrades, statWithValidWinRate));

        Map<String, Object> summary = service.getStatisticsSummary(symbol, testDate.minusDays(7), testDate);

        // Only statWithValidWinRate should be counted for avgWinRate
        assertEquals(83.33, summary.get("avgWinRate"));
    }

    @Test
    void calculateStatisticsForDay_filtersNullRealizedPnLInStreams() {
        // Test line 120 - filter for realizedPnL in sum calculation
        Trade tradeWithPnL1 = Trade.builder()
                .realizedPnL(200.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade tradeWithNullPnL = Trade.builder()
                .realizedPnL(null)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade tradeWithPnL2 = Trade.builder()
                .realizedPnL(-50.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenReturn(Arrays.asList(tradeWithPnL1, tradeWithNullPnL, tradeWithPnL2));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // realizedPnL should be 200 - 50 = 150 (null trade excluded)
        assertEquals(150.0, stats.getRealizedPnL());
        assertEquals(3, stats.getTotalTrades());
        assertEquals(1, stats.getWinningTrades());
        assertEquals(2, stats.getLosingTrades()); // null PnL counts as not winning
    }

    @Test
    void calculateStatisticsForDay_withNullHoldDuration_handlesGracefully() {
        Trade tradeWithHoldTime = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(25)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        Trade tradeWithNullHoldTime = Trade.builder()
                .realizedPnL(50.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .holdDurationMinutes(null)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any()))
                .thenReturn(Arrays.asList(tradeWithHoldTime, tradeWithNullHoldTime));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt())).thenReturn(Collections.emptyList());

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // Only trade with hold time should be counted
        assertEquals(25.0, stats.getAvgHoldMinutes());
        assertEquals(25, stats.getMaxHoldMinutes());
        assertEquals(25, stats.getMinHoldMinutes());
        assertEquals(25, stats.getTimeInMarketMinutes());
    }

    @Test
    void calculateStatisticsForDay_consecutiveWins_breaksOnNullPnL() {
        Trade winningTrade = Trade.builder()
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        DailyStatistics prevWin = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .realizedPnL(50.0)
                .build();
        
        DailyStatistics prevNull = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .realizedPnL(null) // Null breaks the streak
                .build();
        
        DailyStatistics prevWin2 = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(3))
                .realizedPnL(75.0)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(List.of(winningTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt()))
                .thenReturn(Arrays.asList(prevWin, prevNull, prevWin2));

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // Streak should be 2 (today + prevWin), broken by null
        assertEquals(2, stats.getConsecutiveWins());
    }

    @Test
    void calculateStatisticsForDay_consecutiveLosses_breaksOnNullPnL() {
        Trade losingTrade = Trade.builder()
                .realizedPnL(-100.0)
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        DailyStatistics prevLoss = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .realizedPnL(-50.0)
                .build();
        
        DailyStatistics prevNull = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(2))
                .realizedPnL(null) // Null breaks the streak
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(List.of(losingTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt()))
                .thenReturn(Arrays.asList(prevLoss, prevNull));

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // Streak should be 2 (today + prevLoss), broken by null
        assertEquals(2, stats.getConsecutiveLosses());
    }

    @Test
    void calculateStatisticsForDay_zeroPnLDay_resetsWinStreak() {
        Trade breakEvenTrade = Trade.builder()
                .realizedPnL(0.0) // Zero P&L - not a win
                .status(Trade.TradeStatus.CLOSED)
                .symbol(symbol)
                .mode(Trade.TradingMode.SIMULATION)
                .build();

        DailyStatistics prevWin = DailyStatistics.builder()
                .tradeDate(testDate.minusDays(1))
                .realizedPnL(100.0)
                .build();

        when(tradeRepository.findByTimestampBetween(any(), any())).thenReturn(List.of(breakEvenTrade));
        when(signalRepository.findByTimestampBetweenOrderByTimestampDesc(any(), any())).thenReturn(Collections.emptyList());
        when(dailyStatisticsRepository.sumPnLSince(anyString(), any())).thenReturn(0.0);
        when(dailyStatisticsRepository.sumTradesSince(anyString(), any())).thenReturn(0L);
        when(dailyStatisticsRepository.findRecentBySymbol(anyString(), anyInt()))
                .thenReturn(List.of(prevWin));

        DailyStatistics stats = service.calculateStatisticsForDay(testDate, symbol);

        // Zero P&L day is not a win, so streak should be 0
        assertEquals(0, stats.getConsecutiveWins());
        assertEquals(0, stats.getConsecutiveLosses());
    }
}
