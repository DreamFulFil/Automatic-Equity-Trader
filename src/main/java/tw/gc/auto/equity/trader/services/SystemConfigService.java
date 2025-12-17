package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.SystemConfig;
import tw.gc.auto.equity.trader.repositories.SystemConfigRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Centralized Configuration Service
 * 
 * Provides unified configuration management with:
 * - Snake_case in DB (e.g., daily_loss_limit)
 * - CamelCase in Java (e.g., dailyLossLimit)
 * - Telegram commands: /config help, /config <key> <value>, /show-configs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    // Configuration keys (snake_case for DB)
    public static final String DAILY_LOSS_LIMIT = "daily_loss_limit";
    public static final String WEEKLY_LOSS_LIMIT = "weekly_loss_limit";
    public static final String MONTHLY_LOSS_LIMIT = "monthly_loss_limit";
    public static final String MIN_SHARPE_RATIO = "min_sharpe_ratio";
    public static final String MIN_WIN_RATE = "min_win_rate";
    public static final String MAX_DRAWDOWN_PCT = "max_drawdown_pct";
    public static final String AI_VETO_ENABLED = "ai_veto_enabled";
    public static final String AI_CONFIDENCE_THRESHOLD = "ai_confidence_threshold";
    public static final String SHADOW_MODE_COUNT = "shadow_mode_count";
    public static final String CURRENT_ACTIVE_STOCK = "current_active_stock";
    public static final String MAX_HOLD_MINUTES = "max_hold_minutes";
    public static final String MIN_HOLD_MINUTES = "min_hold_minutes";
    public static final String STOP_LOSS_AMOUNT = "stop_loss_amount";
    
    // Default values
    private static final Map<String, ConfigDefault> DEFAULTS = new HashMap<>();
    
    static {
        DEFAULTS.put(DAILY_LOSS_LIMIT, new ConfigDefault("1500", "Daily loss limit in TWD"));
        DEFAULTS.put(WEEKLY_LOSS_LIMIT, new ConfigDefault("7000", "Weekly loss limit in TWD"));
        DEFAULTS.put(MONTHLY_LOSS_LIMIT, new ConfigDefault("7000", "Monthly loss limit in TWD"));
        DEFAULTS.put(MIN_SHARPE_RATIO, new ConfigDefault("1.0", "Minimum Sharpe ratio for strategy selection"));
        DEFAULTS.put(MIN_WIN_RATE, new ConfigDefault("50.0", "Minimum win rate percentage for strategy selection"));
        DEFAULTS.put(MAX_DRAWDOWN_PCT, new ConfigDefault("20.0", "Maximum drawdown percentage allowed"));
        DEFAULTS.put(AI_VETO_ENABLED, new ConfigDefault("true", "Enable AI-based trade veto"));
        DEFAULTS.put(AI_CONFIDENCE_THRESHOLD, new ConfigDefault("0.65", "AI confidence threshold for signals"));
        DEFAULTS.put(SHADOW_MODE_COUNT, new ConfigDefault("10", "Number of shadow mode strategies to track"));
        DEFAULTS.put(CURRENT_ACTIVE_STOCK, new ConfigDefault("2454.TW", "Currently active trading stock"));
        DEFAULTS.put(MAX_HOLD_MINUTES, new ConfigDefault("45", "Maximum position hold time in minutes"));
        DEFAULTS.put(MIN_HOLD_MINUTES, new ConfigDefault("3", "Minimum position hold time in minutes"));
        DEFAULTS.put(STOP_LOSS_AMOUNT, new ConfigDefault("500", "Stop loss amount per contract/lot in TWD"));
    }

    @PostConstruct
    public void initialize() {
        log.info("🔧 SystemConfigService initialized");
        initializeDefaults();
    }
    
    /**
     * Initialize default configurations if not present
     */
    @Transactional
    public void initializeDefaults() {
        for (Map.Entry<String, ConfigDefault> entry : DEFAULTS.entrySet()) {
            if (configRepository.findByKey(entry.getKey()).isEmpty()) {
                SystemConfig config = SystemConfig.builder()
                    .key(entry.getKey())
                    .value(entry.getValue().value)
                    .description(entry.getValue().description)
                    .build();
                configRepository.save(config);
                log.info("✅ Initialized config: {} = {}", entry.getKey(), entry.getValue().value);
            }
        }
    }

    /**
     * Get configuration value as String
     */
    public String getString(String key) {
        return configRepository.findByKey(key)
            .map(SystemConfig::getValue)
            .orElseGet(() -> {
                ConfigDefault defaultVal = DEFAULTS.get(key);
                return defaultVal != null ? defaultVal.value : null;
            });
    }
    
    /**
     * Get configuration value as Integer
     */
    public int getInt(String key, int defaultValue) {
        try {
            String value = getString(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get configuration value as Double
     */
    public double getDouble(String key, double defaultValue) {
        try {
            String value = getString(key);
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get configuration value as Boolean
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
    
    /**
     * Set configuration value
     */
    @Transactional
    public void setValue(String key, String value) {
        Optional<SystemConfig> existing = configRepository.findByKey(key);
        
        if (existing.isPresent()) {
            existing.get().setValue(value);
            configRepository.save(existing.get());
        } else {
            ConfigDefault defaultConfig = DEFAULTS.get(key);
            SystemConfig config = SystemConfig.builder()
                .key(key)
                .value(value)
                .description(defaultConfig != null ? defaultConfig.description : "User-defined configuration")
                .build();
            configRepository.save(config);
        }
        
        log.info("🔧 Config updated: {} = {}", key, value);
    }
    
    /**
     * Get all configurations as a formatted string for Telegram
     */
    public String getAllConfigsFormatted() {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 SYSTEM CONFIGURATIONS\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        List<SystemConfig> configs = configRepository.findAll();
        
        // Risk Limits
        sb.append("📊 Risk Limits:\n");
        appendConfig(sb, configs, DAILY_LOSS_LIMIT, "Daily Loss Limit");
        appendConfig(sb, configs, WEEKLY_LOSS_LIMIT, "Weekly Loss Limit");
        appendConfig(sb, configs, MONTHLY_LOSS_LIMIT, "Monthly Loss Limit");
        appendConfig(sb, configs, MAX_DRAWDOWN_PCT, "Max Drawdown %");
        
        sb.append("\n📈 Strategy Filters:\n");
        appendConfig(sb, configs, MIN_SHARPE_RATIO, "Min Sharpe Ratio");
        appendConfig(sb, configs, MIN_WIN_RATE, "Min Win Rate %");
        appendConfig(sb, configs, SHADOW_MODE_COUNT, "Shadow Mode Count");
        
        sb.append("\n🤖 AI Settings:\n");
        appendConfig(sb, configs, AI_VETO_ENABLED, "AI Veto Enabled");
        appendConfig(sb, configs, AI_CONFIDENCE_THRESHOLD, "AI Confidence Threshold");
        
        sb.append("\n⏱️ Trading Controls:\n");
        appendConfig(sb, configs, MAX_HOLD_MINUTES, "Max Hold Minutes");
        appendConfig(sb, configs, MIN_HOLD_MINUTES, "Min Hold Minutes");
        appendConfig(sb, configs, STOP_LOSS_AMOUNT, "Stop Loss Amount");
        appendConfig(sb, configs, CURRENT_ACTIVE_STOCK, "Active Stock");
        
        return sb.toString();
    }
    
    private void appendConfig(StringBuilder sb, List<SystemConfig> configs, String key, String displayName) {
        String value = configs.stream()
            .filter(c -> c.getKey().equals(key))
            .map(SystemConfig::getValue)
            .findFirst()
            .orElse(DEFAULTS.containsKey(key) ? DEFAULTS.get(key).value : "N/A");
        
        sb.append(String.format("  %s: %s\n", displayName, value));
    }
    
    /**
     * Get help text for config command
     */
    public String getConfigHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("🔧 CONFIG COMMAND HELP\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append("Usage:\n");
        sb.append("  /config help - Show this help\n");
        sb.append("  /config <key> <value> - Set config value\n");
        sb.append("  /show-configs - Show all configs\n\n");
        sb.append("Available Keys:\n");
        
        for (Map.Entry<String, ConfigDefault> entry : DEFAULTS.entrySet()) {
            sb.append(String.format("  %s\n    %s\n", entry.getKey(), entry.getValue().description));
        }
        
        return sb.toString();
    }
    
    /**
     * Validate and set config value
     * @return error message if validation fails, null if successful
     */
    @Transactional
    public String validateAndSetConfig(String key, String value) {
        // Validate key exists
        if (!DEFAULTS.containsKey(key)) {
            return "❌ Unknown config key: " + key + "\nUse /config help for available keys";
        }
        
        // Type validation
        ConfigDefault defaultConfig = DEFAULTS.get(key);
        try {
            // Numeric validation for numeric configs
            if (key.contains("limit") || key.contains("amount") || key.contains("minutes") || key.contains("count")) {
                Integer.parseInt(value);
            } else if (key.contains("ratio") || key.contains("rate") || key.contains("threshold") || key.contains("pct")) {
                Double.parseDouble(value);
            } else if (key.contains("enabled")) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    return "❌ Value must be 'true' or 'false' for " + key;
                }
            }
        } catch (NumberFormatException e) {
            return "❌ Invalid value format for " + key + ": expected number";
        }
        
        setValue(key, value);
        return null; // Success
    }
    
    private record ConfigDefault(String value, String description) {}
}
