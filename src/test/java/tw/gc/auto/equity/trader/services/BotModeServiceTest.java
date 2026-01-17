package tw.gc.auto.equity.trader.services;

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
    private BridgeManagerService bridgeManager;
    
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

    @Test
    void testGetSetting_returnsValue() {
        when(settingsRepo.findByKey("some_key"))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key("some_key")
                        .value("some_value")
                        .build()));

        Optional<String> result = botModeService.getSetting("some_key");

        assertTrue(result.isPresent());
        assertEquals("some_value", result.get());
    }

    @Test
    void testGetSetting_returnsEmpty() {
        when(settingsRepo.findByKey("missing"))
                .thenReturn(Optional.empty());

        Optional<String> result = botModeService.getSetting("missing");

        assertFalse(result.isPresent());
    }

    @Test
    void testUpdateSetting_createsNew() {
        when(settingsRepo.findByKey("new_key"))
                .thenReturn(Optional.empty());
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        botModeService.updateSetting("new_key", "new_value");

        verify(settingsRepo).save(any(BotSettings.class));
    }

    @Test
    void testUpdateSetting_updatesExisting() {
        BotSettings existing = BotSettings.builder()
                .key("existing_key")
                .value("old_value")
                .build();
        when(settingsRepo.findByKey("existing_key"))
                .thenReturn(Optional.of(existing));
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        botModeService.updateSetting("existing_key", "new_value");

        verify(settingsRepo).save(existing);
        assertEquals("new_value", existing.getValue());
    }

    @Test
    void testInitialize_createsDefaults_whenMissing() {
        when(settingsRepo.existsByKey(anyString())).thenReturn(false);
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("simulation")
                        .build()));
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        botModeService.initialize();

        verify(settingsRepo, atLeastOnce()).save(any(BotSettings.class));
        verify(shioajiSettingsService).updateSimulationMode(true);
    }

    @Test
    void testInitialize_skipsDefaults_whenPresent() {
        when(settingsRepo.existsByKey(anyString())).thenReturn(true);
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("live")
                        .build()));

        botModeService.initialize();

        verify(shioajiSettingsService).updateSimulationMode(false);
    }

    @Test
    void testSwitchToLiveMode_callsShioajiUpdate() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("simulation")
                        .build()));
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        botModeService.switchToLiveMode();

        verify(shioajiSettingsService).updateSimulationMode(false);
        verify(settingsRepo).save(any(BotSettings.class));
    }

    @Test
    void testSwitchToSimulationMode_callsShioajiUpdate() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("live")
                        .build()));
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        botModeService.switchToSimulationMode();

        verify(shioajiSettingsService).updateSimulationMode(true);
        verify(settingsRepo).save(any(BotSettings.class));
    }

    @Test
    void testSwitchToLiveMode_handlesMissingJasyptPassword() {
        // Clear environment
        System.clearProperty("jasypt.encryptor.password");
        
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("simulation")
                        .build()));
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Should not throw even if JASYPT_PASSWORD is missing
        botModeService.switchToLiveMode();

        verify(settingsRepo).save(any(BotSettings.class));
        verify(shioajiSettingsService).updateSimulationMode(false);
    }

    @Test
    void testSwitchToLiveMode_handlesBridgeRestartException() {
        System.setProperty("jasypt.encryptor.password", "test-password");
        
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
                .thenReturn(Optional.of(BotSettings.builder()
                        .key(BotSettings.TRADING_MODE)
                        .value("simulation")
                        .build()));
        when(settingsRepo.save(any(BotSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Should not throw even if bridge restart fails
        botModeService.switchToLiveMode();

        verify(settingsRepo).save(any(BotSettings.class));
    }
}
