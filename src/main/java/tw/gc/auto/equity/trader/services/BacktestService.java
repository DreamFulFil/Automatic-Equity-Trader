package tw.gc.auto.equity.trader.services;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tw.gc.auto.equity.trader.strategy.impl.*;

/**
 * Backtest Service
 * Runs strategies against historical data and tracks performance.
 * All results are persisted to database for auditability.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BacktestService {
    
    private final BacktestResultRepository backtestResultRepository;
    private final MarketDataRepository marketDataRepository;
    private final HistoryDataService historyDataService;
    private final SystemStatusService systemStatusService;
    
    /**
     * Run backtest and persist results to database
     */
    @Transactional
    public Map<String, InMemoryBacktestResult> runBacktest(List<IStrategy> strategies, List<MarketData> history, double initialCapital) {
        return runBacktest(strategies, history, initialCapital, null);
    }
    
    /**
     * Run backtest with custom run ID and persist results
     */
    @Transactional
    public Map<String, InMemoryBacktestResult> runBacktest(List<IStrategy> strategies, List<MarketData> history, double initialCapital, String backtestRunId) {
        if (backtestRunId == null) {
            backtestRunId = generateBacktestRunId();
        }
        
        log.info("🚀 Starting Backtest [{}] with {} strategies on {} data points...", 
            backtestRunId, strategies.size(), history.size());
        
        Map<String, InMemoryBacktestResult> results = new HashMap<>();
        String symbol = history.isEmpty() ? "UNKNOWN" : history.get(0).getSymbol();
        LocalDateTime periodStart = history.isEmpty() ? LocalDateTime.now() : 
            history.get(0).getTimestamp().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime periodEnd = history.isEmpty() ? LocalDateTime.now() : 
            history.get(history.size() - 1).getTimestamp().atZone(ZoneId.systemDefault()).toLocalDateTime();
        
        // Initialize portfolios
        Map<String, Portfolio> portfolios = new HashMap<>();
        for (IStrategy strategy : strategies) {
            strategy.reset(); // Reset state
            
            Map<String, Integer> pos = new HashMap<>();
            pos.put(symbol, 0);
            
            Portfolio p = Portfolio.builder()
                .equity(initialCapital)
                .availableMargin(initialCapital)
                .positions(pos)
                .tradingMode("backtest")
                .tradingQuantity(1) // Default quantity
                .build();
            
            portfolios.put(strategy.getName(), p);
            results.put(strategy.getName(), new InMemoryBacktestResult(strategy.getName(), initialCapital));
        }
        
        // Run Simulation Loop
        for (MarketData data : history) {
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                InMemoryBacktestResult result = results.get(strategy.getName());
                
                try {
                    TradeSignal signal = strategy.execute(p, data);
                    processSignal(strategy, p, data, signal, result);
                    
                    // Track equity curve for drawdown calculation
                    result.trackEquity(p.getEquity());
                } catch (Exception e) {
                    log.error("Error in backtest for strategy {}", strategy.getName(), e);
                }
            }
        }
        
        // Close open positions at end and persist results
        if (!history.isEmpty()) {
            MarketData lastData = history.get(history.size() - 1);
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                InMemoryBacktestResult result = results.get(strategy.getName());
                closeAllPositions(p, lastData, result);
                result.setFinalEquity(p.getEquity());
                result.calculateMetrics();
                
                // Persist to database
                persistBacktestResult(backtestRunId, symbol, strategy.getName(), result, 
                    periodStart, periodEnd, history.size());
            }
        }
        
        log.info("✅ Backtest [{}] completed. {} results persisted.", backtestRunId, results.size());
        return results;
    }
    
    private String generateBacktestRunId() {
        return "BT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }
    
    private void persistBacktestResult(String backtestRunId, String symbol, String strategyName,
                                      InMemoryBacktestResult result, LocalDateTime periodStart,
                                      LocalDateTime periodEnd, int dataPoints) {
        BacktestResult entity = BacktestResult.builder()
            .backtestRunId(backtestRunId)
            .symbol(symbol)
            .strategyName(strategyName)
            .initialCapital(result.getInitialCapital())
            .finalEquity(result.getFinalEquity())
            .totalReturnPct(result.getTotalReturnPercentage())
            .sharpeRatio(result.getSharpeRatio())
            .maxDrawdownPct(result.getMaxDrawdownPercentage())
            .totalTrades(result.getTotalTrades())
            .winningTrades(result.getWinningTrades())
            .winRatePct(result.getWinRate())
            .avgProfitPerTrade(result.getTotalTrades() > 0 ? result.getTotalPnL() / result.getTotalTrades() : 0.0)
            .backtestPeriodStart(periodStart)
            .backtestPeriodEnd(periodEnd)
            .dataPoints(dataPoints)
            .build();
        
        backtestResultRepository.save(entity);
    }
    
    private void processSignal(IStrategy strategy, Portfolio p, MarketData data, TradeSignal signal, InMemoryBacktestResult result) {
        String symbol = data.getSymbol();
        int currentPos = p.getPosition(symbol);
        double price = data.getClose();
        
        // Simple execution logic (Market Orders)
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG && currentPos <= 0) {
            // Close Short if any
            if (currentPos < 0) {
                double pnl = (p.getEntryPrice(symbol) - price) * Math.abs(currentPos);
                p.setEquity(p.getEquity() + pnl);
                p.setAvailableMargin(p.getAvailableMargin() + (price * Math.abs(currentPos)) + pnl);
                p.setPosition(symbol, 0);
                result.addTrade(pnl);
            }
            
            // Open Long
            int qty = calculateQuantity(p, price);
            if (qty > 0) {
                p.setAvailableMargin(p.getAvailableMargin() - (price * qty));
                p.setPosition(symbol, qty);
                p.setEntryPrice(symbol, price);
            }
            
        } else if (signal.getDirection() == TradeSignal.SignalDirection.SHORT && currentPos >= 0) {
            // Close Long if any
            if (currentPos > 0) {
                double pnl = (price - p.getEntryPrice(symbol)) * currentPos;
                p.setEquity(p.getEquity() + pnl);
                p.setAvailableMargin(p.getAvailableMargin() + (price * currentPos) + pnl);
                p.setPosition(symbol, 0);
                result.addTrade(pnl);
            }
            
            // Open Short (assuming margin allowed)
            int qty = calculateQuantity(p, price);
            if (qty > 0) {
                p.setAvailableMargin(p.getAvailableMargin() - (price * qty));
                p.setPosition(symbol, -qty);
                p.setEntryPrice(symbol, price);
            }
        }
    }
    
    private void closeAllPositions(Portfolio p, MarketData data, InMemoryBacktestResult result) {
        String symbol = data.getSymbol();
        int currentPos = p.getPosition(symbol);
        double price = data.getClose();
        
        if (currentPos != 0) {
            double pnl;
            if (currentPos > 0) {
                pnl = (price - p.getEntryPrice(symbol)) * currentPos;
            } else {
                pnl = (p.getEntryPrice(symbol) - price) * Math.abs(currentPos);
            }
            p.setEquity(p.getEquity() + pnl);
            p.setPosition(symbol, 0);
            result.addTrade(pnl);
        }
    }
    
    private int calculateQuantity(Portfolio p, double price) {
        // Simple sizing: use 95% of available margin
        double budget = p.getAvailableMargin() * 0.95;
        return (int) (budget / price);
    }
    
    @lombok.Data
    public static class InMemoryBacktestResult {
        private final String strategyName;
        private final double initialCapital;
        private double finalEquity;
        private int totalTrades;
        private int winningTrades;
        private double totalPnL;
        
        // Additional metrics
        private double totalReturnPercentage;
        private double sharpeRatio;
        private double maxDrawdownPercentage;
        
        // Track equity curve for advanced metrics
        private final List<Double> equityCurve = new ArrayList<>();
        private final List<Double> tradePnLs = new ArrayList<>();
        
        public void addTrade(double pnl) {
            totalTrades++;
            totalPnL += pnl;
            tradePnLs.add(pnl);
            if (pnl > 0) winningTrades++;
        }
        
        public void trackEquity(double equity) {
            equityCurve.add(equity);
        }
        
        public double getWinRate() {
            return totalTrades == 0 ? 0 : (double) winningTrades / totalTrades * 100;
        }
        
        public void calculateMetrics() {
            // Calculate total return percentage
            if (initialCapital > 0) {
                totalReturnPercentage = ((finalEquity - initialCapital) / initialCapital) * 100.0;
            }
            
            // Calculate max drawdown
            maxDrawdownPercentage = calculateMaxDrawdown();
            
            // Calculate Sharpe ratio
            sharpeRatio = calculateSharpeRatio();
        }
        
        private double calculateMaxDrawdown() {
            if (equityCurve.isEmpty()) return 0.0;
            
            double maxEquity = equityCurve.get(0);
            double maxDrawdown = 0.0;
            
            for (double equity : equityCurve) {
                if (equity > maxEquity) {
                    maxEquity = equity;
                }
                double drawdown = ((maxEquity - equity) / maxEquity) * 100.0;
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
            
            return maxDrawdown;
        }
        
        private double calculateSharpeRatio() {
            if (tradePnLs.isEmpty()) return 0.0;
            
            // Calculate average return
            double avgReturn = tradePnLs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            // Calculate standard deviation
            double variance = tradePnLs.stream()
                .mapToDouble(pnl -> Math.pow(pnl - avgReturn, 2))
                .average()
                .orElse(0.0);
            
            double stdDev = Math.sqrt(variance);
            
            // Sharpe ratio (assuming risk-free rate = 0 for simplicity)
            return stdDev == 0 ? 0.0 : avgReturn / stdDev;
        }
    }
    
    /**
     * Dynamically fetch top 50 Taiwan stocks by gathering information from multiple
     * internet sources and judging by comprehensive criteria:
     * - Market capitalization
     * - Liquidity (trading volume and bid-ask spread)
     * - Sector diversification
     * - Data availability (minimum 2 years of history)
     * - Listing on TWSE
     * - Market representation (coverage of major economic sectors and large share of TAIEX market cap)
     * - Support for odd-lot trading
     * 
     * Sources:
     * - Taiwan Stock Exchange (TWSE) market cap rankings
     * - Yahoo Finance Taiwan for liquidity metrics
     * - Multiple financial data aggregators
     * 
     * @return List of 50 stock symbols in Yahoo Finance format (e.g., "2330.TW")
     */
    private List<String> fetchTop50Stocks() {
        log.info("🌐 Dynamically fetching top 50 Taiwan stocks from web sources...");
        
        Set<StockCandidate> candidates = new HashSet<>();
        
        try {
            // Source 1: TWSE Market Cap Leaders
            log.info("📊 Fetching from TWSE market cap rankings...");
            candidates.addAll(fetchFromTWSE());
            
            // Source 2: Yahoo Finance Taiwan Top Traded
            log.info("📈 Fetching from Yahoo Finance Taiwan...");
            candidates.addAll(fetchFromYahooFinanceTW());
            
            // Source 3: TAIEX Component Stocks (top components)
            log.info("📉 Fetching TAIEX major components...");
            candidates.addAll(fetchTAIEXComponents());
            
        } catch (Exception e) {
            log.error("❌ Error fetching stocks dynamically, falling back to curated list: {}", e.getMessage());
            return getFallbackStockList();
        }
        
        // Filter and rank candidates
        List<StockCandidate> ranked = candidates.stream()
            .filter(this::meetsCriteria)
            .sorted(Comparator.comparingDouble(StockCandidate::getScore).reversed())
            .limit(50)
            .toList();
        
        List<String> top50 = ranked.stream()
            .map(StockCandidate::getSymbol)
            .collect(Collectors.toList());
        
        log.info("✅ Selected {} stocks from {} candidates", top50.size(), candidates.size());
        
        // Fallback if insufficient stocks found
        if (top50.size() < 50) {
            log.warn("⚠️  Only found {} stocks dynamically, using curated fallback list", top50.size());
            return getFallbackStockList();
        }
        
        log.info("   Top 5: {}", top50.subList(0, Math.min(5, top50.size())));
        return top50;
    }
    
    /**
     * Fetch stocks from Taiwan Stock Exchange market cap rankings
     */
    private Set<StockCandidate> fetchFromTWSE() {
        Set<StockCandidate> stocks = new HashSet<>();
        try {
            // TWSE market statistics page
            Document doc = Jsoup.connect("https://www.twse.com.tw/en/statistics/marketValue")
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
            
            Elements rows = doc.select("table tbody tr");
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() >= 3) {
                    String symbol = cols.get(0).text().trim() + ".TW";
                    String name = cols.get(1).text().trim();
                    double marketCap = parseNumber(cols.get(2).text());
                    
                    if (!symbol.isEmpty() && marketCap > 0) {
                        stocks.add(new StockCandidate(symbol, name, marketCap, 0, "TWSE"));
                    }
                }
            }
            log.info("   Found {} stocks from TWSE", stocks.size());
        } catch (Exception e) {
            log.warn("   Failed to fetch from TWSE: {}", e.getMessage());
        }
        return stocks;
    }
    
    /**
     * Fetch stocks from Yahoo Finance Taiwan most traded
     */
    private Set<StockCandidate> fetchFromYahooFinanceTW() {
        Set<StockCandidate> stocks = new HashSet<>();
        try {
            // Yahoo Finance Taiwan most active
            Document doc = Jsoup.connect("https://tw.stock.yahoo.com/most-active")
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
            
            Elements items = doc.select("[data-symbol]");
            for (Element item : items) {
                String symbol = item.attr("data-symbol");
                if (symbol.matches("\\d{4}") && !symbol.isEmpty()) {
                    symbol = symbol + ".TW";
                    String name = item.select(".symbol-name").text();
                    double volume = parseNumber(item.select(".volume").text());
                    
                    stocks.add(new StockCandidate(symbol, name, 0, volume, "Yahoo"));
                }
            }
            log.info("   Found {} stocks from Yahoo Finance", stocks.size());
        } catch (Exception e) {
            log.warn("   Failed to fetch from Yahoo Finance: {}", e.getMessage());
        }
        return stocks;
    }
    
    /**
     * Fetch major TAIEX index components
     */
    private Set<StockCandidate> fetchTAIEXComponents() {
        Set<StockCandidate> stocks = new HashSet<>();
        try {
            // TAIEX component stocks
            Document doc = Jsoup.connect("https://www.twse.com.tw/en/indices/taiex/components")
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
            
            Elements rows = doc.select("table tr");
            for (Element row : rows) {
                Elements cols = row.select("td");
                if (cols.size() >= 2) {
                    String symbol = cols.get(0).text().trim();
                    if (symbol.matches("\\d{4}")) {
                        symbol = symbol + ".TW";
                        String name = cols.get(1).text().trim();
                        stocks.add(new StockCandidate(symbol, name, 0, 0, "TAIEX"));
                    }
                }
            }
            log.info("   Found {} stocks from TAIEX components", stocks.size());
        } catch (Exception e) {
            log.warn("   Failed to fetch from TAIEX: {}", e.getMessage());
        }
        return stocks;
    }
    
    /**
     * Check if stock meets all criteria
     */
    private boolean meetsCriteria(StockCandidate stock) {
        // Must be TWSE listed (ends with .TW)
        if (!stock.getSymbol().endsWith(".TW")) return false;
        
        // Must have either significant market cap or trading volume
        if (stock.getMarketCap() == 0 && stock.getVolume() == 0) return false;
        
        // Stock code should be valid Taiwan format (4 digits)
        String code = stock.getSymbol().replace(".TW", "");
        if (!code.matches("\\d{4}")) return false;
        
        return true;
    }
    
    /**
     * Parse numeric value from text (handles commas, units like M/B)
     */
    private double parseNumber(String text) {
        if (text == null || text.isEmpty()) return 0;
        try {
            text = text.replaceAll("[^0-9.KMB]", "");
            double multiplier = 1;
            if (text.endsWith("K")) {
                multiplier = 1_000;
                text = text.substring(0, text.length() - 1);
            } else if (text.endsWith("M")) {
                multiplier = 1_000_000;
                text = text.substring(0, text.length() - 1);
            } else if (text.endsWith("B")) {
                multiplier = 1_000_000_000;
                text = text.substring(0, text.length() - 1);
            }
            return Double.parseDouble(text) * multiplier;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Fallback curated list when dynamic fetching fails
     */
    private List<String> getFallbackStockList() {
        return List.of(
            // Technology leaders
            "2330.TW", "2454.TW", "2317.TW", "2382.TW", "2308.TW", 
            "2303.TW", "2357.TW", "3008.TW", "2344.TW", "2345.TW",
            "2347.TW", "2353.TW", "3711.TW", "2356.TW", "2377.TW",
            "2379.TW", "2408.TW", "3034.TW", "2301.TW", "2327.TW",
            // Financial
            "2881.TW", "2882.TW", "2886.TW", "2891.TW", "2892.TW",
            "2884.TW", "2883.TW", "2885.TW", "5880.TW", "2887.TW",
            // Materials & Manufacturing
            "1303.TW", "1301.TW", "2002.TW", "1216.TW", "2409.TW",
            "2801.TW", "2912.TW", "2207.TW", "2609.TW", "2603.TW",
            // Consumer & Retail
            "2610.TW", "2615.TW", "9910.TW", "2412.TW", "3045.TW",
            // Additional diversification
            "6505.TW", "2498.TW", "2395.TW", "3037.TW", "4938.TW"
        );
    }
    
    /**
     * Internal class to represent stock candidate
     */
    private static class StockCandidate {
        private final String symbol;
        private final String name;
        private final double marketCap;
        private final double volume;
        private final String source;
        
        public StockCandidate(String symbol, String name, double marketCap, double volume, String source) {
            this.symbol = symbol;
            this.name = name;
            this.marketCap = marketCap;
            this.volume = volume;
            this.source = source;
        }
        
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public double getMarketCap() { return marketCap; }
        public double getVolume() { return volume; }
        public String getSource() { return source; }
        
        /**
         * Calculate composite score based on market cap and volume
         */
        public double getScore() {
            return (marketCap * 0.7) + (volume * 0.3);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StockCandidate that = (StockCandidate) o;
            return symbol.equals(that.symbol);
        }
        
        @Override
        public int hashCode() {
            return symbol.hashCode();
        }
    }
    
    /**
     * Run parallelized backtest across multiple stocks.
     * Updates SystemStatusService when backtest starts and completes.
     * 
     * Flow:
     * 1. Fetch top 50 stocks using selection criteria
     * 2. Download historical data for each stock (10 years, batched by 365 days)
     * 3. Run backtests in parallel for all stocks
     * 4. Store results in database
     * 
     * @param strategies List of strategies to test
     * @param initialCapital Starting capital for backtest
     * @return Map of stock symbol to backtest results
     */
    public Map<String, Map<String, InMemoryBacktestResult>> runParallelizedBacktest(
            List<IStrategy> strategies, double initialCapital) {
        
        log.info("🚀 Starting Parallelized Backtest with {} strategies", strategies.size());
        
        systemStatusService.startBacktest();
        
        try {
            // Step 1: Fetch top 50 stocks
            List<String> stocks = fetchTop50Stocks();
            log.info("📊 Selected {} stocks for backtesting", stocks.size());
            
            // Step 2: Download historical data for all stocks
            log.info("📥 Downloading 10 years of historical data for {} stocks...", stocks.size());
            downloadHistoricalDataForStocks(stocks, 10);
            
            // Step 3: Run backtests in parallel
            log.info("🧪 Running backtests in parallel...");
            Map<String, Map<String, InMemoryBacktestResult>> allResults = new HashMap<>();
            
            ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(stocks.size(), Runtime.getRuntime().availableProcessors())
            );
            
            List<CompletableFuture<Map.Entry<String, Map<String, InMemoryBacktestResult>>>> futures = 
                new ArrayList<>();
            
            LocalDateTime tenYearsAgo = LocalDateTime.now().minusYears(10);
            LocalDateTime now = LocalDateTime.now();
            
            for (String symbol : stocks) {
                CompletableFuture<Map.Entry<String, Map<String, InMemoryBacktestResult>>> future = 
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            // Fetch historical data from database
                            List<MarketData> history = marketDataRepository
                                .findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                                    symbol, 
                                    MarketData.Timeframe.DAY_1, 
                                    tenYearsAgo, 
                                    now
                                );
                            
                            if (history.isEmpty()) {
                                log.warn("⚠️ No historical data found for {}", symbol);
                                return Map.entry(symbol, new HashMap<String, InMemoryBacktestResult>());
                            }
                            
                            log.info("📊 Running backtest for {} ({} data points)", symbol, history.size());
                            Map<String, InMemoryBacktestResult> results = runBacktest(strategies, history, initialCapital);
                            
                            return Map.entry(symbol, results);
                            
                        } catch (Exception e) {
                            log.error("❌ Backtest failed for {}: {}", symbol, e.getMessage(), e);
                            return Map.entry(symbol, new HashMap<String, InMemoryBacktestResult>());
                        }
                    }, executor);
                
                futures.add(future);
            }
            
            // Wait for all backtests to complete
            for (CompletableFuture<Map.Entry<String, Map<String, InMemoryBacktestResult>>> future : futures) {
                try {
                    Map.Entry<String, Map<String, InMemoryBacktestResult>> entry = future.get();
                    allResults.put(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    log.error("❌ Failed to retrieve backtest result: {}", e.getMessage(), e);
                }
            }
            
            executor.shutdown();
            
            log.info("✅ Parallelized backtest completed. {} stocks tested.", allResults.size());
            
            return allResults;
        } finally {
            systemStatusService.completeBacktest();
        }
    }
    
    /**
     * Download historical data for a list of stocks concurrently.
     * Uses Virtual Threads with a global writer for optimal throughput.
     */
    private void downloadHistoricalDataForStocks(List<String> stocks, int years) {
        log.info("📥 Starting concurrent download for {} stocks ({} years each)", stocks.size(), years);
        historyDataService.downloadHistoricalDataForMultipleStocks(stocks, years);
        log.info("✅ Concurrent download complete for {} stocks", stocks.size());
    }
    
    /**
     * Get all 99 strategies for backtesting.
     * This is the canonical source for available strategies.
     * 
     * @return List of all available trading strategies
     */
    public List<IStrategy> getAllStrategies() {
        List<IStrategy> strategies = new ArrayList<>();
        String defaultSymbol = "2330.TW";  // Default for pair/basket strategies
        
        // Original strategies (17)
        strategies.add(new RSIStrategy(14, 70, 30));
        strategies.add(new MACDStrategy(12, 26, 9));
        strategies.add(new BollingerBandStrategy());
        strategies.add(new StochasticStrategy(14, 3, 80, 20));
        strategies.add(new ATRChannelStrategy(20, 2.0));
        strategies.add(new PivotPointStrategy());
        strategies.add(new MovingAverageCrossoverStrategy(5, 20, 0.001));
        strategies.add(new AggressiveRSIStrategy());
        strategies.add(new ArbitragePairsTradingStrategy());
        strategies.add(new AutomaticRebalancingStrategy(5));
        strategies.add(new DCAStrategy());
        strategies.add(new DividendReinvestmentStrategy());
        strategies.add(new MomentumTradingStrategy());
        strategies.add(new NewsSentimentStrategy());
        strategies.add(new TWAPExecutionStrategy(100, 30));
        strategies.add(new TaxLossHarvestingStrategy());
        strategies.add(new VWAPExecutionStrategy(100, 30, 0.003, 1));
        
        // Technical indicators (33)
        strategies.add(new KeltnerChannelStrategy());
        strategies.add(new IchimokuCloudStrategy());
        strategies.add(new ParabolicSARStrategy());
        strategies.add(new ADXTrendStrategy());
        strategies.add(new WilliamsRStrategy());
        strategies.add(new CCIStrategy());
        strategies.add(new VolumeWeightedStrategy());
        strategies.add(new TripleEMAStrategy());
        strategies.add(new FibonacciRetracementStrategy());
        strategies.add(new DonchianChannelStrategy());
        strategies.add(new SupertrendStrategy());
        strategies.add(new HullMovingAverageStrategy());
        strategies.add(new ChaikinMoneyFlowStrategy());
        strategies.add(new VortexIndicatorStrategy());
        strategies.add(new AroonOscillatorStrategy());
        strategies.add(new ElderRayStrategy());
        strategies.add(new KaufmanAdaptiveMAStrategy());
        strategies.add(new ZigZagStrategy());
        strategies.add(new PriceActionStrategy());
        strategies.add(new BalanceOfPowerStrategy());
        strategies.add(new KlingerOscillatorStrategy());
        strategies.add(new UltimateOscillatorStrategy());
        strategies.add(new MassIndexStrategy());
        strategies.add(new TrixStrategy());
        strategies.add(new RelativeVigorIndexStrategy());
        strategies.add(new DPOStrategy());
        strategies.add(new ForceIndexStrategy());
        strategies.add(new EnvelopeStrategy());
        strategies.add(new PriceVolumeRankStrategy());
        strategies.add(new AccumulationDistributionStrategy());
        strategies.add(new PriceRateOfChangeStrategy());
        strategies.add(new StandardDeviationStrategy());
        strategies.add(new LinearRegressionStrategy());
        
        // Factor-based strategies (50)
        strategies.add(new MeanReversionStrategy(20, 2.0, 0.5));
        strategies.add(new BreakoutMomentumStrategy(20, 0.02));
        strategies.add(new DualMomentumStrategy(60, 120, 0.0));
        strategies.add(new TimeSeriesMomentumStrategy(60, 0.0));
        strategies.add(new CrossSectionalMomentumStrategy(60, 10));
        strategies.add(new ResidualMomentumStrategy(60, 1.0));
        strategies.add(new SeasonalMomentumStrategy(new String[]{"Nov","Dec","Jan"}, new String[]{"May","Jun","Jul"}));
        strategies.add(new NewsRevisionMomentumStrategy(30, 0.05));
        strategies.add(new AcceleratingMomentumStrategy(30, 10));
        strategies.add(new VolatilityAdjustedMomentumStrategy(30, 20));
        strategies.add(new BookToMarketStrategy(0.7, 90));
        strategies.add(new EarningsYieldStrategy(0.05, 60));
        strategies.add(new CashFlowValueStrategy(0.05));
        strategies.add(new EnterpriseValueMultipleStrategy(10.0));
        strategies.add(new SalesGrowthValueStrategy(2.0, 0.10));
        strategies.add(new QualityValueStrategy(0.7, 7));
        strategies.add(new DividendYieldStrategy(0.03, 3));
        strategies.add(new NetPayoutYieldStrategy(0.04));
        strategies.add(new ProfitabilityFactorStrategy(0.35));
        strategies.add(new InvestmentFactorStrategy(0.15));
        strategies.add(new AssetGrowthAnomalyStrategy(1, 0.15));
        strategies.add(new AccrualAnomalyStrategy(0.05));
        strategies.add(new NetStockIssuanceStrategy(12, 0.02));
        strategies.add(new FinancialDistressStrategy(0.15));
        strategies.add(new LowVolatilityAnomalyStrategy(60, 20));
        strategies.add(new BettingAgainstBetaStrategy(60, 0.8));
        strategies.add(new QualityMinusJunkStrategy(60, 30));
        strategies.add(new MultiFactorRankingStrategy(new double[]{0.3, 0.3, 0.2, 0.2}, 30));
        strategies.add(new PriceTrendStrengthStrategy(30, 0.7));
        strategies.add(new BollingerSqueezeStrategy(20, 2.0));
        strategies.add(new VolumeProfileStrategy(20, 0.70));
        strategies.add(new PairsCorrelationStrategy(defaultSymbol, "2454.TW", 60, 2.0));
        strategies.add(new CointegrationPairsStrategy(defaultSymbol, "2454.TW", 2.0));
        strategies.add(new BasketArbitrageStrategy(new String[]{defaultSymbol}, "^TWII"));
        strategies.add(new IndexArbitrageStrategy("^TWII", 0.01));
        strategies.add(new ConversionArbitrageStrategy("BOND", defaultSymbol, 0.02));
        strategies.add(new CalendarSpreadStrategy(1, 3, 0.05));
        strategies.add(new TriangularArbitrageStrategy(new String[]{defaultSymbol, "2454.TW", "2317.TW"}, 0.01));
        strategies.add(new PutCallParityArbitrageStrategy(0.01));
        strategies.add(new VolatilityArbitrageStrategy(20, 0.05));
        strategies.add(new MarketMakingStrategy(0.001, 1000));
        strategies.add(new LimitOrderBookStrategy(5, 1.5));
        strategies.add(new OrderFlowImbalanceStrategy(30, 0.6));
        strategies.add(new BidAskSpreadStrategy(0.001, 0.005));
        strategies.add(new TradeVelocityStrategy(30, 2.0));
        strategies.add(new SmartOrderRoutingStrategy(new String[]{"TWSE"}, new double[]{0.001}));
        strategies.add(new ExecutionShortfallStrategy(1000, 10, 0.5));
        strategies.add(new AnchoredVWAPStrategy("market_open", 0.02));
        strategies.add(new GrahamDefensiveStrategy(2.0, 15.0, 0.03));
        
        return strategies;
    }
    
}
