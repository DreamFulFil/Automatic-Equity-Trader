package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.repositories.BarRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BacktestController.class)
class BacktestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BacktestService backtestService;

    @MockBean
    private BarRepository barRepository;

    @Test
    void testRunBacktest() throws Exception {
        // Mock Data
        Bar bar = Bar.builder()
                .symbol("2454.TW")
                .open(1000.0)
                .high(1010.0)
                .low(990.0)
                .close(1000.0)
                .volume(100L)
                .timestamp(LocalDateTime.now())
                .build();
        when(barRepository.findBySymbolAndTimeframeAndTimestampBetween(any(), any(), any(), any()))
                .thenReturn(List.of(bar));

        Map<String, BacktestService.BacktestResult> results = new HashMap<>();
        results.put("TestStrategy", new BacktestService.BacktestResult("TestStrategy", 80000.0));
        when(backtestService.runBacktest(any(), any(), anyDouble())).thenReturn(results);

        // Execute
        mockMvc.perform(get("/api/backtest/run")
                .param("symbol", "2454.TW")
                .param("start", LocalDateTime.now().minusDays(1).toString())
                .param("end", LocalDateTime.now().toString()))
                .andExpect(status().isOk());
    }
}
