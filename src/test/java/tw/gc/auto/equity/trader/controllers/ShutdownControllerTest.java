package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.services.ShioajiSettingsService;
import tw.gc.auto.equity.trader.services.TradingEngineService;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShutdownController.class)
class ShutdownControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingEngineService tradingEngine;

    @MockBean
    private ShioajiSettingsService shioajiSettingsService;

    @MockBean
    private ApplicationContext applicationContext;

    @Test
    void triggerShutdown_shouldReturnMessage() throws Exception {
        mockMvc.perform(post("/api/shutdown"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "Graceful shutdown initiated - flattening positions and stopping application"));
    }

    @Test
    void getShioajiSettings_shouldReturnSettings() throws Exception {
        ShioajiSettings settings = ShioajiSettings.builder()
                .id(1L)
                .simulation(true)
                .updatedAt(LocalDateTime.now())
                .build();

        when(shioajiSettingsService.getSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/shioaji/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.simulation").value(true));

        verify(shioajiSettingsService).getSettings();
    }

    @Test
    void getShioajiSettings_whenNull_shouldReturnNull() throws Exception {
        when(shioajiSettingsService.getSettings()).thenReturn(null);

        mockMvc.perform(get("/api/shioaji/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void getShioajiSettings_simulationFalse_shouldReturnLiveMode() throws Exception {
        ShioajiSettings settings = ShioajiSettings.builder()
                .id(2L)
                .simulation(false)
                .updatedAt(LocalDateTime.now())
                .build();

        when(shioajiSettingsService.getSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/shioaji/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulation").value(false));
    }
}
