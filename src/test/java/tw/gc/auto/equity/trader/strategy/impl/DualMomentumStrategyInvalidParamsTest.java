package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

class DualMomentumStrategyInvalidParamsTest {

    @Test
    void execute_returnsNeutralForInvalidPeriods() {
        DualMomentumStrategy s = new DualMomentumStrategy(0, 1, 0.01);
        Portfolio p = Portfolio.builder().positions(new HashMap<>()).build();
        MarketData md = MarketData.builder().symbol("TEST").close(100).build();

        TradeSignal sig = s.execute(p, md);
        assertThat(sig.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        assertThat(sig.getReason()).contains("Invalid momentum periods");
    }
}
