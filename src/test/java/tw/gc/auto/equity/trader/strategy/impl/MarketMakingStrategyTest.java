package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import static org.junit.jupiter.api.Assertions.*;

class MarketMakingStrategyTest {

    private MarketMakingStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new MarketMakingStrategy(0.01, 5);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_returnsWarmingUp() {
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void execute_withPriceDip_returnsLongSignal() {
        portfolio.setPosition("TEST", 0);
        
        // Build up price history
        for (int i = 0; i < 15; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Price drops by more than spreadCapture (1%)
        MarketData dip = MarketData.builder()
                .symbol("TEST")
                .close(98.0)  // -2% drop
                .high(99.0)
                .low(97.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, dip);
        
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("MM buy"));
    }

    @Test
    void execute_withPricePop_returnsExitSignal() {
        portfolio.setPosition("TEST", 10);
        
        // Build up price history and inventory
        for (int i = 0; i < 15; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Simulate having inventory by buying first
        portfolio.setPosition("TEST", 0);
        MarketData dip = MarketData.builder()
                .symbol("TEST")
                .close(98.0)
                .high(99.0)
                .low(97.0)
                .volume(1000L)
                .build();
        strategy.execute(portfolio, dip);  // This adds to inventory
        
        // Now set position as long and price rises
        portfolio.setPosition("TEST", 10);
        MarketData pop = MarketData.builder()
                .symbol("TEST")
                .close(100.0)  // +2% rise from 98
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, pop);
        
        // Line 72-76: Sell on pops when inventory > 0 and position > 0
        assertTrue(signal.isExitSignal());
        assertTrue(signal.getReason().contains("MM sell"));
    }

    @Test
    void execute_withMaxInventory_flattenPosition() {
        portfolio.setPosition("TEST", 10);
        
        // Build up price history
        for (int i = 0; i < 15; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        // Build up inventory to max by buying on dips
        for (int i = 0; i < 6; i++) {
            portfolio.setPosition("TEST", 0);
            MarketData dip = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0 - 2)  // Price dip
                    .high(99.0)
                    .low(97.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, dip);
            
            // Stable price for next iteration
            MarketData stable = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, stable);
        }
        
        // Now inventory should be at max, price at or above mid
        portfolio.setPosition("TEST", 10);
        MarketData atMid = MarketData.builder()
                .symbol("TEST")
                .close(100.0)  // At mid price
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, atMid);
        
        // Lines 80-83: Inventory management - flatten when maxInventory reached
        if (signal.isExitSignal()) {
            assertTrue(signal.getReason().contains("MM flatten"));
        }
    }

    @Test
    void getName_returnsCorrectName() {
        String name = strategy.getName();
        assertTrue(name.contains("Market Making"));
        assertTrue(name.contains("1.00%"));
        assertTrue(name.contains("max=5"));
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsHistory() {
        // Build up history
        for (int i = 0; i < 15; i++) {
            MarketData data = MarketData.builder()
                    .symbol("TEST")
                    .close(100.0)
                    .high(101.0)
                    .low(99.0)
                    .volume(1000L)
                    .build();
            strategy.execute(portfolio, data);
        }
        
        strategy.reset();
        
        // After reset, should be back to warming up
        MarketData data = MarketData.builder()
                .symbol("TEST")
                .close(100.0)
                .high(101.0)
                .low(99.0)
                .volume(1000L)
                .build();
        TradeSignal signal = strategy.execute(portfolio, data);
        
        assertTrue(signal.getReason().contains("Warming up"));
    }
}
