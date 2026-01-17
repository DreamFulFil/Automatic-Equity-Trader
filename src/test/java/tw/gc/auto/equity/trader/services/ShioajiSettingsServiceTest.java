package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.ShioajiSettings;
import tw.gc.auto.equity.trader.repositories.ShioajiSettingsRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShioajiSettingsServiceTest {

    @Mock
    private ShioajiSettingsRepository shioajiSettingsRepository;

    private ShioajiSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ShioajiSettingsService(shioajiSettingsRepository);
    }

    @Test
    void initialize_withNoSettings_shouldCreateDefaults() {
        when(shioajiSettingsRepository.findFirst()).thenReturn(null);

        service.initialize();

        ArgumentCaptor<ShioajiSettings> captor = ArgumentCaptor.forClass(ShioajiSettings.class);
        verify(shioajiSettingsRepository).save(captor.capture());

        ShioajiSettings saved = captor.getValue();
        assertThat(saved.isSimulation()).isTrue();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void initialize_withExistingSettings_shouldNotCreateNew() {
        ShioajiSettings existing = createDefaultSettings();
        when(shioajiSettingsRepository.findFirst()).thenReturn(existing);

        service.initialize();

        verify(shioajiSettingsRepository, never()).save(any());
    }

    @Test
    void ensureDefaultSettings_whenNotExists_shouldCreateWithSimulationTrue() {
        when(shioajiSettingsRepository.findFirst()).thenReturn(null);

        service.ensureDefaultSettings();

        ArgumentCaptor<ShioajiSettings> captor = ArgumentCaptor.forClass(ShioajiSettings.class);
        verify(shioajiSettingsRepository).save(captor.capture());
        
        assertThat(captor.getValue().isSimulation()).isTrue();
    }

    @Test
    void ensureDefaultSettings_whenExists_shouldNotCreate() {
        ShioajiSettings existing = createDefaultSettings();
        when(shioajiSettingsRepository.findFirst()).thenReturn(existing);

        service.ensureDefaultSettings();

        verify(shioajiSettingsRepository, never()).save(any());
    }

    @Test
    void getSettings_withExistingSettings_shouldReturnSettings() {
        ShioajiSettings expected = createDefaultSettings();
        when(shioajiSettingsRepository.findFirst()).thenReturn(expected);

        ShioajiSettings result = service.getSettings();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getSettings_withNoSettings_shouldThrowException() {
        when(shioajiSettingsRepository.findFirst()).thenReturn(null);

        assertThatThrownBy(() -> service.getSettings())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Shioaji settings not found");
    }

    @Test
    void updateSimulationMode_toTrue_shouldUpdateSettings() {
        ShioajiSettings settings = createDefaultSettings();
        settings.setSimulation(false);
        when(shioajiSettingsRepository.findFirst()).thenReturn(settings);

        service.updateSimulationMode(true);

        assertThat(settings.isSimulation()).isTrue();
        verify(shioajiSettingsRepository).save(settings);
    }

    @Test
    void updateSimulationMode_toFalse_shouldUpdateSettings() {
        ShioajiSettings settings = createDefaultSettings();
        settings.setSimulation(true);
        when(shioajiSettingsRepository.findFirst()).thenReturn(settings);

        service.updateSimulationMode(false);

        assertThat(settings.isSimulation()).isFalse();
        verify(shioajiSettingsRepository).save(settings);
    }

    @Test
    void updateSimulationMode_shouldUpdateTimestamp() {
        ShioajiSettings settings = createDefaultSettings();
        LocalDateTime oldTime = settings.getUpdatedAt();
        when(shioajiSettingsRepository.findFirst()).thenReturn(settings);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // ignore
        }

        service.updateSimulationMode(false);

        assertThat(settings.getUpdatedAt()).isAfter(oldTime);
    }

    @Test
    void isSimulationMode_whenTrue_shouldReturnTrue() {
        ShioajiSettings settings = createDefaultSettings();
        settings.setSimulation(true);
        when(shioajiSettingsRepository.findFirst()).thenReturn(settings);

        boolean result = service.isSimulationMode();

        assertThat(result).isTrue();
    }

    @Test
    void isSimulationMode_whenFalse_shouldReturnFalse() {
        ShioajiSettings settings = createDefaultSettings();
        settings.setSimulation(false);
        when(shioajiSettingsRepository.findFirst()).thenReturn(settings);

        boolean result = service.isSimulationMode();

        assertThat(result).isFalse();
    }

    @Test
    void updateSimulationMode_calledTwice_shouldSaveTwice() {
        ShioajiSettings settings = createDefaultSettings();
        when(shioajiSettingsRepository.findFirst()).thenReturn(settings);

        service.updateSimulationMode(true);
        service.updateSimulationMode(false);

        verify(shioajiSettingsRepository, times(2)).save(settings);
    }

    @Test
    void ensureDefaultSettings_createsSettingsWithCurrentTimestamp() {
        when(shioajiSettingsRepository.findFirst()).thenReturn(null);

        service.ensureDefaultSettings();

        ArgumentCaptor<ShioajiSettings> captor = ArgumentCaptor.forClass(ShioajiSettings.class);
        verify(shioajiSettingsRepository).save(captor.capture());
        
        assertThat(captor.getValue().getUpdatedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    private ShioajiSettings createDefaultSettings() {
        return ShioajiSettings.builder()
                .id(1L)
                .simulation(true)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
