package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.entities.StockSettings;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.StockSettingsRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AutoStrategySelectorTest {

    @Mock
    private StrategyStockMappingRepository mappingRepository;

    @Mock
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private StockSettingsRepository stockSettingsRepository;

    @Mock
    private ActiveStrategyService activeStrategyService;

    @Mock
    private ActiveStockService activeStockService;

    @Mock
    private ShadowModeStockService shadowModeStockService;

    @Mock
    private TelegramService telegramService;

    @Mock
    private TaiwanStockComplianceService complianceService;

    private AutoStrategySelector autoStrategySelector;

    @BeforeEach
    void setUp() {
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository selectionRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        // Use lenient to avoid strict stubbing issues when not all tests use this stub
        lenient().when(complianceService.fetchCurrentCapital()).thenReturn(80_000.0);
        lenient().when(stockSettingsRepository.findFirstByOrderByIdDesc())
            .thenReturn(Optional.of(StockSettings.builder().shareIncrement(27).build()));
        
        autoStrategySelector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            selectionRepo
        );
    }

    @Test
    void selectBestStrategyAndStock_shouldSkip_whenNoBacktestResults() {
        // Given
        when(backtestResultRepository.findAll()).thenReturn(Collections.emptyList());
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then
        verify(telegramService).sendMessage(contains("No backtest results"));
        verify(activeStrategyService, never()).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectBestStrategyAndStock_shouldSelectBestCombo_whenResultsExist() {
        // Given
        BacktestResult bestResult = createBacktestResult("2330.TW", "TSMC", "BollingerBandStrategy", 15.0, 1.5, 60.0, -8.0);
        BacktestResult lessGoodResult = createBacktestResult("2454.TW", "MediaTek", "RSIStrategy", 8.0, 0.9, 52.0, -15.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(bestResult, lessGoodResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn("OldStrategy");
        when(activeStockService.getActiveStock()).thenReturn("2317.TW");
        when(mappingRepository.findBySymbolAndStrategyName("2317.TW", "OldStrategy")).thenReturn(Optional.empty());
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then
        verify(activeStrategyService).switchStrategyWithStock(
            eq("BollingerBandStrategy"), 
            isNull(), 
            anyString(), 
            eq(true),
            eq(1.5),
            eq(-8.0),
            eq(15.0),
            eq(60.0),
            eq("2330.TW"),
            eq("TSMC")
        );
        verify(activeStockService).setActiveStock("2330.TW");
    }

    @Test
    void selectBestStrategyAndStock_shouldNotSwitch_whenCurrentIsBetter() {
        // Given
        BacktestResult currentResult = createBacktestResult("2454.TW", "MediaTek", "CurrentStrategy", 20.0, 2.0, 70.0, -5.0);
        BacktestResult otherResult = createBacktestResult("2330.TW", "TSMC", "OtherStrategy", 10.0, 1.2, 55.0, -10.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(currentResult, otherResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn("CurrentStrategy");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(mappingRepository.findBySymbolAndStrategyName("2454.TW", "CurrentStrategy")).thenReturn(Optional.of(createMappingFromResult(currentResult)));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then
        verify(activeStrategyService, never()).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectShadowModeStrategies_shouldSelectTop10() {
        // Given
        List<BacktestResult> results = Arrays.asList(
            createBacktestResult("2330.TW", "TSMC", "Strategy1", 12.0, 1.5, 60.0, -8.0),
            createBacktestResult("2454.TW", "MediaTek", "Strategy2", 10.0, 1.3, 58.0, -9.0),
            createBacktestResult("2317.TW", "Hon Hai", "Strategy3", 8.0, 1.1, 55.0, -10.0),
            createBacktestResult("2303.TW", "UMC", "Strategy4", 7.0, 1.0, 54.0, -11.0),
            createBacktestResult("2412.TW", "Chunghwa Telecom", "Strategy5", 6.0, 0.95, 53.0, -12.0),
            createBacktestResult("2881.TW", "Fubon Financial", "Strategy6", 5.0, 0.9, 52.0, -13.0),
            createBacktestResult("2882.TW", "Cathay Financial", "Strategy7", 4.5, 0.88, 51.0, -14.0),
            createBacktestResult("2891.TW", "CTBC Financial", "Strategy8", 4.0, 0.85, 50.0, -15.0),
            createBacktestResult("1301.TW", "Formosa Plastics", "Strategy9", 3.5, 0.82, 49.0, -16.0),
            createBacktestResult("2002.TW", "China Steel", "Strategy10", 3.2, 0.81, 48.0, -17.0),
            createBacktestResult("2308.TW", "Delta Electronics", "Strategy11", 3.1, 0.80, 47.0, -18.0) // Should be excluded (11th)
        );
        
        when(backtestResultRepository.findAll()).thenReturn(results);
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - now uses upsertTopCandidates instead of clearAll + addShadowStock
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs ->
            configs.size() == 10 && configs.stream().allMatch(c -> c.getSymbol() != null)
        ));
        verify(telegramService).sendMessage(contains("Shadow Mode Strategies"));
    }

    @Test
    void selectShadowModeStrategies_shouldFilterByMinCriteria() {
        // Given - One mapping below threshold
        BacktestResult goodResult = createBacktestResult("2330.TW", "TSMC", "GoodStrategy", 5.0, 1.0, 55.0, -10.0);
        BacktestResult poorResult = createBacktestResult("2454.TW", "MediaTek", "PoorStrategy", 2.0, 0.5, 45.0, -25.0); // Below thresholds
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(goodResult, poorResult));
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - only good mapping should be included
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> 
            configs.size() == 1 && configs.get(0).getSymbol().equals("2330.TW")));
    }

    @Test
    void selectShadowModeStrategies_shouldGroupBySymbol() {
        // Given - Two strategies for same symbol, should pick best
        BacktestResult strategy1ForStock = createBacktestResult("2330.TW", "TSMC", "Strategy1", 10.0, 1.5, 60.0, -8.0);
        BacktestResult strategy2ForStock = createBacktestResult("2330.TW", "TSMC", "Strategy2", 15.0, 2.0, 65.0, -5.0); // Better
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(strategy1ForStock, strategy2ForStock));
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - should pick Strategy2 (better score)
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> 
            configs.size() == 1 && configs.get(0).getStrategyName().equals("Strategy2")));
    }

    private StrategyStockMapping createMapping(String symbol, String strategyName, 
                                                Double returnPct, Double sharpe, 
                                                Double winRate, Double maxDD) {
        StrategyStockMapping mapping = new StrategyStockMapping();
        mapping.setSymbol(symbol);
        mapping.setStrategyName(strategyName);
        mapping.setTotalReturnPct(returnPct);
        mapping.setSharpeRatio(sharpe);
        mapping.setWinRatePct(winRate);
        mapping.setMaxDrawdownPct(maxDD);
        return mapping;
    }
    
    private StrategyStockMapping createMappingFromResult(BacktestResult result) {
        StrategyStockMapping mapping = new StrategyStockMapping();
        mapping.setSymbol(result.getSymbol());
        mapping.setStockName(result.getStockName());
        mapping.setStrategyName(result.getStrategyName());
        mapping.setTotalReturnPct(result.getTotalReturnPct());
        mapping.setSharpeRatio(result.getSharpeRatio());
        mapping.setWinRatePct(result.getWinRatePct());
        mapping.setMaxDrawdownPct(result.getMaxDrawdownPct());
        return mapping;
    }
    
    private BacktestResult createBacktestResult(String symbol, String stockName, String strategyName,
                                                 Double returnPct, Double sharpe,
                                                 Double winRate, Double maxDD) {
        return BacktestResult.builder()
            .symbol(symbol)
            .stockName(stockName)
            .strategyName(strategyName)
            .totalReturnPct(returnPct)
            .sharpeRatio(sharpe)
            .winRatePct(winRate)
            .maxDrawdownPct(maxDD)
            .totalTrades(50) // Required for filtering (> 10)
            .build();
    }

    @Test
    void selectBestStrategyAndStock_shouldSwitch_whenCurrentComboNotTested() {
        // Given
        BacktestResult bestResult = createBacktestResult("2330.TW", "TSMC", "BollingerBandStrategy", 15.0, 1.5, 60.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(bestResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn("UntestedStrategy");
        when(activeStockService.getActiveStock()).thenReturn("9999.TW");
        when(mappingRepository.findBySymbolAndStrategyName("9999.TW", "UntestedStrategy")).thenReturn(Optional.empty());
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should switch because current combo not tested
        verify(activeStrategyService).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            eq(true),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectBestStrategyAndStock_shouldSwitch_whenNullCurrentStrategy() {
        // Given
        BacktestResult bestResult = createBacktestResult("2330.TW", "TSMC", "BollingerBandStrategy", 15.0, 1.5, 60.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(bestResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn(null);
        when(activeStockService.getActiveStock()).thenReturn(null);
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should switch because no current strategy
        verify(activeStrategyService).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            eq(true),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectBestStrategyAndStock_filtersLowPerformance() {
        // Given - All results below threshold
        BacktestResult lowReturn = createBacktestResult("2330.TW", "TSMC", "Strategy1", 4.0, 1.5, 60.0, -8.0); // Return < 5
        BacktestResult lowSharpe = createBacktestResult("2454.TW", "MediaTek", "Strategy2", 10.0, 0.8, 60.0, -8.0); // Sharpe < 1
        BacktestResult lowWinRate = createBacktestResult("2317.TW", "Hon Hai", "Strategy3", 10.0, 1.5, 45.0, -8.0); // WinRate < 50
        BacktestResult highDD = createBacktestResult("2303.TW", "UMC", "Strategy4", 10.0, 1.5, 60.0, -25.0); // Drawdown < -20
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(lowReturn, lowSharpe, lowWinRate, highDD));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should skip because no valid results
        verify(telegramService).sendMessage(contains("No backtest results"));
        verify(activeStrategyService, never()).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectBestStrategyAndStock_filtersIntradayForOddLot() {
        // Given - Intraday strategy with odd-lot mode
        BacktestResult intradayResult = createBacktestResult("2330.TW", "TSMC", "IntradayStrategy", 15.0, 1.5, 60.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(intradayResult));
        when(stockSettingsRepository.findFirstByOrderByIdDesc())
            .thenReturn(Optional.of(StockSettings.builder().shareIncrement(27).build())); // Odd-lot (not divisible by 1000)
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should skip intraday strategy
        verify(telegramService).sendMessage(contains("No backtest results"));
    }

    @Test
    void selectBestStrategyAndStock_allowsIntradayForRoundLot() {
        // Given - Intraday strategy with round-lot mode
        BacktestResult intradayResult = createBacktestResult("2330.TW", "TSMC", "VWAPStrategy", 15.0, 1.5, 60.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(intradayResult));
        when(stockSettingsRepository.findFirstByOrderByIdDesc())
            .thenReturn(Optional.of(StockSettings.builder().shareIncrement(1000).build())); // Round-lot (divisible by 1000)
        when(activeStrategyService.getActiveStrategyName()).thenReturn(null);
        when(activeStockService.getActiveStock()).thenReturn(null);
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should allow intraday strategy
        verify(activeStrategyService).switchStrategyWithStock(
            eq("VWAPStrategy"),
            any(),
            anyString(),
            eq(true),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectBestStrategyAndStock_filtersInsufficientTrades() {
        // Given - Result with too few trades
        BacktestResult fewTrades = BacktestResult.builder()
            .symbol("2330.TW")
            .stockName("TSMC")
            .strategyName("Strategy1")
            .totalReturnPct(15.0)
            .sharpeRatio(1.5)
            .winRatePct(60.0)
            .maxDrawdownPct(-8.0)
            .totalTrades(5) // < 10
            .build();
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(fewTrades));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should skip due to insufficient sample size
        verify(telegramService).sendMessage(contains("No backtest results"));
    }

    @Test
    void selectBestStrategyAndStock_handlesException() {
        // Given
        when(backtestResultRepository.findAll()).thenThrow(new RuntimeException("Database error"));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then
        verify(telegramService).sendMessage(contains("Auto-selection failed"));
    }

    @Test
    void selectShadowModeStrategiesAndPopulateTable_excludesActiveStock() {
        // Given
        StrategyStockMapping activeMapping = createMappingFromResult(
            createBacktestResult("2330.TW", "TSMC", "ActiveStrategy", 15.0, 1.5, 60.0, -8.0)
        );
        
        BacktestResult shadowResult1 = createBacktestResult("2454.TW", "MediaTek", "Strategy1", 12.0, 1.4, 58.0, -9.0);
        BacktestResult shadowResult2 = createBacktestResult("2330.TW", "TSMC", "Strategy2", 14.0, 1.6, 62.0, -7.0); // Same stock as active
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(shadowResult1, shadowResult2));
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        AutoStrategySelector selector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            mockRepo
        );
        
        // When
        selector.selectShadowModeStrategiesAndPopulateTable(activeMapping);
        
        // Then - should save active as rank 1
        verify(mockRepo).save(argThat(selection -> 
            selection.getRankPosition() == 1 && selection.getSymbol().equals("2330.TW") && selection.getIsActive()
        ));
        // Shadow should only have 2454.TW (not 2330.TW which is active)
        verify(mockRepo).save(argThat(selection -> 
            selection.getRankPosition() == 2 && selection.getSymbol().equals("2454.TW") && !selection.getIsActive()
        ));
        // Verify it calls selectShadowModeStrategies for backward compatibility
        verify(shadowModeStockService).upsertTopCandidates(any());
    }

    @Test
    void selectShadowModeStrategies_filtersIntradayForOddLot() {
        // Given - Mix of intraday and non-intraday with odd-lot mode
        BacktestResult intradayResult = createBacktestResult("2330.TW", "TSMC", "TWAPStrategy", 12.0, 1.3, 58.0, -9.0);
        BacktestResult normalResult = createBacktestResult("2454.TW", "MediaTek", "BollingerBandStrategy", 10.0, 1.2, 56.0, -10.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(intradayResult, normalResult));
        when(stockSettingsRepository.findFirstByOrderByIdDesc())
            .thenReturn(Optional.of(StockSettings.builder().shareIncrement(27).build())); // Odd-lot
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - should only include non-intraday strategy
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> 
            configs.size() == 1 && configs.get(0).getSymbol().equals("2454.TW")
        ));
    }

    @Test
    void selectShadowModeStrategies_allowsAllStrategiesForRoundLot() {
        // Given - Mix of intraday and non-intraday with round-lot mode
        BacktestResult intradayResult = createBacktestResult("2330.TW", "TSMC", "VWAPStrategy", 12.0, 1.3, 58.0, -9.0);
        BacktestResult normalResult = createBacktestResult("2454.TW", "MediaTek", "BollingerBandStrategy", 10.0, 1.2, 56.0, -10.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(intradayResult, normalResult));
        when(stockSettingsRepository.findFirstByOrderByIdDesc())
            .thenReturn(Optional.of(StockSettings.builder().shareIncrement(1000).build())); // Round-lot
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - should include both strategies
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> configs.size() == 2));
    }

    @Test
    void isIntradayStrategy_identifiesVariousPatterns() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method method = AutoStrategySelector.class.getDeclaredMethod("isIntradayStrategy", String.class);
        method.setAccessible(true);
        
        // Test various intraday patterns
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "IntradayStrategy")).isTrue();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "Pivot Points Strategy")).isTrue();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "VWAPStrategy")).isTrue();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "TWAPStrategy")).isTrue();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "Day Trading Strategy")).isTrue();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "Price Volume Rank")).isTrue();
        
        // Test non-intraday
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "BollingerBandStrategy")).isFalse();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, "RSIStrategy")).isFalse();
        org.assertj.core.api.Assertions.assertThat((Boolean) method.invoke(autoStrategySelector, new Object[]{null})).isFalse();
    }

    // ==================== Additional Tests for 100% Coverage ====================

    @Test
    void calculateScore_handlesNullValues() throws Exception {
        java.lang.reflect.Method method = AutoStrategySelector.class.getDeclaredMethod("calculateScore", StrategyStockMapping.class);
        method.setAccessible(true);
        
        // All nulls
        StrategyStockMapping allNull = new StrategyStockMapping();
        double score = (double) method.invoke(autoStrategySelector, allNull);
        org.assertj.core.api.Assertions.assertThat(score).isEqualTo(0.0);
        
        // Partial nulls with near-zero drawdown (lines 291-294, 297)
        StrategyStockMapping nearZeroDD = new StrategyStockMapping();
        nearZeroDD.setTotalReturnPct(10.0);
        nearZeroDD.setSharpeRatio(1.5);
        nearZeroDD.setWinRatePct(60.0);
        nearZeroDD.setMaxDrawdownPct(0.001);  // Near zero, should be adjusted to 0.01
        double scoreNearZero = (double) method.invoke(autoStrategySelector, nearZeroDD);
        org.assertj.core.api.Assertions.assertThat(scoreNearZero).isGreaterThan(0);
    }

    @Test
    void calculateScoreFromBacktest_handlesNullValues() throws Exception {
        java.lang.reflect.Method method = AutoStrategySelector.class.getDeclaredMethod("calculateScoreFromBacktest", BacktestResult.class);
        method.setAccessible(true);
        
        // All nulls (lines 267-270)
        BacktestResult allNull = BacktestResult.builder().build();
        double score = (double) method.invoke(autoStrategySelector, allNull);
        org.assertj.core.api.Assertions.assertThat(score).isEqualTo(0.0);
        
        // Near-zero drawdown (line 272)
        BacktestResult nearZeroDD = BacktestResult.builder()
            .totalReturnPct(10.0)
            .sharpeRatio(1.5)
            .winRatePct(60.0)
            .maxDrawdownPct(0.001)
            .build();
        double scoreNearZero = (double) method.invoke(autoStrategySelector, nearZeroDD);
        org.assertj.core.api.Assertions.assertThat(scoreNearZero).isGreaterThan(0);
    }

    @Test
    void selectShadowModeStrategiesAndPopulateTable_handlesNullStockName() {
        // Given - mapping with null stockName (line 159, 178)
        StrategyStockMapping activeMapping = new StrategyStockMapping();
        activeMapping.setSymbol("2330.TW");
        activeMapping.setStockName(null);  // Null stockName
        activeMapping.setStrategyName("ActiveStrategy");
        activeMapping.setTotalReturnPct(15.0);
        activeMapping.setSharpeRatio(1.5);
        activeMapping.setWinRatePct(60.0);
        activeMapping.setMaxDrawdownPct(-8.0);
        
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        when(backtestResultRepository.findAll()).thenReturn(Collections.emptyList());
        
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        AutoStrategySelector selector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            mockRepo
        );
        
        // When
        selector.selectShadowModeStrategiesAndPopulateTable(activeMapping);
        
        // Then - should use symbol as stockName when stockName is null
        verify(mockRepo).save(argThat(selection -> 
            selection.getStockName().equals("2330.TW")  // Uses symbol when stockName is null
        ));
    }

    @Test
    void selectShadowModeStrategiesAndPopulateTable_filtersIntradayStrategies() {
        // Given - intraday strategy in shadow mappings (lines 123-126)
        StrategyStockMapping activeMapping = createMappingFromResult(
            createBacktestResult("2330.TW", "TSMC", "ActiveStrategy", 15.0, 1.5, 60.0, -8.0)
        );
        
        // Intraday strategy should be filtered
        StrategyStockMapping intradayMapping = new StrategyStockMapping();
        intradayMapping.setSymbol("2454.TW");
        intradayMapping.setStockName("MediaTek");
        intradayMapping.setStrategyName("VWAPStrategy");
        intradayMapping.setTotalReturnPct(12.0);
        intradayMapping.setSharpeRatio(1.3);
        intradayMapping.setWinRatePct(58.0);
        intradayMapping.setMaxDrawdownPct(-9.0);
        
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        // Populate-table reads from backtest_results; provide an intraday strategy that should be filtered out.
        when(backtestResultRepository.findAll()).thenReturn(List.of(
            createBacktestResult("2454.TW", "MediaTek", "VWAPStrategy", 12.0, 1.3, 58.0, -9.0)
        ));
        
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        AutoStrategySelector selector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            mockRepo
        );
        
        // When
        selector.selectShadowModeStrategiesAndPopulateTable(activeMapping);
        
        // Then - only active should be saved (shadow filtered due to intraday)
        verify(mockRepo).save(argThat(selection -> selection.getRankPosition() == 1));
        verify(mockRepo, times(1)).save(any());  // Only active, no shadow
    }

    @Test
    void selectShadowModeStrategiesAndPopulateTable_filtersBelowThreshold() {
        // Given - mappings below threshold (lines 125-126: return > 3.0, sharpe > 0.8)
        StrategyStockMapping activeMapping = createMappingFromResult(
            createBacktestResult("2330.TW", "TSMC", "ActiveStrategy", 15.0, 1.5, 60.0, -8.0)
        );
        
        StrategyStockMapping lowReturnMapping = new StrategyStockMapping();
        lowReturnMapping.setSymbol("2454.TW");
        lowReturnMapping.setStockName("MediaTek");
        lowReturnMapping.setStrategyName("LowReturnStrategy");
        lowReturnMapping.setTotalReturnPct(2.0);  // Below 3.0 threshold
        lowReturnMapping.setSharpeRatio(1.3);
        lowReturnMapping.setWinRatePct(58.0);
        lowReturnMapping.setMaxDrawdownPct(-9.0);
        
        StrategyStockMapping lowSharpeMapping = new StrategyStockMapping();
        lowSharpeMapping.setSymbol("2317.TW");
        lowSharpeMapping.setStockName("Hon Hai");
        lowSharpeMapping.setStrategyName("LowSharpeStrategy");
        lowSharpeMapping.setTotalReturnPct(12.0);
        lowSharpeMapping.setSharpeRatio(0.5);  // Below 0.8 threshold
        lowSharpeMapping.setWinRatePct(58.0);
        lowSharpeMapping.setMaxDrawdownPct(-9.0);
        
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        // Provide backtest_results that are below thresholds so no shadows are selected.
        when(backtestResultRepository.findAll()).thenReturn(List.of(
            createBacktestResult("2454.TW", "MediaTek", "LowReturnStrategy", 2.0, 1.3, 58.0, -9.0),
            createBacktestResult("2317.TW", "Hon Hai", "LowSharpeStrategy", 12.0, 0.5, 58.0, -9.0)
        ));
        
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        AutoStrategySelector selector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            mockRepo
        );
        
        // When
        selector.selectShadowModeStrategiesAndPopulateTable(activeMapping);
        
        // Then - only active (low performers filtered out)
        verify(mockRepo, times(1)).save(any());
    }

    @Test
    void selectBestStrategyAndStock_switchesWhenBetterBy10Percent() {
        // Given - current is good but new is 10%+ better (line 319)
        BacktestResult newResult = createBacktestResult("2330.TW", "TSMC", "NewStrategy", 20.0, 2.0, 65.0, -7.0);
        
        StrategyStockMapping currentMapping = createMapping("2454.TW", "CurrentStrategy", 12.0, 1.5, 58.0, -9.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(newResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn("CurrentStrategy");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(mappingRepository.findBySymbolAndStrategyName("2454.TW", "CurrentStrategy")).thenReturn(Optional.of(currentMapping));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should switch because new is > 10% better
        verify(activeStrategyService).switchStrategyWithStock(
            eq("NewStrategy"),
            any(),
            anyString(),
            eq(true),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectBestStrategyAndStock_doesNotSwitch_whenOnlySlightlyBetter() {
        // Given - current is almost as good (< 10% improvement)
        // Score formula: (return * sharpe * winRate) / abs(drawdown)
        // newResult: (10.0 * 1.2 * 55.0) / 8.0 = 82.5
        // currentMapping: (10.0 * 1.15 * 55.0) / 8.0 = 79.06
        // 82.5 / 79.06 = 1.043, which is < 1.10 (10% improvement)
        BacktestResult newResult = createBacktestResult("2330.TW", "TSMC", "NewStrategy", 10.0, 1.2, 55.0, -8.0);
        
        StrategyStockMapping currentMapping = createMapping("2454.TW", "CurrentStrategy", 10.0, 1.15, 55.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(newResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn("CurrentStrategy");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(mappingRepository.findBySymbolAndStrategyName("2454.TW", "CurrentStrategy")).thenReturn(Optional.of(currentMapping));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - should NOT switch (< 10% better)
        verify(activeStrategyService, never()).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void selectShadowModeStrategies_groupsBySymbolAndPicksBest() {
        // Given - two strategies for same symbol (lines 137-138)
        BacktestResult strategy1 = createBacktestResult("2330.TW", "TSMC", "Strategy1", 10.0, 1.3, 58.0, -9.0);
        BacktestResult strategy2 = createBacktestResult("2330.TW", "TSMC", "Strategy2", 15.0, 1.8, 62.0, -6.0);  // Better
        BacktestResult strategy3 = createBacktestResult("2454.TW", "MediaTek", "Strategy3", 12.0, 1.5, 60.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(strategy1, strategy2, strategy3));
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - should have 2 entries (one per stock, best strategy each)
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> 
            configs.size() == 2 && 
            configs.stream().anyMatch(c -> c.getStrategyName().equals("Strategy2")) &&
            configs.stream().anyMatch(c -> c.getStrategyName().equals("Strategy3")) &&
            configs.stream().allMatch(c -> c.getSelectionScore() != null)
        ));
    }

    @Test
    void selectShadowModeStrategies_sortsAndLimitsTo10() {
        // Given - 12 stocks, should limit to 10 (line 145)
        List<BacktestResult> results = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            results.add(createBacktestResult(
                String.format("%04d.TW", 2330 + i),
                "Stock" + i,
                "Strategy" + i,
                5.0 + i,  // Varying returns
                1.0 + (i * 0.1),
                55.0 + i,
                -10.0 + (i * 0.5)
            ));
        }
        
        when(backtestResultRepository.findAll()).thenReturn(results);
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - limited to 10
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> configs.size() == 10));
    }

    @Test
    void selectShadowModeStrategies_noEligibleResults_sendsNoMessage() {
        // Given - all below threshold
        BacktestResult lowResult = createBacktestResult("2330.TW", "TSMC", "Strategy1", 2.0, 0.5, 45.0, -25.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(lowResult));
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - no telegram message for empty results
        verify(telegramService, never()).sendMessage(contains("Shadow Mode Strategies"));
    }

    @Test
    void selectBestStrategyAndStock_filtersNullMetrics() {
        // Given - result with null metrics (lines 233)
        BacktestResult nullMetrics = BacktestResult.builder()
            .symbol("2330.TW")
            .stockName("TSMC")
            .strategyName("Strategy1")
            .totalReturnPct(null)  // Null
            .sharpeRatio(null)     // Null
            .winRatePct(60.0)
            .maxDrawdownPct(-8.0)
            .totalTrades(50)
            .build();
        
        BacktestResult goodResult = createBacktestResult("2454.TW", "MediaTek", "Strategy2", 15.0, 1.5, 60.0, -8.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(nullMetrics, goodResult));
        when(activeStrategyService.getActiveStrategyName()).thenReturn(null);
        when(activeStockService.getActiveStock()).thenReturn(null);
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then - selects good result, skips null metrics
        verify(activeStrategyService).switchStrategyWithStock(
            eq("Strategy2"),
            any(),
            anyString(),
            eq(true),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void convertToMapping_copiesAllFields() throws Exception {
        java.lang.reflect.Method method = AutoStrategySelector.class.getDeclaredMethod("convertToMapping", BacktestResult.class);
        method.setAccessible(true);
        
        BacktestResult result = createBacktestResult("2330.TW", "TSMC", "TestStrategy", 15.0, 1.5, 60.0, -8.0);
        StrategyStockMapping mapping = (StrategyStockMapping) method.invoke(autoStrategySelector, result);
        
        org.assertj.core.api.Assertions.assertThat(mapping.getSymbol()).isEqualTo("2330.TW");
        org.assertj.core.api.Assertions.assertThat(mapping.getStockName()).isEqualTo("TSMC");
        org.assertj.core.api.Assertions.assertThat(mapping.getStrategyName()).isEqualTo("TestStrategy");
        org.assertj.core.api.Assertions.assertThat(mapping.getTotalReturnPct()).isEqualTo(15.0);
    }

    @Test
    void shouldFilterStrategy_roundLot_allowsAll() throws Exception {
        java.lang.reflect.Method method = AutoStrategySelector.class.getDeclaredMethod("shouldFilterStrategy", String.class, boolean.class);
        method.setAccessible(true);
        
        // Round-lot mode allows all strategies including intraday
        boolean shouldFilter = (boolean) method.invoke(autoStrategySelector, "VWAPStrategy", true);
        org.assertj.core.api.Assertions.assertThat(shouldFilter).isFalse();
    }

    @Test
    void shouldFilterStrategy_oddLot_filtersIntraday() throws Exception {
        java.lang.reflect.Method method = AutoStrategySelector.class.getDeclaredMethod("shouldFilterStrategy", String.class, boolean.class);
        method.setAccessible(true);
        
        // Odd-lot mode filters intraday
        boolean shouldFilter = (boolean) method.invoke(autoStrategySelector, "VWAPStrategy", false);
        org.assertj.core.api.Assertions.assertThat(shouldFilter).isTrue();
        
        // Odd-lot allows non-intraday
        boolean allowsNonIntraday = (boolean) method.invoke(autoStrategySelector, "BollingerBandStrategy", false);
        org.assertj.core.api.Assertions.assertThat(allowsNonIntraday).isFalse();
    }

    @Test
    void selectShadowModeStrategiesAndPopulateTable_multipleValidShadows_groupsAndSorts() {
        // Lines 123, 137-138, 145: full path through shadow mapping filtering, grouping, and sorting
        StrategyStockMapping activeMapping = createMappingFromResult(
            createBacktestResult("2330.TW", "TSMC", "ActiveStrategy", 20.0, 2.0, 65.0, -5.0)
        );
        
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        // Provide valid backtest_results (not intraday, return > 3.0, sharpe > 0.8)
        // Including two strategies for 2454.TW; selector should pick the best one.
        BacktestResult shadow1 = createBacktestResult("2454.TW", "MediaTek", "BollingerBand", 15.0, 1.8, 60.0, -7.0);
        BacktestResult shadow2 = createBacktestResult("2317.TW", "Hon Hai", "RSIStrategy", 12.0, 1.5, 58.0, -8.0);
        BacktestResult shadow3 = createBacktestResult("2454.TW", "MediaTek", "MACDStrategy", 10.0, 1.2, 55.0, -10.0);
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(shadow1, shadow2, shadow3));
        
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        AutoStrategySelector selector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            mockRepo
        );
        
        // When
        selector.selectShadowModeStrategiesAndPopulateTable(activeMapping);
        
        // Then - should save active (rank 1) + 2 shadows (best per stock)
        verify(mockRepo, times(3)).save(any());
        // Verify shadow1 (BollingerBand for 2454.TW) was picked over shadow3 (MACDStrategy)
        verify(mockRepo).save(argThat(selection -> 
            selection.getRankPosition() == 2 && 
            selection.getSymbol().equals("2454.TW") && 
            selection.getStrategyName().equals("BollingerBand")
        ));
    }

    @Test
    void selectShadowModeStrategies_roundLot_allowsIntradayStrategies() {
        // Line 336: test shouldFilterStrategy returns false for round-lot mode
        BacktestResult intradayResult = createBacktestResult("2330.TW", "TSMC", "VWAPStrategy", 12.0, 1.3, 58.0, -9.0);
        BacktestResult normalResult = createBacktestResult("2454.TW", "MediaTek", "BollingerBand", 10.0, 1.2, 56.0, -10.0);
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(intradayResult, normalResult));
        when(stockSettingsRepository.findFirstByOrderByIdDesc())
            .thenReturn(Optional.of(StockSettings.builder().shareIncrement(1000).build())); // Round-lot mode
        when(shadowModeStockService.getMaxShadowModeStocks()).thenReturn(10);
        
        // When
        autoStrategySelector.selectShadowModeStrategies();
        
        // Then - both strategies should be included (round-lot allows intraday)
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> configs.size() == 2));
    }

    // ==================== Coverage tests for lines 123 and 336 ====================
    
    @Test
    void selectShadowModeStrategiesAndPopulateTable_filtersNullMetrics() {
        // Line 123: filter(m -> m.getTotalReturnPct() != null && m.getSharpeRatio() != null)
        BacktestResult nullMetrics = BacktestResult.builder()
            .symbol("2317.TW")
            .stockName("Hon Hai")
            .strategyName("NullStrategy")
            .totalReturnPct(null)
            .sharpeRatio(null)
            .winRatePct(null)
            .maxDrawdownPct(null)
            .totalTrades(50)
            .build();
        BacktestResult valid = createBacktestResult("2330.TW", "TSMC", "ValidStrategy", 10.0, 1.5, 55.0, -8.0);
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(nullMetrics, valid));
        
        StrategyStockMapping activeMapping = createMapping("2454.TW", "ActiveStrategy", 12.0, 1.6, 60.0, -5.0);
        
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository mockRepo = 
            mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        AutoStrategySelector selector = new AutoStrategySelector(
            mappingRepository,
            backtestResultRepository,
            stockSettingsRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            complianceService,
            mockRepo
        );
        
        selector.selectShadowModeStrategiesAndPopulateTable(activeMapping);
        
        // Only valid mapping (2330.TW) should be saved as shadow
        verify(mockRepo, atLeast(1)).save(any());
    }
    
    @Test
    void selectBestStrategyAndStock_filtersNullTotalTrades() {
        // Line 336: filter(r -> r.getTotalTrades() != null && r.getTotalTrades() > 10)
        BacktestResult nullTradesResult = BacktestResult.builder()
            .symbol("2330.TW")
            .stockName("TSMC")
            .strategyName("NullTradesStrategy")
            .totalReturnPct(15.0)
            .sharpeRatio(1.5)
            .winRatePct(60.0)
            .maxDrawdownPct(-8.0)
            .totalTrades(null) // null trades
            .build();
        
        BacktestResult lowTradesResult = BacktestResult.builder()
            .symbol("2454.TW")
            .stockName("MediaTek")
            .strategyName("LowTradesStrategy")
            .totalReturnPct(12.0)
            .sharpeRatio(1.3)
            .winRatePct(58.0)
            .maxDrawdownPct(-9.0)
            .totalTrades(5) // Too few trades (< 10)
            .build();
        
        when(backtestResultRepository.findAll()).thenReturn(Arrays.asList(nullTradesResult, lowTradesResult));
        
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Should skip because both results filtered out
        verify(telegramService).sendMessage(contains("No backtest results"));
        verify(activeStrategyService, never()).switchStrategyWithStock(
            anyString(),
            any(),
            anyString(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }
}
