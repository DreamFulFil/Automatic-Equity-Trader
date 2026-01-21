package tw.gc.auto.equity.trader.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.BotSettings;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.entities.Trade.TradeStatus;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RiskManagerAgent
 */
class RiskManagerAgentTest {
    
    private RiskManagerAgent riskManager;
    private TradeRepository mockTradeRepo;
    private BotSettingsRepository botSettingsRepository;
    
    @BeforeEach
    void setUp() {
        mockTradeRepo = mock(TradeRepository.class);
        botSettingsRepository = mock(BotSettingsRepository.class);
        riskManager = new RiskManagerAgent(mockTradeRepo, botSettingsRepository);
    }
    
    @Test
    void testCheckLimits_NoLimitHit() {
        // Setup: P&L within limits
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(0.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertFalse((boolean) result.get("should_halt"));
        assertFalse((boolean) result.get("daily_limit_hit"));
        assertFalse((boolean) result.get("weekly_limit_hit"));
        assertFalse((boolean) result.get("monthly_limit_hit"));
    }
    
    @Test
    void testCheckLimits_DailyLimitHit() {
        // Setup: Daily loss exceeds limit (default -2500)
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-3000.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertTrue((boolean) result.get("should_halt"));
        assertTrue((boolean) result.get("daily_limit_hit"));
        assertEquals("Daily loss limit", result.get("halt_reason"));
    }
    
    @Test
    void testCheckGoLiveEligibility_NotEnoughTrades() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(10L); // Less than minimum 20
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(8L);
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(-500.0);
        
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        assertTrue((boolean) result.get("success"));
        assertFalse((boolean) result.get("eligible"));
        assertFalse((boolean) result.get("has_enough_trades"));
    }
    
    @Test
    void testCheckGoLiveEligibility_LowWinRate() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(30L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(10L); // 33% win rate < 55%
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(-500.0);
        
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        assertTrue((boolean) result.get("success"));
        assertFalse((boolean) result.get("eligible"));
        assertFalse((boolean) result.get("win_rate_ok"));
    }
    
    @Test
    void testCheckGoLiveEligibility_Eligible() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(30L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(20L); // 67% win rate > 55%
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(-1000.0); // 1% < 5%
        
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        assertTrue((boolean) result.get("success"));
        assertTrue((boolean) result.get("eligible"));
        assertTrue((boolean) result.get("has_enough_trades"));
        assertTrue((boolean) result.get("win_rate_ok"));
        assertTrue((boolean) result.get("drawdown_ok"));
    }
    
    @Test
    void testDynamicLimitConfiguration() {
        riskManager.setDailyLossLimit(5000);
        riskManager.setWeeklyLossLimit(10000);
        
        // At -4000, should not hit new limit
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-4000.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertFalse((boolean) result.get("daily_limit_hit"));
    }

