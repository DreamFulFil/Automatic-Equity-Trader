package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tw.gc.auto.equity.trader.entities.ShadowModeStock;
import tw.gc.auto.equity.trader.services.ShadowModeStockService;
import tw.gc.auto.equity.trader.services.StrategyManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShadowModeController.class)
class ShadowModeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShadowModeStockService shadowModeStockService;

    @MockBean
    private StrategyManager strategyManager;

    @Test
    void getEnabledStocks_shouldReturnStockList() throws Exception {
        ShadowModeStock stock1 = ShadowModeStock.builder()
                .id(1L)
                .symbol("2330.TW")
                .stockName("TSMC")
                .strategyName("MomentumStrategy")
                .expectedReturnPercentage(12.5)
                .rankPosition(1)
                .enabled(true)
                .build();

        ShadowModeStock stock2 = ShadowModeStock.builder()
                .id(2L)
                .symbol("2454.TW")
                .stockName("MediaTek")
                .strategyName("RSIStrategy")
                .expectedReturnPercentage(10.2)
                .rankPosition(2)
                .enabled(true)
                .build();

        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of(stock1, stock2));

        mockMvc.perform(get("/api/shadow-mode/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("2330.TW"))
                .andExpect(jsonPath("$[0].strategyName").value("MomentumStrategy"))
                .andExpect(jsonPath("$[1].symbol").value("2454.TW"));
    }

    @Test
    void getEnabledStocks_whenEmpty_shouldReturnEmptyList() throws Exception {
        when(shadowModeStockService.getEnabledStocks()).thenReturn(List.of());

        mockMvc.perform(get("/api/shadow-mode/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void configureStocks_withValidPayload_shouldReturnSuccess() throws Exception {
        doNothing().when(shadowModeStockService).configureStocks(anyList());

        String payload = """
            [
                {"symbol": "2330.TW", "stockName": "TSMC", "strategyName": "MomentumStrategy", "expectedReturnPercentage": 12.5, "rankPosition": 1},
                {"symbol": "2454.TW", "stockName": "MediaTek", "strategyName": "RSIStrategy", "expectedReturnPercentage": 10.2, "rankPosition": 2}
            ]
            """;

        mockMvc.perform(post("/api/shadow-mode/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.stocks_configured").value("2"));

        verify(shadowModeStockService).configureStocks(anyList());
    }

    @Test
    void configureStocks_whenException_shouldReturnError() throws Exception {
        doThrow(new RuntimeException("Database error")).when(shadowModeStockService).configureStocks(anyList());

        mockMvc.perform(post("/api/shadow-mode/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Database error"));
    }

    @Test
    void addStock_withAllParams_shouldReturnStock() throws Exception {
        ShadowModeStock stock = ShadowModeStock.builder()
                .id(1L)
                .symbol("2330.TW")
                .stockName("TSMC")
                .strategyName("MomentumStrategy")
                .expectedReturnPercentage(12.5)
                .rankPosition(1)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(shadowModeStockService.addOrUpdateStock(
                eq("2330.TW"), eq("TSMC"), eq("MomentumStrategy"), eq(12.5), eq(1)))
                .thenReturn(stock);

        mockMvc.perform(post("/api/shadow-mode/add")
                        .param("symbol", "2330.TW")
                        .param("stockName", "TSMC")
                        .param("strategyName", "MomentumStrategy")
                        .param("expectedReturnPercentage", "12.5")
                        .param("rankPosition", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("2330.TW"))
                .andExpect(jsonPath("$.stockName").value("TSMC"))
                .andExpect(jsonPath("$.strategyName").value("MomentumStrategy"))
                .andExpect(jsonPath("$.expectedReturnPercentage").value(12.5))
                .andExpect(jsonPath("$.rankPosition").value(1));
    }

    @Test
    void addStock_withRequiredParamsOnly_shouldReturnStock() throws Exception {
        ShadowModeStock stock = ShadowModeStock.builder()
                .id(1L)
                .symbol("2330.TW")
                .stockName("TSMC")
                .strategyName("MomentumStrategy")
                .enabled(true)
                .build();

        when(shadowModeStockService.addOrUpdateStock(
                eq("2330.TW"), eq("TSMC"), eq("MomentumStrategy"), isNull(), isNull()))
                .thenReturn(stock);

        mockMvc.perform(post("/api/shadow-mode/add")
                        .param("symbol", "2330.TW")
                        .param("stockName", "TSMC")
                        .param("strategyName", "MomentumStrategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("2330.TW"));
    }

    @Test
    void removeStock_whenExists_shouldReturnSuccess() throws Exception {
        doNothing().when(shadowModeStockService).removeStock(eq("2330.TW"));

        mockMvc.perform(delete("/api/shadow-mode/remove")
                        .param("symbol", "2330.TW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Stock removed from shadow mode"));

        verify(shadowModeStockService).removeStock("2330.TW");
    }

    @Test
    void removeStock_whenNotExists_shouldReturnError() throws Exception {
        doThrow(new RuntimeException("Stock not found")).when(shadowModeStockService).removeStock(anyString());

        mockMvc.perform(delete("/api/shadow-mode/remove")
                        .param("symbol", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Stock not found"));
    }
}
