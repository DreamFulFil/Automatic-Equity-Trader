package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriceActionStrategyTest {

    private static Portfolio portfolioWithPos(String symbol, int pos) {
        Map<String, Integer> positions = new HashMap<>();
        positions.put(symbol, pos);
        return Portfolio.builder()
            .positions(positions)
            .equity(100000.0)
            .tradingMode("stock")
            .tradingQuantity(1)
            .build();
    }

    private static MarketData bar(String symbol, double open, double close) {
        return MarketData.builder()
            .symbol(symbol)
            .open(open)
            .close(close)
            .high(Math.max(open, close))
            .low(Math.min(open, close))
            .build();
    }

    @Test
    void execute_warmup_returnsNeutralUntilThreeBars() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", 0);

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.execute(p, bar("TEST", 100, 99)).getDirection());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.execute(p, bar("TEST", 99, 98)).getDirection());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.execute(p, bar("TEST", 98, 97)).getDirection());
    }

    @Test
    void execute_bullishEngulfing_returnsLong_whenNotAlreadyLong() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", 0);

        s.execute(p, bar("TEST", 100, 101)); // warmup
        s.execute(p, bar("TEST", 105, 100)); // bearish
        TradeSignal signal = s.execute(p, bar("TEST", 99, 106)); // bullish engulf

        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("bullish"));
    }

    @Test
    void execute_bullishEngulfing_skipsLong_whenAlreadyLong() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", 1);

        s.execute(p, bar("TEST", 100, 101));
        s.execute(p, bar("TEST", 105, 100));
        TradeSignal signal = s.execute(p, bar("TEST", 99, 106));

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void execute_bearishEngulfing_returnsShort_whenNotAlreadyShort() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", 0);

        s.execute(p, bar("TEST", 100, 99));
        s.execute(p, bar("TEST", 90, 95)); // bullish
        TradeSignal signal = s.execute(p, bar("TEST", 96, 89)); // bearish engulf

        assertEquals(TradeSignal.SignalDirection.SHORT, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("bearish"));
    }

    @Test
    void execute_bearishEngulfing_skipsShort_whenAlreadyShort() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", -1);

        s.execute(p, bar("TEST", 100, 99));
        s.execute(p, bar("TEST", 90, 95));
        TradeSignal signal = s.execute(p, bar("TEST", 96, 89));

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, signal.getDirection());
    }

    @Test
    void execute_morningStar_returnsLong_whenNotAlreadyLong() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", 0);

        // b0 bearish big
        s.execute(p, bar("TEST", 110, 100));
        // b1 small body
        s.execute(p, bar("TEST", 101, 100.5));
        // b2 bullish closing above mid of b0
        TradeSignal signal = s.execute(p, bar("TEST", 100.5, 106));

        assertEquals(TradeSignal.SignalDirection.LONG, signal.getDirection());
        assertTrue(signal.getReason().toLowerCase().contains("morning"));
    }

    @Test
    void reset_clearsHistory() {
        PriceActionStrategy s = new PriceActionStrategy();
        Portfolio p = portfolioWithPos("TEST", 0);
        s.execute(p, bar("TEST", 100, 99));
        s.execute(p, bar("TEST", 99, 98));
        s.reset();

        TradeSignal afterReset = s.execute(p, bar("TEST", 98, 97));
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, afterReset.getDirection());
        assertTrue(afterReset.getReason().toLowerCase().contains("warming"));
    }
}
