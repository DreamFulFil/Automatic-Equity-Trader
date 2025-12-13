package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Dollar-Cost Averaging (DCA) Strategy
 * 
 * Long-term investment strategy that buys fixed amounts at regular intervals
 * regardless of price, reducing the impact of volatility.
 * 
 * Strategy Logic:
 * 1. Check if enough time has passed since last purchase (configurable interval)
 * 2. If flat or below target position, generate BUY signal
 * 3. Never sells (hold long-term unless position exceeds target)
 * 4. Ignores short-term price movements
 * 
 * Configuration:
 * - purchaseIntervalMinutes: Time between purchases (default: 30 minutes)
 * - targetPosition: Maximum position size before stopping purchases
 * 
 * Limitations:
 * - Does NOT support odd-lot intraday trading (per global rules)
 * - Suitable for long-term accumulation only
 * - Not designed for intraday lunch-window trading
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
@Slf4j
public class DCAStrategy implements IStrategy {
    
    private final int purchaseIntervalMinutes;
    private final int targetPosition;
    private final Map<String, LocalDateTime> lastPurchaseTime = new HashMap<>();
    
    /**
     * Create DCA strategy with default settings
     * Interval: 30 minutes, Target: Max trading quantity
     */
    public DCAStrategy() {
        this(30, Integer.MAX_VALUE);
    }
    
    /**
     * Create DCA strategy with custom settings
     * 
     * @param purchaseIntervalMinutes Time between purchases (minutes)
     * @param targetPosition Maximum position size to accumulate
     */
    public DCAStrategy(int purchaseIntervalMinutes, int targetPosition) {
        this.purchaseIntervalMinutes = purchaseIntervalMinutes;
        this.targetPosition = targetPosition;
        log.info("[DCA] Strategy initialized: interval={}min, target={}", 
                purchaseIntervalMinutes, targetPosition);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            log.trace("[DCA] No market data available");
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        int currentPosition = portfolio.getPosition(symbol);
        LocalDateTime now = LocalDateTime.now();
        
        log.trace("[DCA] Analysis: symbol={}, position={}, target={}, price={}", 
                symbol, currentPosition, targetPosition, data.getClose());
        
        // Check if already at or above target position
        if (currentPosition >= targetPosition) {
            log.debug("[DCA] Target position reached: current={}, target={}", currentPosition, targetPosition);
            return TradeSignal.neutral("Target position reached");
        }
        
        // Check if enough time has passed since last purchase
        LocalDateTime lastPurchase = lastPurchaseTime.get(symbol);
        if (lastPurchase != null) {
            long minutesSinceLastPurchase = Duration.between(lastPurchase, now).toMinutes();
            if (minutesSinceLastPurchase < purchaseIntervalMinutes) {
                long remainingMinutes = purchaseIntervalMinutes - minutesSinceLastPurchase;
                log.trace("[DCA] Purchase interval not reached: {}min remaining", remainingMinutes);
                return TradeSignal.neutral(
                    String.format("Next purchase in %d minutes", remainingMinutes));
            }
        }
        
        // Generate buy signal - time to accumulate
        lastPurchaseTime.put(symbol, now);
        double confidence = 0.85; // High confidence for systematic DCA
        String reason = String.format("DCA: Time to buy (interval: %dmin, position: %d/%d)", 
                purchaseIntervalMinutes, currentPosition, targetPosition);
        
        log.info("[DCA] BUY SIGNAL: {} @ {} TWD (position: {}/{})", 
                symbol, data.getClose(), currentPosition, targetPosition);
        
        return TradeSignal.longSignal(confidence, reason);
    }
    
    @Override
    public String getName() {
        return "Dollar-Cost Averaging (DCA)";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }
    
    @Override
    public void reset() {
        log.info("[DCA] Resetting strategy state");
        lastPurchaseTime.clear();
    }
    
    // Package-private for testing
    LocalDateTime getLastPurchaseTime(String symbol) {
        return lastPurchaseTime.get(symbol);
    }
}
