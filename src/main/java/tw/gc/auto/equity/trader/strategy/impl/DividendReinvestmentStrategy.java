package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.*;

@Slf4j
public class DividendReinvestmentStrategy implements IStrategy {
    public DividendReinvestmentStrategy() {
        log.info("[DRIP] Strategy initialized");
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || data.getSymbol() == null) {
            return TradeSignal.neutral("No market data");
        }
        log.trace("[DRIP] Monitoring for dividend reinvestment opportunities");
        return TradeSignal.neutral("No dividends to reinvest");
    }
    
    @Override
    public String getName() { return "Dividend Reinvestment (DRIP)"; }
    
    @Override
    public StrategyType getType() { return StrategyType.LONG_TERM; }
}
