package tw.gc.auto.equity.trader.entities;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveShadowSelectionTest {

    @Test
    void builder_defaults_and_onUpdate_updatesTimestamp() throws Exception {
        ActiveShadowSelection s = ActiveShadowSelection.builder()
                .rankPosition(1)
                .symbol("TST")
                .stockName("Test Co")
                .strategyName("StrategyX")
                .source(ActiveShadowSelection.SelectionSource.BACKTEST)
                .build();

        assertThat(s.getIsActive()).isFalse();
        assertThat(s.getSelectedAt()).isNotNull();
        assertThat(s.getUpdatedAt()).isNotNull();

        LocalDateTime before = s.getUpdatedAt();
        // ensure time advances
        Thread.sleep(5);
        s.onUpdate();
        assertThat(s.getUpdatedAt()).isAfter(before);
    }
}
