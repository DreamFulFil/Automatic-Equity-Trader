package tw.gc.auto.equity.trader.strategy.factory;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockStrategyFactoryTest {

    @Test
    void createStrategies_instantiatesAndExecutesAllStrategies() {
        StockStrategyFactory factory = new StockStrategyFactory();
        List<IStrategy> strategies = factory.createStrategies();

        assertFalse(strategies.isEmpty());

        for (IStrategy s : strategies) {
            assertNotNull(s.getName());
            assertNotNull(s.getType());
            assertDoesNotThrow(s::reset, s.getClass().getSimpleName() + " reset() should not throw");

            Portfolio p = Portfolio.builder()
                .positions(new HashMap<>())
                .entryPrices(new HashMap<>())
                .equity(100000.0)
                .availableMargin(100000.0)
                .tradingMode("stock")
                .tradingQuantity(1)
                .build();

            for (int i = 0; i < 50; i++) {
                double price = 100.0 + (i * 0.2) + Math.sin(i / 4.0) * 2.0;
                MarketData md = marketData("2454.TW", price, 1000L + i, LocalDateTime.now().minusMinutes(50 - i));

                TradeSignal sig = assertDoesNotThrow(() -> s.execute(p, md), s.getClass().getSimpleName() + " execute() should not throw");
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
        }
    }

    private static MarketData marketData(String symbol, double price, long volume, LocalDateTime ts) {
        return MarketData.builder()
            .symbol(symbol)
            .name("TEST")
            .timestamp(ts)
            .timeframe(MarketData.Timeframe.MIN_1)
            .open(price)
            .high(price * 1.001)
            .low(price * 0.999)
            .close(price)
            .volume(volume)
            .assetType(MarketData.AssetType.STOCK)
            .build();
    }
}
