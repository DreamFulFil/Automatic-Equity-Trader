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
        verify(activeStrategyService, never()).switchStrategy(anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any());
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
        verify(activeStrategyService).switchStrategy(
            eq("BollingerBandStrategy"), 
            isNull(), 
            anyString(), 
            eq(true),
            eq(1.5),
            eq(-8.0),
            eq(15.0),
            eq(60.0)
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
        verify(activeStrategyService, never()).switchStrategy(anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any());
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
        verify(shadowModeStockService).upsertTopCandidates(argThat(configs -> configs.size() == 10));
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
        verify(activeStrategyService).switchStrategy(anyString(), any(), anyString(), eq(true), any(), any(), any(), any());
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
        verify(activeStrategyService).switchStrategy(anyString(), any(), anyString(), eq(true), any(), any(), any(), any());
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
        verify(activeStrategyService, never()).switchStrategy(anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any());
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
        verify(activeStrategyService).switchStrategy(eq("VWAPStrategy"), any(), anyString(), eq(true), any(), any(), any(), any());
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
        when(mappingRepository.findAll()).thenReturn(Arrays.asList(
            createMappingFromResult(shadowResult1),
            createMappingFromResult(shadowResult2)
        ));
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
}
