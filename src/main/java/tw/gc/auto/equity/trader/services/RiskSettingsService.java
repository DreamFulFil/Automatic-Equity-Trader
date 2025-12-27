package tw.gc.auto.equity.trader.services;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.RiskSettings;
import tw.gc.auto.equity.trader.repositories.RiskSettingsRepository;

import java.time.LocalDateTime;

/**
 * RiskSettingsService - Manages risk management configuration.
 * Provides access to risk-specific settings stored in database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskSettingsService {

    @NonNull
    private final RiskSettingsRepository riskSettingsRepo;

    @PostConstruct
    public void initialize() {
        ensureDefaultSettings();
        log.info("⚠️ RiskSettingsService initialized");
    }

    /**
     * Ensure default risk settings exist in database
     */
    @Transactional
    public void ensureDefaultSettings() {
        if (riskSettingsRepo.findFirst() == null) {
            RiskSettings defaultSettings = RiskSettings.builder()
                    .maxSharesPerTrade(50)
                    .dailyLossLimitTwd(1000)
                    .weeklyLossLimitTwd(4000)
                    .maxHoldMinutes(45)
                    .updatedAt(LocalDateTime.now())
                    .build();
            riskSettingsRepo.save(defaultSettings);
            log.info("✅ Created default risk settings");
        }
    }

    /**
     * Get current risk settings
     */
    public RiskSettings getSettings() {
        RiskSettings settings = riskSettingsRepo.findFirst();
        if (settings == null) {
            throw new IllegalStateException("Risk settings not found in database");
        }
        return settings;
    }

    /**
     * Update risk settings
     */
    @Transactional
    public RiskSettings updateSettings(int maxPosition, int dailyLossLimit, int weeklyLossLimit, int maxHoldMinutes) {
        RiskSettings settings = getSettings();
        settings.setMaxSharesPerTrade(maxPosition);
        settings.setDailyLossLimitTwd(dailyLossLimit);
        settings.setWeeklyLossLimitTwd(weeklyLossLimit);
        settings.setMaxHoldMinutes(maxHoldMinutes);
        settings.setUpdatedAt(LocalDateTime.now());
        return riskSettingsRepo.save(settings);
    }

    /**
     * Get maximum position size (deprecated - use getMaxSharesPerTrade)
     */
    @Deprecated
    public int getMaxPosition() {
        return getSettings().getMaxSharesPerTrade();
    }

    /**
     * Get daily loss limit
     */
    public int getDailyLossLimit() {
        return getSettings().getDailyLossLimitTwd();
    }

    /**
     * Get weekly loss limit
     */
    public int getWeeklyLossLimit() {
        return getSettings().getWeeklyLossLimitTwd();
    }

    /**
     * Get maximum hold time in minutes
     */
    public int getMaxHoldMinutes() {
        return getSettings().getMaxHoldMinutes();
    }
    
    /**
     * Get all risk settings as RiskSettings entity
     */
    public RiskSettings getRiskSettings() {
        return getSettings();
    }
    
    /**
     * Update a specific risk setting by key
     * @param key Setting name (snake_case)
     * @param value New value
     * @return Error message if validation fails, null if successful
     */
    @Transactional
    public String updateRiskSetting(String key, String value) {
        try {
            RiskSettings settings = getSettings();
            
            switch (key.toLowerCase()) {
                case "max_shares_per_trade":
                    int shares = Integer.parseInt(value);
                    if (shares <= 0 || shares > 1000) return "❌ max_shares_per_trade must be 1-1000";
                    settings.setMaxSharesPerTrade(shares);
                    break;
                    
                case "daily_loss_limit_twd":
                    int dailyLimit = Integer.parseInt(value);
                    if (dailyLimit <= 0) return "❌ daily_loss_limit_twd must be > 0";
                    settings.setDailyLossLimitTwd(dailyLimit);
                    break;
                    
                case "weekly_loss_limit_twd":
                    int weeklyLimit = Integer.parseInt(value);
                    if (weeklyLimit <= 0) return "❌ weekly_loss_limit_twd must be > 0";
                    settings.setWeeklyLossLimitTwd(weeklyLimit);
                    break;
                    
                case "stop_loss_twd_per_trade":
                    int stopLoss = Integer.parseInt(value);
                    if (stopLoss <= 0) return "❌ stop_loss_twd_per_trade must be > 0";
                    settings.setStopLossTwdPerTrade(stopLoss);
                    break;
                    
                case "max_daily_trades":
                    int maxTrades = Integer.parseInt(value);
                    if (maxTrades <= 0 || maxTrades > 100) return "❌ max_daily_trades must be 1-100";
                    settings.setMaxDailyTrades(maxTrades);
                    break;
                    
                case "min_hold_minutes":
                    int minHold = Integer.parseInt(value);
                    if (minHold < 0 || minHold > 180) return "❌ min_hold_minutes must be 0-180";
                    settings.setMinHoldMinutes(minHold);
                    break;
                    
                case "max_hold_minutes":
                    int maxHold = Integer.parseInt(value);
                    if (maxHold <= 0 || maxHold > 360) return "❌ max_hold_minutes must be 1-360";
                    settings.setMaxHoldMinutes(maxHold);
                    break;
                    
                case "min_sharpe_ratio":
                    double minSharpe = Double.parseDouble(value);
                    if (minSharpe < 0) return "❌ min_sharpe_ratio must be >= 0";
                    settings.setMinSharpeRatio(minSharpe);
                    break;
                    
                case "min_win_rate":
                    double minWinRate = Double.parseDouble(value);
                    if (minWinRate < 0 || minWinRate > 1.0) return "❌ min_win_rate must be 0.0-1.0";
                    settings.setMinWinRate(minWinRate);
                    break;
                    
                case "max_drawdown_percent":
                    double maxDrawdown = Double.parseDouble(value);
                    if (maxDrawdown < 0 || maxDrawdown > 100) return "❌ max_drawdown_percent must be 0-100";
                    settings.setMaxDrawdownPercent(maxDrawdown);
                    break;
                    
                case "strategy_backtest_days":
                    int backtestDays = Integer.parseInt(value);
                    if (backtestDays < 30 || backtestDays > 3650) return "❌ strategy_backtest_days must be 30-3650";
                    settings.setStrategyBacktestDays(backtestDays);
                    break;
                    
                case "min_total_trades_in_backtest":
                    int minTrades = Integer.parseInt(value);
                    if (minTrades < 10 || minTrades > 10000) return "❌ min_total_trades_in_backtest must be 10-10000";
                    settings.setMinTotalTradesInBacktest(minTrades);
                    break;
                    
                case "enable_ai_veto":
                    boolean enableAi = Boolean.parseBoolean(value);
                    settings.setEnableAiVeto(enableAi);
                    break;
                    
                case "enable_volatility_filter":
                    boolean enableVolatility = Boolean.parseBoolean(value);
                    settings.setEnableVolatilityFilter(enableVolatility);
                    break;
                    
                case "volatility_threshold_multiplier":
                    double volatilityThreshold = Double.parseDouble(value);
                    if (volatilityThreshold < 0.1 || volatilityThreshold > 10.0) return "❌ volatility_threshold_multiplier must be 0.1-10.0";
                    settings.setVolatilityThresholdMultiplier(volatilityThreshold);
                    break;
                    
                default:
                    return "❌ Unknown risk setting: " + key;
            }
            
            settings.setUpdatedAt(LocalDateTime.now());
            riskSettingsRepo.save(settings);
            log.info("✅ Updated risk setting: {} = {}", key, value);
            return null; // Success
            
        } catch (NumberFormatException e) {
            return "❌ Invalid value format for " + key + ": " + value;
        } catch (Exception e) {
            log.error("Failed to update risk setting: {} = {}", key, value, e);
            return "❌ Error updating setting: " + e.getMessage();
        }
    }
    
    /**
     * Get all risk settings formatted for Telegram display
     */
    public String getAllRiskSettingsFormatted() {
        RiskSettings settings = getSettings();
        return String.format(
            "⚙️ RISK SETTINGS\n" +
            "━━━━━━━━━━━━━━━━\n" +
            "📊 Position Sizing:\n" +
            "  • max_shares_per_trade = %d\n\n" +
            "💰 Loss Limits:\n" +
            "  • daily_loss_limit_twd = %d\n" +
            "  • weekly_loss_limit_twd = %d\n" +
            "  • stop_loss_twd_per_trade = %d\n\n" +
            "📅 Trade Frequency:\n" +
            "  • max_daily_trades = %d\n" +
            "  • min_hold_minutes = %d\n" +
            "  • max_hold_minutes = %d\n\n" +
            "📈 Strategy Filters:\n" +
            "  • min_sharpe_ratio = %.2f\n" +
            "  • min_win_rate = %.2f\n" +
            "  • max_drawdown_percent = %.1f%%\n" +
            "  • strategy_backtest_days = %d\n" +
            "  • min_total_trades_in_backtest = %d\n\n" +
            "🤖 AI & Filters:\n" +
            "  • enable_ai_veto = %s\n" +
            "  • enable_volatility_filter = %s\n" +
            "  • volatility_threshold_multiplier = %.1f\n\n" +
            "📝 To update: /risk <key> <value>\n" +
            "Example: /risk daily_loss_limit_twd 1500",
            settings.getMaxSharesPerTrade(),
            settings.getDailyLossLimitTwd(),
            settings.getWeeklyLossLimitTwd(),
            settings.getStopLossTwdPerTrade(),
            settings.getMaxDailyTrades(),
            settings.getMinHoldMinutes(),
            settings.getMaxHoldMinutes(),
            settings.getMinSharpeRatio(),
            settings.getMinWinRate(),
            settings.getMaxDrawdownPercent(),
            settings.getStrategyBacktestDays(),
            settings.getMinTotalTradesInBacktest(),
            settings.isEnableAiVeto() ? "✅" : "❌",
            settings.isEnableVolatilityFilter() ? "✅" : "❌",
            settings.getVolatilityThresholdMultiplier()
        );
    }
    
    /**
     * Get help text for risk settings configuration
     */
    public String getRiskSettingsHelp() {
        return "⚙️ RISK SETTINGS CONFIGURATION\n" +
               "━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
               "📋 Commands:\n" +
               "  /risk - Show all current settings\n" +
               "  /risk <key> <value> - Update a setting\n\n" +
               "🔑 Available Keys:\n\n" +
               "📊 Position Sizing:\n" +
               "  • max_shares_per_trade (1-1000)\n\n" +
               "💰 Loss Limits:\n" +
               "  • daily_loss_limit_twd\n" +
               "  • weekly_loss_limit_twd\n" +
               "  • stop_loss_twd_per_trade\n\n" +
               "📅 Trade Frequency:\n" +
               "  • max_daily_trades (1-100)\n" +
               "  • min_hold_minutes (0-180)\n" +
               "  • max_hold_minutes (1-360)\n\n" +
               "📈 Strategy Filters:\n" +
               "  • min_sharpe_ratio (0+)\n" +
               "  • min_win_rate (0.0-1.0)\n" +
               "  • max_drawdown_percent (0-100)\n" +
               "  • strategy_backtest_days (30-3650)\n" +
               "  • min_total_trades_in_backtest (10-10000)\n\n" +
               "🤖 AI & Filters:\n" +
               "  • enable_ai_veto (true/false)\n" +
               "  • enable_volatility_filter (true/false)\n" +
               "  • volatility_threshold_multiplier (0.1-10.0)\n\n" +
               "💡 Example:\n" +
               "  /risk daily_loss_limit_twd 1500";
    }
}