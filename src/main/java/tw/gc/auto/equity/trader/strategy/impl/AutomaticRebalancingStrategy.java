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
 * Automatic Portfolio Rebalancing Strategy
 * 
 * Long-term strategy that maintains target asset allocation by periodically
 * rebalancing portfolio to desired weights.
 * 
 * Strategy Logic:
 * 1. Track portfolio deviation from target allocation
 * 2. Generate rebalancing signals when deviation exceeds threshold
 * 3. Rebalance periodically (weekly/monthly) or when drift is significant
 * 
 * Configuration:
 * - targetAllocation: Desired position size
 * - rebalanceIntervalDays: Days between rebalance checks (default: 7)
 * - driftThreshold: Max acceptable deviation (default: 0.10 = 10%)
 * 
 * @since December 2025 - Strategy Pattern Refactoring
 */
@Slf4j
public class AutomaticRebalancingStrategy implements IStrategy {
    
    private final int targetAllocation;
    private final int rebalanceIntervalDays;
    private final double driftThreshold;
    private final Map<String, LocalDateTime> lastRebalance = new HashMap<>();
    
    public AutomaticRebalancingStrategy(int targetAllocation) {
        this(targetAllocation, 7, 0.10);
    }
    
    public AutomaticRebalancingStrategy(int targetAllocation, int rebalanceIntervalDays, double driftThreshold) {
        this.targetAllocation = targetAllocation;
        this.rebalanceIntervalDays = rebalanceIntervalDays;
        this.driftThreshold = driftThreshold;
        log.info("[Rebalancing] Strategy initialized: target={}, interval={}d, drift={}%",
                targetAllocation, rebalanceIntervalDays, driftThreshold * 100);
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            return TradeSignal.neutral("No market data");
        }
        
        String symbol = data.getSymbol();
        int currentPosition = portfolio.getPosition(symbol);
        LocalDateTime now = LocalDateTime.now();
        
        // Check if it's time to rebalance
        LocalDateTime lastRebal = lastRebalance.get(symbol);
        if (lastRebal != null) {
            long daysSince = Duration.between(lastRebal, now).toDays();
            if (daysSince < rebalanceIntervalDays) {
                return TradeSignal.neutral(String.format("Next rebalance in %d days", 
                        rebalanceIntervalDays - daysSince));
            }
        }
        
        // Calculate drift
        double drift = (double) Math.abs(currentPosition - targetAllocation) / targetAllocation;
        
        log.trace("[Rebalancing] Analysis: current={}, target={}, drift={}%",
                currentPosition, targetAllocation, drift * 100);
        
        if (drift >= driftThreshold) {
            lastRebalance.put(symbol, now);
            
            if (currentPosition < targetAllocation) {
                int buyAmount = targetAllocation - currentPosition;
                log.info("[Rebalancing] REBALANCE BUY: {} units (current={}, target={})",
                        buyAmount, currentPosition, targetAllocation);
                return TradeSignal.longSignal(0.80,
                        String.format("Rebalance: increase position by %d units", buyAmount));
            } else {
                int sellAmount = currentPosition - targetAllocation;
                log.info("[Rebalancing] REBALANCE SELL: {} units (current={}, target={})",
                        sellAmount, currentPosition, targetAllocation);
                return TradeSignal.shortSignal(0.80,
                        String.format("Rebalance: decrease position by %d units", sellAmount));
            }
        }
        
        return TradeSignal.neutral("Portfolio within drift threshold");
    }
    
    @Override
    public String getName() {
        return "Automatic Rebalancing";
    }
    
    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }
    
    @Override
    public void reset() {
        log.info("[Rebalancing] Resetting strategy state");
        lastRebalance.clear();
    }
}