    @Test
    void testCheckTradeRisk_UsesBotSettingsOverrides() {
        when(botSettingsRepository.findByKey(BotSettings.DAILY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.DAILY_LOSS_LIMIT).value("1000").build()));
        when(botSettingsRepository.findByKey(BotSettings.WEEKLY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.WEEKLY_LOSS_LIMIT).value("2000").build()));
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-900.0, -1500.0); // daily then weekly

        Trade trade = Trade.builder().mode(TradingMode.SIMULATION).realizedPnL(-200.0).build();

        Map<String, Object> result = riskManager.checkTradeRisk(trade);

        assertFalse((boolean) result.get("allowed"));
        assertEquals("Daily loss limit", result.get("reason"));
    }

    @Test
    void testStateTransitions() {
        assertEquals(RiskManagerAgent.BotState.RUNNING, riskManager.getState());
        riskManager.handlePause();
        assertEquals(RiskManagerAgent.BotState.PAUSED, riskManager.getState());
        riskManager.handleResume();
        assertEquals(RiskManagerAgent.BotState.RUNNING, riskManager.getState());
        riskManager.handleStop();
        assertEquals(RiskManagerAgent.BotState.STOPPED, riskManager.getState());
        riskManager.handleStart("SIMULATION");
        assertEquals(RiskManagerAgent.BotState.RUNNING, riskManager.getState());
    }
    
    @Test
    void testGetTradeStats() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(50L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED))
                .thenReturn(30L);
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(5000.0);
        
        Map<String, Object> result = riskManager.getTradeStats(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertEquals(50L, result.get("total_trades_30d"));
        assertEquals(30L, result.get("winning_trades"));
        assertEquals("60.0%", result.get("win_rate"));
        assertEquals(5000.0, result.get("total_pnl_30d"));
    }
    
    @Test
    void testFallbackResponse() {
        Map<String, Object> fallback = riskManager.getFallbackResponse();
        
        assertFalse((boolean) fallback.get("success"));
        assertTrue((boolean) fallback.get("should_halt")); // Fail-safe: halt on error
    }

    @Test
    void testExecute_CheckCommand() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(0.0);
        
        Map<String, Object> input = new HashMap<>();
        input.put("command", "check");
        input.put("mode", TradingMode.SIMULATION);
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals("RiskManager", result.get("agent"));
        assertFalse((boolean) result.get("should_halt"));
    }

    @Test
    void testExecute_GoLiveCommand() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(25L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(20L);
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(-1000.0);
        
        Map<String, Object> input = new HashMap<>();
        input.put("command", "golive");
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertTrue((boolean) result.get("eligible"));
    }

    @Test
    void testExecute_StatsCommand() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(30L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(18L);
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(2500.0);
        
        Map<String, Object> input = new HashMap<>();
        input.put("command", "stats");
        input.put("mode", TradingMode.SIMULATION);
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals(30L, result.get("total_trades_30d"));
    }

    @Test
    void testExecute_StartCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "start");
        input.put("mode", TradingMode.LIVE);
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals("RUNNING", result.get("state"));
        assertEquals("LIVE", result.get("mode"));
    }

    @Test
    void testExecute_PauseCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "pause");
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals("PAUSED", result.get("state"));
    }

    @Test
    void testExecute_ResumeCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "resume");
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals("RUNNING", result.get("state"));
    }

    @Test
    void testExecute_StopCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "stop");
        Map<String, Object> result = riskManager.execute(input);
        
        assertTrue((boolean) result.get("success"));
        assertEquals("STOPPED", result.get("state"));
    }

    @Test
    void testExecute_UnknownCommand() {
        Map<String, Object> input = new HashMap<>();
        input.put("command", "invalid");
        Map<String, Object> result = riskManager.execute(input);
        
        assertFalse((boolean) result.get("success"));
        assertEquals("Unknown command: invalid", result.get("error"));
        assertEquals("RiskManager", result.get("agent"));
    }

    @Test
    void testCheckTradeRisk_NullRepository() {
        RiskManagerAgent agent = new RiskManagerAgent(null, botSettingsRepository);
        Trade trade = Trade.builder().mode(TradingMode.SIMULATION).realizedPnL(-100.0).build();
        
        Map<String, Object> result = agent.checkTradeRisk(trade);
        
        assertFalse((boolean) result.get("allowed"));
        assertEquals("Trade repository not available", result.get("reason"));
    }

    @Test
    void testCheckTradeRisk_AllowedTrade() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-100.0, -200.0);
        Trade trade = Trade.builder().mode(TradingMode.SIMULATION).realizedPnL(-50.0).build();
        
        Map<String, Object> result = riskManager.checkTradeRisk(trade);
        
        assertTrue((boolean) result.get("allowed"));
        assertEquals("Within limits", result.get("reason"));
    }

    @Test
    void testCheckTradeRisk_WeeklyLimitHit() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-100.0, -6500.0);
        when(botSettingsRepository.findByKey(BotSettings.DAILY_LOSS_LIMIT)).thenReturn(Optional.empty());
        when(botSettingsRepository.findByKey(BotSettings.WEEKLY_LOSS_LIMIT)).thenReturn(Optional.empty());
        
        Trade trade = Trade.builder().mode(TradingMode.SIMULATION).realizedPnL(-600.0).build();
        
        Map<String, Object> result = riskManager.checkTradeRisk(trade);
        
        assertFalse((boolean) result.get("allowed"));
        assertEquals("Weekly loss limit", result.get("reason"));
    }

    @Test
    void testCheckTradeRisk_NullMode() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-100.0);
        Trade trade = Trade.builder().realizedPnL(-50.0).build(); // null mode
        
        Map<String, Object> result = riskManager.checkTradeRisk(trade);
        
        assertTrue((boolean) result.get("allowed"));
    }

    @Test
    void testCheckTradeRisk_NullRealizedPnL() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-100.0);
        Trade trade = Trade.builder().mode(TradingMode.SIMULATION).build(); // null PnL
        
        Map<String, Object> result = riskManager.checkTradeRisk(trade);
        
        assertTrue((boolean) result.get("allowed"));
    }

    @Test
    void testCheckLimits_NullRepository() {
        RiskManagerAgent agent = new RiskManagerAgent(null, botSettingsRepository);
        
        Map<String, Object> result = agent.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertEquals(0.0, result.get("daily_pnl"));
        assertEquals(0.0, result.get("weekly_pnl"));
        assertEquals(0.0, result.get("monthly_pnl"));
    }

    @Test
    void testCheckLimits_WeeklyLimitHit() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-1000.0, -7500.0, -8000.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertTrue((boolean) result.get("should_halt"));
        assertTrue((boolean) result.get("weekly_limit_hit"));
        assertEquals("Weekly loss limit", result.get("halt_reason"));
    }

    @Test
    void testCheckLimits_MonthlyLimitHit() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-1000.0, -3000.0, -7500.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertTrue((boolean) result.get("should_halt"));
        assertTrue((boolean) result.get("monthly_limit_hit"));
        assertEquals("Monthly loss limit", result.get("halt_reason"));
    }

    @Test
    void testCheckLimits_WeeklyProfitHit() {
        riskManager.setWeeklyProfitLimit(2500);
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(500.0, 2600.0, 3000.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertFalse((boolean) result.get("should_halt"));
        assertTrue((boolean) result.get("should_celebrate"));
        assertTrue((boolean) result.get("weekly_profit_hit"));
    }

    @Test
    void testCheckLimits_MonthlyProfitHit() {
        riskManager.setMonthlyProfitLimit(8000);
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(500.0, 5000.0, 8500.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertFalse((boolean) result.get("should_halt"));
        assertTrue((boolean) result.get("should_celebrate"));
        assertTrue((boolean) result.get("monthly_profit_hit"));
    }

    @Test
    void testCheckGoLiveEligibility_NullRepository() {
        RiskManagerAgent agent = new RiskManagerAgent(null, botSettingsRepository);
        
        Map<String, Object> result = agent.checkGoLiveEligibility();
        
        assertFalse((boolean) result.get("eligible"));
        assertEquals("Trade repository not available", result.get("reason"));
    }

    @Test
    void testCheckGoLiveEligibility_NullMaxDrawdown() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(25L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(20L);
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(null);
        
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        assertTrue((boolean) result.get("eligible"));
        assertTrue((boolean) result.get("drawdown_ok"));
    }

    @Test
    void testCheckGoLiveEligibility_HighDrawdown() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(25L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(20L);
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(-8000.0);
        
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        assertFalse((boolean) result.get("eligible"));
        assertFalse((boolean) result.get("drawdown_ok"));
    }

    @Test
    void testCheckGoLiveEligibility_ZeroTrades() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(0L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(0L);
        when(mockTradeRepo.maxDrawdownSince(any(), any())).thenReturn(0.0);
        
        Map<String, Object> result = riskManager.checkGoLiveEligibility();
        
        assertFalse((boolean) result.get("eligible"));
        assertEquals("0.0%", result.get("win_rate"));
    }

    @Test
    void testGetTradeStats_NullRepository() {
        RiskManagerAgent agent = new RiskManagerAgent(null, botSettingsRepository);
        
        Map<String, Object> result = agent.getTradeStats(TradingMode.SIMULATION);
        
        assertFalse((boolean) result.get("success"));
        assertEquals("Trade repository not available", result.get("error"));
    }

    @Test
    void testGetTradeStats_ZeroTrades() {
        when(mockTradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(0L);
        when(mockTradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED)).thenReturn(0L);
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(0.0);
        
        Map<String, Object> result = riskManager.getTradeStats(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        assertEquals("N/A", result.get("win_rate"));
    }

    @Test
    void testBuildStateSnapshot() {
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-500.0, -1500.0, -2000.0);
        when(mockTradeRepo.countTradesSince(any(), any())).thenReturn(5L);
        
        Map<String, Object> snapshot = riskManager.buildStateSnapshot(TradingMode.SIMULATION);
        
        assertEquals(-500.0, snapshot.get("daily_pnl"));
        assertEquals(-1500.0, snapshot.get("weekly_pnl"));
        assertEquals(-2000.0, snapshot.get("monthly_pnl"));
        assertEquals(5L, snapshot.get("trades_last_24h"));
        assertEquals("SIMULATION", snapshot.get("mode"));
        assertEquals("RUNNING", snapshot.get("state"));
        assertNotNull(snapshot.get("timestamp"));
    }

    @Test
    void testBuildStateSnapshot_NullRepository() {
        RiskManagerAgent agent = new RiskManagerAgent(null, botSettingsRepository);
        
        Map<String, Object> snapshot = agent.buildStateSnapshot(TradingMode.SIMULATION);
        
        assertEquals(0.0, snapshot.get("daily_pnl"));
        assertEquals(0.0, snapshot.get("weekly_pnl"));
        assertEquals(0.0, snapshot.get("monthly_pnl"));
        assertEquals(0L, snapshot.get("trades_last_24h"));
    }

    @Test
    void testBuildStateSnapshot_WithBotSettingsOverrides() {
        when(botSettingsRepository.findByKey(BotSettings.DAILY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.DAILY_LOSS_LIMIT).value("3000").build()));
        when(botSettingsRepository.findByKey(BotSettings.WEEKLY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.WEEKLY_LOSS_LIMIT).value("10000").build()));
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-1000.0, -3000.0, -4000.0);
        when(mockTradeRepo.countTradesSince(any(), any())).thenReturn(3L);
        
        Map<String, Object> snapshot = riskManager.buildStateSnapshot(TradingMode.SIMULATION);
        
        assertEquals(3000, snapshot.get("daily_limit"));
        assertEquals(10000, snapshot.get("weekly_limit"));
    }

    @Test
    void testParseIntSafe_InvalidValue() {
        when(botSettingsRepository.findByKey(BotSettings.DAILY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.DAILY_LOSS_LIMIT).value("invalid").build()));
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-500.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertTrue((boolean) result.get("success"));
        // When parsing fails, limit becomes 0, so even small losses would hit limit
        assertTrue((boolean) result.get("daily_limit_hit"));
    }

    @Test
    void testGetRiskManagerSystemPrompt() {
        String prompt = RiskManagerAgent.getRiskManagerSystemPrompt();
        
        assertNotNull(prompt);
        assertTrue(prompt.contains("APPROVE"));
        assertTrue(prompt.contains("VETO"));
        assertTrue(prompt.contains("capital preservation"));
    }

    @Test
    void testSetMonthlyLossLimit() {
        riskManager.setMonthlyLossLimit(15000);
        
        // At -14000, should not hit new 15000 limit
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-1000.0, -3000.0, -14000.0);
        
        Map<String, Object> result = riskManager.checkLimits(TradingMode.SIMULATION);
        
        assertFalse((boolean) result.get("monthly_limit_hit"));
    }

    @Test
    void testBuildStateSnapshot_ZeroLimits() {
        // When limits are 0, drawdown percentage calculation should handle divide by zero
        when(botSettingsRepository.findByKey(BotSettings.DAILY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.DAILY_LOSS_LIMIT).value("0").build()));
        when(botSettingsRepository.findByKey(BotSettings.WEEKLY_LOSS_LIMIT))
                .thenReturn(Optional.of(BotSettings.builder().key(BotSettings.WEEKLY_LOSS_LIMIT).value("0").build()));
        when(mockTradeRepo.sumPnLSince(any(), any())).thenReturn(-500.0, -1500.0, -2000.0);
        when(mockTradeRepo.countTradesSince(any(), any())).thenReturn(5L);
        
        Map<String, Object> snapshot = riskManager.buildStateSnapshot(TradingMode.SIMULATION);
        
        assertEquals(0.0, snapshot.get("daily_drawdown_pct"));
        assertEquals(0.0, snapshot.get("weekly_drawdown_pct"));
    }
}
