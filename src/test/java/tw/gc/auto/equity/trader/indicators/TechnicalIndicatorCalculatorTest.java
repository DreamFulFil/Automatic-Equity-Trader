package tw.gc.auto.equity.trader.indicators;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TechnicalIndicatorCalculatorTest {

    @Test
    void testSimpleMovingAverage() {
        var prices = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        var result = TechnicalIndicatorCalculator.simpleMovingAverage(prices, 3);
        assertTrue(result.isPresent());
        assertEquals(4.0, result.get(), 1e-6);
    }

    @Test
    void testSimpleMovingAverageInsufficientData() {
        var prices = List.of(1.0, 2.0);
        var result = TechnicalIndicatorCalculator.simpleMovingAverage(prices, 3);
        assertTrue(result.isEmpty());
    }

    @Test
    void testExponentialMovingAverage() {
        var prices = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        var result = TechnicalIndicatorCalculator.exponentialMovingAverage(prices, 3);
        assertTrue(result.isPresent());
        assertEquals(4.0, result.get(), 1e-6);
    }

    @Test
    void testRsiAllGains() {
        var prices = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        var result = TechnicalIndicatorCalculator.relativeStrengthIndex(prices, 5);
        assertTrue(result.isPresent());
        assertEquals(100.0, result.get(), 1e-6);
    }

    @Test
    void testRsiAllLosses() {
        var prices = List.of(6.0, 5.0, 4.0, 3.0, 2.0, 1.0);
        var result = TechnicalIndicatorCalculator.relativeStrengthIndex(prices, 5);
        assertTrue(result.isPresent());
        assertEquals(0.0, result.get(), 1e-6);
    }

    @Test
    void testMacdConstantSeries() {
        var prices = Collections.nCopies(40, 10.0);
        var result = TechnicalIndicatorCalculator.macd(prices, 12, 26, 9);
        assertTrue(result.isPresent());
        assertEquals(0.0, result.get().macdLine(), 1e-6);
        assertEquals(0.0, result.get().signalLine(), 1e-6);
        assertEquals(0.0, result.get().histogram(), 1e-6);
    }

    @Test
    void testBollingerBands() {
        var prices = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        var result = TechnicalIndicatorCalculator.bollingerBands(prices, 5, 2.0);
        assertTrue(result.isPresent());
        var bands = result.get();
        assertEquals(3.0, bands.middle(), 1e-6);
        assertEquals(3.0 + 2.0 * Math.sqrt(2.0), bands.upper(), 1e-6);
        assertEquals(3.0 - 2.0 * Math.sqrt(2.0), bands.lower(), 1e-6);
    }

    @Test
    void testOnBalanceVolume() {
        var closes = List.of(10.0, 11.0, 10.0, 12.0);
        var volumes = List.of(100L, 200L, 150L, 300L);
        var result = TechnicalIndicatorCalculator.onBalanceVolume(closes, volumes);
        assertTrue(result.isPresent());
        assertEquals(350L, result.get());
    }

    @Test
    void testArimaForecastTrend() {
        var values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);
        var result = TechnicalIndicatorCalculator.arimaForecast(values, 1, 1, 0, 2);
        assertTrue(result.isPresent());
        assertEquals(2, result.get().forecast().size());
        assertEquals(11.0, result.get().forecast().get(0), 1e-6);
        assertEquals(12.0, result.get().forecast().get(1), 1e-6);
    }

    @Test
    void testArimaUnsupportedQThrows() {
        var values = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        assertThrows(IllegalArgumentException.class,
                () -> TechnicalIndicatorCalculator.arimaForecast(values, 1, 0, 1, 1));
    }

    @Test
    void testOnBalanceVolumeMismatchedInputThrows() {
        var closes = List.of(10.0, 11.0);
        var volumes = List.of(100L);
        assertThrows(IllegalArgumentException.class,
                () -> TechnicalIndicatorCalculator.onBalanceVolume(closes, volumes));
    }

    @Test
    void testNullInputThrows() {
        assertThrows(NullPointerException.class,
                () -> TechnicalIndicatorCalculator.simpleMovingAverage(null, 3));
    }
}
