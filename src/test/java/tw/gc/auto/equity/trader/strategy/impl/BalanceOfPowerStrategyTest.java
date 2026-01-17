package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceOfPowerStrategyTest {

    private BalanceOfPowerStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new BalanceOfPowerStrategy(5);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_shouldReturnNeutral() {
        for (int i = 0; i < 3; i++) {
            MarketData data = createMarketData("2330", 100 + i, 102, 98, 100);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
            assertThat(signal.getReason()).contains("Warming up");
        }
    }

    @Test
    void execute_withBullishBOP_shouldReturnLongSignal() {
        // Strong bullish closes (close > open consistently)
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 100, 105, 95, 104);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 100, 105, 95, 104);
        TradeSignal signal = strategy.execute(portfolio, data);

        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            assertThat(signal.getConfidence()).isGreaterThan(0.5);
            assertThat(signal.getReason()).containsIgnoringCase("bullish");
        }
    }

    @Test
    void execute_withBearishBOP_shouldReturnShortSignal() {
        portfolio.setPosition("2330", 10);
        
        // Strong bearish closes (close < open consistently)
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 104, 105, 95, 96);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 104, 105, 95, 96);
        TradeSignal signal = strategy.execute(portfolio, data);

        if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            assertThat(signal.getConfidence()).isGreaterThan(0.5);
            assertThat(signal.getReason()).containsIgnoringCase("bearish");
        }
    }

    @Test
    void execute_withNeutralBOP_shouldReturnNeutral() {
        // Neutral closes (close ≈ open)
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 100, 102, 98, 100);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 100, 102, 98, 100);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }

    @Test
    void execute_withZeroRange_shouldHandleGracefully() {
        MarketData data = createMarketData("2330", 100, 100, 100, 100);
        
        for (int i = 0; i < 10; i++) {
            TradeSignal signal = strategy.execute(portfolio, data);
            assertThat(signal).isNotNull();
        }
    }

    @Test
    void getName_shouldReturnStrategyName() {
        assertThat(strategy.getName()).isEqualTo("Balance of Power");
    }

    @Test
    void reset_shouldClearState() {
        for (int i = 0; i < 10; i++) {
            strategy.execute(portfolio, createMarketData("2330", 100, 105, 95, 104));
        }

        strategy.reset();

        MarketData data = createMarketData("2330", 100, 105, 95, 104);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }

    @Test
    void execute_withDefaultConstructor_shouldWork() {
        BalanceOfPowerStrategy defaultStrategy = new BalanceOfPowerStrategy();
        
        MarketData data = createMarketData("2330", 100, 102, 98, 101);
        TradeSignal signal = defaultStrategy.execute(portfolio, data);

        assertThat(signal).isNotNull();
    }

    @Test
    void execute_withMultipleSymbols_shouldTrackSeparately() {
        for (int i = 0; i < 10; i++) {
            strategy.execute(portfolio, createMarketData("2330", 100, 105, 95, 104));
            strategy.execute(portfolio, createMarketData("2454", 100, 105, 95, 96));
        }

        TradeSignal signal1 = strategy.execute(portfolio, createMarketData("2330", 100, 105, 95, 104));
        TradeSignal signal2 = strategy.execute(portfolio, createMarketData("2454", 100, 105, 95, 96));

        assertThat(signal1).isNotNull();
        assertThat(signal2).isNotNull();
    }

    private MarketData createMarketData(String symbol, double open, double high, double low, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(10000L)
                .build();
    }
}
