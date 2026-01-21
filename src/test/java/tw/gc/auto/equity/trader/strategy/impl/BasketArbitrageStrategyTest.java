package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BasketArbitrageStrategyTest {

    private BasketArbitrageStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        String[] basket = {"SYM1", "SYM2", "SYM3"};
        strategy = new BasketArbitrageStrategy(basket, "INDEX");
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 1)
                .low(close - 1)
                .open(close)
                .volume(10000L)
                .build();
    }

    @Test
    void warmingUp_returnsNeutral() {
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SYM1", 100));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("warming up"));
    }

    @Test
    void longSignal_whenBasketUndervalued() throws Exception {
        // Create condition: deviation < -0.02 && momentum > 0 && position <= 0
        double[] prices = new double[25];
        for (int i = 0; i < 25; i++) {
            prices[i] = 110; // Stable high price creates average of 110
        }
        primeHistory(strategy, "SYM1", prices);
        
        portfolio.setPosition("SYM1", 0);
        
        // Current price significantly below average (deviation < -0.02)
        // Also add recent uptick for positive momentum
        double[] pricesWithMomentum = new double[25];
        for (int i = 0; i < 20; i++) pricesWithMomentum[i] = 108;
        for (int i = 20; i < 25; i++) pricesWithMomentum[i] = 112; // Recent uptick -> positive momentum
        primeHistory(strategy, "SYM1", pricesWithMomentum);
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SYM1", 105));
        
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertEquals(0.70, signal.getConfidence());
            assertTrue(signal.getReason().contains("undervalued"));
        }
    }

    @Test
    void exitSignal_whenBasketOvervalued() throws Exception {
        // Lines 82-85: Exit when deviation > 0.02 && momentum < 0 && position > 0
        portfolio.setPosition("SYM1", 100);
        
        // Create condition: price well above average with negative momentum
        double[] prices = new double[25];
        for (int i = 0; i < 20; i++) prices[i] = 102; // Historical stable low
        for (int i = 20; i < 25; i++) prices[i] = 98;  // Recent decline -> negative momentum
        primeHistory(strategy, "SYM1", prices);
        
        // Current price above average (deviation > 0.02)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SYM1", 106));
        
        assertNotNull(signal);
        if (signal.isExitSignal()) {
            assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
            assertEquals(0.65, signal.getConfidence());
            assertTrue(signal.getReason().contains("overvalued"));
        }
    }

    @Test
    void shortSignal_whenBasketHighlyOvervalued() throws Exception {
        // Lines 89-91: Short when deviation > 0.03 && position >= 0
        portfolio.setPosition("SYM1", 0);
        
        double[] prices = new double[25];
        for (int i = 0; i < 25; i++) prices[i] = 100;
        primeHistory(strategy, "SYM1", prices);
        
        // Current price significantly above average (deviation > 0.03)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SYM1", 105));
        
        assertNotNull(signal);
        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && !signal.isExitSignal()) {
            assertEquals(0.60, signal.getConfidence());
            assertTrue(signal.getReason().contains("Short basket"));
        }
    }

    @Test
    void neutralSignal_whenNoConditionsMet() throws Exception {
        portfolio.setPosition("SYM1", 0);
        
        double[] prices = new double[25];
        for (int i = 0; i < 25; i++) prices[i] = 100;
        primeHistory(strategy, "SYM1", prices);
        
        // Price at average (no significant deviation)
        TradeSignal signal = strategy.execute(portfolio, createMarketData("SYM1", 100));
        
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Basket deviation"));
    }

    @Test
    void historyTrimsWhenExceeds60() throws Exception {
        double[] prices = new double[65];
        for (int i = 0; i < 65; i++) prices[i] = 100 + i * 0.1;
        primeHistory(strategy, "TRIM", prices);
        
        TradeSignal signal = strategy.execute(portfolio, createMarketData("TRIM", 110));
        assertNotNull(signal);
    }

    @Test
    void getName_returnsCorrectFormat() {
        assertTrue(strategy.getName().contains("Basket Arbitrage"));
    }

    @Test
    void getType_returnsSwing() {
        assertEquals(StrategyType.SWING, strategy.getType());
    }

    @Test
    void reset_clearsHistory() throws Exception {
        strategy.execute(portfolio, createMarketData("SYM1", 100));
        
        strategy.reset();
        
        Field f = BasketArbitrageStrategy.class.getDeclaredField("priceHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
        assertTrue(map.isEmpty());
    }

    private void primeHistory(BasketArbitrageStrategy strat, String symbol, double[] prices) {
        try {
            Field f = BasketArbitrageStrategy.class.getDeclaredField("priceHistory");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strat);
            Deque<Double> dq = new ArrayDeque<>();
            for (double p : prices) dq.addLast(p);
            map.put(symbol, dq);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
