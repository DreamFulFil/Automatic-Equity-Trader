package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VWAPExecutionStrategyEdgeTest {

    @Test
    void execute_handlesNullDataAndNullSymbol() {
        VWAPExecutionStrategy s = new VWAPExecutionStrategy(10, 1);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        TradeSignal sig1 = s.execute(p, null);
        assertThat(sig1).isNotNull();
        assertThat(sig1.getDirection()).isNotNull();

        TradeSignal sig2 = s.execute(p, MarketData.builder().symbol(null).build());
        assertThat(sig2).isNotNull();
        assertThat(sig2.getDirection()).isNotNull();
    }

    @Test
    void execute_triggersWindowExpiredBranches() throws Exception {
        String symbol = "TEST";
        VWAPExecutionStrategy s = new VWAPExecutionStrategy(10, 1);
        Portfolio p = Portfolio.builder().positions(Map.of(symbol, 0)).build();

        // Force start time far enough in the past to expire the window.
        Field startTimeField = VWAPExecutionStrategy.class.getDeclaredField("startTime");
        startTimeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> startTime = (Map<String, LocalDateTime>) startTimeField.get(s);
        startTime.put(symbol, LocalDateTime.now().minusMinutes(10));

        // Case 1: executed < target => urgent longSignal
        TradeSignal sig1 = s.execute(p, MarketData.builder().symbol(symbol).close(100).volume(1).build());
        assertThat(sig1.getDirection()).isEqualTo(TradeSignal.SignalDirection.LONG);

        // Case 2: executed >= target => neutral
        Field executedVolumeField = VWAPExecutionStrategy.class.getDeclaredField("executedVolume");
        executedVolumeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> executed = (Map<String, Integer>) executedVolumeField.get(s);
        executed.put(symbol, 10);

        TradeSignal sig2 = s.execute(p, MarketData.builder().symbol(symbol).close(100).volume(1).build());
        assertThat(sig2.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }
}
