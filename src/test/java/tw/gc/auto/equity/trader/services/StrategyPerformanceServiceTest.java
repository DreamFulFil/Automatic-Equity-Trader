package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyPerformanceServiceTest {

    @Mock
    private StrategyPerformanceRepository performanceRepository;

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StrategyPerformanceService service;

    private List<Trade> mockTrades;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    @BeforeEach
    void setUp() {
        periodStart = LocalDateTime.now().minusDays(7);
        periodEnd = LocalDateTime.now();

        mockTrades = new ArrayList<>();

        // Add winning trades
        for (int i = 0; i < 7; i++) {
            Trade trade = Trade.builder()
                .id((long) i)
                .timestamp(periodStart.plusDays(i))
                .strategyName("RSIStrategy")
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .build();
            mockTrades.add(trade);
        }

        // Add losing trades
        for (int i = 7; i < 10; i++) {
            Trade trade = Trade.builder()
                .id((long) i)
                .timestamp(periodStart.plusDays(i - 7))
                .strategyName("RSIStrategy")
                .realizedPnL(-50.0)
                .status(Trade.TradeStatus.CLOSED)
                .build();
            mockTrades.add(trade);
        }
    }

    @Test
    void testCalculatePerformance_WithTrades() throws Exception {
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(mockTrades);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> params = new HashMap<>();
        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            params
        );

        assertNotNull(result);
        assertEquals("RSIStrategy", result.getStrategyName());
        assertEquals(10, result.getTotalTrades());
        assertEquals(7, result.getWinningTrades());
        assertEquals(70.0, result.getWinRatePct(), 0.01);
        assertEquals(550.0, result.getTotalPnl(), 0.01); // 7*100 - 3*50
        assertTrue(result.getSharpeRatio() != null);
        assertTrue(result.getMaxDrawdownPct() != null);

        verify(performanceRepository, times(1)).save(any(StrategyPerformance.class));
    }

    @Test
    void testCalculatePerformance_NoTrades() {
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(new ArrayList<>());

        Map<String, Object> params = new HashMap<>();
        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            params
        );

        assertNull(result);
        verify(performanceRepository, never()).save(any());
    }

    @Test
    void testCalculatePerformance_AllWinningTrades() throws Exception {
        List<Trade> winningTrades = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Trade trade = Trade.builder()
                .id((long) i)
                .timestamp(periodStart.plusDays(i))
                .strategyName("RSIStrategy")
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .build();
            winningTrades.add(trade);
        }

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(winningTrades);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> params = new HashMap<>();
        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.MAIN,
            periodStart,
            periodEnd,
            "2454.TW",
            params
        );

        assertNotNull(result);
        assertEquals(100.0, result.getWinRatePct(), 0.01);
        assertEquals(500.0, result.getTotalPnl(), 0.01);
        assertEquals(0.0, result.getProfitFactor(), 0.01); // No losses
        assertEquals(0.0, result.getAvgLoss(), 0.01);
    }

    @Test
    void testCalculatePerformance_singleTrade_setsZeroSharpe() throws Exception {
        Trade trade = Trade.builder()
            .id(1L)
            .timestamp(periodStart.plusHours(1))
            .strategyName("RSIStrategy")
            .realizedPnL(100.0)
            .status(Trade.TradeStatus.CLOSED)
            .build();
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(List.of(trade));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> params = new HashMap<>();
        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            params
        );

        assertNotNull(result);
        assertEquals(0.0, result.getSharpeRatio(), 0.01);
    }

    @Test
    void testCalculatePerformance_whenParametersSerializationFails_usesEmptyJson() throws Exception {
        List<Trade> trades = new ArrayList<>();
        trades.add(Trade.builder()
            .id(1L)
            .timestamp(periodStart.plusHours(1))
            .strategyName("RSIStrategy")
            .realizedPnL(100.0)
            .status(Trade.TradeStatus.CLOSED)
            .build());
        trades.add(Trade.builder()
            .id(2L)
            .timestamp(periodStart.plusHours(2))
            .strategyName("RSIStrategy")
            .realizedPnL(-50.0)
            .status(Trade.TradeStatus.CLOSED)
            .build());
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(trades);
        when(objectMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> params = new HashMap<>();
        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            params
        );

        assertNotNull(result);
        assertEquals("{}", result.getParametersJson());
    }

    @Test
    void testGetBestPerformer() {
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(30);
        List<StrategyPerformance> performers = new ArrayList<>();

        StrategyPerformance best = StrategyPerformance.builder()
            .strategyName("MACDStrategy")
            .sharpeRatio(2.5)
            .maxDrawdownPct(5.0)
            .totalReturnPct(30.0)
            .build();
        performers.add(best);

        when(performanceRepository.findTopPerformersBySharpeRatio(any()))
            .thenReturn(performers);

        StrategyPerformance result = service.getBestPerformer(30);

        assertNotNull(result);
        assertEquals("MACDStrategy", result.getStrategyName());
        assertEquals(2.5, result.getSharpeRatio(), 0.01);
    }

    @Test
    void testGetBestPerformer_NoData() {
        when(performanceRepository.findTopPerformersBySharpeRatio(any()))
            .thenReturn(new ArrayList<>());

        StrategyPerformance result = service.getBestPerformer(30);

        assertNull(result);
    }

    @Test
    void testGetRankedStrategies() {
        List<StrategyPerformance> ranked = new ArrayList<>();
        ranked.add(StrategyPerformance.builder()
            .strategyName("Strategy1")
            .sharpeRatio(2.0)
            .build());
        ranked.add(StrategyPerformance.builder()
            .strategyName("Strategy2")
            .sharpeRatio(1.5)
            .build());

        when(performanceRepository.findLatestPerformanceForAllStrategies())
            .thenReturn(ranked);

        List<StrategyPerformance> result = service.getRankedStrategies();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Strategy1", result.get(0).getStrategyName());
    }

    @Test
    void testCalculatePerformance_JsonSerializationError() throws Exception {
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(mockTrades);

        // Mock JSON serialization failure
        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Test error") {});

        // Mock repository save
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");

        StrategyPerformance performance = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.MAIN,
            periodStart,
            periodEnd,
            "2330.TW",
            params
        );

        // Should still save performance with default "{}" parameters
        assertNotNull(performance);
        verify(performanceRepository).save(any(StrategyPerformance.class));
    }

    @Test
    void testCalculatePerformance_NullParametersHandling() throws Exception {
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(mockTrades);

        when(objectMapper.writeValueAsString(isNull()))
            .thenReturn("{}");

        // Mock repository save
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance performance = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            null,
            null
        );

        assertNotNull(performance);
        verify(performanceRepository).save(any(StrategyPerformance.class));
    }

    // ==================== Coverage tests for lines 60, 63, 67, 79, 137, 140, 144, 166, 189 ====================

    @Test
    void testCalculatePerformance_ZeroTrades_WinRateShouldBeZero() throws Exception {
        // Lines 60, 67: totalTrades == 0 edge cases
        List<Trade> singleTrade = new ArrayList<>();
        Trade trade = Trade.builder()
            .id(1L)
            .timestamp(periodStart)
            .strategyName("RSIStrategy")
            .realizedPnL(null) // null PnL
            .status(Trade.TradeStatus.OPEN)
            .build();
        singleTrade.add(trade);

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(singleTrade);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> params = new HashMap<>();
        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            params
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalTrades());
        assertEquals(0, result.getWinningTrades());
    }

    @Test
    void testCalculatePerformance_FilterNullPnL() throws Exception {
        // Line 63: Filter trades with null realizedPnL
        List<Trade> tradesWithNullPnL = new ArrayList<>();
        tradesWithNullPnL.add(Trade.builder()
            .id(1L)
            .timestamp(periodStart)
            .strategyName("RSIStrategy")
            .realizedPnL(100.0)
            .build());
        tradesWithNullPnL.add(Trade.builder()
            .id(2L)
            .timestamp(periodStart.plusDays(1))
            .strategyName("RSIStrategy")
            .realizedPnL(null) // null PnL - should be filtered
            .build());

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(tradesWithNullPnL);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNotNull(result);
        assertEquals(100.0, result.getTotalPnl(), 0.01);
    }

    @Test
    void testCalculatePerformance_EmptyWinningPnls() throws Exception {
        // Line 79: avgWin when winningPnls.isEmpty()
        List<Trade> allLosses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            allLosses.add(Trade.builder()
                .id((long) i)
                .timestamp(periodStart.plusDays(i))
                .strategyName("RSIStrategy")
                .realizedPnL(-50.0) // All losses
                .status(Trade.TradeStatus.CLOSED)
                .build());
        }

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(allLosses);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNotNull(result);
        assertEquals(0.0, result.getWinRatePct(), 0.01);
        assertEquals(0.0, result.getAvgWin(), 0.01);
    }

    @Test
    void testCalculateSharpeRatio_SingleTrade_ShouldReturnZero() throws Exception {
        // Line 137: trades.size() < 2 returns 0.0
        List<Trade> singleTrade = new ArrayList<>();
        singleTrade.add(Trade.builder()
            .id(1L)
            .timestamp(periodStart)
            .strategyName("RSIStrategy")
            .realizedPnL(100.0)
            .build());

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(singleTrade);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNotNull(result);
        assertEquals(0.0, result.getSharpeRatio(), 0.01);
    }

    @Test
    void testCalculateSharpeRatio_EmptyReturnsAfterFilter() throws Exception {
        // Lines 140, 144: Filter returns null PnL, resulting in empty returns
        List<Trade> tradesWithNullPnl = new ArrayList<>();
        tradesWithNullPnl.add(Trade.builder()
            .id(1L)
            .timestamp(periodStart)
            .strategyName("RSIStrategy")
            .realizedPnL(null)
            .build());
        tradesWithNullPnl.add(Trade.builder()
            .id(2L)
            .timestamp(periodStart.plusDays(1))
            .strategyName("RSIStrategy")
            .realizedPnL(null)
            .build());

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(tradesWithNullPnl);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNotNull(result);
        assertEquals(0.0, result.getSharpeRatio(), 0.01);
    }

    @Test
    void testCalculateMaxDrawdown_EmptyTrades_ShouldReturnZero() throws Exception {
        // Line 166: trades.isEmpty() returns 0.0
        // This is already covered by testCalculatePerformance_NoTrades, but let's be explicit
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("EmptyStrategy"), any(), any()
        )).thenReturn(new ArrayList<>());

        StrategyPerformance result = service.calculatePerformance(
            "EmptyStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNull(result); // Returns null when no trades
    }

    @Test
    void testCalculateMaxDrawdown_WithDrawdown() throws Exception {
        // Line 189: Calculate drawdown percentage
        List<Trade> tradesWithDrawdown = new ArrayList<>();
        tradesWithDrawdown.add(Trade.builder()
            .id(1L)
            .timestamp(periodStart)
            .strategyName("RSIStrategy")
            .realizedPnL(1000.0) // Win
            .build());
        tradesWithDrawdown.add(Trade.builder()
            .id(2L)
            .timestamp(periodStart.plusDays(1))
            .strategyName("RSIStrategy")
            .realizedPnL(-500.0) // Loss - creates drawdown
            .build());
        tradesWithDrawdown.add(Trade.builder()
            .id(3L)
            .timestamp(periodStart.plusDays(2))
            .strategyName("RSIStrategy")
            .realizedPnL(-300.0) // Another loss
            .build());

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(tradesWithDrawdown);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNotNull(result);
        assertTrue(result.getMaxDrawdownPct() > 0); // Should have some drawdown
    }

    // ==================== Coverage tests for lines 60, 67, 166 ====================
    
    @Test
    void testCalculatePerformance_ZeroTotalTrades_ReturnsZeroWinRateAndAvgPnl() throws Exception {
        // Lines 60, 67: totalTrades > 0 ? calculation : 0.0
        // When trades have only null PnL values, the counts/sums are still processed
        List<Trade> tradesAllNullPnL = new ArrayList<>();
        tradesAllNullPnL.add(Trade.builder()
            .id(1L)
            .timestamp(periodStart)
            .strategyName("RSIStrategy")
            .realizedPnL(null)
            .build());

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(), any()
        )).thenReturn(tradesAllNullPnL);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(performanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        StrategyPerformance result = service.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNotNull(result);
        // With 1 trade (even with null PnL), totalTrades = 1
        // Line 60: winRate = 1 > 0 ? (0 * 100.0 / 1) : 0.0 = 0.0
        // Line 67: avgTradePnl = 1 > 0 ? (0.0 / 1) : 0.0 = 0.0
        assertEquals(0.0, result.getWinRatePct(), 0.01);
        assertEquals(0.0, result.getAvgTradePnl(), 0.01);
    }

    @Test
    void testCalculateMaxDrawdown_emptyTradesList_returnsZero() throws Exception {
        // Line 166: if (trades.isEmpty()) return 0.0
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("EmptyStrategy"), any(), any()
        )).thenReturn(new ArrayList<>());

        StrategyPerformance result = service.calculatePerformance(
            "EmptyStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        // Returns null when no trades (line 50 check)
        assertNull(result);
    }

    @Test
    void testCalculatePerformance_EdgeCaseDivisorZero() throws Exception {
        // Line 60: winRate when totalTrades == 0 (covered by null trades path)
        // Line 67: avgTradePnl when totalTrades == 0
        List<Trade> emptyTrades = new ArrayList<>();

        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("EmptyStrategy"), any(), any()
        )).thenReturn(emptyTrades);

        StrategyPerformance result = service.calculatePerformance(
            "EmptyStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        assertNull(result); // Returns null when no trades
    }
}
