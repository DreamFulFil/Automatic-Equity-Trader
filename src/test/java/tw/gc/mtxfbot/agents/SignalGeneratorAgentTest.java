package tw.gc.mtxfbot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;
import tw.gc.mtxfbot.entities.Signal;
import tw.gc.mtxfbot.agents.SignalGeneratorAgent.MarketData;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SignalGeneratorAgentTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private SignalGeneratorAgent agent;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        agent = new SignalGeneratorAgent(restTemplate, objectMapper, "http://localhost:8888");
    }

    @Test
    void generateSignal_BullishMomentumVolumeRsiAligned() throws Exception {
        MarketData data = MarketData.builder()
                .symbol("2454.TW")
                .prices(List.of(100.0, 100.2, 100.4, 100.8, 101.0, 101.5))
                .volumes(List.of(1200L, 1250L, 1300L, 1400L, 1500L, 2100L))
                .timeframe("1m")
                .build();
        when(restTemplate.getForObject(anyString(), Mockito.eq(String.class)))
                .thenReturn(objectMapper.writeValueAsString(data));

        Signal signal = agent.generateSignal("2454.TW");

        assertNotNull(signal);
        assertEquals(Signal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getConfidence() >= 0.65, "Confidence should be high for aligned momentum");
        assertEquals("2454.TW", signal.getSymbol());
        assertFalse(Boolean.TRUE.equals(signal.getExitSignal()));
    }

    @Test
    void generateSignal_RsiExtremeTriggersHold() throws Exception {
        MarketData data = MarketData.builder()
                .symbol("2454.TW")
                .prices(List.of(100.0, 101.5, 103.0, 104.5, 106.0, 107.5, 109.0, 110.5, 112.0, 113.5, 115.0, 116.5, 118.0, 119.5, 121.0))
                .volumes(List.of(1500L, 1500L, 1500L, 1500L, 1500L, 1800L, 1900L, 2000L, 2100L, 2200L, 2300L, 2400L, 2500L, 2600L, 2700L))
                .timeframe("1m")
                .build();
        when(restTemplate.getForObject(anyString(), Mockito.eq(String.class)))
                .thenReturn(objectMapper.writeValueAsString(data));

        Signal signal = agent.generateSignal("2454.TW");

        assertEquals(Signal.SignalDirection.HOLD, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("rsi"));
    }

    @Test
    void generateSignal_VolumeNotConfirmed() throws Exception {
        MarketData data = MarketData.builder()
                .symbol("2454.TW")
                .prices(List.of(100.0, 100.1, 100.3, 100.4, 100.6, 100.8, 101.0))
                .volumes(List.of(2000L, 1900L, 1800L, 1700L, 1600L, 1500L, 1400L))
                .timeframe("1m")
                .build();
        when(restTemplate.getForObject(anyString(), Mockito.eq(String.class)))
                .thenReturn(objectMapper.writeValueAsString(data));

        Signal signal = agent.generateSignal("2454.TW");

        assertEquals(Signal.SignalDirection.HOLD, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("volume"));
    }

    @Test
    void execute_ReturnsMetricsMap() throws Exception {
        MarketData data = MarketData.builder()
                .symbol("2454.TW")
                .prices(List.of(100.0, 100.2, 100.4, 100.8, 101.0, 101.5))
                .volumes(List.of(1200L, 1250L, 1300L, 1400L, 1500L, 2100L))
                .timeframe("1m")
                .build();
        when(restTemplate.getForObject(anyString(), Mockito.eq(String.class)))
                .thenReturn(objectMapper.writeValueAsString(data));

        Map<String, Object> result = agent.execute(Map.of());

        assertTrue((boolean) result.get("success"));
        assertEquals("LONG", result.get("direction"));
        assertTrue((double) result.get("confidence") > 0.6);
        assertTrue((double) result.get("volume_ratio") > 1.0);
    }
}
