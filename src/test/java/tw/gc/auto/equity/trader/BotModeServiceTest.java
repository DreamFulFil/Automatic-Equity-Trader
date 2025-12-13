package tw.gc.auto.equity.trader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.BotSettings;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BotModeService
 */
@ExtendWith(MockitoExtension.class)
class BotModeServiceTest {
    
    @Mock
    private BotSettingsRepository settingsRepo;
    
    @Mock
    private ShioajiSettingsService shioajiSettingsService;
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private BridgeManager bridgeManager;
    
    private BotModeService botModeService;
    
    @BeforeEach
    void setUp() {
        botModeService = new BotModeService(settingsRepo, shioajiSettingsService, restTemplate, bridgeManager);
    }
    
    @Test
    void testGetTradingMode_DefaultsToSimulation() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.empty());
        
        TradingMode mode = botModeService.getTradingMode();
        
        assertEquals(TradingMode.SIMULATION, mode);
    }
    
    @Test
    void testGetTradingMode_ReturnsStoredValue() {
        BotSettings setting = BotSettings.builder()
                .key(BotSettings.TRADING_MODE)
                .value("live")
                .build();
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(setting));
        
        TradingMode mode = botModeService.getTradingMode();
        
        assertEquals(TradingMode.LIVE, mode);
    }
    
    @Test
    void testIsSimulationMode() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("simulation")
                        .build()));
        
        assertTrue(botModeService.isSimulationMode());
        assertFalse(botModeService.isLiveMode());
    }
    
    @Test
    void testIsLiveMode() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("live")
                        .build()));
        
        assertFalse(botModeService.isSimulationMode());
        assertTrue(botModeService.isLiveMode());
    }
    
    @Test
    void testSwitchToLiveMode() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("simulation")
                        .build()));
        
        botModeService.switchToLiveMode();
        
        verify(settingsRepo).save(any(BotSettings.class));
    }
    
    @Test
    void testSwitchToSimulationMode() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("live")
                        .build()));
        
        botModeService.switchToSimulationMode();
        
        verify(settingsRepo).save(any(BotSettings.class));
    }
    
    @Test
    void testGetSettingAsInt() {
        when(settingsRepo.findByKey("test_key"))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key("test_key")
                        .value("42")
                        .build()));
        
        int value = botModeService.getSettingAsInt("test_key", 0);
        
        assertEquals(42, value);
    }
    
    @Test
    void testGetSettingAsInt_DefaultOnMissing() {
        when(settingsRepo.findByKey("missing_key"))
                .thenReturn(Optional.empty());
        
        int value = botModeService.getSettingAsInt("missing_key", 99);
        
        assertEquals(99, value);
    }
    
    @Test
    void testGetSettingAsInt_DefaultOnInvalidNumber() {
        when(settingsRepo.findByKey("test_key"))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key("test_key")
                        .value("not-a-number")
                        .build()));
        
        int value = botModeService.getSettingAsInt("test_key", 50);
        
        assertEquals(50, value);
    }
}
