package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class BidAskSpreadStrategyTest {

    @Test
    void execute_wideSpread_buyAndSellBranches() {
        BidAskSpreadStrategy strategy = new BidAskSpreadStrategy(0.01, 0.005);
        Portfolio portfolio = Portfolio.builder().positions(new HashMap<>()).build();

        // Big alternating moves to ensure impliedSpread is large.
        double[] closes = {100, 110, 100, 110, 100, 110, 100, 110, 100, 110};
        for (int i = 0; i < closes.length; i++) {
            strategy.execute(portfolio, md("TEST", closes[i], LocalDateTime.of(2024, 1, 1, 9, 0).plusMinutes(i)));
        }

        TradeSignal buy = strategy.execute(portfolio, md("TEST", 70, LocalDateTime.of(2024, 1, 1, 9, 11)));
        assertNotNull(buy);

        portfolio.setPosition("TEST", 1);
        TradeSignal sell = strategy.execute(portfolio, md("TEST", 120, LocalDateTime.of(2024, 1, 1, 9, 12)));
        assertNotNull(sell);
    }

    @Test
    void execute_meanReversionBranch_andHelpers() {
        BidAskSpreadStrategy strategy = new BidAskSpreadStrategy(0.01, 0.05);
        Portfolio portfolio = Portfolio.builder().positions(new HashMap<>()).build();

        // Construct returns with positive covariance (cov >= 0) so impliedSpread == normalSpread,
        // then a sufficiently large down move to trigger mean reversion long.
        double[] closes = {100.00, 101.00, 102.01, 103.03, 104.06, 105.10, 106.15, 107.21, 108.29, 105.04};
        for (int i = 0; i < closes.length; i++) {
            strategy.execute(portfolio, md("TEST", closes[i], LocalDateTime.of(2024, 1, 2, 9, 0).plusMinutes(i)));
        }

        TradeSignal s = strategy.execute(portfolio, md("TEST", 105.04, LocalDateTime.of(2024, 1, 2, 9, 10)));
        assertNotNull(s);
        assertNotNull(s.getDirection());

        // Cover priceHistory eviction
        for (int i = 0; i < 31; i++) {
            strategy.execute(portfolio, md("TEST", 100 + i, LocalDateTime.of(2024, 1, 3, 9, 0).plusMinutes(i)));
        }

        assertNotNull(strategy.getName());
        assertNotNull(strategy.getType());

        strategy.reset();
        assertNotNull(strategy.execute(portfolio, md("TEST", 100, LocalDateTime.of(2024, 1, 3, 10, 0))));
    }

    private static MarketData md(String symbol, double close, LocalDateTime ts) {
        return MarketData.builder()
            .symbol(symbol)
            .timestamp(ts)
            .timeframe(MarketData.Timeframe.MIN_1)
            .open(close)
            .high(close)
            .low(close)
            .close(close)
            .volume(1000L)
            .build();
    }
}
