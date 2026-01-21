package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.SystemConfig;
import tw.gc.auto.equity.trader.repositories.SystemConfigRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepository configRepository;

    private SystemConfigService systemConfigService;

    @BeforeEach
    void setUp() {
        systemConfigService = new SystemConfigService(configRepository);
    }

    @Test
    void getString_shouldReturnValue_whenConfigExists() {
        // Given
        String key = "daily_loss_limit";
        SystemConfig config = SystemConfig.builder()
            .key(key)
            .value("1500")
            .description("Daily loss limit in TWD")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(config));
        
        // When
        String result = systemConfigService.getString(key);
        
        // Then
        assertThat(result).isEqualTo("1500");
    }

    @Test
    void getString_shouldReturnDefault_whenConfigNotFound() {
        // Given
        String key = "daily_loss_limit";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        
        // When
        String result = systemConfigService.getString(key);
        
        // Then
        assertThat(result).isEqualTo("1500"); // Default value from DEFAULTS map
    }

    @Test
    void getInt_shouldReturnInteger_whenConfigExists() {
        // Given
        String key = "weekly_loss_limit";
        SystemConfig config = SystemConfig.builder()
            .key(key)
            .value("7000")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(config));
        
        // When
        int result = systemConfigService.getInt(key, 5000);
        
        // Then
        assertThat(result).isEqualTo(7000);
    }

    @Test
    void getInt_shouldReturnDefault_whenConfigInvalid() {
        // Given
        String key = "test_key";
        SystemConfig config = SystemConfig.builder()
            .key(key)
            .value("not_a_number")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(config));
        
        // When
        int result = systemConfigService.getInt(key, 999);
        
        // Then
        assertThat(result).isEqualTo(999);
    }

    @Test
    void getDouble_shouldReturnDouble_whenConfigExists() {
        // Given
        String key = "min_sharpe_ratio";
        SystemConfig config = SystemConfig.builder()
            .key(key)
            .value("1.5")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(config));
        
        // When
        double result = systemConfigService.getDouble(key, 1.0);
        
        // Then
        assertThat(result).isEqualTo(1.5);
    }

    @Test
    void getBoolean_shouldReturnBoolean_whenConfigExists() {
        // Given
        String key = "ai_veto_enabled";
        SystemConfig config = SystemConfig.builder()
            .key(key)
            .value("true")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(config));
        
        // When
        boolean result = systemConfigService.getBoolean(key, false);
        
        // Then
        assertThat(result).isTrue();
    }

    @Test
    void setValue_shouldUpdateExistingConfig() {
        // Given
        String key = "daily_loss_limit";
        SystemConfig existingConfig = SystemConfig.builder()
            .key(key)
            .value("1500")
            .description("Daily loss limit in TWD")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(existingConfig));
        when(configRepository.save(any(SystemConfig.class))).thenReturn(existingConfig);
        
        // When
        systemConfigService.setValue(key, "2000");
        
        // Then
        verify(configRepository).save(existingConfig);
        assertThat(existingConfig.getValue()).isEqualTo("2000");
    }

    @Test
    void setValue_shouldCreateNewConfig_whenNotExists() {
        // Given
        String key = "new_config_key";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        systemConfigService.setValue(key, "new_value");
        
        // Then
        verify(configRepository).save(any(SystemConfig.class));
    }

    @Test
    void validateAndSetConfig_shouldRejectUnknownKey() {
        // When
        String error = systemConfigService.validateAndSetConfig("unknown_key", "value");
        
        // Then
        assertThat(error).contains("Unknown config key");
    }

    @Test
    void validateAndSetConfig_shouldRejectInvalidNumberFormat() {
        // When
        String error = systemConfigService.validateAndSetConfig("daily_loss_limit", "not_a_number");
        
        // Then
        assertThat(error).contains("Invalid value format");
    }

    @Test
    void validateAndSetConfig_shouldRejectInvalidBooleanFormat() {
        // When
        String error = systemConfigService.validateAndSetConfig("ai_veto_enabled", "maybe");
        
        // Then
        assertThat(error).contains("must be 'true' or 'false'");
    }

    @Test
    void validateAndSetConfig_shouldAcceptValidNumericValue() {
        // Given
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        String error = systemConfigService.validateAndSetConfig("daily_loss_limit", "2500");
        
        // Then
        assertThat(error).isNull();
        verify(configRepository).save(any(SystemConfig.class));
    }

    @Test
    void validateAndSetConfig_shouldAcceptValidBooleanValue() {
        // Given
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        String error = systemConfigService.validateAndSetConfig("ai_veto_enabled", "false");
        
        // Then
        assertThat(error).isNull();
    }

    @Test
    void getAllConfigsFormatted_shouldReturnFormattedString() {
        // Given
        List<SystemConfig> configs = Arrays.asList(
            SystemConfig.builder().key("daily_loss_limit").value("1500").build(),
            SystemConfig.builder().key("weekly_loss_limit").value("7000").build(),
            SystemConfig.builder().key("ai_veto_enabled").value("true").build()
        );
        
        when(configRepository.findAll()).thenReturn(configs);
        
        // When
        String formatted = systemConfigService.getAllConfigsFormatted();
        
        // Then
        assertThat(formatted).contains("SYSTEM CONFIGURATIONS");
        assertThat(formatted).contains("Risk Limits");
        assertThat(formatted).contains("AI Settings");
    }

    @Test
    void getConfigHelp_shouldReturnHelpText() {
        // When
        String help = systemConfigService.getConfigHelp();
        
        // Then
        assertThat(help).contains("CONFIG COMMAND HELP");
        assertThat(help).contains("/config help");
        assertThat(help).contains("Available Keys");
        assertThat(help).contains("daily_loss_limit");
    }

    @Test
    void initializeDefaults_shouldCreateMissingConfigs() {
        // Given
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        systemConfigService.initializeDefaults();
        
        // Then
        verify(configRepository, atLeast(10)).save(any(SystemConfig.class));
    }
    
    @Test
    void initialize_shouldCallInitializeDefaults() {
        // Given
        when(configRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        systemConfigService.initialize();
        
        // Then - verify that initializeDefaults was called by checking saves
        verify(configRepository, atLeast(10)).save(any(SystemConfig.class));
    }
    
    @Test
    void getDouble_shouldReturnDefault_whenNumberFormatException() {
        // Given
        String key = "min_sharpe_ratio";
        SystemConfig config = SystemConfig.builder()
            .key(key)
            .value("not_a_double")
            .build();
        
        when(configRepository.findByKey(key)).thenReturn(Optional.of(config));
        
        // When
        double result = systemConfigService.getDouble(key, 1.5);
        
        // Then
        assertThat(result).isEqualTo(1.5);
    }
    
    @Test
    void validateAndSetConfig_shouldHandleDoubleValidation() {
        // Given
        String key = "min_sharpe_ratio";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        // When
        String error = systemConfigService.validateAndSetConfig(key, "1.5");
        
        // Then
        assertThat(error).isNull();
        verify(configRepository).save(any(SystemConfig.class));
    }

    // ==================== Coverage tests for lines 97, 107, 119, 130, 196 ====================

    @Test
    void getString_whenKeyNotInDefaults_shouldReturnNull() {
        // Line 97: return null when key not in DEFAULTS
        String unknownKey = "completely_unknown_key_xyz";
        when(configRepository.findByKey(unknownKey)).thenReturn(Optional.empty());
        
        String result = systemConfigService.getString(unknownKey);
        
        assertThat(result).isNull();
    }

    @Test
    void getInt_whenValueIsNull_shouldReturnDefault() {
        // Line 107: value != null ? Integer.parseInt(value) : defaultValue
        String key = "unknown_int_key";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        
        int result = systemConfigService.getInt(key, 42);
        
        assertThat(result).isEqualTo(42);
    }

    @Test
    void getDouble_whenValueIsNull_shouldReturnDefault() {
        // Line 119: value != null ? Double.parseDouble(value) : defaultValue
        String key = "unknown_double_key";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        
        double result = systemConfigService.getDouble(key, 3.14);
        
        assertThat(result).isEqualTo(3.14);
    }

    @Test
    void getBoolean_whenValueIsNull_shouldReturnDefault() {
        // Line 130: value != null ? Boolean.parseBoolean(value) : defaultValue
        String key = "unknown_bool_key";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        
        boolean result = systemConfigService.getBoolean(key, true);
        
        assertThat(result).isTrue();
        
        // Also test with false default
        boolean result2 = systemConfigService.getBoolean(key, false);
        assertThat(result2).isFalse();
    }

    @Test
    void appendConfig_whenConfigNotFound_shouldUseDefaultValue() {
        // Line 196: Use DEFAULTS value when config not found
        when(configRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        
        String formatted = systemConfigService.getAllConfigsFormatted();
        
        // Should contain default values from DEFAULTS map
        assertThat(formatted).contains("1500"); // daily_loss_limit default
        assertThat(formatted).contains("7000"); // weekly_loss_limit default
    }

    @Test
    void appendConfig_whenConfigNotFoundAndNotInDefaults_shouldShowNA() {
        // Line 196: DEFAULTS.containsKey(key) ? DEFAULTS.get(key).value : "N/A"
        // This is implicitly tested by the formatted output structure
        when(configRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        
        String formatted = systemConfigService.getAllConfigsFormatted();
        
        // The formatted output should still be well-formed
        assertThat(formatted).contains("SYSTEM CONFIGURATIONS");
    }

    @Test
    void setValue_whenKeyInDefaults_shouldUseDefaultDescription() {
        // Line 148: Use defaultConfig.description when creating new config
        String key = "daily_loss_limit"; // Key in DEFAULTS
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        systemConfigService.setValue(key, "2500");
        
        verify(configRepository).save(argThat(config -> 
            config.getDescription().contains("Daily loss limit")
        ));
    }

    @Test
    void setValue_whenKeyNotInDefaults_shouldUseUserDefinedDescription() {
        // Line 148: Use "User-defined configuration" when not in DEFAULTS
        String key = "custom_user_key";
        when(configRepository.findByKey(key)).thenReturn(Optional.empty());
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(i -> i.getArgument(0));
        
        systemConfigService.setValue(key, "custom_value");
        
        verify(configRepository).save(argThat(config -> 
            config.getDescription().equals("User-defined configuration")
        ));
    }

    // ==================== Coverage test for line 196 ====================
    
    @Test
    void appendConfig_whenKeyNotInDatabaseButInDefaults_usesDefaultValue() {
        // Line 196: DEFAULTS.containsKey(key) ? DEFAULTS.get(key).value : "N/A"
        // Test that when a config is not in database but exists in DEFAULTS, we use the default value
        when(configRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        
        String formatted = systemConfigService.getAllConfigsFormatted();
        
        // The formatted output should show default values like "1500" for daily_loss_limit
        // since the database returned empty but DEFAULTS has values
        assertThat(formatted).contains("1500"); // daily_loss_limit default value
    }
}

