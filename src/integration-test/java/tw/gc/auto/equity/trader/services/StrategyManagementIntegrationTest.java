package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.ActiveStrategyConfig;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.ActiveStrategyConfigRepository;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for strategy management functionality
 * Spring-independent using Mockito
 */
@ExtendWith(MockitoExtension.class)
class StrategyManagementIntegrationTest {

    @Mock
    private ActiveStrategyConfigRepository activeStrategyConfigRepository;
    
    @Mock
    private StrategyPerformanceRepository strategyPerformanceRepository;
    
    @Mock
    private TradeRepository tradeRepository;
    
    private ActiveStrategyService activeStrategyService;
    private StrategyPerformanceService strategyPerformanceService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        activeStrategyService = new ActiveStrategyService(activeStrategyConfigRepository, objectMapper);
        strategyPerformanceService = new StrategyPerformanceService(
            strategyPerformanceRepository, 
            tradeRepository, 
            objectMapper
        );
    }

    @Test
    void testActiveStrategyPersistence() throws Exception {
        // Given: No existing strategy
        when(activeStrategyConfigRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.empty());
        
        ActiveStrategyConfig defaultConfig = ActiveStrategyConfig.builder()
            .id(1L)
            .strategyName("RSIStrategy")
            .parametersJson("{}")
            .lastUpdated(LocalDateTime.now())
            .switchReason("System initialization")
            .autoSwitched(false)
            .build();
        
        when(activeStrategyConfigRepository.save(any(ActiveStrategyConfig.class)))
            .thenReturn(defaultConfig);

        // When: Get active strategy (should create default)
        ActiveStrategyConfig config = activeStrategyService.getActiveStrategy();
        
        // Then: Should have created default strategy
        assertNotNull(config);
        assertNotNull(config.getStrategyName());
        verify(activeStrategyConfigRepository, times(1)).save(any(ActiveStrategyConfig.class));
    }

    @Test
    void testStrategySwitchingWithMetrics() throws Exception {
        // Given: Existing strategy
        ActiveStrategyConfig existing = ActiveStrategyConfig.builder()
            .id(1L)
            .strategyName("RSIStrategy")
            .parametersJson("{}")
            .lastUpdated(LocalDateTime.now().minusDays(1))
            .build();
        
        when(activeStrategyConfigRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(existing));
        
        when(activeStrategyConfigRepository.save(any(ActiveStrategyConfig.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Switch strategy with metrics
        Map<String, Object> params = new HashMap<>();
        params.put("period", 14);
        
        activeStrategyService.switchStrategy(
            "MACDStrategy",
            params,
            "Performance-based switch",
            true,
            2.0,
            8.0,
            25.0,
            65.0
        );

        // Then: Should update with all metrics
        verify(activeStrategyConfigRepository).save(argThat(config ->
            config.getStrategyName().equals("MACDStrategy") &&
            config.isAutoSwitched() &&
            config.getSharpeRatio() == 2.0 &&
            config.getMaxDrawdownPct() == 8.0 &&
            config.getTotalReturnPct() == 25.0 &&
            config.getWinRatePct() == 65.0
        ));
    }

    @Test
    void testStrategyPerformanceCalculation() throws Exception {
        // Given: Mock trades for strategy
        List<Trade> mockTrades = new ArrayList<>();
        
        // Add 7 winning trades
        for (int i = 0; i < 7; i++) {
            mockTrades.add(Trade.builder()
                .id((long) i)
                .timestamp(LocalDateTime.now().minusDays(6 - i))
                .strategyName("RSIStrategy")
                .realizedPnL(100.0)
                .status(Trade.TradeStatus.CLOSED)
                .build());
        }
        
        // Add 3 losing trades
        for (int i = 7; i < 10; i++) {
            mockTrades.add(Trade.builder()
                .id((long) i)
                .timestamp(LocalDateTime.now().minusDays(9 - i))
                .strategyName("RSIStrategy")
                .realizedPnL(-50.0)
                .status(Trade.TradeStatus.CLOSED)
                .build());
        }
        
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            eq("RSIStrategy"), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(mockTrades);
        
        when(strategyPerformanceRepository.save(any(StrategyPerformance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Calculate performance
        LocalDateTime periodStart = LocalDateTime.now().minusDays(7);
        LocalDateTime periodEnd = LocalDateTime.now();
        
        StrategyPerformance result = strategyPerformanceService.calculatePerformance(
            "RSIStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            periodStart,
            periodEnd,
            "2454.TW",
            new HashMap<>()
        );

        // Then: Should calculate correct metrics
        assertNotNull(result);
        assertEquals("RSIStrategy", result.getStrategyName());
        assertEquals(10, result.getTotalTrades());
        assertEquals(7, result.getWinningTrades());
        assertEquals(70.0, result.getWinRatePct(), 0.01);
        assertEquals(550.0, result.getTotalPnl(), 0.01); // 7*100 - 3*50
        assertNotNull(result.getSharpeRatio());
        assertNotNull(result.getMaxDrawdownPct());
        
        verify(strategyPerformanceRepository).save(any(StrategyPerformance.class));
    }

    @Test
    void testBestPerformerSelection() {
        // Given: Multiple strategy performances (already sorted by Sharpe ratio DESC)
        List<StrategyPerformance> performers = Arrays.asList(
            StrategyPerformance.builder()
                .strategyName("MACDStrategy")
                .sharpeRatio(2.2)
                .maxDrawdownPct(8.0)
                .totalReturnPct(22.0)
                .winRatePct(72.0)
                .build(),
            StrategyPerformance.builder()
                .strategyName("BollingerBandStrategy")
                .sharpeRatio(1.8)
                .maxDrawdownPct(12.0)
                .totalReturnPct(18.0)
                .winRatePct(68.0)
                .build(),
            StrategyPerformance.builder()
                .strategyName("RSIStrategy")
                .sharpeRatio(1.5)
                .maxDrawdownPct(10.0)
                .totalReturnPct(15.0)
                .winRatePct(65.0)
                .build()
        );
        
        when(strategyPerformanceRepository.findTopPerformersBySharpeRatio(any(LocalDateTime.class)))
            .thenReturn(performers);

        // When: Get best performer
        StrategyPerformance best = strategyPerformanceService.getBestPerformer(30);

        // Then: Should return first strategy (highest Sharpe ratio)
        assertNotNull(best);
        assertEquals("MACDStrategy", best.getStrategyName());
        assertEquals(2.2, best.getSharpeRatio(), 0.01);
        assertEquals(8.0, best.getMaxDrawdownPct(), 0.01);
    }

    @Test
    void testParameterUpdatePreservesStrategy() throws Exception {
        // Given: Existing strategy
        ActiveStrategyConfig existing = ActiveStrategyConfig.builder()
            .id(1L)
            .strategyName("RSIStrategy")
            .parametersJson("{\"period\":14}")
            .lastUpdated(LocalDateTime.now().minusHours(1))
            .build();
        
        when(activeStrategyConfigRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(existing));
        
        when(activeStrategyConfigRepository.save(any(ActiveStrategyConfig.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Update parameters only
        Map<String, Object> newParams = new HashMap<>();
        newParams.put("period", 20);
        
        activeStrategyService.updateParameters(newParams);

        // Then: Should keep same strategy but update params
        verify(activeStrategyConfigRepository).save(argThat(config ->
            config.getStrategyName().equals("RSIStrategy") &&
            config.getParametersJson().contains("20")
        ));
    }

    @Test
    void testAutoSwitchFlag() throws Exception {
        // Given: Existing strategy
        ActiveStrategyConfig existing = ActiveStrategyConfig.builder()
            .id(1L)
            .strategyName("RSIStrategy")
            .parametersJson("{}")
            .build();
        
        when(activeStrategyConfigRepository.findFirstByOrderByIdAsc())
            .thenReturn(Optional.of(existing));
        
        when(activeStrategyConfigRepository.save(any(ActiveStrategyConfig.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Manual switch
        activeStrategyService.switchStrategy(
            "ManualStrategy",
            new HashMap<>(),
            "Manual test",
            false
        );

        // Then: Should mark as manual (called once)
        verify(activeStrategyConfigRepository, times(1)).save(argThat(config ->
            !config.isAutoSwitched() &&
            config.getSwitchReason().equals("Manual test")
        ));

        // When: Auto switch
        activeStrategyService.switchStrategy(
            "AutoStrategy",
            new HashMap<>(),
            "Automated switch",
            true
        );

        // Then: Should have saved twice total (once for manual, once for auto)
        verify(activeStrategyConfigRepository, times(2)).save(any(ActiveStrategyConfig.class));
        
        // Verify the second save had auto-switch flag
        verify(activeStrategyConfigRepository, atLeastOnce()).save(argThat(config ->
            config.isAutoSwitched() &&
            config.getSwitchReason().contains("Automated")
        ));
    }

    @Test
    void testPerformanceCalculationWithNoTrades() {
        // Given: No trades for strategy
        when(tradeRepository.findByStrategyNameAndTimestampBetween(
            anyString(), any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(new ArrayList<>());

        // When: Calculate performance
        StrategyPerformance result = strategyPerformanceService.calculatePerformance(
            "UnusedStrategy",
            StrategyPerformance.PerformanceMode.SHADOW,
            LocalDateTime.now().minusDays(7),
            LocalDateTime.now(),
            "2454.TW",
            new HashMap<>()
        );

        // Then: Should return null (no data)
        assertNull(result);
        verify(strategyPerformanceRepository, never()).save(any());
    }

    @Test
    void testGetRankedStrategies() {
        // Given: Multiple strategies with performance
        List<StrategyPerformance> ranked = Arrays.asList(
            StrategyPerformance.builder()
                .strategyName("Strategy1")
                .sharpeRatio(2.0)
                .build(),
            StrategyPerformance.builder()
                .strategyName("Strategy2")
                .sharpeRatio(1.5)
                .build(),
            StrategyPerformance.builder()
                .strategyName("Strategy3")
                .sharpeRatio(1.2)
                .build()
        );
        
        when(strategyPerformanceRepository.findLatestPerformanceForAllStrategies())
            .thenReturn(ranked);

        // When: Get ranked strategies
        List<StrategyPerformance> result = strategyPerformanceService.getRankedStrategies();

        // Then: Should return ordered by Sharpe ratio
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("Strategy1", result.get(0).getStrategyName());
        assertEquals(2.0, result.get(0).getSharpeRatio(), 0.01);
    }
}
