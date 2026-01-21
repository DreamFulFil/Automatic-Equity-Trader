package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class NewsSentimentStrategyTest {

    private NewsSentimentStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new NewsSentimentStrategy();
        portfolio = new Portfolio();
    }

    @Test
    void execute_withNullData_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No market data", signal.getReason());
    }

    @Test
    void execute_withValidData_returnsNeutralSentiment() {
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("Neutral sentiment", signal.getReason());
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("News / Sentiment-Based Trading", strategy.getName());
    }

    @Test
    void getType_returnsShortTerm() {
        assertEquals(StrategyType.SHORT_TERM, strategy.getType());
    }
}
