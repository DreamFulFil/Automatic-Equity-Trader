package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.repositories.StrategyPerformanceRepository;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoStrategySelectorTest {

    @Mock
    private StrategyPerformanceRepository performanceRepository;

    @Mock
    private StrategyStockMappingRepository mappingRepository;

    @Mock
    private ActiveStrategyService activeStrategyService;

    @Mock
    private ActiveStockService activeStockService;

    @Mock
    private ShadowModeStockService shadowModeStockService;

    @Mock
    private TelegramService telegramService;

    private AutoStrategySelector autoStrategySelector;

    @BeforeEach
    void setUp() {
        tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository selectionRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.ActiveShadowSelectionRepository.class);
        
        autoStrategySelector = new AutoStrategySelector(
            performanceRepository,
            mappingRepository,
            activeStrategyService,
            activeStockService,
            shadowModeStockService,
            telegramService,
            selectionRepo
        );
    }

    @Test
    void selectBestStrategyAndStock_shouldSkip_whenNoBacktestResults() {
        // Given
        when(mappingRepository.findAll()).thenReturn(Collections.emptyList());
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then
        verify(telegramService).sendMessage(contains("No backtest results"));
        verify(activeStrategyService, never()).switchStrategy(anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void selectBestStrategyAndStock_shouldSelectBestCombo_whenResultsExist() {
        // Given
        StrategyStockMapping bestMapping = createMapping("2330.TW", "BollingerBandStrategy", 15.0, 1.5, 60.0, -8.0);
        StrategyStockMapping lessGoodMapping = createMapping("2454.TW", "RSIStrategy", 8.0, 0.9, 52.0, -15.0);
        
        when(mappingRepository.findAll()).thenReturn(Arrays.asList(bestMapping, lessGoodMapping));
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
        StrategyStockMapping currentMapping = createMapping("2454.TW", "CurrentStrategy", 20.0, 2.0, 70.0, -5.0);
        StrategyStockMapping otherMapping = createMapping("2330.TW", "OtherStrategy", 10.0, 1.2, 55.0, -10.0);
        
        when(mappingRepository.findAll()).thenReturn(Arrays.asList(currentMapping, otherMapping));
        when(activeStrategyService.getActiveStrategyName()).thenReturn("CurrentStrategy");
        when(activeStockService.getActiveStock()).thenReturn("2454.TW");
        when(mappingRepository.findBySymbolAndStrategyName("2454.TW", "CurrentStrategy")).thenReturn(Optional.of(currentMapping));
        
        // When
        autoStrategySelector.selectBestStrategyAndStock();
        
        // Then
        verify(activeStrategyService, never()).switchStrategy(anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void selectShadowModeStrategies_shouldSelectTop10() {
        // Given
        List<StrategyStockMapping> mappings = Arrays.asList(
            createMapping("2330.TW", "Strategy1", 12.0, 1.5, 60.0, -8.0),
            createMapping("2454.TW", "Strategy2", 10.0, 1.3, 58.0, -9.0),
            createMapping("2317.TW", "Strategy3", 8.0, 1.1, 55.0, -10.0),
            createMapping("2303.TW", "Strategy4", 7.0, 1.0, 54.0, -11.0),
            createMapping("2412.TW", "Strategy5", 6.0, 0.95, 53.0, -12.0),
            createMapping("2881.TW", "Strategy6", 5.0, 0.9, 52.0, -13.0),
            createMapping("2882.TW", "Strategy7", 4.5, 0.88, 51.0, -14.0),
            createMapping("2891.TW", "Strategy8", 4.0, 0.85, 50.0, -15.0),
            createMapping("1301.TW", "Strategy9", 3.5, 0.82, 49.0, -16.0),
            createMapping("2002.TW", "Strategy10", 3.2, 0.81, 48.0, -17.0),
            createMapping("2308.TW", "Strategy11", 3.1, 0.80, 47.0, -18.0) // Should be excluded (11th)
        );
        
        when(mappingRepository.findAll()).thenReturn(mappings);
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
        StrategyStockMapping goodMapping = createMapping("2330.TW", "GoodStrategy", 5.0, 1.0, 55.0, -10.0);
        StrategyStockMapping poorMapping = createMapping("2454.TW", "PoorStrategy", 2.0, 0.5, 45.0, -25.0); // Below thresholds
        
        when(mappingRepository.findAll()).thenReturn(Arrays.asList(goodMapping, poorMapping));
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
        StrategyStockMapping strategy1ForStock = createMapping("2330.TW", "Strategy1", 10.0, 1.5, 60.0, -8.0);
        StrategyStockMapping strategy2ForStock = createMapping("2330.TW", "Strategy2", 15.0, 2.0, 65.0, -5.0); // Better
        
        when(mappingRepository.findAll()).thenReturn(Arrays.asList(strategy1ForStock, strategy2ForStock));
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
}
