package tw.gc.auto.equity.trader.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tw.gc.auto.equity.trader.config.TradingProperties;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService;
import tw.gc.auto.equity.trader.compliance.TaiwanStockComplianceService.ComplianceResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * OrderExecutionService - Handles order execution with retry logic, balance checks, and blackout enforcement.
 * 
 * Integrates with the EarningsBlackoutService to enforce trading restrictions around earnings dates.
 * Before placing any new orders (not exits), verifies the symbol is not in an earnings blackout window.
 * 
 * Phase 5 Enhancement: Supports signal confidence for calibrated risk scoring.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderExecutionService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    private static final double DEFAULT_BASE_EQUITY = 1_000_000.0;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TradingProperties tradingProperties;
    private final TelegramService telegramService;
    private final DataLoggingService dataLoggingService;
    private final PositionManager positionManager;
    private final RiskManagementService riskManagementService;
    private final StockRiskSettingsService stockRiskSettingsService;
    private final EarningsBlackoutService earningsBlackoutService;
    private final TaiwanStockComplianceService taiwanComplianceService;
    @SuppressWarnings("unused")
    private final LlmService llmService;
    private final TradeRiskScorer tradeRiskScorer;
    
    // Phase 7: Fundamental analysis integration
    private final FundamentalFilter fundamentalFilter;

    private String getBridgeUrl() {
        return tradingProperties.getBridge().getUrl();
    }

    /**
     * Check account balance/position and adjust quantity to prevent insufficient funds/shares
     */
    public int checkBalanceAndAdjustQuantity(String action, int requestedQuantity, double price, String instrument, String tradingMode) {
        try {
            if ("BUY".equals(action)) {
                // Check available balance for BUY orders
                String accountJson = restTemplate.getForObject(getBridgeUrl() + "/account", String.class);
                JsonNode accountData = objectMapper.readTree(accountJson);
                
                if (!"ok".equals(accountData.path("status").asText())) {
                    log.warn("⚠️ Could not get account balance - proceeding with requested quantity");
                    return requestedQuantity;
                }
                
                double availableBalance = accountData.path("available_margin").asDouble(0.0);
                if (availableBalance <= 0) {
                    log.warn("⚠️ No available balance in account");
                    return 0;
                }
                
                // Calculate maximum affordable quantity
                double maxAffordable = availableBalance / price;
                int maxQuantity = (int) Math.floor(maxAffordable);
                
                // For stocks, ensure we don't exceed reasonable limits
                if ("stock".equals(tradingMode)) {
                    maxQuantity = Math.min(maxQuantity, 1000); // Safety limit
                }
                
                if (maxQuantity < requestedQuantity) {
                    log.warn("⚠️ Balance insufficient - reducing quantity from {} to {} (balance: {:.0f} TWD, price: {:.0f} TWD)",
                        requestedQuantity, maxQuantity, availableBalance, price);
                    return Math.max(0, maxQuantity);
                }
                
            } else if ("SELL".equals(action)) {
                // Check current position for SELL orders
                int availableShares = Math.abs(positionManager.getPosition(instrument));
                
                if (availableShares <= 0) {
                    log.warn("⚠️ No position to sell for {}", instrument);
                    return 0;
                }
                
                if (requestedQuantity > availableShares) {
                    log.warn("⚠️ Insufficient shares - reducing quantity from {} to {} (position: {} shares)",
                        requestedQuantity, availableShares, availableShares);
                    return availableShares;
                }
            }
            
            return requestedQuantity;
            
        } catch (Exception e) {
            log.error("❌ Failed to check account balance/position: {}", e.getMessage());
            // On error, proceed with requested quantity to avoid blocking trades
            return requestedQuantity;
        }
    }

    /**
     * Execute order with retry wrapper and quantity bug fix.
     * Enforces earnings blackout check and Taiwan compliance before placing new (non-exit) orders.
     * 
     * This overload is for backward compatibility - uses default signal confidence (null).
     */
    public void executeOrderWithRetry(String action, int quantity, double price, String instrument, boolean isExit, boolean emergencyShutdown, String strategyName) {
        executeOrderWithRetry(action, quantity, price, instrument, isExit, emergencyShutdown, strategyName, null);
    }

    /**
     * Execute order with retry wrapper, quantity bug fix, and signal confidence support.
     * Enforces earnings blackout check and Taiwan compliance before placing new (non-exit) orders.
     * 
     * Phase 5 Enhancement: Uses signal confidence for calibrated risk scoring.
     * High-confidence signals (>0.9) get relaxed veto criteria.
     * Low-confidence signals (<0.5) get stricter veto criteria.
     * 
     * @param action BUY or SELL
     * @param quantity Number of shares
     * @param price Target price
     * @param instrument Stock symbol
     * @param isExit Whether this is an exit order
     * @param emergencyShutdown Whether emergency shutdown is active
     * @param strategyName Name of the strategy generating this order
     * @param signalConfidence Signal confidence (0.0-1.0), null for unknown
     */
    public void executeOrderWithRetry(String action, int quantity, double price, String instrument, boolean isExit, boolean emergencyShutdown, String strategyName, Double signalConfidence) {
        // Earnings blackout check - block new entries but allow exits
        if (!isExit && isInEarningsBlackout()) {
            log.warn("📅 EARNINGS BLACKOUT: Blocking {} order for {} - blackout window active", action, instrument);
            telegramService.sendMessage(String.format(
                "📅 ORDER BLOCKED - EARNINGS BLACKOUT\nAction: %s %d %s @ %.0f\nReason: Earnings blackout window active\nExisting positions can still be closed.",
                action, quantity, instrument, price));
            return;
        }

        if (!isExit && "BUY".equalsIgnoreCase(action)) {
            var settings = stockRiskSettingsService.getSettings();
            RiskManagementService.PreTradeRiskResult riskResult = riskManagementService.evaluatePreTradeRisk(
                    instrument,
                    quantity,
                    price,
                    settings.getMaxSectorExposurePct(),
                    settings.getMaxAdvParticipationPct(),
                    settings.getMinAverageDailyVolume(),
                    DEFAULT_BASE_EQUITY
            );

            if (!riskResult.allowed()) {
                log.warn("📉 PRE-TRADE RISK BLOCK: {}", riskResult.reason());
                telegramService.sendMessage(String.format(
                        "📉 ORDER BLOCKED - RISK LIMIT\nAction: %s %d %s @ %.0f\nReason: %s",
                        action, quantity, instrument, price, riskResult.reason()
                ));
                return;
            }

            if (riskResult.adjustedQuantity() != quantity) {
                log.warn("⚠️ Liquidity cap adjusted quantity: {} → {}", quantity, riskResult.adjustedQuantity());
                quantity = riskResult.adjustedQuantity();
            }
        }

        // Taiwan compliance check - block if odd-lot day trading without sufficient capital
        if (!isExit && strategyName != null) {
            boolean isDayTrade = taiwanComplianceService.isIntradayStrategy(strategyName);
            double currentCapital = taiwanComplianceService.fetchCurrentCapital();
            ComplianceResult complianceResult = taiwanComplianceService.checkTradeCompliance(quantity, isDayTrade, currentCapital);
            
            if (!complianceResult.isApproved()) {
                log.warn("🇹🇼 TAIWAN COMPLIANCE VETO: {}", complianceResult.getVetoReason());
                telegramService.sendMessage(String.format(
                    "🇹🇼 ORDER BLOCKED - TAIWAN COMPLIANCE\nAction: %s %d %s @ %.0f\nReason: %s",
                    action, quantity, instrument, price, complianceResult.getVetoReason()));
                return;
            }
        }
        
        // Phase 7: Fundamental filter check - block if poor fundamentals (P/E, debt, revenue)
        if (!isExit) {
            try {
                FundamentalFilter.FilterResult fundamentalResult = fundamentalFilter.evaluateStock(instrument);
                if (!fundamentalResult.isPassed()) {
                    log.warn("📊 FUNDAMENTAL FILTER VETO: {}", fundamentalResult.getReason());
                    telegramService.sendMessage(String.format(
                        "📊 ORDER BLOCKED - POOR FUNDAMENTALS\nAction: %s %d %s @ %.0f\nReason: %s%s",
                        action, quantity, instrument, price, fundamentalResult.getReason(),
                        fundamentalResult.isCaution() ? "\n⚠️ Caution flag set" : ""));
                    return;
                }
                // Log warnings if there are caution flags (passed but marginal)
                if (fundamentalResult.isCaution()) {
                    log.info("⚠️ Fundamental caution for {}", instrument);
                }
            } catch (Exception e) {
                log.warn("⚠️ Fundamental filter check failed for {}: {} - Allowing trade to proceed", instrument, e.getMessage());
                // On failure, log but proceed with trade to avoid blocking all trades due to data issues
            }
        }
        
        // Phase 5: Calibrated risk scoring with signal confidence support
        // Uses TradeRiskScorer for weighted risk assessment instead of binary LLM veto
        if (!isExit && stockRiskSettingsService.isAiVetoEnabled()) {
            try {
                Map<String, Object> tradeProposal = new HashMap<>();
                tradeProposal.put("symbol", instrument);
                tradeProposal.put("direction", action);
                tradeProposal.put("shares", quantity);
                tradeProposal.put("entry_logic", "Price-based signal");
                tradeProposal.put("strategy_name", strategyName != null ? strategyName : "Unknown");
                tradeProposal.put("daily_pnl", String.format("%.0f", riskManagementService.getDailyPnL()));
                tradeProposal.put("weekly_pnl", String.format("%.0f", riskManagementService.getWeeklyPnL()));
                tradeProposal.put("drawdown_percent", riskManagementService.getCurrentDrawdownPercent());
                tradeProposal.put("trades_today", riskManagementService.getTradesToday());
                tradeProposal.put("win_streak", riskManagementService.getWinStreak());
                tradeProposal.put("loss_streak", riskManagementService.getLossStreak());
                tradeProposal.put("volatility_level", "Normal"); // TODO: Get from market regime service
                tradeProposal.put("time_of_day", LocalDateTime.now(TAIPEI_ZONE).toString());
                tradeProposal.put("session_phase", "Intraday");
                tradeProposal.put("news_headlines", "");
                tradeProposal.put("strategy_days_active", 30); // Default to mature strategy
                tradeProposal.put("recent_backtest_stats", "N/A");
                
                // Phase 5.4: Pass signal confidence for calibrated risk scoring
                // High-confidence signals (>0.9) get relaxed veto criteria (threshold 80)
                // Low-confidence signals (<0.5) get stricter veto criteria (threshold 60)
                if (signalConfidence != null) {
                    tradeProposal.put("signal_confidence", signalConfidence);
                    log.debug("📊 Signal confidence: {}", signalConfidence);
                }
                
                // Use TradeRiskScorer for weighted risk assessment
                Map<String, Object> riskResult = tradeRiskScorer.quickRiskCheck(tradeProposal);
                boolean vetoed = (Boolean) riskResult.getOrDefault("veto", true);
                String vetoReason = (String) riskResult.getOrDefault("reason", "Unknown");
                Double riskScore = (Double) riskResult.getOrDefault("risk_score", 100.0);
                
                if (vetoed) {
                    log.warn("📊 RISK SCORE VETO: {} (score: {:.1f})", vetoReason, riskScore);
                    telegramService.sendMessage(String.format(
                        "📊 ORDER BLOCKED - RISK SCORE VETO\nAction: %s %d %s @ %.0f\nRisk Score: %.1f\nReason: %s%s\n\nUse /risk enable_ai_veto false to disable risk scoring",
                        action, quantity, instrument, price, riskScore, vetoReason,
                        signalConfidence != null ? String.format("\nSignal Confidence: %.2f", signalConfidence) : ""));
                    return;
                } else {
                    log.info("✅ Risk check passed (score: {:.1f}): {}", riskScore, vetoReason);
                }
            } catch (Exception e) {
                log.error("❌ Risk scoring check failed: {} - Defaulting to VETO (fail-safe)", e.getMessage());
                telegramService.sendMessage(String.format(
                    "⚠️ RISK SCORING FAILED - Trade blocked as fail-safe\nError: %s", e.getMessage()));
                return;
            }
        }

        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                
                String effectiveStrategyName = (strategyName != null && !strategyName.trim().isEmpty()) 
                    ? strategyName 
                    : "Strategy_STOCK_" + System.currentTimeMillis();
                
                Map<String, Object> orderMap = new HashMap<>();
                orderMap.put("action", action);
                orderMap.put("quantity", String.valueOf(quantity));
                orderMap.put("price", price);
                orderMap.put("is_exit", isExit);
                orderMap.put("strategy", effectiveStrategyName);
                
                log.info("📤 [Strategy: {}] Sending {} order (attempt {}, exit={}): {}", 
                    effectiveStrategyName, instrument, attempt, isExit, orderMap);
                String result = restTemplate.postForObject(
                        getBridgeUrl() + "/order", orderMap, String.class);
                log.debug("📥 Order response: {}", result);
                
                // Success - update position
                if ("BUY".equals(action)) {
                    positionManager.updatePosition(instrument, quantity);
                } else {
                    positionManager.updatePosition(instrument, -quantity);
                }

                if (!isExit) {
                    positionManager.updateEntry(instrument, price, LocalDateTime.now(ZoneId.of("Asia/Taipei")));
                }
                    
                telegramService.sendMessage(String.format(
                        "✅ ORDER FILLED [%s]\n%s %d %s @ %.0f\nPosition: %d", 
                        effectiveStrategyName, action, quantity, instrument, price, positionManager.getPosition(instrument)));
                
                if (!isExit) {
                    Trade trade = Trade.builder()
                            .timestamp(LocalDateTime.now(ZoneId.of("Asia/Taipei")))
                            .action("BUY".equals(action) ? Trade.TradeAction.BUY : Trade.TradeAction.SELL)
                            .quantity(quantity)
                            .entryPrice(price)
                            .symbol(instrument)
                            .strategyName(effectiveStrategyName)
                            .reason("Signal execution")
                            .mode(emergencyShutdown ? Trade.TradingMode.SIMULATION : Trade.TradingMode.LIVE)
                            .status(Trade.TradeStatus.OPEN)
                            .assetType(Trade.AssetType.STOCK)
                            .build();
                    dataLoggingService.logTrade(trade);
                }
                
                return; // Success - exit retry loop
                
            } catch (Exception e) {
                log.error("❌ Order execution failed (attempt {}): {}", attempt, e.getMessage());
                
                if (attempt >= maxRetries) {
                    telegramService.sendMessage("🚨 Order failed after " + maxRetries + " attempts: " + e.getMessage());
                    return;
                }
                
                // Exponential backoff: 1s, 2s, 4s
                try {
                    Thread.sleep(1000 * (1L << (attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
    
    /**
     * Legacy method without strategyName parameter for backward compatibility
     */
    public void executeOrderWithRetry(String action, int quantity, double price, String instrument, boolean isExit, boolean emergencyShutdown) {
        executeOrderWithRetry(action, quantity, price, instrument, isExit, emergencyShutdown, null);
    }

    public void flattenPosition(String reason, String instrument, String tradingMode, boolean emergencyShutdown) {
        int pos = positionManager.getPosition(instrument);
        if (pos == 0) return;
        
        String action = pos > 0 ? "SELL" : "BUY";
        try {
            String signalJson = restTemplate.getForObject(getBridgeUrl() + "/signal", String.class);
            JsonNode signal = objectMapper.readTree(signalJson);
            double currentPrice = signal.path("current_price").asDouble(0.0);
            
            // Store entry metadata before executeOrderWithRetry overwrites it (though isExit=true prevents overwrite)
            double originalEntryPrice = positionManager.getEntryPrice(instrument);
            LocalDateTime entryTime = positionManager.getEntryTime(instrument);
            int holdDurationMinutes = entryTime == null ? 0 :
                    (int) java.time.Duration.between(entryTime, LocalDateTime.now(ZoneId.of("Asia/Taipei"))).toMinutes();
            
            executeOrderWithRetry(action, Math.abs(pos), currentPrice, instrument, true, emergencyShutdown); // isExit=true triggers cooldown
            
            double multiplier = "stock".equals(tradingMode) ? 1.0 : 50.0;
            double pnl = (currentPrice - originalEntryPrice) * pos * multiplier;
            riskManagementService.recordPnL(instrument, pnl, stockRiskSettingsService.getWeeklyLossLimit());

            Trade.TradingMode mode = emergencyShutdown ? Trade.TradingMode.SIMULATION : Trade.TradingMode.LIVE;
            dataLoggingService.closeLatestTrade(instrument, mode, currentPrice, pnl, holdDurationMinutes);
            
            positionManager.setPosition(instrument, 0);
            positionManager.clearEntry(instrument);
            
            log.info("🔒 Position flattened - P&L: {} TWD (Reason: {})", pnl, reason);
            telegramService.sendMessage(String.format(
                    "🔒 POSITION CLOSED\nReason: %s\nP&L: %.0f TWD\nDaily P&L: %.0f TWD\nWeekly P&L: %.0f TWD", 
                    reason, pnl, riskManagementService.getDailyPnL(), riskManagementService.getWeeklyPnL()));
            
            if (riskManagementService.isWeeklyLimitHit()) {
                telegramService.sendMessage(String.format(
                    "🚨 WEEKLY LOSS LIMIT HIT\nWeekly P&L: %.0f TWD\nTrading paused until next Monday!",
                    riskManagementService.getWeeklyPnL()
                ));
            }
            
        } catch (Exception e) {
            log.error("❌ Flatten failed", e);
        }
    }

    /**
     * Check if today is an earnings blackout day.
     * Returns true if trading should be blocked for new entries.
     */
    public boolean isInEarningsBlackout() {
        try {
            LocalDate today = LocalDate.now(TAIPEI_ZONE);
            return earningsBlackoutService.isDateBlackout(today);
        } catch (Exception e) {
            log.warn("⚠️ Could not check earnings blackout status: {}", e.getMessage());
            return false; // Fail-open: allow trading if blackout check fails
        }
    }

    /**
     * Get earnings blackout status for monitoring/reporting
     */
    public boolean getEarningsBlackoutStatus() {
        return isInEarningsBlackout();
    }
}
