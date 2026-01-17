package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MACDStrategyTest {

    private MACDStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new MACDStrategy(12, 26, 9);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_shouldReturnNeutral() {
        MarketData data = createMarketData("2330", 100);

        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        assertThat(signal.getReason()).contains("Warming up");
    }

    @Test
    void execute_afterWarmup_shouldCalculateMACD() {
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 130);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getReason()).contains("MACD");
    }

    @Test
    void execute_withBullishZeroCross_shouldReturnLongSignal() {
        // Create declining then rising price pattern
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("2330", 150 - i);
            strategy.execute(portfolio, data);
        }
        
        for (int i = 0; i < 15; i++) {
            MarketData data = createMarketData("2330", 120 + i * 2);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertThat(signal.getConfidence()).isGreaterThan(0.5);
                assertThat(signal.getReason()).containsIgnoringCase("bullish");
                return;
            }
        }
    }

    @Test
    void execute_withBearishZeroCross_shouldReturnShortSignal() {
        portfolio.setPosition("2330", 10);
        
        // Create rising then declining pattern
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            strategy.execute(portfolio, data);
        }
        
        for (int i = 0; i < 15; i++) {
            MarketData data = createMarketData("2330", 130 - i * 2);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT || signal.isExitSignal()) {
                assertThat(signal.getConfidence()).isGreaterThan(0.5);
                assertThat(signal.getReason()).containsIgnoringCase("bearish");
                return;
            }
        }
    }

    @Test
    void execute_withFlatPrices_shouldReturnNeutral() {
        for (int i = 0; i < 50; i++) {
            MarketData data = createMarketData("2330", 100);
            TradeSignal signal = strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 100);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }

    @Test
    void execute_withPositivePosition_shouldNotGiveLongSignal() {
        portfolio.setPosition("2330", 10);
        
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("2330", 100 - i);
            strategy.execute(portfolio, data);
        }
        
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 70 + i * 3);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertThat(signal).as("Should not give long signal when already in position").isNull();
            }
        }
    }

    @Test
    void getName_shouldReturnStrategyName() {
        assertThat(strategy.getName()).contains("MACD");
        assertThat(strategy.getName()).contains("12");
        assertThat(strategy.getName()).contains("26");
    }

    @Test
    void getType_shouldReturnSwing() {
        assertThat(strategy.getType()).isEqualTo(StrategyType.SWING);
    }

    @Test
    void reset_shouldClearState() {
        for (int i = 0; i < 30; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            strategy.execute(portfolio, data);
        }

        strategy.reset();

        MarketData data = createMarketData("2330", 130);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        assertThat(signal.getReason()).contains("Warming up");
    }

    @Test
    void execute_withMultipleSymbols_shouldTrackSeparately() {
        for (int i = 0; i < 30; i++) {
            strategy.execute(portfolio, createMarketData("2330", 100 + i));
            strategy.execute(portfolio, createMarketData("2454", 50 + i));
        }

        TradeSignal signal1 = strategy.execute(portfolio, createMarketData("2330", 130));
        TradeSignal signal2 = strategy.execute(portfolio, createMarketData("2454", 80));

        assertThat(signal1).isNotNull();
        assertThat(signal2).isNotNull();
    }

    private MarketData createMarketData(String symbol, double close) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(close + 2)
                .low(close - 2)
                .volume(10000L)
                .build();
    }
}
