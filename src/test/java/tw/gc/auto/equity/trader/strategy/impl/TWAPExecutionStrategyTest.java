package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class TWAPExecutionStrategyTest {

    private MarketData mk(String sym) {
        return MarketData.builder()
                .symbol(sym)
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(100.0)
                .high(101.0)
                .low(99.0)
                .close(100.0)
                .volume(1000L)
                .build();
    }

    @Test
    void onPaceReturnsNeutral() {
        TWAPExecutionStrategy s = new TWAPExecutionStrategy(10, 60);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        TradeSignal sig = s.execute(p, mk("T"));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, sig.getDirection());
    }

    @Test
    void behindScheduleExecutesSlice() throws Exception {
        TWAPExecutionStrategy s = new TWAPExecutionStrategy(10, 10);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        // backdate startTime to 5 minutes ago to be behind schedule
        Field f = TWAPExecutionStrategy.class.getDeclaredField("startTime");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, LocalDateTime> m = (java.util.Map<String, LocalDateTime>) f.get(s);
        m.put("T", LocalDateTime.now().minusMinutes(5));

        TradeSignal sig = s.execute(p, mk("T"));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.LONG, sig.getDirection());
        assertTrue(sig.getConfidence() >= 0.79 && sig.getConfidence() <= 0.81);
    }

    @Test
    void urgentWhenWindowClosing() throws Exception {
        TWAPExecutionStrategy s = new TWAPExecutionStrategy(5, 10);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        Field f = TWAPExecutionStrategy.class.getDeclaredField("startTime");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, LocalDateTime> m = (java.util.Map<String, LocalDateTime>) f.get(s);
        m.put("U", LocalDateTime.now().minusMinutes(15)); // past window

        TradeSignal sig = s.execute(p, mk("U"));
        assertNotNull(sig);
        assertEquals(TradeSignal.SignalDirection.LONG, sig.getDirection());
        assertTrue(sig.getConfidence() > 0.9);
    }

    @Test
    void resetClearsState() throws Exception {
        TWAPExecutionStrategy s = new TWAPExecutionStrategy(3, 5);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        s.execute(p, mk("R"));

        s.reset();

        Field fExec = TWAPExecutionStrategy.class.getDeclaredField("executedVolume");
        fExec.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> exec = (java.util.Map<String, Integer>) fExec.get(s);
        assertTrue(exec.isEmpty());
    }
}
 
