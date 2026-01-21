package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.entities.BotSettings;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;
import tw.gc.auto.equity.trader.repositories.SystemConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceCoveragePatchTest {

    @Mock
    private BotSettingsRepository settingsRepo;
    @Mock
    private ShioajiSettingsService shioajiSettingsService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private BridgeManagerService bridgeManager;
    @Mock
    private SystemConfigRepository configRepository;

    private BotModeService botModeService;
    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        botModeService = new BotModeService(settingsRepo, shioajiSettingsService, restTemplate, bridgeManager);
        systemConfigService = new SystemConfigService(configRepository);
    }

    @Test
    void botMode_shutdownBridge_bridgeRestartSkippedOnFailure() {
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
            .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.TRADING_MODE).value("simulation").build()));
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
            .thenThrow(new RuntimeException("skip"));

        botModeService.switchToLiveMode();
        verifyNoInteractions(bridgeManager);
    }

    @Test
    void botMode_shutdownBridge_missingPassword_logsAndReturns() {
        System.setProperty("jasypt.encryptor.password", "");
        when(settingsRepo.findByKey(BotSettings.TRADING_MODE))
            .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.TRADING_MODE).value("simulation").build()));
        doNothing().when(bridgeManager).restartBridge(anyString());

        botModeService.switchToLiveMode();
        verify(bridgeManager).restartBridge("");
    }

    @Test
    void systemConfig_appendConfig_fallsBackToDefault() {
        when(configRepository.findAll()).thenReturn(List.of());
        String output = systemConfigService.getAllConfigsFormatted();
        assertThat(output).contains("Max Drawdown");
    }
}
