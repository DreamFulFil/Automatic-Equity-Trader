package tw.gc.auto.equity.trader.strategy;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTest {

    @Test
    void isFlat_coversNullEmptyAndNonZeroPositions() {
        Portfolio p1 = Portfolio.builder().positions(null).build();
        assertThat(p1.isFlat()).isTrue();

        Portfolio p2 = Portfolio.builder().positions(Map.of()).build();
        assertThat(p2.isFlat()).isTrue();

        Map<String, Integer> zeros = new HashMap<>();
        zeros.put("AAA", 0);
        zeros.put("BBB", 0);
        Portfolio p3 = Portfolio.builder().positions(zeros).build();
        assertThat(p3.isFlat()).isTrue();

        Map<String, Integer> nonZero = new HashMap<>();
        nonZero.put("AAA", 1);
        Portfolio p4 = Portfolio.builder().positions(nonZero).build();
        assertThat(p4.isFlat()).isFalse();
    }

    @Test
    void getPositionAndEntryHelpers_coverNullMapsAndDefaults() {
        Portfolio p = Portfolio.builder().positions(null).entryPrices(null).entryTimes(null).build();
        assertThat(p.getPosition("AAA")).isZero();
        assertThat(p.getEntryPrice("AAA")).isEqualTo(0.0);
        assertThat(p.getEntryTime("AAA")).isNull();

        p.setPosition("AAA", 2);
        p.setEntryPrice("AAA", 123.45);
        assertThat(p.getPosition("AAA")).isEqualTo(2);
        assertThat(p.getEntryPrice("AAA")).isEqualTo(123.45);

        LocalDateTime t = LocalDateTime.now();
        Portfolio p2 = Portfolio.builder().entryTimes(Map.of("AAA", t)).build();
        assertThat(p2.getEntryTime("AAA")).isEqualTo(t);
    }
}
