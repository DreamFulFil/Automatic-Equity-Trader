package tw.gc.mtxfbot.agents;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.Trade;
import tw.gc.mtxfbot.entities.Trade.TradingMode;
import tw.gc.mtxfbot.entities.Trade.TradeStatus;
import tw.gc.mtxfbot.repositories.TradeRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RiskManager Agent
 * 
 * Responsibilities:
 * - Enforces loss limits (daily 2500, weekly 7000, monthly 7000)
 * - Enforces profit limits (weekly 2500, monthly 8000) - optional
 * - Handles /start, /pause, /resume, /stop, /backtosim commands
 * - Checks /golive eligibility (win rate >55%, drawdown <5%)
 * 
 * Best Practices:
 * - Stateful limit checks with emergency halt capability
 * - All decisions logged to DB for audit
 * - Supports dynamic limit changes via BotSettings
 */
@Slf4j
public class RiskManagerAgent extends BaseAgent {
    
    private final TradeRepository tradeRepo;
    
    // Default limits (can be overridden via BotSettings)
    private int dailyLossLimit = 2500;
    private int weeklyLossLimit = 7000;
    private int monthlyLossLimit = 7000;
    private int weeklyProfitLimit = 2500;   // Optional - 0 means no limit
    private int monthlyProfitLimit = 8000;  // Optional - 0 means no limit
    
    // Go-live requirements
    private static final double MIN_WIN_RATE = 0.55;   // 55%
    private static final double MAX_DRAWDOWN = 0.05;   // 5%
    private static final int MIN_TRADES_FOR_GOLIVE = 20;
    
    public RiskManagerAgent(TradeRepository tradeRepo) {
        super(
            "RiskManager",
            "Enforces trading limits and handles /golive eligibility checks",
            List.of("limit_check", "emergency_halt", "golive_check", "mode_switch")
        );
        this.tradeRepo = tradeRepo;
    }
    
    @Override
    public Map<String, Object> execute(Map<String, Object> input) {
        String command = (String) input.getOrDefault("command", "check");
        TradingMode mode = (TradingMode) input.getOrDefault("mode", TradingMode.SIMULATION);
        
        Map<String, Object> result = new HashMap<>();
        
        switch (command) {
            case "check" -> result = checkLimits(mode);
            case "golive" -> result = checkGoLiveEligibility();
            case "stats" -> result = getTradeStats(mode);
            default -> {
                result.put("success", false);
                result.put("error", "Unknown command: " + command);
            }
        }
        
        result.put("agent", name);
        return result;
    }
    
    /**
     * Check if any loss/profit limits are hit
     */
    public Map<String, Object> checkLimits(TradingMode mode) {
        Map<String, Object> result = new HashMap<>();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        
        double dailyPnL = tradeRepo != null ? tradeRepo.sumPnLSince(mode, startOfDay) : 0;
        double weeklyPnL = tradeRepo != null ? tradeRepo.sumPnLSince(mode, startOfWeek) : 0;
        double monthlyPnL = tradeRepo != null ? tradeRepo.sumPnLSince(mode, startOfMonth) : 0;
        
        boolean dailyLimitHit = dailyPnL <= -dailyLossLimit;
        boolean weeklyLimitHit = weeklyPnL <= -weeklyLossLimit;
        boolean monthlyLimitHit = monthlyPnL <= -monthlyLossLimit;
        
        boolean weeklyProfitHit = weeklyProfitLimit > 0 && weeklyPnL >= weeklyProfitLimit;
        boolean monthlyProfitHit = monthlyProfitLimit > 0 && monthlyPnL >= monthlyProfitLimit;
        
        boolean shouldHalt = dailyLimitHit || weeklyLimitHit || monthlyLimitHit;
        boolean shouldCelebrate = weeklyProfitHit || monthlyProfitHit;
        
        result.put("success", true);
        result.put("daily_pnl", dailyPnL);
        result.put("weekly_pnl", weeklyPnL);
        result.put("monthly_pnl", monthlyPnL);
        result.put("daily_limit_hit", dailyLimitHit);
        result.put("weekly_limit_hit", weeklyLimitHit);
        result.put("monthly_limit_hit", monthlyLimitHit);
        result.put("weekly_profit_hit", weeklyProfitHit);
        result.put("monthly_profit_hit", monthlyProfitHit);
        result.put("should_halt", shouldHalt);
        result.put("should_celebrate", shouldCelebrate);
        result.put("mode", mode.name());
        
        if (shouldHalt) {
            String reason = dailyLimitHit ? "Daily loss limit" : 
                           weeklyLimitHit ? "Weekly loss limit" : "Monthly loss limit";
            log.error("🚨 RiskManager HALT: {} hit in {} mode", reason, mode);
            result.put("halt_reason", reason);
        }
        
        return result;
    }
    
