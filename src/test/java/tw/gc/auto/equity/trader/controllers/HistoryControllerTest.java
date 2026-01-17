package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.services.HistoryDataService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    @Mock
    private HistoryDataService historyDataService;

    @InjectMocks
    private HistoryController historyController;

    private HistoryDataService.DownloadResult successResult;

    @BeforeEach
    void setUp() {
        successResult = new HistoryDataService.DownloadResult(
                "2330.TW", 1000, 1000, 0);
    }

    @Test
    void testDownloadHistoricalDataWithSymbols() {
        List<String> symbols = List.of("2330.TW", "2454.TW");
        Map<String, HistoryDataService.DownloadResult> results = Map.of(
                "2330.TW", successResult,
                "2454.TW", successResult
        );

        when(historyDataService.downloadHistoricalDataForMultipleStocks(symbols, 10))
                .thenReturn(results);

        Map<String, HistoryDataService.DownloadResult> result = 
                historyController.downloadHistoricalData(symbols, 10);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(historyDataService).downloadHistoricalDataForMultipleStocks(symbols, 10);
    }

    @Test
    void testDownloadHistoricalDataWithNullSymbolsUsesDefault() {
        when(historyDataService.downloadHistoricalDataForMultipleStocks(anyList(), eq(10)))
                .thenReturn(Map.of());

        Map<String, HistoryDataService.DownloadResult> result = 
                historyController.downloadHistoricalData(null, 10);

        assertNotNull(result);
        verify(historyDataService).downloadHistoricalDataForMultipleStocks(anyList(), eq(10));
    }

    @Test
    void testDownloadHistoricalDataWithEmptySymbolsUsesDefault() {
        when(historyDataService.downloadHistoricalDataForMultipleStocks(anyList(), eq(10)))
                .thenReturn(Map.of());

        Map<String, HistoryDataService.DownloadResult> result = 
                historyController.downloadHistoricalData(List.of(), 10);

        assertNotNull(result);
        verify(historyDataService).downloadHistoricalDataForMultipleStocks(anyList(), eq(10));
    }

    @Test
    void testDownloadSingleStock() throws Exception {
        when(historyDataService.downloadHistoricalData("2330.TW", 10))
                .thenReturn(successResult);

        HistoryDataService.DownloadResult result = 
                historyController.downloadSingleStock("2330.TW", 10);

        assertNotNull(result);
        assertEquals("2330.TW", result.getSymbol());
        verify(historyDataService).downloadHistoricalData("2330.TW", 10);
    }

    @Test
    void testResetTruncation() {
        doNothing().when(historyDataService).resetTruncationFlag();

        Map<String, String> result = historyController.resetTruncation();

        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertTrue(result.get("message").contains("reset"));
        verify(historyDataService).resetTruncationFlag();
    }
}
