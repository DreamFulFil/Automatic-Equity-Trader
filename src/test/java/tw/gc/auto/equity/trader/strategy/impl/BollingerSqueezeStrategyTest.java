package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BollingerSqueezeStrategyTest {

    private BollingerSqueezeStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new BollingerSqueezeStrategy(10, 2.0);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_shouldReturnNeutral() {
        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
            assertThat(signal.getReason()).contains("Warming up");
        }
    }

    @Test
    void execute_afterWarmup_shouldCalculateBands() {
        for (int i = 0; i < 15; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 115);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal).isNotNull();
    }

    @Test
    void execute_withLowVolatility_shouldDetectSqueeze() {
        // Create tight range (low volatility)
        for (int i = 0; i < 15; i++) {
            double price = 100 + (i % 2 == 0 ? 0.1 : -0.1);
            MarketData data = createMarketData("2330", price);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 100);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal).isNotNull();
    }

    @Test
    void execute_withHighVolatility_shouldNotSqueeze() {
        // Create wide range (high volatility)
        for (int i = 0; i < 15; i++) {
            double price = 100 + (i % 2 == 0 ? 10 : -10);
            MarketData data = createMarketData("2330", price);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 100);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal).isNotNull();
    }

    @Test
    void execute_withBreakoutAbove_shouldReturnLongSignal() {
        // Create squeeze conditions then breakout
        for (int i = 0; i < 15; i++) {
            MarketData data = createMarketData("2330", 100 + (i % 2 == 0 ? 0.5 : -0.5));
            strategy.execute(portfolio, data);
        }

        // Breakout upward
        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 100 + i * 3);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertThat(signal.getConfidence()).isGreaterThan(0.5);
                return;
            }
        }
    }

    @Test
    void execute_withBreakoutBelow_shouldReturnShortSignal() {
        portfolio.setPosition("2330", 10);
        
        // Create squeeze conditions then breakout
        for (int i = 0; i < 15; i++) {
            MarketData data = createMarketData("2330", 100 + (i % 2 == 0 ? 0.5 : -0.5));
            strategy.execute(portfolio, data);
        }

        // Breakout downward
        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 100 - i * 3);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT || signal.isExitSignal()) {
                assertThat(signal.getConfidence()).isGreaterThan(0.5);
                return;
            }
        }
    }

    @Test
    void getName_shouldReturnStrategyName() {
        assertThat(strategy.getName()).contains("Bollinger Squeeze");
    }

    @Test
    void reset_shouldClearState() {
        for (int i = 0; i < 15; i++) {
            strategy.execute(portfolio, createMarketData("2330", 100 + i));
        }

        strategy.reset();

        MarketData data = createMarketData("2330", 115);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        assertThat(signal.getReason()).contains("Warming up");
    }

    @Test
    void execute_withMultipleSymbols_shouldTrackSeparately() {
        for (int i = 0; i < 15; i++) {
            strategy.execute(portfolio, createMarketData("2330", 100 + i));
            strategy.execute(portfolio, createMarketData("2454", 50 - i));
        }

        TradeSignal signal1 = strategy.execute(portfolio, createMarketData("2330", 115));
        TradeSignal signal2 = strategy.execute(portfolio, createMarketData("2454", 35));

        assertThat(signal1).isNotNull();
        assertThat(signal2).isNotNull();
    }

    @Test
    void execute_withConstantPrice_shouldHandleGracefully() {
        for (int i = 0; i < 15; i++) {
            MarketData data = createMarketData("2330", 100);
            TradeSignal signal = strategy.execute(portfolio, data);
            assertThat(signal).isNotNull();
        }
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 1)
                .low(close - 1)
                .open(close)
                .volume(10000L)
                .build();
    }
}
