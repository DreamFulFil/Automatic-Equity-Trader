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
}
