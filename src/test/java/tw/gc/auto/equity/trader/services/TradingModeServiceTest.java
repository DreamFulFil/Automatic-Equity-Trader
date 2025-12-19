package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.SystemConfig;
import tw.gc.auto.equity.trader.repositories.SystemConfigRepository;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingModeServiceTest {

    @Mock
    private SystemConfigRepository systemConfigRepository;

    @Mock
    private TradingStateService tradingStateService;

    @Mock
    private StrategyManager strategyManager;

    @Mock
    private RestTemplate restTemplate;

    private TradingModeService tradingModeService;

    @BeforeEach
    void setUp() throws Exception {
        // Use lenient mocking for common stubs
        when(systemConfigRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        // Create service without calling @PostConstruct init()
        // We instantiate directly without Spring context
        tradingModeService = new TradingModeService(
            systemConfigRepository,
            tradingStateService,
            strategyManager,
            restTemplate
        );
    }

    @Test
    void switchMode_toSameMode_shouldNotSwitch() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        
        tradingModeService.switchMode("stock");
        
        // Should not call bridge or update DB when already in same mode
        verify(restTemplate, never()).postForObject(anyString(), any(), eq(java.util.Map.class));
    }

    @Test
    void switchMode_toDifferentMode_shouldCallBridgeAndUpdateDB() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of("status", "ok"));
        when(systemConfigRepository.findByKey("CURRENT_TRADING_MODE"))
                .thenReturn(Optional.of(SystemConfig.builder().key("CURRENT_TRADING_MODE").value("stock").build()));
        
        tradingModeService.switchMode("futures");
        
        // Verify bridge was called
        verify(restTemplate).postForObject(
            eq("http://localhost:8888/mode"),
            argThat(map -> ((java.util.Map<?, ?>) map).get("mode").equals("futures")),
            eq(java.util.Map.class)
        );
        
        // Verify DB was updated
        verify(systemConfigRepository).save(argThat(config -> 
            config.getValue().equals("futures")
        ));
        
        // Verify state was updated
        verify(tradingStateService).setTradingMode("futures");
        
        // Verify strategies were reinitialized
        verify(strategyManager).reinitializeStrategies(any());
    }

    @Test
    void switchMode_whenBridgeFails_shouldThrowException() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        
        assertThrows(RuntimeException.class, () -> tradingModeService.switchMode("futures"));
        
        // DB should not be updated on failure
        verify(systemConfigRepository, never()).save(any());
    }

    @Test
    void switchMode_toFutures_shouldUseFuturesStrategyFactory() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of("status", "ok"));
        when(systemConfigRepository.findByKey("CURRENT_TRADING_MODE"))
                .thenReturn(Optional.of(SystemConfig.builder().key("CURRENT_TRADING_MODE").value("stock").build()));
        
        tradingModeService.switchMode("futures");
        
        verify(strategyManager).reinitializeStrategies(
            argThat(factory -> factory.getClass().getSimpleName().contains("Futures"))
        );
    }

    @Test
    void switchMode_toStock_shouldUseStockStrategyFactory() {
        when(tradingStateService.getTradingMode()).thenReturn("futures");
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of("status", "ok"));
        when(systemConfigRepository.findByKey("CURRENT_TRADING_MODE"))
                .thenReturn(Optional.of(SystemConfig.builder().key("CURRENT_TRADING_MODE").value("futures").build()));
        
        tradingModeService.switchMode("stock");
        
        verify(strategyManager).reinitializeStrategies(
            argThat(factory -> factory.getClass().getSimpleName().contains("Stock"))
        );
    }

    @Test
    void switchMode_withNewConfig_shouldCreateNewConfigEntry() {
        when(tradingStateService.getTradingMode()).thenReturn("stock");
        when(restTemplate.postForObject(anyString(), any(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of("status", "ok"));
        when(systemConfigRepository.findByKey("CURRENT_TRADING_MODE"))
                .thenReturn(Optional.empty());
        
        tradingModeService.switchMode("futures");
        
        verify(systemConfigRepository).save(argThat(config -> 
            config.getKey().equals("CURRENT_TRADING_MODE") && config.getValue().equals("futures")
        ));
    }
}
