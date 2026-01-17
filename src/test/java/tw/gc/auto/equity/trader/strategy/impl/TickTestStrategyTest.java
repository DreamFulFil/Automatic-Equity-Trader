package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class TickTestStrategyTest {

    @Test
    void execute_warmupThenLongSignal() {
        TickTestStrategy s = new TickTestStrategy(3, 0.5);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.execute(p, md(100)).getDirection());
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.execute(p, md(101)).getDirection());

        TradeSignal sig = s.execute(p, md(102));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.LONG, sig.getDirection());
    }

    @Test
    void execute_shortSignalAndExitSignal() {
        TickTestStrategy s = new TickTestStrategy(3, 0.5);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // warmup
        s.execute(p, md(100));
        s.execute(p, md(99));

        // strong selling -> short (flat)
        TradeSignal shortSig = s.execute(p, md(98));
        assertEquals(TradeSignal.SignalDirection.SHORT, shortSig.getDirection());

        // now pretend we are long; strong selling should trigger exit
        p.setPosition("TEST", 1);
        s.reset();
        s.execute(p, md(100));
        s.execute(p, md(99));
        TradeSignal exitSig = s.execute(p, md(98));
        assertTrue(exitSig.isExitSignal());
    }

    @Test
    void reset_clearsState() {
        TickTestStrategy s = new TickTestStrategy(3, 0.5);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        s.execute(p, md(100));
        s.reset();
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, s.execute(p, md(101)).getDirection());
    }

    private static MarketData md(double close) {
        return MarketData.builder()
            .symbol("TEST")
            .timestamp(LocalDateTime.now())
            .timeframe(MarketData.Timeframe.MIN_1)
            .open(close)
            .high(close)
            .low(close)
            .close(close)
            .volume(1000)
            .assetType(MarketData.AssetType.STOCK)
            .build();
    }
}
