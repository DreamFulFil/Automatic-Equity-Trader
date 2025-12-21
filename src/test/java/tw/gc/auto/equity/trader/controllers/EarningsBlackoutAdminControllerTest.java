package tw.gc.auto.equity.trader.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tw.gc.auto.equity.trader.entities.EarningsBlackoutMeta;
import tw.gc.auto.equity.trader.services.EarningsBlackoutService;
import tw.gc.auto.equity.trader.services.RiskManagementService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EarningsBlackoutAdminController.class)
class EarningsBlackoutAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EarningsBlackoutService earningsBlackoutService;

    @MockitoBean
    private RiskManagementService riskManagementService;

    @Test
    void seedFromJson_withValidPayload_shouldReturnSeeded() throws Exception {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")))
                .tickersChecked(new LinkedHashSet<>(Set.of("TSM", "2317.TW")))
                .build();
        
        when(earningsBlackoutService.seedFromJson(any(JsonNode.class)))
                .thenReturn(Optional.of(meta));
        when(earningsBlackoutService.isDataStale(any())).thenReturn(false);

        String payload = """
            {
                "dates": ["2099-01-01"],
                "tickers_checked": ["TSM", "2317.TW"],
                "last_updated": "2099-01-01T00:00:00Z",
                "source": "test"
            }
            """;

        mockMvc.perform(post("/admin/earnings-blackout/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("seeded"))
                .andExpect(jsonPath("$.stale").value(false));

        verify(riskManagementService).isEarningsBlackout();
    }

    @Test
    void seedFromJson_withEmptyPayload_shouldReturnIgnored() throws Exception {
        when(earningsBlackoutService.seedFromJson(any(JsonNode.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/earnings-blackout/seed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"))
                .andExpect(jsonPath("$.datesCount").value(0))
                .andExpect(jsonPath("$.stale").value(true));
    }

    @Test
    void refresh_whenSuccessful_shouldReturnRefreshed() throws Exception {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")))
                .tickersChecked(new LinkedHashSet<>(Set.of("TSM")))
                .build();

        when(earningsBlackoutService.manualRefresh()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.isDataStale(any())).thenReturn(false);

        mockMvc.perform(post("/admin/earnings-blackout/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("refreshed"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void refresh_whenFails_shouldReturnFailed() throws Exception {
        when(earningsBlackoutService.manualRefresh()).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/earnings-blackout/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"));
    }

    @Test
    void status_whenDataPresent_shouldReturnDetails() throws Exception {
        EarningsBlackoutMeta meta = EarningsBlackoutMeta.builder()
                .lastUpdated(OffsetDateTime.now(ZoneId.of("Asia/Taipei")))
                .tickersChecked(new LinkedHashSet<>(Set.of("TSM", "2317.TW")))
                .ttlDays(7)
                .build();

        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.of(meta));
        when(earningsBlackoutService.isDataStale(any())).thenReturn(false);

        mockMvc.perform(get("/admin/earnings-blackout/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(true))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.ttlDays").value(7));
    }

    @Test
    void status_whenNoData_shouldReturnEmpty() throws Exception {
        when(earningsBlackoutService.getLatestMeta()).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/earnings-blackout/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.present").value(false))
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.datesCount").value(0));
    }
}
