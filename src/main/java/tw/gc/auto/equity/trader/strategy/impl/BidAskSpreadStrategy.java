package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

/**
 * BidAskSpreadStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Roll (1984) - 'A Simple Implicit Measure of the Bid-Ask Spread'
 * 
 * Logic:
 * Exploit temporary liquidity imbalances
 * 
 * Status: TEMPLATE - Requires full implementation with proper:
 * - State management (price history, indicators)
 * - Entry/exit logic
 * - Risk management
 * - Academic validation
 */
@Slf4j
public class BidAskSpreadStrategy implements IStrategy {
    
    // Parameters from academic research
    private final double normalSpread;
    private final double wideSpreadThreshold;
    
    public BidAskSpreadStrategy(double normalSpread, double wideSpreadThreshold) {
        this.normalSpread = normalSpread;
        this.wideSpreadThreshold = wideSpreadThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        // TODO: Implement BidAskSpreadStrategy logic based on academic research
        // Reference: Roll (1984) - 'A Simple Implicit Measure of the Bid-Ask Spread'
        log.warn("{} not yet implemented - returning neutral", getName());
        return TradeSignal.neutral("Strategy template - not implemented");
    }

    @Override
    public String getName() {
        return "Bid Ask Spread Strategy";
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
