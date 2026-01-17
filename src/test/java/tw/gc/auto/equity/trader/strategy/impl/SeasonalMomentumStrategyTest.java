package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeasonalMomentumStrategyTest {

    private SeasonalMomentumStrategy strategy;
    private Portfolio mockPortfolio;

    @BeforeEach
    void setUp() {
        String[] strongMonths = {"Nov", "Dec", "Jan"};
        String[] weakMonths = {"May", "Jun", "Sep"};
        strategy = new SeasonalMomentumStrategy(strongMonths, weakMonths);
        mockPortfolio = mock(Portfolio.class);
    }

    @Test
    void getName() {
        assertEquals("Seasonal Momentum Strategy", strategy.getName());
    }

    @Test
    void getType() {
        assertEquals(StrategyType.LONG_TERM, strategy.getType());
    }

    @Test
    void execute_StrongMonth_PositiveMomentum_LongSignal() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build up price history for momentum calculation
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i * 0.5, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 110.0, Month.NOVEMBER, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getConfidence() > 0.6);
        assertTrue(signal.getReason().contains("Seasonal bullish"));
        assertTrue(signal.getReason().contains("strong month"));
    }

    @Test
    void execute_StrongMonth_NegativeMomentum_Neutral() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build up price history with declining prices
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 110.0 - i * 0.5, Month.DECEMBER, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 100.0, Month.DECEMBER, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void execute_StrongMonth_ShortPosition_LongSignal_NotExit() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(-10);

        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i * 0.1, Month.JANUARY, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 102.0, Month.JANUARY, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        // position=-10 satisfies position<=0, so line 70 condition is checked first
        // momentum is positive, so LONG signal is returned, not EXIT
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertFalse(signal.isExitSignal()); // Not an exit signal
        assertTrue(signal.getReason().contains("Seasonal bullish"));
    }

    @Test
    void execute_StrongMonth_ShortPosition_NegativeMomentum_ExitSignal() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(-10);

        // Build declining prices for negative momentum
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 110.0 - i * 0.5, Month.JANUARY, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 100.0, Month.JANUARY, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        // Negative momentum means line 70 condition fails (momentum >= 0)
        // Then line 91 condition is checked: position < 0 && strongMonth
        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("Exit short"));
    }

    @Test
    void execute_WeakMonth_LongPosition_ExitSignal() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(10);

        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i * 0.1, Month.MAY, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 102.0, Month.MAY, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertTrue(signal.isExitSignal());
        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().contains("Seasonal exit"));
        assertTrue(signal.getReason().contains("historically weak"));
    }

    @Test
    void execute_WeakMonth_NegativeMomentum_ShortSignal() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build up price history with declining prices
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 110.0 - i * 0.5, Month.JUNE, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 100.0, Month.JUNE, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getConfidence() > 0.5);
        assertTrue(signal.getReason().contains("Seasonal bearish"));
        assertTrue(signal.getReason().contains("negative momentum"));
    }

    @Test
    void execute_WeakMonth_PositiveMomentum_Neutral() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build up price history with rising prices
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i * 0.5, Month.SEPTEMBER, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 110.0, Month.SEPTEMBER, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void execute_NeutralMonth_Neutral() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i * 0.2, Month.MARCH, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 104.0, Month.MARCH, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
        assertTrue(signal.getReason().contains("Month: Mar"));
    }

    @Test
    void execute_InsufficientHistory_StrongMonth_LongSignal() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Only 10 data points, less than required 20 for momentum calculation
        for (int i = 0; i < 10; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 110.0, Month.NOVEMBER, 11);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        // With insufficient history, momentum is 0, but strong month + position<=0 still triggers LONG
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
    }

    @Test
    void execute_StrongMonth_ExistingLongPosition_Neutral() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(10);

        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0 + i * 0.5, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 110.0, Month.NOVEMBER, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void execute_WeakMonth_ExistingShortPosition_Neutral() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(-10);

        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 110.0 - i * 0.5, Month.MAY, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 100.0, Month.MAY, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void execute_PriceHistoryLimit_RemovesOldest() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Add 35 data points (more than 30 limit)
        for (int i = 0; i < 35; i++) {
            MarketData data = createMarketData("2454.TW", 100.0 + i, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, data);
        }

        // The price history should only keep last 30 points
        // Momentum should be calculated based on recent data
        MarketData data = createMarketData("2454.TW", 135.0, Month.NOVEMBER, 36);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertNotNull(signal);
    }

    @Test
    void execute_MultipleSymbols_SeparateHistory() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);
        when(mockPortfolio.getPosition("0050.TW")).thenReturn(0);

        // Add data for symbol 1
        for (int i = 0; i < 20; i++) {
            MarketData data1 = createMarketData("2454.TW", 100.0 + i, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, data1);
        }

        // Add data for symbol 2
        for (int i = 0; i < 20; i++) {
            MarketData data2 = createMarketData("0050.TW", 50.0 + i * 0.5, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, data2);
        }

        // Each symbol should have its own price history
        MarketData final1 = createMarketData("2454.TW", 120.0, Month.NOVEMBER, 21);
        TradeSignal signal1 = strategy.execute(mockPortfolio, final1);

        MarketData final2 = createMarketData("0050.TW", 60.0, Month.NOVEMBER, 21);
        TradeSignal signal2 = strategy.execute(mockPortfolio, final2);

        assertNotNull(signal1);
        assertNotNull(signal2);
    }

    @Test
    void execute_MonthAbbreviation_AllMonths() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        Month[] months = {
            Month.JANUARY, Month.FEBRUARY, Month.MARCH, Month.APRIL,
            Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST,
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER
        };

        for (Month month : months) {
            for (int i = 0; i < 20; i++) {
                MarketData data = createMarketData("2454.TW", 100.0 + i * 0.1, month, 1 + i);
                strategy.execute(mockPortfolio, data);
            }
            strategy.reset();
        }
    }

    @Test
    void reset_ClearsPriceHistory_InsufficientForMomentum() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build up history
        for (int i = 0; i < 25; i++) {
            MarketData data = createMarketData("2454.TW", 100.0 + i, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, data);
        }

        // Reset should clear history
        strategy.reset();

        // After reset, still in strong month but momentum is 0, so LONG signal is still generated
        MarketData data = createMarketData("2454.TW", 130.0, Month.NOVEMBER, 26);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        // With cleared history and strong month, we get LONG signal (momentum=0, position<=0)
        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
    }

    @Test
    void execute_StrongMonth_ZeroMomentum_LongSignal() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build flat price history
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0, Month.NOVEMBER, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 100.0, Month.NOVEMBER, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().contains("momentum: 0.00%"));
    }

    @Test
    void execute_WeakMonth_ZeroMomentum_Neutral() {
        when(mockPortfolio.getPosition("2454.TW")).thenReturn(0);

        // Build flat price history
        for (int i = 0; i < 20; i++) {
            MarketData warmup = createMarketData("2454.TW", 100.0, Month.MAY, 1 + i);
            strategy.execute(mockPortfolio, warmup);
        }

        MarketData data = createMarketData("2454.TW", 100.0, Month.MAY, 21);
        TradeSignal signal = strategy.execute(mockPortfolio, data);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    private MarketData createMarketData(String symbol, double price, Month month, int day) {
        return MarketData.builder()
                .symbol(symbol)
                .close(price)
                .timestamp(LocalDateTime.of(2024, month, Math.min(day, 28), 10, 0))
                .build();
    }
}
