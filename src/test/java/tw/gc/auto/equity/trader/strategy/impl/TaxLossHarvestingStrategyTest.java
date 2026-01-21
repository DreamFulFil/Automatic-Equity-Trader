package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class TaxLossHarvestingStrategyTest {

    private TaxLossHarvestingStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new TaxLossHarvestingStrategy();
        portfolio = new Portfolio();
    }

    @Test
    void execute_withNullData_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, null);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No data", signal.getReason());
    }

    @Test
    void execute_withNullPortfolio_returnsNeutral() {
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        TradeSignal signal = strategy.execute(null, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No data", signal.getReason());
    }

    @Test
    void execute_withBothNull_returnsNeutral() {
        TradeSignal signal = strategy.execute(null, null);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No data", signal.getReason());
    }

    @Test
    void execute_withValidData_returnsNoOpportunities() {
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertEquals("No tax-loss opportunities", signal.getReason());
    }

    @Test
    void getName_returnsCorrectName() {
        assertEquals("Tax-Loss Harvesting", strategy.getName());
    }

    @Test
    void getType_returnsLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }
}
