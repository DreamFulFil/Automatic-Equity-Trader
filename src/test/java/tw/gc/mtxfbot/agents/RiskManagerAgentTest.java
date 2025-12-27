package tw.gc.mtxfbot.agents;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.mtxfbot.entities.BotSettings;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.entities.Trade.TradingMode;
import tw.gc.mtxfbot.entities.Trade.TradeStatus;
import tw.gc.mtxfbot.repositories.BotSettingsRepository;
import tw.gc.mtxfbot.repositories.TradeRepository;

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
}
