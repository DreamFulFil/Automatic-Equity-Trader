package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class BacktestServiceStockCandidateTest {

    @Test
    void stockCandidate_reflection_getters_score_equals_hashcode() throws Exception {
        Class<?> cls = Class.forName("tw.gc.auto.equity.trader.services.BacktestService$StockCandidate");

        Constructor<?> ctor = cls.getDeclaredConstructor(String.class, String.class, double.class, double.class, String.class);
        ctor.setAccessible(true);

        Object a = ctor.newInstance("2330.TW", "TSMC", 1_000_000.0, 500_000.0, "TWSE");
        Object b = ctor.newInstance("2330.TW", "TSMC Alt", 10.0, 20.0, "Yahoo");

        Method getSymbol = cls.getDeclaredMethod("getSymbol");
        Method getName = cls.getDeclaredMethod("getName");
        Method getMarketCap = cls.getDeclaredMethod("getMarketCap");
        Method getVolume = cls.getDeclaredMethod("getVolume");
        Method getSource = cls.getDeclaredMethod("getSource");
        Method getScore = cls.getDeclaredMethod("getScore");

        assertEquals("2330.TW", getSymbol.invoke(a));
        assertEquals("TSMC", getName.invoke(a));
        assertEquals(1_000_000.0, (double) getMarketCap.invoke(a));
        assertEquals(500_000.0, (double) getVolume.invoke(a));
        assertEquals("TWSE", getSource.invoke(a));

        double expectedScore = 1_000_000.0 * 0.7 + 500_000.0 * 0.3;
        assertEquals(expectedScore, (double) getScore.invoke(a), 1e-6);

        // equals() compares symbol only, so a and b should be equal
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
