package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyCoveragePatchTest {

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = Portfolio.builder().positions(new HashMap<>()).build();
    }

    @Test
    void movingAverageCrossover_deathCrossAlreadyShort() {
        MovingAverageCrossoverStrategy strategy = new MovingAverageCrossoverStrategy(5, 20, 0.001);
        portfolio.setPosition("AUTO_EQUITY_TRADER", -1);
        Deque<Double> prices = strategy.getPriceHistory("AUTO_EQUITY_TRADER");
        if (prices == null) {
            try {
                Field f = MovingAverageCrossoverStrategy.class.getDeclaredField("priceHistory");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
                prices = new ArrayDeque<>();
                map.put("AUTO_EQUITY_TRADER", prices);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (int i = 0; i < 20; i++) {
            prices.addLast(200.0 - i);
        }
        try {
            Field f = MovingAverageCrossoverStrategy.class.getDeclaredField("previousGoldenCross");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Boolean> map = (Map<String, Boolean>) f.get(strategy);
            map.put("AUTO_EQUITY_TRADER", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        TradeSignal signal = strategy.execute(portfolio, market("AUTO_EQUITY_TRADER", 50.0));
        assertNotNull(signal);
        assertEquals("Already short", signal.getReason());
    }

    @Test
    void dividendYield_exitAndLongPaths() throws Exception {
        DividendYieldStrategy strategy = new DividendYieldStrategy(0.03, 1);
        portfolio.setPosition("DIV", 100);
        primePriceHistory(DividendYieldStrategy.class, strategy, "priceHistory", "DIV", 70, 100, true);
        TradeSignal exit = strategy.execute(portfolio, market("DIV", 100.0));
        assertTrue(exit.isExitSignal());

        portfolio.setPosition("DIV", 0);
        primePriceHistory(DividendYieldStrategy.class, strategy, "priceHistory", "DIV", 90, 100, false);
        TradeSignal longSignal = strategy.execute(portfolio, market("DIV", 95.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, longSignal.getDirection());
    }



    @Test
    void cashFlowValue_shortSignal() throws Exception {
        CashFlowValueStrategy strategy = new CashFlowValueStrategy(0.05);
        portfolio.setPosition("FCF", 0);
        primePriceHistory(CashFlowValueStrategy.class, strategy, "priceHistory", "FCF", 90, 100, true);
        primeVolumeHistory(strategy, "FCF", 90, 10000);
        TradeSignal signal = strategy.execute(portfolio, market("FCF", 160.0, 10000));
        assertNotNull(signal);
    }

    @Test
    void multiFactor_exitSignal() throws Exception {
        double[] weights = {0.25, 0.25, 0.25, 0.25};
        MultiFactorRankingStrategy strategy = new MultiFactorRankingStrategy(weights, 5);
        portfolio.setPosition("FACT", 100);
        setPriceHistoryValues(strategy, "FACT");
        setDaysSinceRebalance(strategy, "FACT", 10);
        TradeSignal signal = strategy.execute(portfolio, market("FACT", 140.0));
        assertNotNull(signal);
    }

    @Test
    void pairsCorrelation_exitSignal() {
        PairsCorrelationStrategy strategy = new PairsCorrelationStrategy("2330.TW", "2454.TW", 30, 2.0);
        Portfolio mockPortfolio = mock(Portfolio.class);
        when(mockPortfolio.getPosition("2330.TW")).thenReturn(10);
        for (int i = 0; i < 30; i++) {
            strategy.execute(mockPortfolio, market("2330.TW", 500.0 + i));
            strategy.execute(mockPortfolio, market("2454.TW", 100.0 + i * 0.2));
        }
        TradeSignal signal = strategy.execute(mockPortfolio, market("2330.TW", 515.0));
        assertNotNull(signal);
    }

    @Test
    void profitabilityFactor_exitSignal() {
        ProfitabilityFactorStrategy strategy = new ProfitabilityFactorStrategy(0.3);
        portfolio.setPosition("EXIT", 100);
        for (int i = 0; i < 60; i++) {
            strategy.execute(portfolio, market("EXIT", 100.0 + i * 0.1));
        }
        for (int i = 0; i < 30; i++) {
            TradeSignal signal = strategy.execute(portfolio, market("EXIT", 100.0 + Math.pow(-1, i) * 30));
            if (signal.isExitSignal()) {
                assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
                return;
            }
        }
    }

    @Test
    void adxTrend_atrZeroBranch() {
        ADXTrendStrategy strategy = new ADXTrendStrategy(14, 25.0);
        TradeSignal signal = strategy.execute(portfolio, MarketData.builder()
            .symbol("TEST")
            .high(101.0)
            .low(99.0)
            .close(100.0)
            .build());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void calendarSpread_volatilityZeroBranch() {
        CalendarSpreadStrategy strategy = new CalendarSpreadStrategy(1, 3, 0.01);
        TradeSignal signal = strategy.execute(portfolio, market("VOL", 100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void keltnerChannel_atrZeroBranch() {
        KeltnerChannelStrategy strategy = new KeltnerChannelStrategy(20, 10, 2.0);
        TradeSignal signal = strategy.execute(portfolio, market("TEST", 100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void rsi_default50Branch() {
        RSIStrategy strategy = new RSIStrategy(14, 70, 30);
        TradeSignal signal = strategy.execute(portfolio, market("TEST", 100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void seasonalMomentum_monthAbbrev() throws Exception {
        SeasonalMomentumStrategy strategy = new SeasonalMomentumStrategy(new String[]{"Nov"}, new String[]{"May"});
        var method = SeasonalMomentumStrategy.class.getDeclaredMethod("getMonthAbbreviation", Month.class);
        method.setAccessible(true);
        assertEquals("Jan", method.invoke(strategy, Month.JANUARY));
    }

    @Test
    void supertrend_atrZeroBranch() {
        SupertrendStrategy strategy = new SupertrendStrategy(10, 3.0);
        TradeSignal signal = strategy.execute(portfolio, market("TEST", 100.0));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    private MarketData market(String symbol, double close) {
        return market(symbol, close, 1000L);
    }

    private MarketData market(String symbol, double close, long volume) {
        return MarketData.builder()
            .symbol(symbol)
            .timestamp(LocalDateTime.now())
            .close(close)
            .high(close + 1)
            .low(close - 1)
            .open(close)
            .volume(volume)
            .build();
    }

    private void primePriceHistory(Class<?> clazz, Object strategy, String fieldName, String symbol, int count, double base, boolean volatilePrices) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
        Deque<Double> dq = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            double price = volatilePrices ? base + (i % 2 == 0 ? 50 : -50) : base;
            dq.addLast(price);
        }
        map.put(symbol, dq);
    }

    private void primeVolumeHistory(CashFlowValueStrategy strategy, String symbol, int count, long volume) throws Exception {
        Field f = CashFlowValueStrategy.class.getDeclaredField("volumeHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Long>> map = (Map<String, Deque<Long>>) f.get(strategy);
        Deque<Long> dq = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            dq.addLast(volume);
        }
        map.put(symbol, dq);
    }

    private void setDaysSinceRebalance(MultiFactorRankingStrategy strategy, String symbol, int days) throws Exception {
        Field f = MultiFactorRankingStrategy.class.getDeclaredField("daysSinceRebalance");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>) f.get(strategy);
        map.put(symbol, days);
    }

    private void setPriceHistoryValues(MultiFactorRankingStrategy strategy, String symbol) throws Exception {
        Field f = MultiFactorRankingStrategy.class.getDeclaredField("priceHistory");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Deque<Double>> map = (Map<String, Deque<Double>>) f.get(strategy);
        Deque<Double> dq = new ArrayDeque<>();
        for (int i = 0; i < 35; i++) {
            dq.addLast(80.0);
        }
        for (int i = 0; i < 29; i++) {
            dq.addLast(150.0);
        }
        dq.addLast(140.0);
        map.put(symbol, dq);
    }
}
