package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

/**
 * BookToMarketStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Fama & French (1992) - 'The Cross-Section of Expected Stock Returns'
 * 
 * Logic:
 * Buy high book-to-market stocks (value)
 * 
 * Status: TEMPLATE - Requires full implementation with proper:
 * - State management (price history, indicators)
 * - Entry/exit logic
 * - Risk management
 * - Academic validation
 */
@Slf4j
public class BookToMarketStrategy implements IStrategy {
    
    // Parameters from academic research
    private final double minBookToMarket;
    private final int rebalanceDays;
    
    public BookToMarketStrategy(double minBookToMarket, int rebalanceDays) {
        this.minBookToMarket = minBookToMarket;
        this.rebalanceDays = rebalanceDays;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        // TODO: Implement BookToMarketStrategy logic based on academic research
        // Reference: Fama & French (1992) - 'The Cross-Section of Expected Stock Returns'
        log.warn("{} not yet implemented - returning neutral", getName());
        return TradeSignal.neutral("Strategy template - not implemented");
    }

    @Override
    public String getName() {
        return "Book To Market Strategy";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.LONG_TERM;
    }

    @Override
    public void reset() {
        // TODO: Clear any internal state
    }
}
