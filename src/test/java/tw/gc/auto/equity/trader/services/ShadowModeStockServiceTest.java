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
}
