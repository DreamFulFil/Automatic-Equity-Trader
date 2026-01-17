package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.services.ShioajiSettingsService;
import tw.gc.auto.equity.trader.services.TradingEngineService;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShutdownController.class)
class ShutdownControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ShutdownController shutdownController;

    @MockitoBean
    private TradingEngineService tradingEngine;

    @MockitoBean
    private ShioajiSettingsService shioajiSettingsService;

    @MockitoBean
    private ApplicationContext applicationContext;
    
    @BeforeEach
    void setUp() {
        // Prevent Spring from closing the test context and disable System.exit.
        shutdownController.setSpringExitHandler(() -> 0);
        shutdownController.setExitEnabled(false);
    }

    @Test
    void triggerShutdown_shouldReturnMessage() throws Exception {
        mockMvc.perform(post("/api/shutdown"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "Graceful shutdown initiated - flattening positions and stopping application"));
    }

    @Test
    void triggerShutdown_exitEnabled_shouldInvokeExitHandler_andHandleException() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        shutdownController.setExitEnabled(true);
        shutdownController.setExitHandler(code -> {
            latch.countDown();
            throw new RuntimeException("blocked exit");
        });

        mockMvc.perform(post("/api/shutdown"))
                .andExpect(status().isOk());

        verify(tradingEngine, timeout(2_000)).flattenPosition(anyString());
        assertTrue(latch.await(2, TimeUnit.SECONDS));
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
