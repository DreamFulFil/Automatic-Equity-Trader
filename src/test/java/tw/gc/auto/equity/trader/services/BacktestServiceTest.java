package tw.gc.auto.equity.trader.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;
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
    private tw.gc.auto.equity.trader.repositories.MarketDataRepository mockMarketDataRepo;

    @BeforeEach
    void setUp() {
        tw.gc.auto.equity.trader.repositories.BacktestResultRepository mockRepo = 
            org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.BacktestResultRepository.class);
        mockMarketDataRepo = 
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
    void runBacktest_shouldPersistResults_viaRepositorySave_and_handleLongShortFlows() {
        // Given - create a mock repository and a custom strategy that opens long then short
        tw.gc.auto.equity.trader.repositories.BacktestResultRepository mockRepo = org.mockito.Mockito.mock(tw.gc.auto.equity.trader.repositories.BacktestResultRepository.class);
        StrategyStockMappingService mockMapping = org.mockito.Mockito.mock(StrategyStockMappingService.class);
        HistoryDataService mockHistory = org.mockito.Mockito.mock(HistoryDataService.class);
        org.mockito.Mockito.when(mockHistory.getStockNameFromHistory(anyString())).thenReturn("MockName");

        BacktestService svc = new BacktestService(
            mockRepo, mockMarketDataRepo, mockHistory, mockSystemStatusService, org.mockito.Mockito.mock(DataSource.class), org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), mockMapping
        );

        // Strategy that returns LONG on first call, SHORT on subsequent calls
        class FlipStrategy implements IStrategy {
            private int calls = 0;
            @Override public String getName() { return "FLIP"; }
            @Override public StrategyType getType() { return StrategyType.SHORT_TERM; }
            @Override public void reset() { calls = 0; }
            @Override public TradeSignal execute(Portfolio p, MarketData d) {
                calls++;
                return calls == 1 ? TradeSignal.longSignal(1.0, "flip") : TradeSignal.shortSignal(1.0, "flip");
            }
        }
        List<IStrategy> strats = List.of(new FlipStrategy());

        // Two data points to trigger LONG then SHORT
        List<MarketData> hist = List.of(
            MarketData.builder().symbol("TEST.TW").timestamp(LocalDateTime.now().minusDays(2)).close(100.0).build(),
            MarketData.builder().symbol("TEST.TW").timestamp(LocalDateTime.now().minusDays(1)).close(110.0).build()
        );

        // When
        Map<String, BacktestService.InMemoryBacktestResult> results = svc.runBacktest(strats, hist, 10000.0);

        // Then
        org.mockito.Mockito.verify(mockRepo, org.mockito.Mockito.atLeastOnce()).save(any());
        assertNotNull(results.get("FLIP"));
        assertTrue(results.get("FLIP").getTotalTrades() >= 1);
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
    void flushBacktestResults_shouldFallBackToJdbcTemplate_whenPgFails() throws Exception {
        // Prepare a BacktestResult payload
        tw.gc.auto.equity.trader.entities.BacktestResult br = tw.gc.auto.equity.trader.entities.BacktestResult.builder()
            .backtestRunId("BT-1")
            .symbol("TEST.TW")
            .strategyName("S")
            .initialCapital(1000.0)
            .finalEquity(1100.0)
            .dataPoints(2)
            .build();

        // Replace dataSource with one that throws on getConnection to force fallback
        javax.sql.DataSource ds = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        org.mockito.Mockito.when(ds.getConnection()).thenThrow(new java.sql.SQLException("no pg"));

        java.lang.reflect.Field dsField = BacktestService.class.getDeclaredField("dataSource");
        dsField.setAccessible(true);
        dsField.set(backtestService, ds);

        // Stub jdbcTemplate.batchUpdate to simulate a successful fallback insert
        java.lang.reflect.Field jdbcField = BacktestService.class.getDeclaredField("jdbcTemplate");
        jdbcField.setAccessible(true);
        org.springframework.jdbc.core.JdbcTemplate jdbc = (org.springframework.jdbc.core.JdbcTemplate) jdbcField.get(backtestService);
        org.mockito.Mockito.when(jdbc.batchUpdate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new int[][]{ new int[]{1} });

        // Call private method flushBacktestResults via reflection
        java.lang.reflect.Method m = BacktestService.class.getDeclaredMethod("flushBacktestResults", java.util.List.class);
        m.setAccessible(true);

        int inserted = (int) m.invoke(backtestService, java.util.List.of(br));

        assertEquals(1, inserted);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1)).batchUpdate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testBacktestResult_CalculateMetrics() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 80000.0);
        
        // Simulate some trades with entry values for proper P&L% calculation
        result.addTrade(1000.0, 10000.0, LocalDateTime.now());  // Win: 10% gain
        result.addTrade(-500.0, 10000.0, LocalDateTime.now());  // Loss: -5%
        result.addTrade(1500.0, 10000.0, LocalDateTime.now());  // Win: 15% gain
        
        // Track equity with position status
        result.trackEquity(81000.0, true);
        result.trackEquity(80500.0, false);
        result.trackEquity(82000.0, true);
        
        // Record prices for buy & hold comparison
        result.recordPrice(100.0);
        result.recordPrice(110.0);
        
        result.setFinalEquity(82000.0);
        result.calculateMetrics();

        assertEquals(3, result.getTotalTrades());
        assertEquals(2, result.getWinningTrades());
        assertEquals(1, result.getLosingTrades());
        assertEquals(66.67, result.getWinRate(), 0.1);
        assertEquals(2000.0, result.getTotalPnL(), 0.01);
        assertEquals(2.5, result.getTotalReturnPercentage(), 0.01);
        assertTrue(result.getSharpeRatio() != 0.0);
        assertTrue(result.getMaxDrawdownPercentage() >= 0.0);
        
        // Verify new comprehensive metrics
        assertEquals(10.0, result.getBuyHoldReturnPct(), 0.01); // (110-100)/100 * 100
        assertTrue(result.getExposureTimePct() > 0); // Was in position some of the time
        assertTrue(result.getBestTradePct() > 0);
        assertTrue(result.getWorstTradePct() < 0);
        assertTrue(result.getProfitFactor() > 0); // Profitable overall
        assertEquals(2500.0, result.getGrossProfit(), 0.01); // 1000 + 1500 from winning trades
    }

    @Test
    void testBacktestResult_MaxDrawdownCalculation() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 100000.0);
        
        // Simulate equity curve with drawdown
        result.trackEquity(100000.0, false);
        result.trackEquity(110000.0, true);  // Peak
        result.trackEquity(105000.0, true);  // Drawdown
        result.trackEquity(100000.0, false); // Max drawdown (10k from peak)
        result.trackEquity(115000.0, true);  // Recovery
        
        result.setFinalEquity(115000.0);
        result.calculateMetrics();

        // Max drawdown should be (110000 - 100000) / 110000 * 100 = 9.09%
        assertTrue(result.getMaxDrawdownPercentage() > 9.0);
        assertTrue(result.getMaxDrawdownPercentage() < 10.0);
        
        // Verify average drawdown is also calculated
        assertTrue(result.getAvgDrawdownPercentage() > 0);
        
        // Verify equity peak
        assertEquals(115000.0, result.getEquityPeak(), 0.01);
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
    void testGetAllStrategies_shouldReturn100Strategies() {
        // When
        List<IStrategy> strategies = backtestService.getAllStrategies();
        
        // Then
        assertNotNull(strategies, "Strategies list should not be null");
        assertEquals(100, strategies.size(), "Should return exactly 100 strategies");
        
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
        assertEquals(100, uniqueCount, "All 100 strategies should have unique names");
        
        System.out.println("\n=== All 100 Strategies ===");
        for (int i = 0; i < strategies.size(); i++) {
            System.out.println((i + 1) + ". " + strategies.get(i).getName());
        }
    }

    @Test
    void testRunParallelizedBacktest_NoStocksWithData() throws Exception {
        // Given - mock repositories to return no data
        when(mockMarketDataRepo.countBySymbolAndTimeframeAndTimestampBetween(anyString(), any(), any(), any()))
            .thenReturn(0L);

        // When
        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results = 
            backtestService.runParallelizedBacktest(strategies, 80000.0);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty map when no stocks have data");
        verify(mockSystemStatusService).startBacktest();
        verify(mockSystemStatusService).completeBacktest();
    }

    @Test
    void testRunParallelizedBacktest_WithStocksWithData() throws Exception {
        // Given - mock repositories to return data for some stocks
        when(mockMarketDataRepo.countBySymbolAndTimeframeAndTimestampBetween(anyString(), any(), any(), any()))
            .thenReturn(100L); // Simulate data exists
        
        List<MarketData> mockHistory = List.of(
            MarketData.builder()
                .symbol("2330.TW")
                .timestamp(LocalDateTime.now().minusDays(1))
                .open(100.0)
                .high(105.0)
                .low(95.0)
                .close(102.0)
                .volume(1000000L)
                .build()
        );
        
        when(mockMarketDataRepo.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenReturn(mockHistory);

        // When
        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results = 
            backtestService.runParallelizedBacktest(strategies, 80000.0);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should return results when stocks have data");
        verify(mockSystemStatusService).startBacktest();
        verify(mockSystemStatusService).completeBacktest();
    }

    @Test
    void testRunParallelizedBacktest_BacktestFailure() throws Exception {
        // Given - mock to throw exception during backtest
        when(mockMarketDataRepo.countBySymbolAndTimeframeAndTimestampBetween(anyString(), any(), any(), any()))
            .thenReturn(100L);
        
        when(mockMarketDataRepo.findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
            anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("Database error"));

        // When
        Map<String, Map<String, BacktestService.InMemoryBacktestResult>> results = 
            backtestService.runParallelizedBacktest(strategies, 80000.0);

        // Then
        assertNotNull(results);
        // Should still contain entries for stocks, but with empty results due to failure
        assertFalse(results.isEmpty());
        verify(mockSystemStatusService).startBacktest();
        verify(mockSystemStatusService).completeBacktest();
    }

    @Test
    void testRunBacktest_WithBacktestRunId() {
        String backtestRunId = "BT-TEST-123";
        double initialCapital = 80000.0;
        
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
                strategies, historicalData, initialCapital, backtestRunId
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
    void testRunBacktest_EmptyStrategies() {
        List<IStrategy> emptyStrategies = new ArrayList<>();
        double initialCapital = 80000.0;
        
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
                emptyStrategies, historicalData, initialCapital
        );

        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty map for empty strategies");
    }

    @Test
    void testRunBacktest_EmptyHistoricalData() {
        List<MarketData> emptyHistory = new ArrayList<>();
        double initialCapital = 80000.0;
        
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
                strategies, emptyHistory, initialCapital
        );

        assertNotNull(results);
        assertEquals(1, results.size(), "Should return results for strategies even with empty historical data");
        assertTrue(results.containsKey("RSI (14, 30/70)"));
    }

    @Test
    void testInMemoryBacktestResult_AddTrade() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 100000.0);
        
        result.addTrade(1000.0);
        result.addTrade(-500.0);
        result.addTrade(2000.0);
        
        assertEquals(3, result.getTotalTrades());
        assertEquals(2, result.getWinningTrades());
        assertEquals(66.67, result.getWinRate(), 0.01);
    }

    @Test
    void testInMemoryBacktestResult_TrackEquity() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 100000.0);
        
        result.trackEquity(105000.0, true);  // In position
        result.trackEquity(103000.0, false); // Out of position
        result.trackEquity(107000.0, true);  // In position
        
        result.calculateMetrics();
        assertTrue(result.getMaxDrawdownPercentage() >= 0.0);
        
        // Verify exposure time is calculated (2/3 bars in position = ~66.67%)
        assertEquals(66.67, result.getExposureTimePct(), 0.1);
    }

    @Test
    void testInMemoryBacktestResult_Getters() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Test Strategy", 100000.0);
        
        assertEquals("Test Strategy", result.getStrategyName());
        assertEquals(100000.0, result.getInitialCapital());
        assertEquals(0, result.getTotalTrades());
        assertEquals(0.0, result.getWinRate());
        assertEquals(0.0, result.getTotalPnL());
        assertEquals(0.0, result.getTotalReturnPercentage());
        assertEquals(0.0, result.getSharpeRatio());
        assertEquals(0.0, result.getMaxDrawdownPercentage());
        
        // Verify new comprehensive metrics getters
        assertEquals(0.0, result.getSortinoRatio());
        assertEquals(0.0, result.getCalmarRatio());
        assertEquals(0.0, result.getExpectancy());
        assertEquals(0.0, result.getSqn());
        assertEquals(0, result.getLosingTrades());
        assertEquals(0.0, result.getBuyHoldReturnPct());
        assertEquals(0.0, result.getAnnualReturnPct());
        assertEquals(0.0, result.getAnnualVolatilityPct());
    }

    @Test
    void testFetchTop50Stocks_FallsBackOnException() throws Exception {
        // This tests the fallback mechanism when dynamic fetching fails
        // Since fetchTop50Stocks uses external APIs, we can only test indirectly
        // The method should return a curated list when exceptions occur
        
        List<String> stocks = backtestService.fetchTop50Stocks();
        
        // Should return at least some stocks (either from API or fallback)
        assertNotNull(stocks);
        assertTrue(stocks.size() > 0, "Should return at least some stocks");
        
        // Fallback list should contain major Taiwan stocks
        // The actual content depends on which source succeeds
    }

    @Test
    void testStockCandidate_Creation() throws Exception {
        // Access inner class via reflection
        Class<?> candidateClass = Class.forName("tw.gc.auto.equity.trader.services.BacktestService$StockCandidate");
        java.lang.reflect.Constructor<?> constructor = candidateClass.getDeclaredConstructor(
            String.class, String.class, double.class, double.class, String.class
        );
        constructor.setAccessible(true);
        
        Object candidate = constructor.newInstance("2330.TW", "TSMC", 1000000.0, 5000000.0, "TWSE");
        
        assertNotNull(candidate);
        assertEquals("2330.TW", candidateClass.getMethod("getSymbol").invoke(candidate));
        assertEquals("TSMC", candidateClass.getMethod("getName").invoke(candidate));
    }

    @Test
    void testBacktest_WithEmptyHistory_CompletesGracefully() {
        List<MarketData> emptyHistory = new ArrayList<>();
        
        // Should handle empty history without crashing
        Map<String, BacktestService.InMemoryBacktestResult> results = backtestService.runBacktest(
            strategies,
            emptyHistory,
            100000.0,
            "BT-TEST-EMPTY"
        );

        // Should return results even with no data
        assertNotNull(results);
        for (var result : results.values()) {
            assertEquals(0, result.getTotalTrades());
            // With empty history, no final equity is set so it remains 0
            assertTrue(result.getFinalEquity() >= 0);
        }
    }

    @Test
    void testComprehensiveBacktestMetrics() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Comprehensive Test", 100000.0);
        result.setCommissionRate(0.005);
        result.setSlippageRate(0.001);
        
        // Simulate realistic trading scenario with multiple trades
        // Trade 1: Buy at day 0, sell at day 5 with 10% gain
        result.recordTradeEntry(LocalDateTime.now().minusDays(10), 100000.0);
        result.addTrade(10000.0, 100000.0, LocalDateTime.now().minusDays(5)); // 10% gain
        
        // Trade 2: Buy at day 4, sell at day 7 with 5% loss
        result.recordTradeEntry(LocalDateTime.now().minusDays(6), 105000.0);
        result.addTrade(-5250.0, 105000.0, LocalDateTime.now().minusDays(3)); // ~5% loss
        
        // Trade 3: Buy at day 6, sell at day 10 with 8% gain
        result.recordTradeEntry(LocalDateTime.now().minusDays(4), 99750.0);
        result.addTrade(7980.0, 99750.0, LocalDateTime.now()); // ~8% gain
        
        // Simulate equity curve with position tracking
        double[] equities = {100000, 102000, 105000, 110000, 108000, 104000, 99750, 102000, 105000, 107730};
        boolean[] inPosition = {true, true, true, true, true, false, true, true, true, true};
        
        for (int i = 0; i < equities.length; i++) {
            result.trackEquity(equities[i], inPosition[i]);
            result.recordPrice(100.0 + i * 2); // Simulating price movement for buy & hold
        }
        
        result.setFinalEquity(107730.0);
        result.calculateMetrics();
        
        // Verify trade statistics
        assertEquals(3, result.getTotalTrades());
        assertEquals(2, result.getWinningTrades());
        assertEquals(1, result.getLosingTrades());
        assertEquals(66.67, result.getWinRate(), 0.1);
        
        // Verify P&L metrics
        double expectedTotalPnL = 10000.0 - 5250.0 + 7980.0;
        assertEquals(expectedTotalPnL, result.getTotalPnL(), 0.01);
        assertEquals(7.73, result.getTotalReturnPercentage(), 0.01);
        
        // Verify gross profit/loss
        assertEquals(17980.0, result.getGrossProfit(), 0.01); // 10000 + 7980
        assertEquals(5250.0, result.getGrossLoss(), 0.01);
        
        // Verify profit factor
        double expectedProfitFactor = 17980.0 / 5250.0;
        assertEquals(expectedProfitFactor, result.getProfitFactor(), 0.01);
        
        // Verify trade percentages
        assertTrue(result.getBestTradePct() > 0);
        assertTrue(result.getWorstTradePct() < 0);
        
        // Verify drawdown metrics
        assertTrue(result.getMaxDrawdownPercentage() > 0);
        assertTrue(result.getEquityPeak() >= 110000.0);
        
        // Verify exposure time (9/10 bars in position = 90%)
        assertEquals(90.0, result.getExposureTimePct(), 0.01);
        
        // Verify buy & hold return
        assertTrue(result.getBuyHoldReturnPct() > 0); // Price went from 100 to 118
        
        // Verify trade durations are tracked
        assertTrue(result.getAvgTradeDurationDays() > 0);
        assertTrue(result.getMaxTradeDurationDays() > 0);
        
        // Verify risk-adjusted metrics are calculated
        assertTrue(Double.isFinite(result.getSharpeRatio()));
        assertTrue(Double.isFinite(result.getSortinoRatio()));
        assertTrue(Double.isFinite(result.getCalmarRatio()));
        
        // Verify quality metrics
        assertTrue(result.getExpectancy() > 0); // Profitable system
        assertTrue(Double.isFinite(result.getSqn()));
        
        // Verify commission and slippage rates are set
        assertEquals(0.005, result.getCommissionRate(), 0.0001);
        assertEquals(0.001, result.getSlippageRate(), 0.0001);
    }

    @Test
    void testQualityMetrics_SQN_Calculation() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("SQN Test", 100000.0);
        
        // Add trades with known P&L for SQN calculation
        // SQN = sqrt(N) * (Avg PnL / StdDev PnL)
        double[] pnls = {1000, 1200, 800, 1100, 900, 1050, 950, 1150, 850, 1000};
        for (double pnl : pnls) {
            result.addTrade(pnl, 10000.0, LocalDateTime.now());
        }
        
        result.setFinalEquity(110000.0);
        result.calculateMetrics();
        
        // Verify SQN is calculated
        assertTrue(result.getSqn() > 0, "SQN should be positive for consistently profitable trades");
        
        // Verify expectancy
        double expectedAvgPnL = 10000.0 / 10; // Sum of pnls / count
        assertTrue(result.getExpectancy() > 0);
    }

    @Test
    void testDrawdownDurationMetrics() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Drawdown Duration Test", 100000.0);
        
        // Create equity curve with two drawdown periods
        // Period 1: Peak at 110000, drawdown of 3 bars
        // Period 2: Peak at 115000, drawdown of 5 bars
        double[] equities = {
            100000, 105000, 110000,  // Rise to first peak
            108000, 105000, 107000,  // Drawdown period 1 (3 bars)
            112000, 115000,          // Rise to second peak
            113000, 110000, 108000, 111000, 114000  // Drawdown period 2 (5 bars before recovery)
        };
        
        for (double eq : equities) {
            result.trackEquity(eq, true);
        }
        
        result.setFinalEquity(114000.0);
        result.calculateMetrics();
        
        // Verify drawdown duration metrics
        assertTrue(result.getMaxDrawdownDurationDays() >= 3, 
            "Max drawdown duration should capture the longest drawdown period");
        assertTrue(result.getAvgDrawdownDurationDays() > 0, 
            "Average drawdown duration should be calculated");
    }

    @Test
    void testAnnualizedMetrics() {
        BacktestService.InMemoryBacktestResult result = new BacktestService.InMemoryBacktestResult("Annualized Test", 100000.0);
        
        // Simulate 252 trading days (1 year) of daily equity tracking
        double equity = 100000.0;
        for (int i = 0; i < 252; i++) {
            // Simulate small daily fluctuations with slight upward drift
            equity *= (1.0 + (Math.random() * 0.02 - 0.008)); // -0.8% to +1.2% daily
            result.trackEquity(equity, true);
        }
        
        result.setFinalEquity(equity);
        result.calculateMetrics();
        
        // Verify annualized metrics are calculated
        assertTrue(Double.isFinite(result.getAnnualReturnPct()), "Annual return should be finite");
        assertTrue(Double.isFinite(result.getAnnualVolatilityPct()), "Annual volatility should be finite");
        assertTrue(result.getAnnualVolatilityPct() > 0, "Annual volatility should be positive");
    }
}
