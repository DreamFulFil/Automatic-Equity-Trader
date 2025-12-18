package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

/**
 * SeasonalMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Heston & Sadka (2008) - 'Seasonality in the Cross-Section'
 * 
 * Logic:
 * Calendar-based momentum (January effect, summer doldrums)
 * 
 * Status: TEMPLATE - Requires full implementation with proper:
 * - State management (price history, indicators)
 * - Entry/exit logic
 * - Risk management
 * - Academic validation
 */
@Slf4j
public class SeasonalMomentumStrategy implements IStrategy {
    
    // Parameters from academic research
    private final String[] strongMonths;
    private final String[] weakMonths;
    
    public SeasonalMomentumStrategy(String[] strongMonths, String[] weakMonths) {
        this.strongMonths = strongMonths;
        this.weakMonths = weakMonths;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        // TODO: Implement SeasonalMomentumStrategy logic based on academic research
        // Reference: Heston & Sadka (2008) - 'Seasonality in the Cross-Section'
        log.warn("{} not yet implemented - returning neutral", getName());
        return TradeSignal.neutral("Strategy template - not implemented");
    }

    @Override
    public String getName() {
        return "Seasonal Momentum Strategy";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        // TODO: Clear any internal state
    }
}
