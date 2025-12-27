package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.*;

@Slf4j
public class ArbitragePairsTradingStrategy implements IStrategy {
    public ArbitragePairsTradingStrategy() {
        log.info("[Arbitrage] Strategy initialized");
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null) {
            return TradeSignal.neutral("No market data");
        }
        log.trace("[Arbitrage] Monitoring for arbitrage opportunities");
        return TradeSignal.neutral("No arbitrage opportunities");
    }
    
    @Override
    public String getName() { return "Arbitrage / Pairs Trading"; }
    
    @Override
    public StrategyType getType() { return StrategyType.SHORT_TERM; }
}
