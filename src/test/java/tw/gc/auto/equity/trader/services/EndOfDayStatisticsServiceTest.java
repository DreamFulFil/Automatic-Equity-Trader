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
}
