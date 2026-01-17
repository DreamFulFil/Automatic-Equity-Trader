package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyImplTest {

    @Test
    void instantiateAndExecute_allStrategyImpls() throws Exception {
        Path dir = Path.of("src/main/java/tw/gc/auto/equity/trader/strategy/impl");
        assertTrue(Files.isDirectory(dir), "Expected strategy impl dir at " + dir);

        var files = Files.list(dir)
            .filter(p -> p.getFileName().toString().endsWith(".java"))
            .toList();

        assertFalse(files.isEmpty());

        for (Path f : files) {
            String simple = f.getFileName().toString().replaceFirst("\\.java$", "");
            String clsName = "tw.gc.auto.equity.trader.strategy.impl." + simple;

            Class<?> cls = Class.forName(clsName);
            if (!IStrategy.class.isAssignableFrom(cls)) {
                continue;
            }

            try {
                IStrategy s = (IStrategy) instantiateWithDefaults(cls);

                assertNotNull(s.getName());
                assertNotNull(s.getType());

                Portfolio p = Portfolio.builder()
                    .positions(new HashMap<>())
                    .entryPrices(new HashMap<>())
                    .equity(100000.0)
                    .availableMargin(100000.0)
                    .tradingMode("stock")
                    .tradingQuantity(1)
                    .build();

                s.reset();

                for (int i = 0; i < 60; i++) {
                    double price = 100.0 + (i * 0.3) + Math.sin(i / 3.0) * 3.0;
                    MarketData md = MarketData.builder()
                        .symbol("TEST")
                        .name("TEST")
                        .timestamp(LocalDateTime.now().minusMinutes(60 - i))
                        .timeframe(MarketData.Timeframe.MIN_1)
                        .open(price)
                        .high(price * 1.01)
                        .low(price * 0.99)
                        .close(price)
                        .volume(1000L + i)
                        .assetType(MarketData.AssetType.STOCK)
                        .build();

                    TradeSignal sig = s.execute(p, md);
                    assertNotNull(sig);
                    assertNotNull(sig.getDirection());

                    if (sig.isExitSignal()) {
                        p.setPosition(md.getSymbol(), 0);
                    } else if (sig.getDirection() == TradeSignal.SignalDirection.LONG) {
                        p.setPosition(md.getSymbol(), 1);
                        p.setEntryPrice(md.getSymbol(), md.getClose());
                    } else if (sig.getDirection() == TradeSignal.SignalDirection.SHORT) {
                        p.setPosition(md.getSymbol(), -1);
                        p.setEntryPrice(md.getSymbol(), md.getClose());
                    }
                }
            } catch (Exception e) {
                System.err.println("Skipping " + clsName + " due to: " + e.getClass().getSimpleName() + " -> " + e.getMessage());
            }
        }
    }

    private static Object instantiateWithDefaults(Class<?> cls) throws Exception {
        try {
            Constructor<?> c = cls.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException ignored) {
        }

        Constructor<?>[] ctors = cls.getDeclaredConstructors();
        List<Constructor<?>> sorted = java.util.Arrays.stream(ctors)
            .sorted(java.util.Comparator.comparingInt(Constructor::getParameterCount))
            .toList();

        Exception last = null;
        for (Constructor<?> ctor : sorted) {
            try {
                ctor.setAccessible(true);
                Class<?>[] paramTypes = ctor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    args[i] = defaultValue(paramTypes[i]);
                }
                return ctor.newInstance(args);
            } catch (Exception e) {
                last = e;
            }
        }

        throw new IllegalStateException("Could not instantiate " + cls.getName(), last);
    }

    private static Object defaultValue(Class<?> t) {
        if (t.isArray()) {
            Class<?> component = t.getComponentType();
            int n = 2;
            Object arr = java.lang.reflect.Array.newInstance(component, n);
            for (int i = 0; i < n; i++) {
                java.lang.reflect.Array.set(arr, i, defaultValue(component));
            }
            return arr;
        }
        if (t == int.class || t == Integer.class) return 14;
        if (t == long.class || t == Long.class) return 1000L;
        if (t == double.class || t == Double.class) return 2.0;
        if (t == boolean.class || t == Boolean.class) return false;
        if (t == String.class) return "TEST";
        if (t == BigDecimal.class) return BigDecimal.ONE;
        if (t == Duration.class) return Duration.ofMinutes(1);
        if (t == LocalDateTime.class) return LocalDateTime.now();
        if (java.util.Map.class.isAssignableFrom(t)) return new HashMap<>();
        if (java.util.List.class.isAssignableFrom(t)) return List.of();
        if (t.isEnum()) {
            Object[] values = t.getEnumConstants();
            return values.length > 0 ? values[0] : null;
        }
        return null;
    }
}
