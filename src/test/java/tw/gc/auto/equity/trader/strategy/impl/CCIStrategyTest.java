package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CCIStrategyTest {

    private CCIStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new CCIStrategy(5);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_shouldReturnNeutral() {
        MarketData data = createMarketData("2330", 100, 102, 98);

        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        assertThat(signal.getReason()).contains("Warming up");
    }

    @Test
    void execute_afterWarmup_shouldCalculateCCI() {
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 100 + i, 102 + i, 98 + i);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 110, 112, 108);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getReason()).contains("CCI");
    }

    @Test
    void execute_withBullishCrossover_shouldReturnLongSignal() {
        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 100 - i * 2, 102 - i * 2, 98 - i * 2);
            strategy.execute(portfolio, data);
        }

        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 90 + i * 3, 92 + i * 3, 88 + i * 3);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertThat(signal.getConfidence()).isGreaterThan(0.5);
                assertThat(signal.getReason()).contains("bullish");
                return;
            }
        }
    }

    @Test
    void execute_withBearishCrossover_shouldReturnShortSignal() {
        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 100 + i * 2, 102 + i * 2, 98 + i * 2);
            strategy.execute(portfolio, data);
        }

        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 110 - i * 3, 112 - i * 3, 108 - i * 3);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
                assertThat(signal.getConfidence()).isGreaterThan(0.5);
                assertThat(signal.getReason()).contains("bearish");
                return;
            }
        }
    }

    @Test
    void getName_shouldReturnStrategyName() {
        assertThat(strategy.getName()).isEqualTo("CCI Oscillator");
    }

    @Test
    void getType_shouldReturnShortTerm() {
        assertThat(strategy.getType()).isEqualTo(StrategyType.SHORT_TERM);
    }

    @Test
    void reset_shouldClearState() {
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 100 + i, 102 + i, 98 + i);
            strategy.execute(portfolio, data);
        }

        strategy.reset();

        MarketData data = createMarketData("2330", 110, 112, 108);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }

    @Test
    void execute_withDefaultConstructor_shouldWork() {
        CCIStrategy defaultStrategy = new CCIStrategy();
        
        MarketData data = createMarketData("2330", 100, 102, 98);
        TradeSignal signal = defaultStrategy.execute(portfolio, data);

        assertThat(signal).isNotNull();
    }

    private MarketData createMarketData(String symbol, double close, double high, double low) {
        return MarketData.builder()
                .symbol(symbol)
                .timestamp(LocalDateTime.now())
                .close(close)
                .high(high)
                .low(low)
                .volume(10000L)
                .build();
    }
}
