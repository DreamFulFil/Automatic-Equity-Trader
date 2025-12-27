package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawdownMonitorServiceTest {

    @Mock
    private ActiveStrategyService activeStrategyService;

    @Mock
    private StrategyPerformanceService strategyPerformanceService;

    @Mock
    private TelegramService telegramService;

    @Mock
    private OrderExecutionService orderExecutionService;

    @Mock
    private TradingStateService tradingStateService;

    @Mock
    private PositionManager positionManager;

    @InjectMocks
    private DrawdownMonitorService service;

    @BeforeEach
    void setUp() {
        // Use lenient() for common stubs that may not be used in all tests
        lenient().when(tradingStateService.getTradingMode()).thenReturn("stock");
        lenient().when(activeStrategyService.getActiveStrategyName()).thenReturn("RSIStrategy");
        lenient().when(activeStrategyService.getActiveStrategyParameters()).thenReturn(new HashMap<>());
    }

    @Test
    void testMonitorDrawdown_BelowThreshold() {
        StrategyPerformance performance = StrategyPerformance.builder()
            .strategyName("RSIStrategy")
            .maxDrawdownPct(10.0) // Below 15% threshold
            .sharpeRatio(1.5)
            .build();

        when(strategyPerformanceService.calculatePerformance(
            eq("RSIStrategy"),
            eq(StrategyPerformance.PerformanceMode.MAIN),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            isNull(),
            any(Map.class)
        )).thenReturn(performance);

        service.monitorDrawdown();

        // Should not trigger any actions
        verify(telegramService, never()).sendMessage(anyString());
        verify(orderExecutionService, never()).flattenPosition(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void testMonitorDrawdown_AboveThreshold() {
        StrategyPerformance currentPerformance = StrategyPerformance.builder()
            .strategyName("RSIStrategy")
            .maxDrawdownPct(20.0) // Above 15% threshold
            .sharpeRatio(1.0)
            .build();

        StrategyPerformance betterPerformance = StrategyPerformance.builder()
            .strategyName("MACDStrategy")
            .maxDrawdownPct(8.0)
            .sharpeRatio(2.0)
            .totalReturnPct(25.0)
            .winRatePct(70.0)
            .build();

        when(strategyPerformanceService.calculatePerformance(
            eq("RSIStrategy"),
            eq(StrategyPerformance.PerformanceMode.MAIN),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            isNull(),
            any(Map.class)
        )).thenReturn(currentPerformance);

        when(strategyPerformanceService.getBestPerformer(30))
            .thenReturn(betterPerformance);

        when(positionManager.getPosition("2454.TW")).thenReturn(100);

        service.monitorDrawdown();

        // Should trigger emergency actions
        verify(telegramService, times(2)).sendMessage(anyString()); // Alert + switch confirmation
        verify(orderExecutionService, times(1)).flattenPosition(
            contains("Emergency"),
            eq("2454.TW"),
            eq("stock"),
            eq(false)
        );
        verify(activeStrategyService, times(1)).switchStrategy(
            eq("MACDStrategy"),
            any(Map.class),
            contains("MDD breach"),
            eq(true),
            eq(2.0),
            eq(8.0),
            eq(25.0),
            eq(70.0)
        );
    }

    @Test
    void testMonitorDrawdown_NoAlternativeStrategy() {
        StrategyPerformance currentPerformance = StrategyPerformance.builder()
            .strategyName("RSIStrategy")
            .maxDrawdownPct(20.0) // Above 15% threshold
            .sharpeRatio(1.0)
            .build();

        when(strategyPerformanceService.calculatePerformance(
            eq("RSIStrategy"),
            eq(StrategyPerformance.PerformanceMode.MAIN),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            isNull(),
            any(Map.class)
        )).thenReturn(currentPerformance);

        // No alternative strategy found
        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(null);

        when(positionManager.getPosition("2454.TW")).thenReturn(100);

        service.monitorDrawdown();

        // Should flatten but not switch
        verify(telegramService, times(2)).sendMessage(anyString()); // Alert + no alternative message
        verify(orderExecutionService, times(1)).flattenPosition(
            contains("Emergency"),
            eq("2454.TW"),
            eq("stock"),
            eq(false)
        );
        verify(activeStrategyService, never()).switchStrategy(
            anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any()
        );
    }

    @Test
    void testMonitorDrawdown_NoPerformanceData() {
        when(strategyPerformanceService.calculatePerformance(
            eq("RSIStrategy"),
            eq(StrategyPerformance.PerformanceMode.MAIN),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            isNull(),
            any(Map.class)
        )).thenReturn(null);

        service.monitorDrawdown();

        // Should not trigger any actions
        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void testMonitorDrawdown_NoPosition() {
        StrategyPerformance currentPerformance = StrategyPerformance.builder()
            .strategyName("RSIStrategy")
            .maxDrawdownPct(20.0)
            .sharpeRatio(1.0)
            .build();

        when(strategyPerformanceService.calculatePerformance(
            eq("RSIStrategy"),
            eq(StrategyPerformance.PerformanceMode.MAIN),
            any(LocalDateTime.class),
            any(LocalDateTime.class),
            isNull(),
            any(Map.class)
        )).thenReturn(currentPerformance);

        when(positionManager.getPosition("2454.TW")).thenReturn(0); // No position

        StrategyPerformance betterPerformance = StrategyPerformance.builder()
            .strategyName("MACDStrategy")
            .maxDrawdownPct(8.0)
            .sharpeRatio(2.0)
            .build();

        when(strategyPerformanceService.getBestPerformer(30)).thenReturn(betterPerformance);

        service.monitorDrawdown();

        // Should not flatten (no position), but should switch strategy
        verify(orderExecutionService, never()).flattenPosition(anyString(), anyString(), anyString(), anyBoolean());
        verify(activeStrategyService, times(1)).switchStrategy(
            anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any()
        );
    }
}
