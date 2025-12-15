package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.ActiveStrategyConfig;
import tw.gc.auto.equity.trader.repositories.ActiveStrategyConfigRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveStrategyServiceTest {

    @Mock
    private ActiveStrategyConfigRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ActiveStrategyService service;

    private ActiveStrategyConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = ActiveStrategyConfig.builder()
            .id(1L)
            .strategyName("RSIStrategy")
            .parametersJson("{\"period\":14}")
            .lastUpdated(LocalDateTime.now())
            .switchReason("Test initialization")
            .autoSwitched(false)
            .sharpeRatio(1.5)
            .maxDrawdownPct(10.0)
            .build();
    }

    @Test
    void testGetActiveStrategy_ExistingConfig() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));

        ActiveStrategyConfig result = service.getActiveStrategy();

        assertNotNull(result);
        assertEquals("RSIStrategy", result.getStrategyName());
        verify(repository, times(1)).findFirstByOrderByIdAsc();
    }

    @Test
    void testGetActiveStrategy_CreatesDefault() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(any(ActiveStrategyConfig.class))).thenReturn(testConfig);

        ActiveStrategyConfig result = service.getActiveStrategy();

        assertNotNull(result);
        verify(repository, times(1)).save(any(ActiveStrategyConfig.class));
    }

    @Test
    void testGetActiveStrategyName() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));

        String name = service.getActiveStrategyName();

        assertEquals("RSIStrategy", name);
    }

    @Test
    void testSwitchStrategy() throws Exception {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"period\":20}");
        when(repository.save(any(ActiveStrategyConfig.class))).thenReturn(testConfig);

        Map<String, Object> params = new HashMap<>();
        params.put("period", 20);

        service.switchStrategy("MACDStrategy", params, "Test switch", false);

        verify(repository, times(1)).save(any(ActiveStrategyConfig.class));
    }

    @Test
    void testSwitchStrategy_WithMetrics() throws Exception {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"period\":20}");
        when(repository.save(any(ActiveStrategyConfig.class))).thenReturn(testConfig);

        Map<String, Object> params = new HashMap<>();
        params.put("period", 20);

        service.switchStrategy(
            "MACDStrategy",
            params,
            "Performance-based switch",
            true,
            2.0,
            8.0,
            25.0,
            65.0
        );

        verify(repository, times(1)).save(argThat(config ->
            config.getStrategyName().equals("MACDStrategy") &&
            config.isAutoSwitched() &&
            config.getSharpeRatio() == 2.0
        ));
    }

    @Test
    void testGetActiveStrategyParameters_ValidJson() throws Exception {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));
        Map<String, Object> expectedParams = new HashMap<>();
        expectedParams.put("period", 14);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(expectedParams);

        Map<String, Object> result = service.getActiveStrategyParameters();

        assertNotNull(result);
        assertEquals(14, result.get("period"));
    }

    @Test
    void testGetActiveStrategyParameters_InvalidJson() throws Exception {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));
        when(objectMapper.readValue(anyString(), eq(Map.class)))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Invalid JSON") {});

        Map<String, Object> result = service.getActiveStrategyParameters();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetActiveStrategyParameters_EmptyJson() throws Exception {
        testConfig.setParametersJson("");
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));

        Map<String, Object> result = service.getActiveStrategyParameters();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testUpdateParameters() throws Exception {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(testConfig));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"period\":30}");
        when(repository.save(any(ActiveStrategyConfig.class))).thenReturn(testConfig);

        Map<String, Object> newParams = new HashMap<>();
        newParams.put("period", 30);

        service.updateParameters(newParams);

        verify(repository, times(1)).save(argThat(config ->
            config.getParametersJson().equals("{\"period\":30}")
        ));
    }
}
