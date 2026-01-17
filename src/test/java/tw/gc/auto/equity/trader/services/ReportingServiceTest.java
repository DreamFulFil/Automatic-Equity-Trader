package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StrategyPerformance;
import tw.gc.auto.equity.trader.repositories.DailyStatisticsRepository;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import org.springframework.test.util.ReflectionTestUtils;
import tw.gc.auto.equity.trader.entities.DailyStatistics;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReportingServiceTest {

    @Mock
    EndOfDayStatisticsService endOfDayStatisticsService;
    @Mock
    DailyStatisticsRepository dailyStatisticsRepository;
    @Mock
    StrategyPerformanceRepository strategyPerformanceRepository;
    @Mock
    StrategyStockMappingRepository strategyStockMappingRepository;
    @Mock
    LlmService llmService;
    @Mock
    RiskManagementService riskManagementService;
    @Mock
    ContractScalingService contractScalingService;
    @Mock
    StockSettingsService stockSettingsService;
    @Mock
    TelegramService telegramService;
    @Mock
    TradingStateService tradingStateService;
    @Mock
    StrategyManager strategyManager;
    @Mock
    ActiveStockService activeStockService;
    @Mock
    ActiveStrategyService activeStrategyService;

    @InjectMocks
    ReportingService reportingService;

    @Test
    void sendDailyPerformanceReport_sendsMessage_andContainsRecommendations() {
        when(riskManagementService.getDailyPnL()).thenReturn(2500.0);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(contractScalingService.getMaxContracts()).thenReturn(3);
        when(activeStrategyService.getActiveStrategyName()).thenReturn("main-strat");
        when(activeStockService.getActiveStock()).thenReturn("TICK");

        StrategyPerformance shadow = StrategyPerformance.builder()
                .strategyName("shadow-strat")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("ABC")
                .sharpeRatio(1.5)
                .totalReturnPct(5.0)
                .calculatedAt(LocalDateTime.now())
                .build();

        when(strategyPerformanceRepository.findAll()).thenReturn(List.of(shadow));

        reportingService.sendDailyPerformanceReport();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(telegramService, times(1)).sendMessage(cap.capture());
        String msg = cap.getValue();
        assertTrue(msg.contains("DAILY PERFORMANCE REPORT"));
        assertTrue(msg.contains("shadow-strat") || msg.contains("/set-main-strategy"));
    }

    @Test
    void sendWeeklyPerformanceReport_sendsMessage_andContainsWeeklyHeader() {
        when(activeStrategyService.getActiveStrategyName()).thenReturn("main-strat");
        when(activeStockService.getActiveStock()).thenReturn("TICK");

        StrategyPerformance main = StrategyPerformance.builder()
                .strategyName("main-strat")
                .performanceMode(StrategyPerformance.PerformanceMode.MAIN)
                .symbol("TICK")
                .sharpeRatio(0.5)
                .totalReturnPct(1.0)
                .periodEnd(LocalDateTime.now().minusDays(1))
                .calculatedAt(LocalDateTime.now())
                .build();

        StrategyPerformance shadow = StrategyPerformance.builder()
                .strategyName("shadow-strat")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("XYZ")
                .sharpeRatio(1.2)
                .totalReturnPct(3.0)
                .periodEnd(LocalDateTime.now().minusDays(1))
                .calculatedAt(LocalDateTime.now())
                .build();

        when(strategyPerformanceRepository.findAll()).thenReturn(List.of(main, shadow));

        reportingService.sendWeeklyPerformanceReport();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(telegramService, times(1)).sendMessage(cap.capture());
        String report = cap.getValue();
        assertTrue(report.contains("WEEKLY PERFORMANCE REPORT"));
        assertTrue(report.contains("MAIN STRATEGY") || report.contains("TOP 5 SHADOW"));
    }

    @Test
    void sendDailySummary_whenProfitable_shouldShowProfitableStatus() {
        when(riskManagementService.getDailyPnL()).thenReturn(1500.0);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(contractScalingService.getMaxContracts()).thenReturn(1);

        reportingService.sendDailySummary();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessage(cap.capture());
        assertTrue(cap.getValue().contains("Profitable"));
    }

    @Test
    void sendDailySummary_whenLoss_shouldShowLossStatus() {
        when(riskManagementService.getDailyPnL()).thenReturn(-1000.0);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(contractScalingService.getMaxContracts()).thenReturn(1);

        reportingService.sendDailySummary();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessage(cap.capture());
        assertTrue(cap.getValue().contains("Loss"));
    }

    @Test
    void sendDailySummary_whenExceptionalDay_shouldCelebrate() {
        when(riskManagementService.getDailyPnL()).thenReturn(5000.0);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(contractScalingService.getMaxContracts()).thenReturn(1);

        reportingService.sendDailySummary();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessage(cap.capture());
        assertTrue(cap.getValue().contains("EXCEPTIONAL DAY"));
    }

    @Test
    void calculateDailyStatistics_success_callsDependencies() {
        ReportingService spy = spy(reportingService);
        doNothing().when(spy).generateDailyInsight(any(), anyString());
        doNothing().when(spy).sendDailyPerformanceReport();

        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(strategyManager.getActiveStrategies()).thenReturn(List.of(mock(tw.gc.auto.equity.trader.strategy.IStrategy.class)));

        spy.calculateDailyStatistics();

        verify(endOfDayStatisticsService).calculateAndSaveStatisticsForDay(any(LocalDate.class), eq("2330.TW"));
        verify(spy).sendDailyPerformanceReport();
    }

    @Test
    void calculateDailyStatistics_exception_isCaught() {
        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        doThrow(new RuntimeException("boom")).when(endOfDayStatisticsService)
                .calculateAndSaveStatisticsForDay(any(LocalDate.class), anyString());

        assertDoesNotThrow(() -> reportingService.calculateDailyStatistics());
    }

    @Test
    void generateWeeklyReport_successAndFailure_areCaught() {
        ReportingService spy = spy(reportingService);
        doNothing().when(spy).sendWeeklyPerformanceReport();
        spy.generateWeeklyReport();
        verify(spy).sendWeeklyPerformanceReport();

        reset(spy);
        doThrow(new RuntimeException("boom")).when(spy).sendWeeklyPerformanceReport();
        assertDoesNotThrow(spy::generateWeeklyReport);
    }

    @Test
    void generateDailyInsight_success_savesInsight() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Taipei"));
        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(date)
                .symbol("2330.TW")
                .totalPnL(1234.0)
                .totalTrades(5)
                .winRate(60.0)
                .build();

        when(dailyStatisticsRepository.findByTradeDateAndSymbol(date, "2330.TW")).thenReturn(Optional.of(stats));
        when(llmService.generateInsight(anyString())).thenReturn("insight");

        reportingService.generateDailyInsight(date, "2330.TW");

        verify(dailyStatisticsRepository).save(stats);
        assertEquals("insight", stats.getLlamaInsight());
        assertNotNull(stats.getInsightGeneratedAt());
    }

    @Test
    void generateDailyInsight_whenNoStats_isCaught() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Taipei"));
        when(dailyStatisticsRepository.findByTradeDateAndSymbol(date, "2330.TW")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> reportingService.generateDailyInsight(date, "2330.TW"));
        verify(dailyStatisticsRepository, never()).save(any());
    }

    @Test
    void sendDailyPerformanceReport_stockMode_includesInsightAndRecommendations() {
        when(riskManagementService.getDailyPnL()).thenReturn(3500.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(100.0);
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");
        when(activeStrategyService.getActiveStrategyName()).thenReturn("main");

        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(contractScalingService.getLastEquity()).thenReturn(2_000_000.0);
        when(stockSettingsService.getBaseStockQuantity(anyDouble())).thenReturn(200);

        tw.gc.auto.equity.trader.entities.StockSettings s = new tw.gc.auto.equity.trader.entities.StockSettings();
        s.setShares(100);
        s.setShareIncrement(5);
        when(stockSettingsService.getSettings()).thenReturn(s);

        DailyStatistics stats = DailyStatistics.builder()
                .tradeDate(LocalDate.now(ZoneId.of("Asia/Taipei")))
                .symbol("2330.TW")
                .llamaInsight("hello")
                .build();
        when(dailyStatisticsRepository.findByTradeDateAndSymbol(any(LocalDate.class), eq("2330.TW")))
                .thenReturn(Optional.of(stats));

        StrategyPerformance best = StrategyPerformance.builder()
                .strategyName("shadow")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("2454.TW")
                .sharpeRatio(2.0)
                .totalReturnPct(10.0)
                .calculatedAt(LocalDateTime.now())
                .build();
        StrategyPerformance main = StrategyPerformance.builder()
                .strategyName("main")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("2330.TW")
                .sharpeRatio(1.0)
                .totalReturnPct(5.0)
                .calculatedAt(LocalDateTime.now())
                .build();
        when(strategyPerformanceRepository.findAll()).thenReturn(List.of(best, main));

        reportingService.sendDailyPerformanceReport();

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessage(cap.capture());
        String msg = cap.getValue();
        assertTrue(msg.contains("Mode: STOCK"));
        assertTrue(msg.contains("AI Insight"));
        assertTrue(msg.contains("EXCEPTIONAL DAY"));
        assertTrue(msg.contains("/set-main-strategy"));
        assertTrue(msg.contains("/change-stock"));
    }

    @Test
    void sendDailyPerformanceReport_insightLookupThrows_isIgnored() {
        when(riskManagementService.getDailyPnL()).thenReturn(0.0);
        when(riskManagementService.getWeeklyPnL()).thenReturn(0.0);
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(contractScalingService.getMaxContracts()).thenReturn(1);
        when(activeStrategyService.getActiveStrategyName()).thenReturn("main");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");
        when(contractScalingService.getLastEquity()).thenReturn(1_000_000.0);

        when(activeStockService.getActiveSymbol(anyString())).thenReturn("2330.TW");
        when(dailyStatisticsRepository.findByTradeDateAndSymbol(any(LocalDate.class), anyString()))
                .thenThrow(new RuntimeException("db"));

        reportingService.sendDailyPerformanceReport();

        verify(telegramService).sendMessage(contains("DAILY PERFORMANCE REPORT"));
    }

    @Test
    void getShadowPerformances_sortsBySharpeDesc() {
        StrategyPerformance low = StrategyPerformance.builder()
                .strategyName("s1")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("A")
                .sharpeRatio(0.5)
                .calculatedAt(LocalDateTime.now())
                .build();
        StrategyPerformance high = StrategyPerformance.builder()
                .strategyName("s2")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("B")
                .sharpeRatio(2.0)
                .calculatedAt(LocalDateTime.now())
                .build();
        when(strategyPerformanceRepository.findAll()).thenReturn(List.of(low, high));

        @SuppressWarnings("unchecked")
        List<StrategyPerformance> sorted = (List<StrategyPerformance>) ReflectionTestUtils.invokeMethod(reportingService, "getShadowPerformances", 24);

        assertEquals("s2", sorted.get(0).getStrategyName());
    }

    @Test
    void generateDailyRecommendations_whenNoChangeSuggested_returnsDefault() {
        StrategyPerformance mainShadow = StrategyPerformance.builder()
                .strategyName("main")
                .performanceMode(StrategyPerformance.PerformanceMode.SHADOW)
                .symbol("2330.TW")
                .sharpeRatio(1.0)
                .totalReturnPct(1.0)
                .calculatedAt(LocalDateTime.now())
                .build();

        String rec = (String) ReflectionTestUtils.invokeMethod(reportingService,
                "generateDailyRecommendations", "main", "2330.TW", List.of(mainShadow));

        assertTrue(rec.contains("performing well"));
    }

    @Test
    void generateWeeklyRecommendations_coversAllBranches() throws Exception {
        Class<?> aggClass = Class.forName("tw.gc.auto.equity.trader.services.ReportingService$WeeklyAggregate");
        var ctor = aggClass.getDeclaredConstructor(String.class, String.class, double.class, double.class, double.class, double.class, double.class, int.class);
        ctor.setAccessible(true);

        Object mainAgg = ctor.newInstance("main", "2330.TW", 1.0, 1.0, -5.0, 50.0, 0.3, 7);
        Object bestShadow = ctor.newInstance("shadow", "2454.TW", 2.0, 2.0, -2.0, 60.0, 0.7, 7);
        Object weakShadow = ctor.newInstance("weak", "", 0.0, 0.0, 0.0, 0.0, 0.5, 1);

        // shadowAggs empty
        String noShadow = (String) ReflectionTestUtils.invokeMethod(reportingService, "generateWeeklyRecommendations", mainAgg, List.of());
        assertTrue(noShadow.contains("No shadow data"));

        // bestShadow null (consistency <= 0.6) -> default continue
        String cont = (String) ReflectionTestUtils.invokeMethod(reportingService, "generateWeeklyRecommendations", mainAgg, List.of(weakShadow));
        assertTrue(cont.contains("Continue"));

        // bestShadow + mainAgg -> strategy + stock + variance warnings
        @SuppressWarnings("unchecked")
        String recs = (String) ReflectionTestUtils.invokeMethod(reportingService, "generateWeeklyRecommendations", mainAgg, List.of(bestShadow));
        assertTrue(recs.contains("/set-main-strategy"));
        assertTrue(recs.contains("/change-stock"));
        assertTrue(recs.contains("HIGH VARIANCE"));
    }

    @Test
    void calculateConsistencyScore_and_calculateTrend_coverBranches() {
        StrategyPerformance p1 = StrategyPerformance.builder()
                .strategyName("s")
                .performanceMode(StrategyPerformance.PerformanceMode.MAIN)
                .symbol("X")
                .totalReturnPct(null)
                .periodEnd(LocalDateTime.now().minusDays(3))
                .sharpeRatio(1.0)
                .build();

        double zeroSize = (double) ReflectionTestUtils.invokeMethod(reportingService, "calculateConsistencyScore", List.of(p1));
        assertEquals(0.0, zeroSize);

        StrategyPerformance p2 = StrategyPerformance.builder()
                .strategyName("s")
                .performanceMode(StrategyPerformance.PerformanceMode.MAIN)
                .symbol("X")
                .totalReturnPct(1.0)
                .periodEnd(LocalDateTime.now().minusDays(2))
                .sharpeRatio(1.0)
                .build();
        StrategyPerformance p3 = StrategyPerformance.builder()
                .strategyName("s")
                .performanceMode(StrategyPerformance.PerformanceMode.MAIN)
                .symbol("X")
                .totalReturnPct(3.0)
                .periodEnd(LocalDateTime.now().minusDays(1))
                .sharpeRatio(2.0)
                .build();

        double score = (double) ReflectionTestUtils.invokeMethod(reportingService, "calculateConsistencyScore", List.of(p2, p3));
        assertTrue(score > 0.0);

        String insufficient = (String) ReflectionTestUtils.invokeMethod(reportingService, "calculateTrend", List.of(p2, p3));
        assertEquals("INSUFFICIENT_DATA", insufficient);

        // improving
        StrategyPerformance t1 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(4)).sharpeRatio(0.1).build();
        StrategyPerformance t2 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(3)).sharpeRatio(0.1).build();
        StrategyPerformance t3 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(2)).sharpeRatio(1.0).build();
        StrategyPerformance t4 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(1)).sharpeRatio(1.0).build();
        assertTrue(((String) ReflectionTestUtils.invokeMethod(reportingService, "calculateTrend", List.of(t1, t2, t3, t4))).contains("IMPROVING"));

        // declining
        StrategyPerformance d1 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(4)).sharpeRatio(1.0).build();
        StrategyPerformance d2 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(3)).sharpeRatio(1.0).build();
        StrategyPerformance d3 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(2)).sharpeRatio(0.0).build();
        StrategyPerformance d4 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(1)).sharpeRatio(0.0).build();
        assertTrue(((String) ReflectionTestUtils.invokeMethod(reportingService, "calculateTrend", List.of(d1, d2, d3, d4))).contains("DECLINING"));

        // stable
        StrategyPerformance s1 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(4)).sharpeRatio(0.5).build();
        StrategyPerformance s2 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(3)).sharpeRatio(0.5).build();
        StrategyPerformance s3 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(2)).sharpeRatio(0.55).build();
        StrategyPerformance s4 = StrategyPerformance.builder().periodEnd(LocalDateTime.now().minusDays(1)).sharpeRatio(0.55).build();
        assertTrue(((String) ReflectionTestUtils.invokeMethod(reportingService, "calculateTrend", List.of(s1, s2, s3, s4))).contains("STABLE"));
    }

    @Test
    void sendWeeklyPerformanceReport_coversNoMainAndNoShadowPaths() {
        when(activeStrategyService.getActiveStrategyName()).thenReturn("main");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");

        // No perfs at all -> mainAggregate null and shadow empty
        when(strategyPerformanceRepository.findAll()).thenReturn(List.of());

        reportingService.sendWeeklyPerformanceReport();

        verify(telegramService).sendMessage(argThat(msg -> msg.contains("No main strategy data") && msg.contains("No shadow data")));
    }

    @Test
    void sendWeeklyPerformanceReport_coversTop5ShadowLoop() {
        when(activeStrategyService.getActiveStrategyName()).thenReturn("main");
        when(activeStockService.getActiveStock()).thenReturn("2330.TW");

        StrategyPerformance main = StrategyPerformance.builder()
                .strategyName("main")
                .performanceMode(StrategyPerformance.PerformanceMode.MAIN)
                .symbol("2330.TW")
                .sharpeRatio(0.5)
                .totalReturnPct(1.0)
                .winRatePct(50.0)
                .maxDrawdownPct(-1.0)
                .periodEnd(LocalDateTime.now().minusDays(1))
                .calculatedAt(LocalDateTime.now())
                .build();

        // 6 shadows -> loop should cap at 5
        StrategyPerformance sh1 = StrategyPerformance.builder().strategyName("s1").performanceMode(StrategyPerformance.PerformanceMode.SHADOW).symbol("A").sharpeRatio(1.0).totalReturnPct(1.0).winRatePct(50.0).maxDrawdownPct(-1.0).periodEnd(LocalDateTime.now().minusDays(1)).calculatedAt(LocalDateTime.now()).build();
        StrategyPerformance sh2 = StrategyPerformance.builder().strategyName("s2").performanceMode(StrategyPerformance.PerformanceMode.SHADOW).symbol("B").sharpeRatio(1.1).totalReturnPct(1.0).winRatePct(50.0).maxDrawdownPct(-1.0).periodEnd(LocalDateTime.now().minusDays(1)).calculatedAt(LocalDateTime.now()).build();
        StrategyPerformance sh3 = StrategyPerformance.builder().strategyName("s3").performanceMode(StrategyPerformance.PerformanceMode.SHADOW).symbol("C").sharpeRatio(1.2).totalReturnPct(1.0).winRatePct(50.0).maxDrawdownPct(-1.0).periodEnd(LocalDateTime.now().minusDays(1)).calculatedAt(LocalDateTime.now()).build();
        StrategyPerformance sh4 = StrategyPerformance.builder().strategyName("s4").performanceMode(StrategyPerformance.PerformanceMode.SHADOW).symbol("D").sharpeRatio(1.3).totalReturnPct(1.0).winRatePct(50.0).maxDrawdownPct(-1.0).periodEnd(LocalDateTime.now().minusDays(1)).calculatedAt(LocalDateTime.now()).build();
        StrategyPerformance sh5 = StrategyPerformance.builder().strategyName("s5").performanceMode(StrategyPerformance.PerformanceMode.SHADOW).symbol("E").sharpeRatio(1.4).totalReturnPct(1.0).winRatePct(50.0).maxDrawdownPct(-1.0).periodEnd(LocalDateTime.now().minusDays(1)).calculatedAt(LocalDateTime.now()).build();
        StrategyPerformance sh6 = StrategyPerformance.builder().strategyName("s6").performanceMode(StrategyPerformance.PerformanceMode.SHADOW).symbol("F").sharpeRatio(1.5).totalReturnPct(1.0).winRatePct(50.0).maxDrawdownPct(-1.0).periodEnd(LocalDateTime.now().minusDays(1)).calculatedAt(LocalDateTime.now()).build();

        when(strategyPerformanceRepository.findAll()).thenReturn(List.of(main, sh1, sh2, sh3, sh4, sh5, sh6));

        reportingService.sendWeeklyPerformanceReport();

        verify(telegramService).sendMessage(argThat(msg -> msg.contains("TOP 5 SHADOW") && msg.contains("1.") && !msg.contains("6.")));
    }
}
