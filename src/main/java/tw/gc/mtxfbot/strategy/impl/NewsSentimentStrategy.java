package tw.gc.mtxfbot.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.mtxfbot.entities.MarketData;
import tw.gc.mtxfbot.strategy.*;

@Slf4j
public class NewsSentimentStrategy implements IStrategy {
    public NewsSentimentStrategy() {
        log.info("[News Sentiment] Strategy initialized");
    }
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        if (data == null) {
            return TradeSignal.neutral("No market data");
        }
        log.trace("[News Sentiment] Analyzing market sentiment");
        return TradeSignal.neutral("Neutral sentiment");
    }
    
    @Override
    public String getName() { return "News / Sentiment-Based Trading"; }
    
    @Override
    public StrategyType getType() { return StrategyType.SHORT_TERM; }
}
