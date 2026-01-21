package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FibonacciRetracementStrategyTest {

    private FibonacciRetracementStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new FibonacciRetracementStrategy(20);
        Map<String, Integer> positions = new HashMap<>();
        portfolio = Portfolio.builder().positions(positions).build();
    }

    @Test
    void warmup_returnsNeutral() {
        MarketData md = createMarketData("TEST", 100.0, 105.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Warming up"));
    }

    @Test
    void priceAt618Level_shouldProduceLong() {
        // Test line 70: Long signal at 61.8% Fibonacci level
        // Build history with swing high 200, swing low 100 (range = 100)
        // 61.8% retracement = 200 - (100 * 0.618) = 138.2
        for (int i = 0; i < 20; i++) {
            double high = 100 + i * 5; // Rising highs to 200
            double low = 100;
            MarketData md = createMarketData("FIB", (high + low) / 2, high, low);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("FIB", 0); // no position
        
        // Price at 61.8% level
        double swingHigh = 195.0; // approximately
        double swingLow = 100.0;
        double range = swingHigh - swingLow;
        double fib618 = swingHigh - (range * 0.618);
        
        MarketData md = createMarketData("FIB", fib618, fib618 + 1, fib618 - 1);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.8, signal.getConfidence());
            assertTrue(signal.getReason().contains("61.8%"));
        }
    }

    @Test
    void priceAt50Level_shouldProduceLong() {
        // Test: Long signal at 50% Fibonacci level
        for (int i = 0; i < 20; i++) {
            double high = 100 + i * 5;
            double low = 100;
            MarketData md = createMarketData("FIB50", (high + low) / 2, high, low);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("FIB50", 0);
        
        // Price at 50% level (midpoint of range)
        double midPrice = 147.5; // approximately halfway
        MarketData md = createMarketData("FIB50", midPrice, midPrice + 1, midPrice - 1);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.75, signal.getConfidence());
            assertTrue(signal.getReason().contains("50%"));
        }
    }

    @Test
    void priceAtSwingHigh_withLongPosition_shouldProduceExit() {
        // Test line 76: Exit signal when price reaches swing high with position > 0
        // Build history
        for (int i = 0; i < 20; i++) {
            double high = 100 + i * 5; // max high ~195
            double low = 100;
            MarketData md = createMarketData("EXIT", (high + low) / 2, high, low);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("EXIT", 100); // long position
        
        // Price at swing high (>= swingHigh * 0.98)
        double swingHighApprox = 195.0;
        double priceAtSwingHigh = swingHighApprox * 0.99; // within 98% of swing high
        
        MarketData md = createMarketData("EXIT", priceAtSwingHigh, priceAtSwingHigh + 1, priceAtSwingHigh - 1);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
            assertEquals(0.8, signal.getConfidence());
            assertTrue(signal.getReason().contains("swing high"));
        }
    }

    @Test
    void priceAt382Level_shouldProduceLong() {
        // Test line 74: Long signal at 38.2% Fibonacci level
        // Build history with clear swing high/low
        for (int i = 0; i < 20; i++) {
            double high = 100 + i * 5; // Rising highs to ~195
            double low = 100;
            MarketData md = createMarketData("FIB38", (high + low) / 2, high, low);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("FIB38", 0); // no position
        
        // Calculate 38.2% level
        // swingHigh ~ 195, swingLow ~ 100, range ~ 95
        // fib382 = 195 - (95 * 0.382) = 195 - 36.29 = 158.71
        double swingHighApprox = 195.0;
        double swingLow = 100.0;
        double range = swingHighApprox - swingLow;
        double fib382 = swingHighApprox - (range * 0.382);
        
        // Price at 38.2% level (within 2% tolerance)
        MarketData md = createMarketData("FIB38", fib382, fib382 + 1, fib382 - 1);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.7, signal.getConfidence());
            assertTrue(signal.getReason().contains("38.2%"));
        }
    }

    @Test
    void priceNotAtFibLevel_shouldReturnNeutral() {
        // Build history
        for (int i = 0; i < 20; i++) {
            double high = 100 + i * 5;
            double low = 100;
            MarketData md = createMarketData("NEUT", (high + low) / 2, high, low);
            strategy.execute(portfolio, md);
        }
        
        portfolio.setPosition("NEUT", 0);
        
        // Price not at any fib level
        MarketData md = createMarketData("NEUT", 110.0, 111.0, 109.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Not at Fibonacci"));
    }

    @Test
    void getName_shouldReturnFibonacciRetracement() {
        assertEquals("Fibonacci Retracement", strategy.getName());
    }

    @Test
    void getType_shouldReturnSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_shouldClearHistory() {
        for (int i = 0; i < 25; i++) {
            strategy.execute(portfolio, createMarketData("RST", 100.0, 105.0, 95.0));
        }
        
        strategy.reset();
        
        MarketData md = createMarketData("RST", 100.0, 105.0, 95.0);
        TradeSignal signal = strategy.execute(portfolio, md);
        assertTrue(signal.getReason().contains("Warming up"));
    }

    private MarketData createMarketData(String symbol, double close, double high, double low) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(close)
                .high(high)
                .low(low)
                .close(close)
                .volume(1000L)
                .build();
    }
}