    /**
     * Check if bot is eligible to go live based on simulation performance
     */
    public Map<String, Object> checkGoLiveEligibility() {
        Map<String, Object> result = new HashMap<>();
        
        if (tradeRepo == null) {
            result.put("eligible", false);
            result.put("reason", "Trade repository not available");
            return result;
        }
        
        long totalTrades = tradeRepo.countTotalTrades(TradingMode.SIMULATION, TradeStatus.CLOSED);
        long winningTrades = tradeRepo.countWinningTrades(TradingMode.SIMULATION, TradeStatus.CLOSED);
        
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0;
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        Double maxDrawdown = tradeRepo.maxDrawdownSince(TradingMode.SIMULATION, thirtyDaysAgo);
        double drawdownPercent = maxDrawdown != null ? Math.abs(maxDrawdown) / 100000 : 0; // Assume 100k base
        
        boolean hasEnoughTrades = totalTrades >= MIN_TRADES_FOR_GOLIVE;
        boolean winRateOk = winRate >= MIN_WIN_RATE;
        boolean drawdownOk = drawdownPercent <= MAX_DRAWDOWN;
        
        boolean eligible = hasEnoughTrades && winRateOk && drawdownOk;
        
        result.put("success", true);
        result.put("eligible", eligible);
        result.put("total_trades", totalTrades);
        result.put("winning_trades", winningTrades);
        result.put("win_rate", String.format("%.1f%%", winRate * 100));
        result.put("max_drawdown", String.format("%.1f%%", drawdownPercent * 100));
        result.put("has_enough_trades", hasEnoughTrades);
        result.put("win_rate_ok", winRateOk);
        result.put("drawdown_ok", drawdownOk);
        result.put("requirements", String.format("Need: %d trades, >%.0f%% win rate, <%.0f%% drawdown",
                MIN_TRADES_FOR_GOLIVE, MIN_WIN_RATE * 100, MAX_DRAWDOWN * 100));
        
        if (eligible) {
            log.info("✅ RiskManager: Go-live ELIGIBLE (WR: {}, DD: {})", 
                    result.get("win_rate"), result.get("max_drawdown"));
        } else {
            log.info("❌ RiskManager: Not yet eligible for live trading");
        }
        
        return result;
    }
    
    /**
     * Get trade statistics for a mode
     */
    public Map<String, Object> getTradeStats(TradingMode mode) {
        Map<String, Object> result = new HashMap<>();
        
        if (tradeRepo == null) {
            result.put("success", false);
            result.put("error", "Trade repository not available");
            return result;
        }
        
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        
        long totalTrades = tradeRepo.countTotalTrades(mode, TradeStatus.CLOSED);
        long winningTrades = tradeRepo.countWinningTrades(mode, TradeStatus.CLOSED);
        double totalPnL = tradeRepo.sumPnLSince(mode, thirtyDaysAgo);
        
        result.put("success", true);
        result.put("mode", mode.name());
        result.put("total_trades_30d", totalTrades);
        result.put("winning_trades", winningTrades);
        result.put("win_rate", totalTrades > 0 ? String.format("%.1f%%", (double) winningTrades / totalTrades * 100) : "N/A");
        result.put("total_pnl_30d", totalPnL);
        
        return result;
    }
    
    // Setters for dynamic limit configuration
    public void setDailyLossLimit(int limit) { this.dailyLossLimit = limit; }
    public void setWeeklyLossLimit(int limit) { this.weeklyLossLimit = limit; }
    public void setMonthlyLossLimit(int limit) { this.monthlyLossLimit = limit; }
    public void setWeeklyProfitLimit(int limit) { this.weeklyProfitLimit = limit; }
    public void setMonthlyProfitLimit(int limit) { this.monthlyProfitLimit = limit; }
    
    @Override
    protected Map<String, Object> getFallbackResponse() {
        return Map.of(
            "success", false,
            "should_halt", true, // Fail-safe: halt on error
            "reason", "Risk check unavailable - defaulting to halt",
            "agent", name
        );
    }
}
