package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StrategyStockMapping;
import tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyStockMappingServiceTest {
    
    @Mock
    private StrategyStockMappingRepository mappingRepository;
    
    @InjectMocks
    private StrategyStockMappingService mappingService;
    
    private StrategyStockMapping testMapping;
    
    @BeforeEach
    void setUp() {
        testMapping = StrategyStockMapping.builder()
            .symbol("2330.TW")
            .stockName("TSMC")
            .strategyName("RSI")
            .sharpeRatio(1.5)
            .totalReturnPct(10.0)
            .winRatePct(60.0)
            .maxDrawdownPct(-8.0)
            .totalTrades(50)
            .avgProfitPerTrade(200.0)
            .riskLevel("LOW")
            .build();
    }
    
    @Test
    void updateMapping_withNewMapping_shouldCreate() {
        when(mappingRepository.findBySymbolAndStrategyName(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(mappingRepository.save(any(StrategyStockMapping.class)))
            .thenReturn(testMapping);
        
        StrategyStockMapping result = mappingService.updateMapping(
            "2330.TW", "TSMC", "RSI",
            1.5, 10.0, 60.0, -8.0, 50, 200.0,
            LocalDateTime.now(), LocalDateTime.now()
        );
        
        assertNotNull(result);
        assertEquals("RSI", result.getStrategyName());
        verify(mappingRepository).save(any(StrategyStockMapping.class));
    }
    
    @Test
    void updateMapping_withExistingMapping_shouldUpdate() {
        when(mappingRepository.findBySymbolAndStrategyName("2330.TW", "RSI"))
            .thenReturn(Optional.of(testMapping));
        when(mappingRepository.save(any(StrategyStockMapping.class)))
            .thenReturn(testMapping);
        
        StrategyStockMapping result = mappingService.updateMapping(
            "2330.TW", "TSMC", "RSI",
            1.8, 12.0, 65.0, -6.0, 55, 220.0,
            LocalDateTime.now(), LocalDateTime.now()
        );
        
        assertNotNull(result);
        verify(mappingRepository).save(any(StrategyStockMapping.class));
    }
    
    @Test
    void getBestStrategyForStock_withMappings_shouldReturnHighestSharpe() {
        StrategyStockMapping mapping1 = StrategyStockMapping.builder()
            .strategyName("RSI")
            .sharpeRatio(1.5)
            .build();
        StrategyStockMapping mapping2 = StrategyStockMapping.builder()
            .strategyName("MACD")
            .sharpeRatio(0.8)
            .build();
        
        when(mappingRepository.findBySymbolOrderBySharpeRatioDesc("2330.TW"))
            .thenReturn(Arrays.asList(mapping1, mapping2));
        
        StrategyStockMapping result = mappingService.getBestStrategyForStock("2330.TW");
        
        assertNotNull(result);
        assertEquals("RSI", result.getStrategyName());
        assertEquals(1.5, result.getSharpeRatio());
    }
    
    @Test
    void getBestStrategyForStock_withNoMappings_shouldReturnNull() {
        when(mappingRepository.findBySymbolOrderBySharpeRatioDesc("2330.TW"))
            .thenReturn(Arrays.asList());
        
        StrategyStockMapping result = mappingService.getBestStrategyForStock("2330.TW");
        
        assertNull(result);
    }
    
    @Test
    void setRecommendedStrategy_shouldClearOthersAndSetNew() {
        StrategyStockMapping mapping1 = StrategyStockMapping.builder()
            .id(1L)
            .strategyName("RSI")
            .recommended(true)
            .build();
        StrategyStockMapping mapping2 = StrategyStockMapping.builder()
            .id(2L)
            .strategyName("MACD")
            .recommended(false)
            .build();
        
        when(mappingRepository.findBySymbolOrderBySharpeRatioDesc("2330.TW"))
            .thenReturn(Arrays.asList(mapping1, mapping2));
        when(mappingRepository.findBySymbolAndStrategyName("2330.TW", "MACD"))
            .thenReturn(Optional.of(mapping2));
        
        mappingService.setRecommendedStrategy("2330.TW", "MACD");
        
        verify(mappingRepository, times(3)).save(any(StrategyStockMapping.class));
    }
    
    @Test
    void getLowRiskCombinations_shouldReturnLimitedResults() {
        List<StrategyStockMapping> lowRiskMappings = Arrays.asList(
            testMapping,
            StrategyStockMapping.builder().riskLevel("LOW").build(),
            StrategyStockMapping.builder().riskLevel("LOW").build()
        );
        
        when(mappingRepository.findLowRiskCombinations())
            .thenReturn(lowRiskMappings);
        
        List<StrategyStockMapping> result = mappingService.getLowRiskCombinations(2);
        
        assertEquals(2, result.size());
    }
    
    @Test
    void getStockPerformanceSummary_withMappings_shouldReturnSummary() {
        when(mappingRepository.findBySymbolOrderBySharpeRatioDesc("2330.TW"))
            .thenReturn(Arrays.asList(testMapping));
        
        Map<String, Object> summary = mappingService.getStockPerformanceSummary("2330.TW");
        
        assertEquals("2330.TW", summary.get("symbol"));
        assertEquals("RSI", summary.get("bestStrategy"));
        assertEquals(1.5, summary.get("bestSharpe"));
        assertEquals(1, summary.get("totalStrategiesTested"));
    }
    
    @Test
    void getStockPerformanceSummary_withNoMappings_shouldReturnMessage() {
        when(mappingRepository.findBySymbolOrderBySharpeRatioDesc("2330.TW"))
            .thenReturn(Arrays.asList());
        
        Map<String, Object> summary = mappingService.getStockPerformanceSummary("2330.TW");
        
        assertEquals("2330.TW", summary.get("symbol"));
        assertTrue(summary.containsKey("message"));
    }
    
    @Test
    void addAIInsights_shouldUpdateMapping() {
        when(mappingRepository.findBySymbolAndStrategyName("2330.TW", "RSI"))
            .thenReturn(Optional.of(testMapping));
        
        mappingService.addAIInsights("2330.TW", "RSI", "This is a good combination");
        
        verify(mappingRepository).save(testMapping);
        assertEquals("This is a good combination", testMapping.getAiInsights());
    }
}
