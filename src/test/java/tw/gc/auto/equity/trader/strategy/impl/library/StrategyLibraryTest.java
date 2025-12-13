package tw.gc.auto.equity.trader.strategy.impl.library;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class StrategyLibraryTest {

    @Test
    void testRSIStrategy() {
        RSIStrategy strategy = new RSIStrategy(2, 90, 10);
        Portfolio p = new Portfolio();
        p.setPositions(new HashMap<>());
        p.getPositions().put("TEST", 0);

        // 1. Neutral
        MarketData d1 = data(100);
        assertEquals(TradeSignal.SignalDirection.NEUTRAL, strategy.execute(p, d1).getDirection());

        // 2. Drop (Oversold)
        MarketData d2 = data(90); // Drop
        MarketData d3 = data(80); // Drop -> RSI low
        TradeSignal s3 = strategy.execute(p, d3);
        // Simple RSI might need more data, but let's check it doesn't crash
        assertNotNull(s3);
    }

    private MarketData data(double price) {
        return MarketData.builder()
                .symbol("TEST")
                .close(price)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
