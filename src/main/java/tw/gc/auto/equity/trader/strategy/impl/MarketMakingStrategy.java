package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

/**
 * MarketMakingStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Ho & Stoll (1981) - 'Optimal Dealer Pricing Under Transactions'
 * 
 * Logic:
 * Provide liquidity, capture spread
 * 
 * Status: TEMPLATE - Requires full implementation with proper:
 * - State management (price history, indicators)
 * - Entry/exit logic
 * - Risk management
 * - Academic validation
 */
@Slf4j
public class MarketMakingStrategy implements IStrategy {
    
    // Parameters from academic research
    private final double spreadCapture;
    private final int maxInventory;
    
    public MarketMakingStrategy(double spreadCapture, int maxInventory) {
        this.spreadCapture = spreadCapture;
        this.maxInventory = maxInventory;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        // TODO: Implement MarketMakingStrategy logic based on academic research
        // Reference: Ho & Stoll (1981) - 'Optimal Dealer Pricing Under Transactions'
        log.warn("{} not yet implemented - returning neutral", getName());
        return TradeSignal.neutral("Strategy template - not implemented");
    }

    @Override
    public String getName() {
        return "Market Making Strategy";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.INTRADAY;
    }

    @Override
    public void reset() {
        // TODO: Clear any internal state
    }
}
