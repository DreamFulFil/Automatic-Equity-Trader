package tw.gc.auto.equity.trader.agents;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.Trade.TradingMode;
import tw.gc.auto.equity.trader.entities.Trade.TradeStatus;
import tw.gc.auto.equity.trader.entities.BotSettings;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.BotSettingsRepository;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.DayOfWeek;
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
    private final BotSettingsRepository botSettingsRepository;
    private BotState state = BotState.RUNNING;
    
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
    
    public RiskManagerAgent(TradeRepository tradeRepo, BotSettingsRepository botSettingsRepository) {
        super(
            "RiskManager",
            "Enforces trading limits and handles /golive eligibility checks",
            List.of("limit_check", "emergency_halt", "golive_check", "mode_switch")
        );
        this.tradeRepo = tradeRepo;
        this.botSettingsRepository = botSettingsRepository;
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
            case "start" -> result = handleStart(mode.name());
            case "pause" -> result = handlePause();
            case "resume" -> result = handleResume();
            case "stop" -> result = handleStop();
            default -> {
                result.put("success", false);
                result.put("error", "Unknown command: " + command);
            }
        }
        
        result.put("agent", name);
        return result;
    }

    /**
     * Check a new trade against configured risk limits using latest P&L.
     */
    public Map<String, Object> checkTradeRisk(Trade newTrade) {
        Map<String, Object> result = new HashMap<>();
        if (tradeRepo == null) {
            result.put("allowed", false);
            result.put("reason", "Trade repository not available");
            return result;
        }

        TradingMode mode = newTrade.getMode() != null ? newTrade.getMode() : TradingMode.SIMULATION;
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime startOfWeek = LocalDateTime.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).withHour(0).withMinute(0).withSecond(0).withNano(0);

        double dailyPnL = tradeRepo.sumPnLSince(mode, startOfDay);
        double weeklyPnL = tradeRepo.sumPnLSince(mode, startOfWeek);
        double newTradePnL = newTrade.getRealizedPnL() != null ? newTrade.getRealizedPnL() : 0.0;

        double projectedDaily = dailyPnL + newTradePnL;
        double projectedWeekly = weeklyPnL + newTradePnL;

        int dailyLimitCfg = resolveLimit(BotSettings.DAILY_LOSS_LIMIT, dailyLossLimit);
        int weeklyLimitCfg = resolveLimit(BotSettings.WEEKLY_LOSS_LIMIT, weeklyLossLimit);

        boolean dailyHit = projectedDaily <= -dailyLimitCfg;
        boolean weeklyHit = projectedWeekly <= -weeklyLimitCfg;

        result.put("allowed", !(dailyHit || weeklyHit));
        result.put("daily_pnl", projectedDaily);
        result.put("weekly_pnl", projectedWeekly);
        result.put("daily_limit", dailyLimitCfg);
        result.put("weekly_limit", weeklyLimitCfg);
        result.put("state", state.name());

        if (dailyHit || weeklyHit) {
            result.put("reason", dailyHit ? "Daily loss limit" : "Weekly loss limit");
        } else {
            result.put("reason", "Within limits");
        }
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
        
        int dailyLimitCfg = resolveLimit(BotSettings.DAILY_LOSS_LIMIT, dailyLossLimit);
        int weeklyLimitCfg = resolveLimit(BotSettings.WEEKLY_LOSS_LIMIT, weeklyLossLimit);
        int monthlyLimitCfg = resolveLimit(BotSettings.MONTHLY_LOSS_LIMIT, monthlyLossLimit);
        int weeklyProfitCfg = resolveLimit(BotSettings.WEEKLY_PROFIT_LIMIT, weeklyProfitLimit);
        int monthlyProfitCfg = resolveLimit(BotSettings.MONTHLY_PROFIT_LIMIT, monthlyProfitLimit);
        
        boolean dailyLimitHit = dailyPnL <= -dailyLimitCfg;
        boolean weeklyLimitHit = weeklyPnL <= -weeklyLimitCfg;
        boolean monthlyLimitHit = monthlyPnL <= -monthlyLimitCfg;
        
        boolean weeklyProfitHit = weeklyProfitCfg > 0 && weeklyPnL >= weeklyProfitCfg;
        boolean monthlyProfitHit = monthlyProfitCfg > 0 && monthlyPnL >= monthlyProfitCfg;
        
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
        result.put("state", state.name());
        
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

    public BotState getState() {
        return state;
    }

    public Map<String, Object> handleStart(String mode) {
        state = BotState.RUNNING;
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("state", state.name());
        result.put("mode", mode);
        return result;
    }

    public Map<String, Object> handlePause() {
        state = BotState.PAUSED;
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("state", state.name());
        return result;
    }

    public Map<String, Object> handleResume() {
        state = BotState.RUNNING;
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("state", state.name());
        return result;
    }

    public Map<String, Object> handleStop() {
        state = BotState.STOPPED;
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("state", state.name());
        return result;
    }
    
    @Override
    protected Map<String, Object> getFallbackResponse() {
        return Map.of(
            "success", false,
            "should_halt", true, // Fail-safe: halt on error
            "reason", "Risk check unavailable - defaulting to halt",
            "agent", name
        );
    }

    /**
     * Returns the system prompt for LLM-based risk assessment.
     * This prompt defines the Paranoid Risk Manager role for Taiwan Stock Trading.
     * MUST be kept in sync with python/app/services/ollama_service.py TRADE_VETO_SYSTEM_PROMPT.
     */
    public static String getRiskManagerSystemPrompt() {
        return """
You are an extremely paranoid, professional risk manager for a fully automated Taiwan stock trading system. Your ONLY goal is capital preservation. Profitability is irrelevant — avoiding losses and drawdowns is everything.

You will receive a full trade proposal containing:
- Proposed trade details (symbol, direction long/short, shares, entry logic, strategy name)
- Current system state (daily P&L, weekly P&L, current drawdown %, trades today, recent win streak/loss streak)
- Market context (volatility level, time of day, session phase)
- Recent news headlines/summaries (in Chinese or English)
- Strategy performance context (recent backtest stats, how long this strategy has been active)

Your job: Decide APPROVE or VETO.

Default to VETO. Only APPROVE if ALL of the following are unambiguously true:

1. News sentiment is clearly neutral or mildly positive. Any negative keyword (e.g., 下跌, 利空, 衰退, 地緣, 貿易戰, 地震, 颱風, 調查, 違規, 警告, 降評, US-China tension, Fed hawkish, earnings miss, etc.) → VETO.
2. No conflicting or high-volume news.
3. System is not in drawdown (>3% daily or >8% weekly) → VETO.
4. Fewer than 3 trades executed today → if more, VETO.
5. No recent losing streak (2+ consecutive losses) → VETO.
6. Trade size is 100 shares or fewer → if more, VETO.
7. Market volatility is normal (not labeled high/choppy) → if high, VETO.
8. Not within first 30 minutes or last 30 minutes of the trading session (09:00–13:30).
9. Strategy has been active for at least 3 days (no brand-new switches) → if new, VETO.
10. If any doubt, uncertainty, missing info, or edge case → VETO.

Respond ONLY with one of these exact formats. No explanation, no extra text:

APPROVE
VETO: <one very short reason phrase>

Examples:
APPROVE
VETO: daily drawdown exceeded
VETO: too many trades today
VETO: negative news keyword
VETO: strategy too new
VETO: high volatility
VETO: size >100 shares
VETO: recent losses
VETO: near session open/close""";
    }

    /**
     * Build a comprehensive state snapshot for LLM context injection.
     * Includes P&L, drawdown, trade count, strategy age, and volatility flags.
     */
    public Map<String, Object> buildStateSnapshot(TradingMode mode) {
        Map<String, Object> snapshot = new HashMap<>();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime last24Hours = now.minusHours(24);
        
        double dailyPnL = tradeRepo != null ? tradeRepo.sumPnLSince(mode, startOfDay) : 0;
        double weeklyPnL = tradeRepo != null ? tradeRepo.sumPnLSince(mode, startOfWeek) : 0;
        double monthlyPnL = tradeRepo != null ? tradeRepo.sumPnLSince(mode, startOfMonth) : 0;
        
        long tradesLast24h = tradeRepo != null ? tradeRepo.countTradesSince(mode, last24Hours) : 0;
        
        int dailyLimitCfg = resolveLimit(BotSettings.DAILY_LOSS_LIMIT, dailyLossLimit);
        int weeklyLimitCfg = resolveLimit(BotSettings.WEEKLY_LOSS_LIMIT, weeklyLossLimit);
        
        double dailyDrawdownPct = dailyLimitCfg > 0 ? (dailyPnL / dailyLimitCfg) * 100 : 0;
        double weeklyDrawdownPct = weeklyLimitCfg > 0 ? (weeklyPnL / weeklyLimitCfg) * 100 : 0;
        
        snapshot.put("daily_pnl", dailyPnL);
        snapshot.put("weekly_pnl", weeklyPnL);
        snapshot.put("monthly_pnl", monthlyPnL);
        snapshot.put("daily_drawdown_pct", dailyDrawdownPct);
        snapshot.put("weekly_drawdown_pct", weeklyDrawdownPct);
        snapshot.put("trades_last_24h", tradesLast24h);
        snapshot.put("daily_limit", dailyLimitCfg);
        snapshot.put("weekly_limit", weeklyLimitCfg);
        snapshot.put("mode", mode.name());
        snapshot.put("state", state.name());
        snapshot.put("timestamp", now.toString());
        
        return snapshot;
    }

    private int resolveLimit(String key, int defaultValue) {
        return botSettingsRepository.findByKey(key)
                .map(BotSettings::getValue)
                .map(this::parseIntSafe)
                .orElse(defaultValue);
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    public enum BotState {
        RUNNING, PAUSED, STOPPED
    }
}
