package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.services.BacktestService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BacktestController.class)
class BacktestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BacktestService backtestService;

    @MockBean
    private MarketDataRepository marketDataRepository;
    
    @MockBean
    private tw.gc.auto.equity.trader.repositories.StrategyStockMappingRepository mappingRepository;
    
    @MockBean
    private tw.gc.auto.equity.trader.services.TaiwanStockNameService stockNameService;
    
    @MockBean
    private tw.gc.auto.equity.trader.services.AutoStrategySelector autoStrategySelector;

    @Test
    void testRunBacktest() throws Exception {
        // Mock Data
        MarketData data = MarketData.builder()
                .symbol("2454.TW")
                .open(1000.0)
                .high(1010.0)
                .low(990.0)
                .close(1000.0)
                .volume(100L)
                .timeframe(MarketData.Timeframe.MIN_1)
                .timestamp(LocalDateTime.now())
                .build();
        when(marketDataRepository.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(any(), any(), any(), any()))
                .thenReturn(List.of(data));

        Map<String, BacktestService.InMemoryBacktestResult> results = new HashMap<>();
        results.put("TestStrategy", new BacktestService.InMemoryBacktestResult("TestStrategy", 80000.0));
        when(backtestService.runBacktest(any(), any(), anyDouble())).thenReturn(results);

        // Execute
        mockMvc.perform(post("/api/backtest/run")
                .param("symbol", "2454.TW")
                .param("start", LocalDateTime.now().minusDays(1).toString())
                .param("end", LocalDateTime.now().toString()))
                .andExpect(status().isOk());
    }
}
