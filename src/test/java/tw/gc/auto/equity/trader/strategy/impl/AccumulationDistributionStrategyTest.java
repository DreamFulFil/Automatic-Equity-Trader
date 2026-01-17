package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccumulationDistributionStrategyTest {

    @Test
    void initializes_then_accumulates_givesLong() {
        AccumulationDistributionStrategy s = new AccumulationDistributionStrategy();
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        MarketData first = MarketData.builder()
                .symbol("TST")
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(100).high(105).low(95).close(102).volume(1000L).build();

        // first call initializes
        TradeSignal t1 = s.execute(p, first);
        assertThat(t1.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);

        // second call with positive movement and volume accumulates -> LONG
        MarketData second = MarketData.builder()
                .symbol("TST")
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(102).high(108).low(100).close(107).volume(2000L).build();

        TradeSignal t2 = s.execute(p, second);
        assertThat(t2.getDirection()).isEqualTo(TradeSignal.SignalDirection.LONG);
    }

    @Test
    void distribution_then_downmove_givesShort() {
        AccumulationDistributionStrategy s = new AccumulationDistributionStrategy();
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        MarketData first = MarketData.builder()
                .symbol("TST")
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(150).high(155).low(145).close(152).volume(1000L).build();

        s.execute(p, first);

        MarketData second = MarketData.builder()
                .symbol("TST")
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(152).high(153).low(140).close(141).volume(3000L).build();

        TradeSignal t2 = s.execute(p, second);
        assertThat(t2.getDirection()).isEqualTo(TradeSignal.SignalDirection.SHORT);
    }
}
