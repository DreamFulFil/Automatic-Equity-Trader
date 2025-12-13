package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.*;

@Slf4j
public class TaxLossHarvestingStrategy implements IStrategy {
    public TaxLossHarvestingStrategy() {
        log.info("[Tax Loss] Strategy initialized");
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null || portfolio == null) {
            return TradeSignal.neutral("No data");
        }
        log.trace("[Tax Loss] Monitoring for tax-loss harvesting opportunities");
        return TradeSignal.neutral("No tax-loss opportunities");
    }
    
    @Override
    public String getName() { return "Tax-Loss Harvesting"; }
    
    @Override
    public StrategyType getType() { return StrategyType.LONG_TERM; }
}
