package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.RSIStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BacktestServiceTest {

    private BacktestService backtestService;
    private List<MarketData> historicalData;
    private List<IStrategy> strategies;

    @BeforeEach
    void setUp() {
        tw.gc.auto.equity.trader.repositories.BacktestResultRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.BacktestResultRepository.class);
        tw.gc.auto.equity.trader.repositories.MarketDataRepository mockMarketDataRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.MarketDataRepository.class);
        HistoryDataService mockHistoryService = 
            org.mockito.Mockito.mock(HistoryDataService.class);
        backtestService = new BacktestService(mockRepo, mockMarketDataRepo, mockHistoryService);
        strategies = new ArrayList<>();
        strategies.add(new RSIStrategy(14, 70, 30));

        // Create sample historical data
        historicalData = new ArrayList<>();
        double[] prices = {100.0, 102.0, 101.0, 105.0, 108.0, 107.0, 110.0, 112.0, 109.0, 115.0};
        
        for (int i = 0; i < prices.length; i++) {
            historicalData.add(MarketData.builder()
                    .symbol("2330.TW")
                    .timestamp(LocalDateTime.now().minusDays(prices.length - i))
                    .open(prices[i] - 0.5)
                    .high(prices[i] + 1.0)
                    .low(prices[i] - 1.0)
                    .close(prices[i])
                    .volume(1000000L)
                    .build());
        }
    }

    @Test
    void testRunBacktest_WithValidData() {
        double initialCapital = 80000.0;
        
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
                strategies, historicalData, initialCapital
        );

        assertNotNull(results);
        assertEquals(1, results.size());
        
        BacktestService.InMemoryBacktestResult result = results.get("RSI (14, 30/70)");
        assertNotNull(result);
        assertEquals("RSI (14, 30/70)", result.getStrategyName());
        assertEquals(initialCapital, result.getInitialCapital());
        assertTrue(result.getFinalEquity() > 0);
    }

    @Test
    void testBacktestResult_CalculateMetrics() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 80000.0);
        
        // Simulate some trades
        result.addTrade(1000.0);  // Win
        result.addTrade(-500.0);  // Loss
        result.addTrade(1500.0);  // Win
        
        // Track equity
        result.trackEquity(81000.0);
        result.trackEquity(80500.0);
        result.trackEquity(82000.0);
        
        result.setFinalEquity(82000.0);
        result.calculateMetrics();

        assertEquals(3, result.getTotalTrades());
        assertEquals(2, result.getWinningTrades());
        assertEquals(66.67, result.getWinRate(), 0.1);
        assertEquals(2000.0, result.getTotalPnL(), 0.01);
        assertEquals(2.5, result.getTotalReturnPercentage(), 0.01);
        assertTrue(result.getSharpeRatio() != 0.0);
        assertTrue(result.getMaxDrawdownPercentage() >= 0.0);
    }

    @Test
    void testBacktestResult_MaxDrawdownCalculation() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 100000.0);
        
        // Simulate equity curve with drawdown
        result.trackEquity(100000.0);
        result.trackEquity(110000.0);  // Peak
        result.trackEquity(105000.0);  // Drawdown
        result.trackEquity(100000.0);  // Max drawdown (10k from peak)
        result.trackEquity(115000.0);  // Recovery
        
        result.setFinalEquity(115000.0);
        result.calculateMetrics();

        // Max drawdown should be (110000 - 100000) / 110000 * 100 = 9.09%
        assertTrue(result.getMaxDrawdownPercentage() > 9.0);
        assertTrue(result.getMaxDrawdownPercentage() < 10.0);
    }

    @Test
    void testBacktestResult_SharpeRatioWithNoTrades() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 80000.0);
        
        result.setFinalEquity(80000.0);
        result.calculateMetrics();

        assertEquals(0, result.getTotalTrades());
        assertEquals(0.0, result.getSharpeRatio());
        assertEquals(0.0, result.getTotalReturnPercentage());
    }

    @Test
    void testBacktestResult_WinRateCalculation() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 80000.0);
        
        result.addTrade(100.0);
        result.addTrade(200.0);
        result.addTrade(-50.0);
        result.addTrade(150.0);
        result.addTrade(-25.0);

        assertEquals(5, result.getTotalTrades());
        assertEquals(3, result.getWinningTrades());
        assertEquals(60.0, result.getWinRate(), 0.01);
    }
}
