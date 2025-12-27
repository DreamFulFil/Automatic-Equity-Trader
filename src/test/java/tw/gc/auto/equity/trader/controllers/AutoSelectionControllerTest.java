package tw.gc.auto.equity.trader.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import tw.gc.auto.equity.trader.services.AutoStrategySelector;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AutoSelectionController.class)
class AutoSelectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AutoStrategySelector autoStrategySelector;

    @Test
    void runAutoSelection_whenSuccessful_shouldReturnSuccess() throws Exception {
        doNothing().when(autoStrategySelector).selectBestStrategyAndStock();
        doNothing().when(autoStrategySelector).selectShadowModeStrategies();

        mockMvc.perform(post("/api/auto-selection/run-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Auto-selection completed successfully"));

        verify(autoStrategySelector).selectBestStrategyAndStock();
        verify(autoStrategySelector).selectShadowModeStrategies();
    }

    @Test
    void runAutoSelection_whenMainFails_shouldReturnError() throws Exception {
        doThrow(new RuntimeException("Strategy selection failed"))
                .when(autoStrategySelector).selectBestStrategyAndStock();

        mockMvc.perform(post("/api/auto-selection/run-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Strategy selection failed"));

        verify(autoStrategySelector).selectBestStrategyAndStock();
        verify(autoStrategySelector, never()).selectShadowModeStrategies();
    }

    @Test
    void runAutoSelection_whenShadowFails_shouldReturnError() throws Exception {
        doNothing().when(autoStrategySelector).selectBestStrategyAndStock();
        doThrow(new RuntimeException("Shadow mode selection failed"))
                .when(autoStrategySelector).selectShadowModeStrategies();

        mockMvc.perform(post("/api/auto-selection/run-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Shadow mode selection failed"));

        verify(autoStrategySelector).selectBestStrategyAndStock();
        verify(autoStrategySelector).selectShadowModeStrategies();
    }
}
