package tw.gc.auto.equity.trader.strategy.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for all 10 trading strategies
 * Ensures all strategies implement the interface correctly
 */
class AllStrategiesTest {
    
    private Portfolio portfolio;
    private MarketData marketData;
    
    @BeforeEach
    void setUp() {
        Map<String, Integer> positions = new HashMap<>();
        positions.put("AUTO_EQUITY_TRADER", 0);
        
        portfolio = Portfolio.builder()
                .positions(positions)
                .equity(100000.0)
                .tradingMode("futures")
                .tradingQuantity(1)
                .build();
        
        marketData = MarketData.builder()
                .symbol("AUTO_EQUITY_TRADER")
                .close(22000.0)
                .volume(1000L)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    @Test
    void testDCAStrategy() {
        IStrategy strategy = new DCAStrategy();
        assertStrategyBasics(strategy, "Dollar-Cost Averaging (DCA)", StrategyType.LONG_TERM);
    }
    
    @Test
    void testMovingAverageCrossoverStrategy() {
        IStrategy strategy = new MovingAverageCrossoverStrategy();
        assertStrategyBasics(strategy, "Moving Average Crossover", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testBollingerBandStrategy() {
        IStrategy strategy = new BollingerBandStrategy();
        assertStrategyBasics(strategy, "Bollinger Band Mean Reversion", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testVWAPExecutionStrategy() {
        IStrategy strategy = new VWAPExecutionStrategy(10, 30);
        assertStrategyBasics(strategy, "VWAP Execution Algorithm", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testAutomaticRebalancingStrategy() {
        IStrategy strategy = new AutomaticRebalancingStrategy(5);
        assertStrategyBasics(strategy, "Automatic Rebalancing", StrategyType.LONG_TERM);
    }
    
    @Test
    void testDividendReinvestmentStrategy() {
        IStrategy strategy = new DividendReinvestmentStrategy();
        assertStrategyBasics(strategy, "Dividend Reinvestment (DRIP)", StrategyType.LONG_TERM);
    }
    
    @Test
    void testTaxLossHarvestingStrategy() {
        IStrategy strategy = new TaxLossHarvestingStrategy();
        assertStrategyBasics(strategy, "Tax-Loss Harvesting", StrategyType.LONG_TERM);
    }
    
    @Test
    void testMomentumTradingStrategy() {
        IStrategy strategy = new MomentumTradingStrategy();
        assertStrategyBasics(strategy, "Momentum Trading", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testArbitragePairsTradingStrategy() {
        IStrategy strategy = new ArbitragePairsTradingStrategy();
        assertStrategyBasics(strategy, "Arbitrage / Pairs Trading", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testNewsSentimentStrategy() {
        IStrategy strategy = new NewsSentimentStrategy();
        assertStrategyBasics(strategy, "News / Sentiment-Based Trading", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testTWAPExecutionStrategy() {
        IStrategy strategy = new TWAPExecutionStrategy(10, 30);
        assertStrategyBasics(strategy, "TWAP Execution Algorithm", StrategyType.SHORT_TERM);
    }
    
    @Test
    void testAllStrategiesReturnSignal() {
        IStrategy[] strategies = {
            new DCAStrategy(),
            new MovingAverageCrossoverStrategy(),
            new BollingerBandStrategy(),
            new VWAPExecutionStrategy(10, 30),
            new AutomaticRebalancingStrategy(5),
            new DividendReinvestmentStrategy(),
            new TaxLossHarvestingStrategy(),
            new MomentumTradingStrategy(),
            new ArbitragePairsTradingStrategy(),
            new NewsSentimentStrategy(),
            new TWAPExecutionStrategy(10, 30)
        };
        
        for (IStrategy strategy : strategies) {
            TradeSignal signal = strategy.execute(portfolio, marketData);
            assertNotNull(signal, strategy.getName() + " should return a signal");
            assertNotNull(signal.getDirection(), strategy.getName() + " signal should have direction");
            assertNotNull(signal.getReason(), strategy.getName() + " signal should have reason");
        }
    }
    
    private void assertStrategyBasics(IStrategy strategy, String expectedName, StrategyType expectedType) {
        assertEquals(expectedName, strategy.getName());
        assertEquals(expectedType, strategy.getType());
        
        TradeSignal signal = strategy.execute(portfolio, marketData);
        assertNotNull(signal);
        assertNotNull(signal.getDirection());
        assertNotNull(signal.getReason());
        
        strategy.reset(); // Should not throw
    }
}
