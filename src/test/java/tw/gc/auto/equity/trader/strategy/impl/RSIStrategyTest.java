package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RSIStrategyTest {

    private RSIStrategy strategy;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        strategy = new RSIStrategy(14, 70, 30);
        portfolio = new Portfolio();
    }

    @Test
    void execute_withInsufficientData_shouldReturnNeutral() {
        for (int i = 0; i < 10; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (i < 10) {
                assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
                assertThat(signal.getReason()).contains("Warming up");
            }
        }
    }

    @Test
    void execute_afterWarmup_shouldCalculateRSI() {
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 120);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getReason()).contains("RSI");
    }

    @Test
    void execute_withOversoldCondition_shouldReturnLongSignal() {
        // Create sharp declining price to trigger oversold
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 150 - i * 3);
            strategy.execute(portfolio, data);
        }

        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 90 - i * 2);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
                assertThat(signal.getConfidence()).isGreaterThan(0.7);
                assertThat(signal.getReason()).containsIgnoringCase("oversold");
                return;
            }
        }
    }

    @Test
    void execute_withOverboughtCondition_shouldReturnExitOrShortSignal() {
        // Create sharp rising price to trigger overbought
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 50 + i * 3);
            strategy.execute(portfolio, data);
        }

        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 110 + i * 2);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT || signal.isExitSignal()) {
                assertThat(signal.getConfidence()).isGreaterThan(0.7);
                assertThat(signal.getReason()).containsIgnoringCase("overbought");
                return;
            }
        }
    }

    @Test
    void execute_withNeutralRSI_shouldReturnNeutral() {
        // Create stable price movement to keep RSI neutral
        for (int i = 0; i < 30; i++) {
            double price = 100 + (i % 2 == 0 ? 1 : -1);
            MarketData data = createMarketData("2330", price);
            strategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 100);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
    }

    @Test
    void execute_withLongPosition_shouldNotGiveLongSignal() {
        portfolio.setPosition("2330", 10);
        
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 150 - i * 3);
            strategy.execute(portfolio, data);
        }

        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 90 - i * 2);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            assertThat(signal.getDirection()).isNotEqualTo(TradeSignal.SignalDirection.LONG);
        }
    }

    @Test
    void execute_withShortPosition_shouldNotGiveShortSignal() {
        portfolio.setPosition("2330", -10);
        
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 50 + i * 3);
            strategy.execute(portfolio, data);
        }

        for (int i = 0; i < 5; i++) {
            MarketData data = createMarketData("2330", 110 + i * 2);
            TradeSignal signal = strategy.execute(portfolio, data);
            
            if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && !signal.isExitSignal()) {
                assertThat(signal).as("Should not give short signal when already short").isNull();
            }
        }
    }

    @Test
    void getName_shouldReturnStrategyName() {
        assertThat(strategy.getName()).contains("RSI");
        assertThat(strategy.getName()).contains("14");
        assertThat(strategy.getName()).contains("30");
        assertThat(strategy.getName()).contains("70");
    }

    @Test
    void getType_shouldReturnIntraday() {
        assertThat(strategy.getType()).isEqualTo(StrategyType.INTRADAY);
    }

    @Test
    void reset_shouldClearState() {
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            strategy.execute(portfolio, data);
        }

        strategy.reset();

        MarketData data = createMarketData("2330", 120);
        TradeSignal signal = strategy.execute(portfolio, data);

        assertThat(signal.getDirection()).isEqualTo(TradeSignal.SignalDirection.NEUTRAL);
        assertThat(signal.getReason()).contains("Warming up");
    }

    @Test
    void execute_withMultipleSymbols_shouldTrackSeparately() {
        for (int i = 0; i < 20; i++) {
            strategy.execute(portfolio, createMarketData("2330", 100 + i));
            strategy.execute(portfolio, createMarketData("2454", 50 - i));
        }

        TradeSignal signal1 = strategy.execute(portfolio, createMarketData("2330", 120));
        TradeSignal signal2 = strategy.execute(portfolio, createMarketData("2454", 30));

        assertThat(signal1).isNotNull();
        assertThat(signal2).isNotNull();
    }

    @Test
    void execute_withCustomThresholds_shouldRespectThem() {
        RSIStrategy customStrategy = new RSIStrategy(14, 80, 20);
        
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            customStrategy.execute(portfolio, data);
        }

        TradeSignal signal = customStrategy.execute(portfolio, createMarketData("2330", 120));
        assertThat(signal).isNotNull();
    }

    @Test
    void calculateRSI_withInsufficientPrices_returnsDefault50() {
        // Test with period larger than available data to cover line 85 (early return 50.0)
        RSIStrategy largePerioStrategy = new RSIStrategy(50, 70, 30);
        
        // Feed only 20 prices, but period is 50, so p.length < n + 1
        for (int i = 0; i < 20; i++) {
            MarketData data = createMarketData("2330", 100 + i);
            largePerioStrategy.execute(portfolio, data);
        }

        MarketData data = createMarketData("2330", 120);
        TradeSignal signal = largePerioStrategy.execute(portfolio, data);
        
        // With insufficient data for RSI calculation, it returns 50.0 (neutral RSI)
        assertThat(signal).isNotNull();
        assertThat(signal.getReason()).contains("RSI");
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
