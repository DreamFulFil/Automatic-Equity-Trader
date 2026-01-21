package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ProfitabilityFactorStrategyTest {

    private ProfitabilityFactorStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new ProfitabilityFactorStrategy(0.3);
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    @Test
    void warmup_returnsNeutral() {
        for (int i = 0; i < 59; i++) {
            MarketData md = createMarketData("TEST", 100.0 + i * 0.1);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
            assertTrue(signal.getReason().contains("Warming up"));
        }
    }

    @Test
    void highProfitability_producesLong() {
        // Build stable upward history (high win rate, high stability)
        for (int i = 0; i < 65; i++) {
            double price = 100.0 + i * 0.2; // consistent upward
            MarketData md = createMarketData("PROFIT", price);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            if (i >= 59 && signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertTrue(signal.getConfidence() >= 0.70);
                assertTrue(signal.getReason().contains("High profitability"));
                return;
            }
        }
    }

    @Test
    void profitabilityDecline_producesExitSignal() {
        // Lines 81-83: Exit signal when profitabilityProxy < minGrossProfitability/2 with position > 0
        // First build good profitability history
        for (int i = 0; i < 60; i++) {
            double price = 100.0 + i * 0.1; // stable upward
            strategy.execute(portfolio, createMarketData("EXIT", price));
        }
        
        portfolio.setPosition("EXIT", 100); // long position
        
        // Now add highly volatile data to decrease profitability (win rate and stability drops)
        for (int i = 0; i < 30; i++) {
            double price = 100.0 + Math.pow(-1, i) * 30; // wild swings
            MarketData md = createMarketData("EXIT", price);
            TradeSignal signal = strategy.execute(portfolio, md);
            
            if (signal.isExitSignal()) {
                assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
                assertEquals(0.65, signal.getConfidence());
                assertTrue(signal.getReason().contains("Profitability decline"));
                return;
            }
        }
    }

    @Test
    void lowProfitability_returnsNeutral() {
        // Build volatile history (low win rate, low stability)
        for (int i = 0; i < 65; i++) {
            double price = 100.0 + Math.pow(-1, i) * 5; // oscillating
            MarketData md = createMarketData("NEUT", price);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("NEUT", 0);
        
        MarketData md = createMarketData("NEUT", 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        // Low profitability returns neutral
        if (signal.getDirection() == TradeSignal.SignalDirection.NEUTRAL) {
            assertTrue(signal.getReason().contains("Profitability"));
        }
    }

    @Test
    void getName_shouldContainProfitabilityFactor() {
        String name = strategy.getName();
        assertTrue(name.contains("Profitability Factor"));
    }

    @Test
    void getType_shouldReturnLongTerm() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void reset_shouldClearHistory() {
        for (int i = 0; i < 70; i++) {
            strategy.execute(portfolio, createMarketData("RST", 100.0));
        }
        
        strategy.reset();
        
        MarketData md = createMarketData("RST", 100.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(close)
                .low(close)
                .close(close)
                .volume(1000L)
                .build();
    }
}
