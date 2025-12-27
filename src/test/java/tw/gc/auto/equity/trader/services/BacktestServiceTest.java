package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.impl.RSIStrategy;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BacktestServiceTest {

    private BacktestService backtestService;
    private List<MarketData> historicalData;
    private List<IStrategy> strategies;
    private SystemStatusService mockSystemStatusService;

    @BeforeEach
    void setUp() {
        tw.gc.auto.equity.trader.repositories.BacktestResultRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.BacktestResultRepository.class);
        tw.gc.auto.equity.trader.repositories.MarketDataRepository mockMarketDataRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.MarketDataRepository.class);
        HistoryDataService mockHistoryService = 
            org.mockito.Mockito.mock(HistoryDataService.class);
        // Provide a stock name from history so mapping receives a name
        org.mockito.Mockito.when(mockHistoryService.getStockNameFromHistory(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn("Taiwan Semiconductor");
        mockSystemStatusService = org.mockito.Mockito.mock(SystemStatusService.class);
        DataSource mockDataSource = org.mockito.Mockito.mock(DataSource.class);
        JdbcTemplate mockJdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(mockSystemStatusService.startBacktest()).thenReturn(true);
        
        // Mock StrategyStockMappingService to ensure Backtest persists mappings
        StrategyStockMappingService mockMappingService = org.mockito.Mockito.mock(StrategyStockMappingService.class);
        
        backtestService = new BacktestService(
            mockRepo, mockMarketDataRepo, mockHistoryService, mockSystemStatusService,
            mockDataSource, mockJdbcTemplate, mockMappingService
        );
        
        // Expose mapping service for verification via a field on test instance (reflection)
        try {
            java.lang.reflect.Field mappingField = BacktestService.class.getDeclaredField("strategyStockMappingService");
            mappingField.setAccessible(true);
            mappingField.set(backtestService, mockMappingService);
        } catch (Exception ignored) { }

        // Initialize strategies used by tests
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
    void testBacktest_PopulatesStrategyStockMapping() {
        // Prepare strategies and historical data (same as setUp logic)
        strategies = new ArrayList<>();
        strategies.add(new RSIStrategy(14, 70, 30));

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

        // Given - spy mapping service
        StrategyStockMappingService spyMappingService = org.mockito.Mockito.mock(StrategyStockMappingService.class);
        try {
            java.lang.reflect.Field mappingField = BacktestService.class.getDeclaredField("strategyStockMappingService");
            mappingField.setAccessible(true);
            mappingField.set(backtestService, spyMappingService);
        } catch (Exception ignored) { }

        // When
        double initialCapital = 80000.0;
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
                strategies, historicalData, initialCapital
        );

        // Then - ensure updateMapping was called at least once for the symbol
        org.mockito.Mockito.verify(spyMappingService, org.mockito.Mockito.atLeastOnce()).updateMapping(
            org.mockito.ArgumentMatchers.eq("2330.TW"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
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

    @Test
    void testFetchTop50Stocks_DynamicFetching() throws Exception {
        // Use reflection to test private method
        java.lang.reflect.Method fetchMethod = BacktestService.class.getDeclaredMethod("fetchTop50Stocks");
        fetchMethod.setAccessible(true);
        
        java.lang.reflect.Method fallbackMethod = BacktestService.class.getDeclaredMethod("getFallbackStockList");
        fallbackMethod.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> dynamicStocks = (List<String>) fetchMethod.invoke(backtestService);
        
        @SuppressWarnings("unchecked")
        List<String> fallbackStocks = (List<String>) fallbackMethod.invoke(backtestService);
        
        // Verify results
        assertNotNull(dynamicStocks, "Stock list should not be null");
        assertEquals(50, dynamicStocks.size(), "Should return exactly 50 stocks");
        
        // Verify format (all should end with .TW)
        for (String stock : dynamicStocks) {
            assertTrue(stock.endsWith(".TW"), "Stock symbol should end with .TW: " + stock);
            String code = stock.replace(".TW", "");
            assertTrue(code.matches("\\d{4}"), "Stock code should be 4 digits: " + code);
        }
        
        // Verify no duplicates
        long uniqueCount = dynamicStocks.stream().distinct().count();
        assertEquals(50, uniqueCount, "All 50 stocks should be unique");
        
        // CRITICAL: When dynamic fetching fails, ALL 50 stocks should match the fallback list exactly
        assertEquals(fallbackStocks, dynamicStocks, 
            "When dynamic fetching fails, all 50 stocks should match fallback list exactly");
        
        // Log for verification
        System.out.println("\n=== Verification: Dynamic vs Fallback ===");
        System.out.println("Dynamic stocks match fallback: " + dynamicStocks.equals(fallbackStocks));
        System.out.println("First 10 dynamic stocks:");
        for (int i = 0; i < 10; i++) {
            System.out.println((i + 1) + ". " + dynamicStocks.get(i));
        }
    }

    @Test
    void testFetchTop50Stocks_FallbackList() throws Exception {
        // Test that fallback list is valid
        java.lang.reflect.Method method = BacktestService.class.getDeclaredMethod("getFallbackStockList");
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        List<String> fallbackStocks = (List<String>) method.invoke(backtestService);
        
        assertNotNull(fallbackStocks, "Fallback stock list should not be null");
        assertEquals(50, fallbackStocks.size(), "Fallback should return exactly 50 stocks");
        
        // Verify format
        for (String stock : fallbackStocks) {
            assertTrue(stock.endsWith(".TW"), "Fallback stock should end with .TW: " + stock);
            String code = stock.replace(".TW", "");
            assertTrue(code.matches("\\d{4}"), "Fallback stock code should be 4 digits: " + code);
        }
        
        System.out.println("\n=== Fallback list (first 10) ===");
        for (int i = 0; i < 10; i++) {
            System.out.println((i + 1) + ". " + fallbackStocks.get(i));
        }
    }

    @Test
    void testJdbcBatchInsert_IncludesStockName() throws Exception {
        // Prepare a BacktestResult with stockName set
        tw.gc.auto.equity.trader.entities.BacktestResult r = tw.gc.auto.equity.trader.entities.BacktestResult.builder()
            .backtestRunId("BT-TEST")
            .symbol("2330.TW")
            .stockName("Taiwan Semiconductor")
            .strategyName("RSI Test")
            .initialCapital(1000.0)
            .finalEquity(1100.0)
            .totalReturnPct(10.0)
            .build();
        List<tw.gc.auto.equity.trader.entities.BacktestResult> list = List.of(r);

        // Instead of stubbing via mock, we will verify the SQL used by calling the private method via reflection
        java.lang.reflect.Method method = BacktestService.class.getDeclaredMethod("jdbcBatchInsertBacktestResults", List.class);
        method.setAccessible(true);

        // Invoke method and assert it returns size
        // For this test we can't easily intercept PreparedStatementSetter, but we can verify that the SQL contains 'stock_name'
        java.lang.reflect.Field jdbcField = BacktestService.class.getDeclaredField("jdbcTemplate");
        jdbcField.setAccessible(true);
        org.springframework.jdbc.core.JdbcTemplate realJdbc = (org.springframework.jdbc.core.JdbcTemplate) jdbcField.get(backtestService);

        // Stub batchUpdate on the mock jdbcTemplate to return a successful batch result (int[][])
        org.mockito.Mockito.doReturn(new int[][]{ new int[]{1} }).when(realJdbc).batchUpdate(anyString(), anyList(), anyInt(), any());

        int inserted = (Integer) method.invoke(backtestService, list);
        assertEquals(1, inserted);

        // Capture the SQL used
        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(realJdbc).batchUpdate(sqlCaptor.capture(), eq(list), eq(1), any());
        String usedSql = sqlCaptor.getValue();
        assertTrue(usedSql.contains("stock_name"), "SQL should include stock_name column");
    }
    
    @Test
    void testGetAllStrategies_shouldReturn99Strategies() {
        // When
        List<IStrategy> strategies = backtestService.getAllStrategies();
        
        // Then
        assertNotNull(strategies, "Strategies list should not be null");
        assertEquals(99, strategies.size(), "Should return exactly 99 strategies");
        
        // Verify all strategies have names
        for (IStrategy strategy : strategies) {
            assertNotNull(strategy.getName(), "Strategy name should not be null");
            assertFalse(strategy.getName().isEmpty(), "Strategy name should not be empty");
        }
        
        // Verify no duplicates by name
        long uniqueCount = strategies.stream()
            .map(IStrategy::getName)
            .distinct()
            .count();
        assertEquals(99, uniqueCount, "All 99 strategies should have unique names");
        
        System.out.println("\n=== All 99 Strategies ===");
        for (int i = 0; i < strategies.size(); i++) {
            System.out.println((i + 1) + ". " + strategies.get(i).getName());
        }
    }
}
