package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.gc.auto.equity.trader.entities.StockRiskSettings;
import tw.gc.auto.equity.trader.repositories.StockRiskSettingsRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StockRiskSettingsServiceTest {

    @Mock
    private StockRiskSettingsRepository repo;

    private StockRiskSettingsService service;

    @BeforeEach
    void setUp() {
        service = new StockRiskSettingsService(repo);
    }

    @Test
    void initialize_createsDefaults_whenMissing() {
        when(repo.findFirst()).thenReturn(null);

        service.initialize();

        ArgumentCaptor<StockRiskSettings> captor = ArgumentCaptor.forClass(StockRiskSettings.class);
        verify(repo, times(1)).save(captor.capture());
        StockRiskSettings saved = captor.getValue();
        assertEquals(50, saved.getMaxSharesPerTrade());
        assertEquals(1000, saved.getDailyLossLimitTwd());
    }

    @Test
    void getSettings_throws_whenMissing() {
        when(repo.findFirst()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.getSettings());
    }

    @Test
    void updateRiskSetting_parsesAndSaves_valid() {
        StockRiskSettings s = StockRiskSettings.builder().maxSharesPerTrade(50).build();
        when(repo.findFirst()).thenReturn(s);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        String res = service.updateRiskSetting("max_shares_per_trade", "60");
        assertNull(res);
        assertEquals(60, s.getMaxSharesPerTrade());
        verify(repo).save(s);
    }

    @Test
    void updateRiskSetting_returnsError_onInvalidNumber() {
        StockRiskSettings s = StockRiskSettings.builder().build();
        when(repo.findFirst()).thenReturn(s);

        String res = service.updateRiskSetting("max_shares_per_trade", "not-a-number");
        assertNotNull(res);
    }

    @Test
    void getAllStockRiskSettingsFormatted_containsExpectedValues() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(77)
                .dailyLossLimitTwd(1500)
                .weeklyLossLimitTwd(5000)
                .stopLossTwdPerTrade(900)
                .maxDailyTrades(3)
                .minHoldMinutes(5)
                .maxHoldMinutes(120)
                .minSharpeRatio(0.5)
                .minWinRate(0.6)
                .maxDrawdownPercent(20.0)
                .strategyBacktestDays(365)
                .minTotalTradesInBacktest(100)
                .enableAiVeto(true)
                .enableVolatilityFilter(false)
                .volatilityThresholdMultiplier(2.5)
                .updatedAt(LocalDateTime.now())
                .build();

        when(repo.findFirst()).thenReturn(settings);

        String out = service.getAllStockRiskSettingsFormatted();

        assertTrue(out.contains("max_shares_per_trade = 77"));
        assertTrue(out.contains("daily_loss_limit_twd = 1500"));
        assertTrue(out.contains("enable_ai_veto = ✅") || out.contains("✅") || out.contains("true"));
        assertTrue(out.contains("enable_volatility_filter = ❌") || out.contains("❌") || out.contains("false"));
    }

    @Test
    void ensureDefaultSettings_doesNothing_whenPresent() {
        when(repo.findFirst()).thenReturn(StockRiskSettings.builder().maxSharesPerTrade(1).build());

        service.ensureDefaultSettings();

        verify(repo, never()).save(any());
    }

    @Test
    void updateSettings_updatesFieldsAndSaves() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(10)
                .dailyLossLimitTwd(100)
                .weeklyLossLimitTwd(200)
                .maxHoldMinutes(10)
                .build();
        when(repo.findFirst()).thenReturn(settings);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        StockRiskSettings saved = service.updateSettings(99, 111, 222, 33);

        assertEquals(99, saved.getMaxSharesPerTrade());
        assertEquals(111, saved.getDailyLossLimitTwd());
        assertEquals(222, saved.getWeeklyLossLimitTwd());
        assertEquals(33, saved.getMaxHoldMinutes());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void simpleGetters_returnUnderlyingSettings() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(12)
                .dailyLossLimitTwd(1001)
                .weeklyLossLimitTwd(4001)
                .maxHoldMinutes(44)
                .enableAiVeto(true)
                .build();
        when(repo.findFirst()).thenReturn(settings);

        assertEquals(12, service.getMaxPosition());
        assertEquals(1001, service.getDailyLossLimit());
        assertEquals(4001, service.getWeeklyLossLimit());
        assertEquals(44, service.getMaxHoldMinutes());
        assertSame(settings, service.getStockRiskSettings());
        assertTrue(service.isAiVetoEnabled());
    }

    @Test
    void updateRiskSetting_allKeys_validValues() {
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(50)
                .dailyLossLimitTwd(1000)
                .weeklyLossLimitTwd(4000)
                .stopLossTwdPerTrade(100)
                .maxDailyTrades(5)
                .minHoldMinutes(0)
                .maxHoldMinutes(45)
                .minSharpeRatio(0.0)
                .minWinRate(0.5)
                .maxDrawdownPercent(10.0)
                .strategyBacktestDays(365)
                .minTotalTradesInBacktest(50)
                .enableAiVeto(false)
                .enableVolatilityFilter(false)
                .volatilityThresholdMultiplier(1.0)
                .build();
        when(repo.findFirst()).thenReturn(settings);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        assertNull(service.updateRiskSetting("max_shares_per_trade", "1"));
        assertNull(service.updateRiskSetting("daily_loss_limit_twd", "1"));
        assertNull(service.updateRiskSetting("weekly_loss_limit_twd", "1"));
        assertNull(service.updateRiskSetting("stop_loss_twd_per_trade", "1"));
        assertNull(service.updateRiskSetting("max_daily_trades", "1"));
        assertNull(service.updateRiskSetting("min_hold_minutes", "0"));
        assertNull(service.updateRiskSetting("max_hold_minutes", "1"));
        assertNull(service.updateRiskSetting("min_sharpe_ratio", "0.1"));
        assertNull(service.updateRiskSetting("min_win_rate", "0.1"));
        assertNull(service.updateRiskSetting("max_drawdown_percent", "1"));
        assertNull(service.updateRiskSetting("strategy_backtest_days", "30"));
        assertNull(service.updateRiskSetting("min_total_trades_in_backtest", "10"));
        assertNull(service.updateRiskSetting("enable_ai_veto", "true"));
        assertNull(service.updateRiskSetting("enable_volatility_filter", "true"));
        assertNull(service.updateRiskSetting("volatility_threshold_multiplier", "0.5"));

        verify(repo, atLeastOnce()).save(settings);
    }

    @Test
    void updateRiskSetting_validationErrors_coverAllGuards() {
        StockRiskSettings settings = StockRiskSettings.builder().build();
        when(repo.findFirst()).thenReturn(settings);

        assertTrue(service.updateRiskSetting("max_shares_per_trade", "0").contains("1-1000"));
        assertTrue(service.updateRiskSetting("daily_loss_limit_twd", "0").contains("> 0"));
        assertTrue(service.updateRiskSetting("weekly_loss_limit_twd", "0").contains("> 0"));
        assertTrue(service.updateRiskSetting("stop_loss_twd_per_trade", "0").contains("> 0"));
        assertTrue(service.updateRiskSetting("max_daily_trades", "101").contains("1-100"));
        assertTrue(service.updateRiskSetting("min_hold_minutes", "-1").contains("0-180"));
        assertTrue(service.updateRiskSetting("max_hold_minutes", "0").contains("1-360"));
        assertTrue(service.updateRiskSetting("min_sharpe_ratio", "-0.1").contains(">= 0"));
        assertTrue(service.updateRiskSetting("min_win_rate", "1.1").contains("0.0-1.0"));
        assertTrue(service.updateRiskSetting("max_drawdown_percent", "101").contains("0-100"));
        assertTrue(service.updateRiskSetting("strategy_backtest_days", "29").contains("30-3650"));
        assertTrue(service.updateRiskSetting("min_total_trades_in_backtest", "9").contains("10-10000"));
        assertTrue(service.updateRiskSetting("volatility_threshold_multiplier", "0.05").contains("0.1-10.0"));
        assertTrue(service.updateRiskSetting("unknown_key", "1").contains("Unknown risk setting"));
    }

    @Test
    void updateRiskSetting_returnsGenericError_whenRepoSaveFails() {
        StockRiskSettings settings = StockRiskSettings.builder().maxSharesPerTrade(1).build();
        when(repo.findFirst()).thenReturn(settings);
        when(repo.save(any())).thenThrow(new RuntimeException("db down"));

        String msg = service.updateRiskSetting("max_shares_per_trade", "2");

        assertNotNull(msg);
        assertTrue(msg.contains("Error updating setting"));
    }

    @Test
    void getStockRiskSettingsHelp_containsKeys() {
        String help = service.getStockRiskSettingsHelp();
        assertTrue(help.contains("max_shares_per_trade"));
        assertTrue(help.contains("volatility_threshold_multiplier"));
    }

    // ==================== Coverage tests for lines 277-278 ====================

    @Test
    void getAllStockRiskSettingsFormatted_enableAiVeto_displaysCheckmark() {
        // Lines 277-278: settings.isEnableAiVeto() ? "✅" : "❌"
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(50)
                .dailyLossLimitTwd(1000)
                .weeklyLossLimitTwd(4000)
                .stopLossTwdPerTrade(100)
                .maxDailyTrades(5)
                .minHoldMinutes(1)
                .maxHoldMinutes(45)
                .minSharpeRatio(1.0)
                .minWinRate(0.5)
                .maxDrawdownPercent(15.0)
                .strategyBacktestDays(180)
                .minTotalTradesInBacktest(50)
                .enableAiVeto(true) // True case
                .enableVolatilityFilter(false) // False case
                .volatilityThresholdMultiplier(1.5)
                .updatedAt(LocalDateTime.now())
                .build();

        when(repo.findFirst()).thenReturn(settings);

        String out = service.getAllStockRiskSettingsFormatted();

        // Verify both branches are covered
        assertTrue(out.contains("enable_ai_veto = ✅"));
        assertTrue(out.contains("enable_volatility_filter = ❌"));
    }

    @Test
    void getAllStockRiskSettingsFormatted_enableVolatilityFilter_displaysCheckmark() {
        // Test the opposite boolean values
        StockRiskSettings settings = StockRiskSettings.builder()
                .maxSharesPerTrade(50)
                .dailyLossLimitTwd(1000)
                .weeklyLossLimitTwd(4000)
                .stopLossTwdPerTrade(100)
                .maxDailyTrades(5)
                .minHoldMinutes(1)
                .maxHoldMinutes(45)
                .minSharpeRatio(1.0)
                .minWinRate(0.5)
                .maxDrawdownPercent(15.0)
                .strategyBacktestDays(180)
                .minTotalTradesInBacktest(50)
                .enableAiVeto(false) // False case
                .enableVolatilityFilter(true) // True case
                .volatilityThresholdMultiplier(1.5)
                .updatedAt(LocalDateTime.now())
                .build();

        when(repo.findFirst()).thenReturn(settings);

        String out = service.getAllStockRiskSettingsFormatted();

        // Verify opposite values
        assertTrue(out.contains("enable_ai_veto = ❌"));
        assertTrue(out.contains("enable_volatility_filter = ✅"));
    }

}
