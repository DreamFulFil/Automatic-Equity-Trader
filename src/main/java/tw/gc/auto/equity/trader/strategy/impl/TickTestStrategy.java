package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

/**
 * TickTestStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Lee & Ready (1991) - 'Inferring Trade Direction from Intraday Data'
 * 
 * Logic:
 * Infer buy/sell pressure from price ticks
 * 
 * Status: TEMPLATE - Requires full implementation with proper:
 * - State management (price history, indicators)
 * - Entry/exit logic
 * - Risk management
 * - Academic validation
 */
@Slf4j
public class TickTestStrategy implements IStrategy {
    
    // Parameters from academic research
    private final int tickWindow;
    private final double directionThreshold;
    
    public TickTestStrategy(int tickWindow, double directionThreshold) {
        this.tickWindow = tickWindow;
        this.directionThreshold = directionThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        // TODO: Implement TickTestStrategy logic based on academic research
        // Reference: Lee & Ready (1991) - 'Inferring Trade Direction from Intraday Data'
        log.warn("{} not yet implemented - returning neutral", getName());
        return TradeSignal.neutral("Strategy template - not implemented");
    }

    @Override
    public String getName() {
        return "Tick Test Strategy";
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
