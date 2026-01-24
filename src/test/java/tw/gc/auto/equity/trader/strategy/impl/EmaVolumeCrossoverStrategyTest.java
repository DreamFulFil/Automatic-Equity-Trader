package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmaVolumeCrossoverStrategyTest {

    @Test
    void longSignal_onBullishCrossoverWithVolume() {
        EmaVolumeCrossoverStrategy strategy = new EmaVolumeCrossoverStrategy(3, 6, 3, 1.2);
        Portfolio portfolio = portfolio("TEST", 0);

        TradeSignal last = null;
        double[] prices = {100, 99, 98, 97, 96, 95, 96, 97, 98, 99};
        long[] volumes = {100, 100, 100, 100, 100, 100, 100, 100, 200, 200};

        for (int i = 0; i < prices.length; i++) {
            last = strategy.execute(portfolio, market("TEST", prices[i], volumes[i]));
            if (last.getDirection() == TradeSignal.SignalDirection.LONG) {
                break;
            }
        }

        assertNotNull(last);
        assertEquals(TradeSignal.SignalDirection.LONG, last.getDirection(), "Expected LONG on bullish crossover with volume");
    }

    @Test
    void exitSignal_onBearishCrossoverWhenLong() {
        EmaVolumeCrossoverStrategy strategy = new EmaVolumeCrossoverStrategy(3, 6, 3, 1.0);
        Portfolio portfolio = portfolio("TEST", 1);

        // Establish bullish state first
        double[] upPrices = {100, 101, 102, 103, 104, 105, 106};
        for (double price : upPrices) {
            strategy.execute(portfolio, market("TEST", price, 150));
        }

        TradeSignal last = null;
        double[] downPrices = {104, 102, 100, 98, 96, 94};
        for (double price : downPrices) {
            last = strategy.execute(portfolio, market("TEST", price, 150));
            if (last.isExitSignal()) {
                break;
            }
        }

        assertNotNull(last);
        assertTrue(last.isExitSignal(), "Expected exit signal on bearish crossover");
        assertEquals(TradeSignal.SignalDirection.SHORT, last.getDirection());
    }

    private Portfolio portfolio(String symbol, int position) {
        Map<String, Integer> positions = new HashMap<>();
        positions.put(symbol, position);
        return Portfolio.builder().positions(positions).build();
    }

    private MarketData market(String symbol, double close, long volume) {
        return MarketData.builder()
            .symbol(symbol)
            .timestamp(LocalDateTime.now())
            .open(close)
            .high(close + 1)
            .low(close - 1)
            .close(close)
            .volume(volume)
            .timeframe(MarketData.Timeframe.DAY_1)
            .build();
    }
}
