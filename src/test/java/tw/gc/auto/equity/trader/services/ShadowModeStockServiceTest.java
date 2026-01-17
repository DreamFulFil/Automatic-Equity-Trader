package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;
import tw.gc.auto.equity.trader.repositories.ShadowModeStockRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShadowModeStockServiceTest {

    @Mock
    private ShadowModeStockRepository shadowModeStockRepository;

    @InjectMocks
    private ShadowModeStockService shadowModeStockService;

    private ShadowModeStock testStock;

    @BeforeEach
    void setUp() {
        testStock = ShadowModeStock.builder()
                .id(1L)
                .symbol("2330.TW")
                .stockName("TSMC")
                .strategyName("RSI (14, 30/70)")
                .expectedReturnPercentage(5.5)
                .rankPosition(1)
                .enabled(true)
                .build();
    }

    @Test
    void testGetEnabledStocks() {
        List<ShadowModeStock> expectedStocks = Arrays.asList(testStock);
        when(shadowModeStockRepository.findByEnabledTrueOrderByRankPosition()).thenReturn(expectedStocks);

        List<ShadowModeStock> result = shadowModeStockService.getEnabledStocks();

        assertEquals(1, result.size());
        assertEquals("2330.TW", result.get(0).getSymbol());
        verify(shadowModeStockRepository).findByEnabledTrueOrderByRankPosition();
    }

    @Test
    void testAddOrUpdateStock_NewStock() {
        when(shadowModeStockRepository.findBySymbol("2454.TW")).thenReturn(Optional.empty());
        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShadowModeStock result = shadowModeStockService.addOrUpdateStock(
                "2454.TW", "MediaTek", "MACD ZeroCross (12,26)", 3.5, 2
        );

        assertNotNull(result);
        assertEquals("2454.TW", result.getSymbol());
        assertEquals("MediaTek", result.getStockName());
        assertEquals("MACD ZeroCross (12,26)", result.getStrategyName());
        assertEquals(3.5, result.getExpectedReturnPercentage());
        assertEquals(2, result.getRankPosition());
        verify(shadowModeStockRepository).save(any(ShadowModeStock.class));
    }

    @Test
    void testAddOrUpdateStock_UpdateExisting() {
        when(shadowModeStockRepository.findBySymbol("2330.TW")).thenReturn(Optional.of(testStock));
        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShadowModeStock result = shadowModeStockService.addOrUpdateStock(
                "2330.TW", "TSMC", "Bollinger Band Mean Reversion", 6.0, 1
        );

        assertNotNull(result);
        assertEquals("2330.TW", result.getSymbol());
        assertEquals("Bollinger Band Mean Reversion", result.getStrategyName());
        assertEquals(6.0, result.getExpectedReturnPercentage());
        verify(shadowModeStockRepository).save(testStock);
    }

    @Test
    void testConfigureStocks() {
        List<ShadowModeStockService.ShadowModeStockConfig> configs = Arrays.asList(
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("2330.TW")
                        .stockName("TSMC")
                        .strategyName("RSI (14, 30/70)")
                        .expectedReturnPercentage(5.5)
                        .build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("2454.TW")
                        .stockName("MediaTek")
                        .strategyName("MACD ZeroCross (12,26)")
                        .expectedReturnPercentage(3.5)
                        .build()
        );

        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shadowModeStockService.configureStocks(configs);

        verify(shadowModeStockRepository).deleteAll();
        verify(shadowModeStockRepository, times(2)).save(any(ShadowModeStock.class));
    }

    @Test
    void testRemoveStock() {
        doNothing().when(shadowModeStockRepository).deleteBySymbol("2330.TW");

        shadowModeStockService.removeStock("2330.TW");

        verify(shadowModeStockRepository).deleteBySymbol("2330.TW");
    }

    @Test
    void testClearAll() {
        doNothing().when(shadowModeStockRepository).deleteAll();

        shadowModeStockService.clearAll();

        verify(shadowModeStockRepository).deleteAll();
    }

    @Test
    void testAddShadowStock() {
        when(shadowModeStockRepository.findBySymbol("2330.TW")).thenReturn(Optional.empty());
        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shadowModeStockService.addShadowStock("2330.TW", "RSI (14, 30/70)");

        verify(shadowModeStockRepository).save(any(ShadowModeStock.class));
    }

    @Test
    void testAddShadowStockWithMetrics_newStock() {
        when(shadowModeStockRepository.findBySymbol("2454.TW")).thenReturn(Optional.empty());
        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shadowModeStockService.addShadowStockWithMetrics(
                "2454.TW", "MediaTek", "MACD", 5.5, 1.2, 65.0, 15.0, 0.85
        );

        verify(shadowModeStockRepository).save(any(ShadowModeStock.class));
    }

    @Test
    void testAddShadowStockWithMetrics_existingStock() {
        when(shadowModeStockRepository.findBySymbol("2330.TW")).thenReturn(Optional.of(testStock));
        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shadowModeStockService.addShadowStockWithMetrics(
                "2330.TW", null, "New Strategy", 7.0, 1.5, 70.0, 12.0, 0.9
        );

        verify(shadowModeStockRepository).save(testStock);
        assertEquals("New Strategy", testStock.getStrategyName());
        assertEquals(7.0, testStock.getExpectedReturnPercentage());
    }

    @Test
    void testUpsertTopCandidates_limitToMaxSlots() {
        List<ShadowModeStockService.ShadowModeStockConfig> candidates = Arrays.asList(
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("1.TW").stockName("Stock1").strategyName("S1")
                        .expectedReturnPercentage(10.0).sharpeRatio(2.0)
                        .winRatePct(80.0).maxDrawdownPct(10.0).selectionScore(0.95).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("2.TW").stockName("Stock2").strategyName("S2")
                        .expectedReturnPercentage(9.0).sharpeRatio(1.9)
                        .winRatePct(75.0).maxDrawdownPct(12.0).selectionScore(0.90).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("3.TW").stockName("Stock3").strategyName("S3")
                        .expectedReturnPercentage(8.0).sharpeRatio(1.8)
                        .winRatePct(70.0).maxDrawdownPct(15.0).selectionScore(0.85).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("4.TW").stockName("Stock4").strategyName("S4")
                        .expectedReturnPercentage(7.0).sharpeRatio(1.7)
                        .winRatePct(65.0).maxDrawdownPct(18.0).selectionScore(0.80).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("5.TW").stockName("Stock5").strategyName("S5")
                        .expectedReturnPercentage(6.0).sharpeRatio(1.6)
                        .winRatePct(60.0).maxDrawdownPct(20.0).selectionScore(0.75).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("6.TW").stockName("Stock6").strategyName("S6")
                        .expectedReturnPercentage(5.5).sharpeRatio(1.5)
                        .winRatePct(58.0).maxDrawdownPct(22.0).selectionScore(0.70).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("7.TW").stockName("Stock7").strategyName("S7")
                        .expectedReturnPercentage(5.0).sharpeRatio(1.4)
                        .winRatePct(55.0).maxDrawdownPct(25.0).selectionScore(0.65).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("8.TW").stockName("Stock8").strategyName("S8")
                        .expectedReturnPercentage(4.5).sharpeRatio(1.3)
                        .winRatePct(52.0).maxDrawdownPct(28.0).selectionScore(0.60).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("9.TW").stockName("Stock9").strategyName("S9")
                        .expectedReturnPercentage(4.0).sharpeRatio(1.2)
                        .winRatePct(50.0).maxDrawdownPct(30.0).selectionScore(0.55).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("10.TW").stockName("Stock10").strategyName("S10")
                        .expectedReturnPercentage(3.5).sharpeRatio(1.1)
                        .winRatePct(48.0).maxDrawdownPct(32.0).selectionScore(0.50).build(),
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("11.TW").stockName("Stock11").strategyName("S11")
                        .expectedReturnPercentage(3.0).sharpeRatio(1.0)
                        .winRatePct(45.0).maxDrawdownPct(35.0).selectionScore(0.45).build()
        );

        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(shadowModeStockRepository).deleteAll();
        doNothing().when(shadowModeStockRepository).flush();

        shadowModeStockService.upsertTopCandidates(candidates);

        verify(shadowModeStockRepository).deleteAll();
        verify(shadowModeStockRepository).flush();
        // Should save exactly 10 (max slots), not 11
        verify(shadowModeStockRepository, times(10)).save(any(ShadowModeStock.class));
    }

    @Test
    void testUpsertTopCandidates_withNullStockName() {
        List<ShadowModeStockService.ShadowModeStockConfig> candidates = Arrays.asList(
                ShadowModeStockService.ShadowModeStockConfig.builder()
                        .symbol("2330.TW").stockName(null).strategyName("RSI")
                        .expectedReturnPercentage(5.0).build()
        );

        when(shadowModeStockRepository.save(any(ShadowModeStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(shadowModeStockRepository).deleteAll();
        doNothing().when(shadowModeStockRepository).flush();

        shadowModeStockService.upsertTopCandidates(candidates);

        verify(shadowModeStockRepository).save(any(ShadowModeStock.class));
    }

    @Test
    void testGetMaxShadowModeStocks() {
        assertEquals(10, shadowModeStockService.getMaxShadowModeStocks());
    }
}
