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

class FuturesStrategyFactoryTest {

    @Test
    void createStrategies_instantiatesAndExecutesAllStrategies() {
        FuturesStrategyFactory factory = new FuturesStrategyFactory();
        List<IStrategy> strategies = factory.createStrategies();

        assertFalse(strategies.isEmpty());

        for (IStrategy s : strategies) {
            Portfolio p = Portfolio.builder()
                .positions(new HashMap<>())
                .entryPrices(new HashMap<>())
                .equity(100000.0)
                .availableMargin(100000.0)
                .tradingMode("futures")
                .tradingQuantity(1)
                .build();

            for (int i = 0; i < 30; i++) {
                double price = 22000.0 + Math.sin(i / 3.0) * 10.0 + i;
                MarketData md = MarketData.builder()
                    .symbol("MTXF")
                    .name("TEST")
                    .timestamp(LocalDateTime.now().minusMinutes(30 - i))
                    .timeframe(MarketData.Timeframe.MIN_1)
                    .open(price)
                    .high(price + 5)
                    .low(price - 5)
                    .close(price)
                    .volume(2000L + i)
                    .assetType(MarketData.AssetType.FUTURE)
                    .build();

                TradeSignal sig = assertDoesNotThrow(() -> s.execute(p, md), s.getClass().getSimpleName() + " execute() should not throw");
                assertNotNull(sig);

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
}
