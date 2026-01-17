package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AcceleratingMomentumStrategyTest {

    @Test
    void warmup_returnsNeutral() {
        AcceleratingMomentumStrategy s = new AcceleratingMomentumStrategy(5, 3);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();

        MarketData md = MarketData.builder()
                .symbol("TST")
                .timestamp(LocalDateTime.now())
                .timeframe(MarketData.Timeframe.DAY_1)
                .open(100)
                .high(100)
                .low(100)
                .close(100)
                .volume(1L)
                .build();

        TradeSignal sig = s.execute(p, md);
        assertThat(sig.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }

    @Test
    void producesLong_whenMomentumAndAccelerationPositive() {
        AcceleratingMomentumStrategy s = new AcceleratingMomentumStrategy(2, 2);
        Map<String,Integer> pos = new HashMap<>();
        Portfolio p = Portfolio.builder().positions(pos).build();

        // feed 4 prices to produce two momentums and positive acceleration
        double[] prices = {100.0, 101.0, 105.0, 112.0};
        for (double price : prices) {
            MarketData md = MarketData.builder()
                    .symbol("TST")
                    .timestamp(LocalDateTime.now())
                    .timeframe(MarketData.Timeframe.DAY_1)
                    .open(price)
                    .high(price)
                    .low(price)
                    .close(price)
                    .volume(1L)
                    .build();
            TradeSignal sig = s.execute(p, md);
            // only check final signal
            if (price == prices[prices.length-1]) {
                assertThat(sig.getDirection()).isEqualTo(TradeSignal.SignalDirection.LONG);
                assertThat(sig.getConfidence()).isGreaterThan(0.5);
            }
        }
    }

    @Test
    void producesShort_whenMomentumAndAccelerationNegative() {
        AcceleratingMomentumStrategy s = new AcceleratingMomentumStrategy(2, 2);
        Map<String,Integer> pos = new HashMap<>();
        Portfolio p = Portfolio.builder().positions(pos).build();

        double[] prices = {120.0, 118.0, 115.0, 110.0};
        for (double price : prices) {
            MarketData md = MarketData.builder()
                    .symbol("TST")
                    .timestamp(LocalDateTime.now())
                    .timeframe(MarketData.Timeframe.DAY_1)
                    .open(price)
                    .high(price)
                    .low(price)
                    .close(price)
                    .volume(1L)
                    .build();
            TradeSignal sig = s.execute(p, md);
            if (price == prices[prices.length-1]) {
                assertThat(sig.getDirection()).isEqualTo(TradeSignal.SignalDirection.SHORT);
                assertThat(sig.getConfidence()).isGreaterThan(0.5);
            }
        }
    }
}
