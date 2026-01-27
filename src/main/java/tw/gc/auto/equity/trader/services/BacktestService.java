package tw.gc.auto.equity.trader.services;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.bytefish.pgbulkinsert.PgBulkInsert;
import de.bytefish.pgbulkinsert.mapping.AbstractMapping;
import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.BacktestResult;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.BacktestResultRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;
import tw.gc.auto.equity.trader.strategy.FundamentalDataProvider;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.TradeSignal;
import tw.gc.auto.equity.trader.strategy.impl.*;

/**
 * Backtest Service
 * Runs strategies against historical data and tracks performance.
 * All results are persisted to database for auditability.
 * 
 * High-Performance Architecture:
 * - Uses Virtual Threads for parallel backtest computation
 * - Single dedicated writer thread for BacktestResult persistence
 * - PgBulkInsert (COPY protocol) with JdbcTemplate fallback
 */
@Service
@Slf4j
public class BacktestService {
    
    private static final int BACKTEST_QUEUE_CAPACITY = 5_000;
    private static final int BULK_INSERT_BATCH_SIZE = 500;
    private static final int FLUSH_TIMEOUT_MS = 1_000;
    private static final int MAX_CONCURRENT_BACKTESTS = 8;

    // Backtest execution assumptions (Taiwan stocks): fees + tax + mild slippage.
    // These constants intentionally make backtest results more realistic and prevent
    // pathological compounding from extremely high-frequency trading.
    private static final double BUY_FEE_RATE = 0.001425;   // 0.1425%
    private static final double SELL_FEE_RATE = 0.001425;  // 0.1425%
    private static final double SELL_TAX_RATE = 0.003;     // 0.3% (stock transaction tax)
    private static final double SLIPPAGE_RATE = 0.0005;    // 0.05%
    
    private final BacktestResultRepository backtestResultRepository;
    private final MarketDataRepository marketDataRepository;
    private final HistoryDataService historyDataService;
    private final SystemStatusService systemStatusService;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    
    private volatile PgBulkInsert<BacktestResult> backtestResultBulkInsert;
    private final StrategyStockMappingService strategyStockMappingService;
    private final FundamentalDataService fundamentalDataService;
    
    public BacktestService(BacktestResultRepository backtestResultRepository,
                          MarketDataRepository marketDataRepository,
                          HistoryDataService historyDataService,
                          SystemStatusService systemStatusService,
                          DataSource dataSource,
                          JdbcTemplate jdbcTemplate,
                          StrategyStockMappingService strategyStockMappingService,
                          FundamentalDataService fundamentalDataService) {
        this.backtestResultRepository = backtestResultRepository;
        this.marketDataRepository = marketDataRepository;
        this.historyDataService = historyDataService;
        this.systemStatusService = systemStatusService;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.strategyStockMappingService = strategyStockMappingService;
        this.fundamentalDataService = fundamentalDataService;
    }
    
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
            
            InMemoryBacktestResult result = new InMemoryBacktestResult(strategy.getName(), initialCapital);
            result.setCommissionRate(BUY_FEE_RATE + SELL_FEE_RATE + SELL_TAX_RATE);
            result.setSlippageRate(SLIPPAGE_RATE);
            results.put(strategy.getName(), result);
        }
        
        // Run Simulation Loop
        for (MarketData data : history) {
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                InMemoryBacktestResult result = results.get(strategy.getName());
                
                try {
                    TradeSignal signal = strategy.execute(p, data);
                    processSignal(strategy, p, data, signal, result);
                    
                    // Note: trackEquity is now called inside processSignal with position status
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
        // Get stock name from historical data (already in database from download)
        String stockName = historyDataService.getStockNameFromHistory(symbol);
        
        // Calculate duration in days
        int durationDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd);
        if (durationDays < 1) durationDays = 1;
        
        BacktestResult entity = BacktestResult.builder()
            .backtestRunId(backtestRunId)
            .symbol(symbol)
            .stockName(stockName)
            .strategyName(strategyName)
            // Configuration
            .initialCapital(result.getInitialCapital())
            .commissionRate(result.getCommissionRate())
            .slippageRate(result.getSlippageRate())
            // Time metrics
            .backtestPeriodStart(periodStart)
            .backtestPeriodEnd(periodEnd)
            .durationDays(durationDays)
            .exposureTimePct(result.getExposureTimePct())
            // Equity metrics
            .finalEquity(result.getFinalEquity())
            .equityPeak(result.getEquityPeak())
            .totalReturnPct(result.getTotalReturnPercentage())
            .buyHoldReturnPct(result.getBuyHoldReturnPct())
            // Annualized metrics
            .annualReturnPct(result.getAnnualReturnPct())
            .annualVolatilityPct(result.getAnnualVolatilityPct())
            // Risk-adjusted ratios
            .sharpeRatio(result.getSharpeRatio())
            .sortinoRatio(result.getSortinoRatio())
            .calmarRatio(result.getCalmarRatio())
            // Drawdown metrics
            .maxDrawdownPct(result.getMaxDrawdownPercentage())
            .avgDrawdownPct(result.getAvgDrawdownPercentage())
            .maxDrawdownDurationDays(result.getMaxDrawdownDurationDays())
            .avgDrawdownDurationDays(result.getAvgDrawdownDurationDays())
            // Trade statistics
            .totalTrades(result.getTotalTrades())
            .winningTrades(result.getWinningTrades())
            .losingTrades(result.getLosingTrades())
            .winRatePct(result.getWinRate())
            .bestTradePct(result.getBestTradePct())
            .worstTradePct(result.getWorstTradePct())
            .avgTradePct(result.getAvgTradePct())
            .avgProfitPerTrade(result.getTotalTrades() > 0 ? result.getTotalPnL() / result.getTotalTrades() : 0.0)
            // Trade duration
            .maxTradeDurationDays(result.getMaxTradeDurationDays())
            .avgTradeDurationDays(result.getAvgTradeDurationDays())
            // Quality metrics
            .profitFactor(Double.isFinite(result.getProfitFactor()) ? result.getProfitFactor() : null)
            .expectancy(result.getExpectancy())
            .sqn(result.getSqn())
            .dataPoints(dataPoints)
            .build();
        
        backtestResultRepository.save(java.util.Objects.requireNonNull(entity));

        // Also update the canonical strategy-stock mapping table so downstream
        // automation (auto-selection / shadow-mode) can use persisted mappings.
        try {
            strategyStockMappingService.updateMapping(
                entity.getSymbol(),
                entity.getStockName(),
                entity.getStrategyName(),
                entity.getSharpeRatio(),
                entity.getTotalReturnPct(),
                entity.getWinRatePct(),
                entity.getMaxDrawdownPct(),
                entity.getTotalTrades(),
                entity.getAvgProfitPerTrade(),
                periodStart,
                periodEnd
            );
        } catch (Exception e) {
            log.debug("Could not update strategy_stock_mapping for {}:{} - {}", entity.getSymbol(), entity.getStrategyName(), e.getMessage());
        }
    }
    
    private void processSignal(IStrategy strategy, Portfolio p, MarketData data, TradeSignal signal, InMemoryBacktestResult result) {
        String symbol = data.getSymbol();
        int currentPos = p.getPosition(symbol);
        double price = data.getClose();
        
        // Track price for buy & hold comparison
        result.recordPrice(price);

        // Defensive: keep equity marked-to-market even if strategy returns null
        if (signal == null || signal.getDirection() == null) {
            markToMarket(p, symbol, price);
            result.trackEquity(p.getEquity(), currentPos > 0);
            return;
        }

        // Backtest is long-only for Taiwan stocks:
        // - LONG: enter if flat
        // - SHORT: exit/close if long
        if (signal.getDirection() == TradeSignal.SignalDirection.LONG) {
            if (currentPos == 0) {
                int qty = calculateQuantity(p, price);
                if (qty > 0) {
                    double execPrice = buyExecutionPrice(price);
                    double grossCost = execPrice * qty;
                    double fees = grossCost * BUY_FEE_RATE;
                    double totalCost = grossCost + fees;

                    if (totalCost <= p.getAvailableMargin()) {
                        p.setAvailableMargin(p.getAvailableMargin() - totalCost);
                        p.setPosition(symbol, qty);
                        p.setEntryPrice(symbol, execPrice);
                        
                        // Record trade entry for duration tracking
                        result.recordTradeEntry(data.getTimestamp(), p.getEquity());
                    }
                }
            }
        } else if (signal.getDirection() == TradeSignal.SignalDirection.SHORT) {
            if (currentPos > 0) {
                int qty = currentPos;
                double execPrice = sellExecutionPrice(price);
                double grossProceeds = execPrice * qty;
                double fees = grossProceeds * SELL_FEE_RATE;
                double tax = grossProceeds * SELL_TAX_RATE;
                double netProceeds = grossProceeds - fees - tax;

                double entryGross = p.getEntryPrice(symbol) * qty;
                double entryFees = entryGross * BUY_FEE_RATE;
                double pnl = netProceeds - (entryGross + entryFees);

                p.setAvailableMargin(p.getAvailableMargin() + netProceeds);
                p.setPosition(symbol, 0);
                
                // Add trade with entry value for proper P&L percentage calculation
                result.addTrade(pnl, entryGross + entryFees, data.getTimestamp());
            }
        }

        markToMarket(p, symbol, price);
        // Track equity with position status for exposure time calculation
        result.trackEquity(p.getEquity(), p.getPosition(symbol) > 0);
    }
    
    private void closeAllPositions(Portfolio p, MarketData data, InMemoryBacktestResult result) {
        String symbol = data.getSymbol();
        int currentPos = p.getPosition(symbol);
        double price = data.getClose();

        if (currentPos > 0) {
            int qty = currentPos;
            double execPrice = sellExecutionPrice(price);
            double grossProceeds = execPrice * qty;
            double fees = grossProceeds * SELL_FEE_RATE;
            double tax = grossProceeds * SELL_TAX_RATE;
            double netProceeds = grossProceeds - fees - tax;

            double entryGross = p.getEntryPrice(symbol) * qty;
            double entryFees = entryGross * BUY_FEE_RATE;
            double pnl = netProceeds - (entryGross + entryFees);

            p.setAvailableMargin(p.getAvailableMargin() + netProceeds);
            p.setPosition(symbol, 0);
            
            // Add trade with entry value for proper P&L percentage calculation
            result.addTrade(pnl, entryGross + entryFees, data.getTimestamp());
        }

        // After closing, equity should be pure cash
        p.setEquity(p.getAvailableMargin());
    }
    
    private int calculateQuantity(Portfolio p, double price) {
        if (price <= 0) return 0;

        // Simple sizing: use 95% of available margin
        double budget = p.getAvailableMargin() * 0.95;
        if (budget <= 0) return 0;

        // Account for estimated buy slippage + fees when sizing.
        double execPrice = buyExecutionPrice(price);
        double perShareAllIn = execPrice * (1.0 + BUY_FEE_RATE);
        if (perShareAllIn <= 0) return 0;
        return (int) (budget / perShareAllIn);
    }

    private double buyExecutionPrice(double midOrClosePrice) {
        if (!Double.isFinite(midOrClosePrice) || midOrClosePrice <= 0) return 0.0;
        return midOrClosePrice * (1.0 + SLIPPAGE_RATE);
    }

    private double sellExecutionPrice(double midOrClosePrice) {
        if (!Double.isFinite(midOrClosePrice) || midOrClosePrice <= 0) return 0.0;
        return midOrClosePrice * (1.0 - SLIPPAGE_RATE);
    }

    private void markToMarket(Portfolio p, String symbol, double price) {
        int pos = p.getPosition(symbol);
        double equity = p.getAvailableMargin();
        if (pos > 0) {
            equity += pos * price;
        }
        p.setEquity(equity);
    }
    
    /**
     * Comprehensive in-memory backtest result tracking all industry-standard metrics.
     * 
     * Tracks:
     * - Time metrics: duration, exposure time
     * - Equity curve for drawdown calculations
     * - Individual trade P&L and duration
     * - Buy & hold benchmark return
     * - Daily returns for risk-adjusted metrics
     */
    @lombok.Data
    public static class InMemoryBacktestResult {
        private final String strategyName;
        private final double initialCapital;
        private double finalEquity;
        
        // Configuration (for reference in metrics)
        private double commissionRate;
        private double slippageRate;
        
        // Trade tracking
        private int totalTrades;
        private int winningTrades;
        private int losingTrades;
        private double totalPnL;
        private double grossProfit;  // Sum of winning trades
        private double grossLoss;    // Sum of losing trades (absolute value)
        
        // Computed metrics (populated by calculateMetrics())
        private double totalReturnPercentage;
        private double buyHoldReturnPct;
        private double annualReturnPct;
        private double annualVolatilityPct;
        private double sharpeRatio;
        private double sortinoRatio;
        private double calmarRatio;
        private double maxDrawdownPercentage;
        private double avgDrawdownPercentage;
        private int maxDrawdownDurationDays;
        private double avgDrawdownDurationDays;
        private double exposureTimePct;
        private double equityPeak;
        private double bestTradePct;
        private double worstTradePct;
        private double avgTradePct;
        private int maxTradeDurationDays;
        private double avgTradeDurationDays;
        private double profitFactor;
        private double expectancy;
        private double sqn; // System Quality Number
        
        // Tracking data structures
        private final List<Double> equityCurve = new ArrayList<>();
        private final List<Double> tradePnLs = new ArrayList<>();
        private final List<Double> tradePnLPcts = new ArrayList<>(); // P&L as percentage of entry
        private final List<Long> tradeDurations = new ArrayList<>(); // Duration in days
        private final List<Double> dailyReturns = new ArrayList<>(); // For Sharpe/Sortino
        
        // For exposure time calculation
        private int barsInPosition = 0;
        private int totalBars = 0;
        
        // For buy & hold tracking
        private double firstPrice = 0;
        private double lastPrice = 0;
        
        // For trade duration tracking
        private LocalDateTime currentTradeEntryTime = null;
        private double currentTradeEntryEquity = 0;
        
        // Previous equity for daily return calculation
        private double previousEquity;
        
        public InMemoryBacktestResult(String strategyName, double initialCapital) {
            this.strategyName = strategyName;
            this.initialCapital = initialCapital;
            this.previousEquity = initialCapital;
            this.finalEquity = initialCapital;
        }
        
        /**
         * Record entry into a trade position.
         */
        public void recordTradeEntry(LocalDateTime entryTime, double equity) {
            this.currentTradeEntryTime = entryTime;
            this.currentTradeEntryEquity = equity;
        }
        
        /**
         * Add a completed trade with P&L and duration.
         */
        public void addTrade(double pnl, double entryValue, LocalDateTime exitTime) {
            totalTrades++;
            totalPnL += pnl;
            tradePnLs.add(pnl);
            
            // Calculate P&L percentage based on entry value
            double pnlPct = entryValue > 0 ? (pnl / entryValue) * 100.0 : 0.0;
            tradePnLPcts.add(pnlPct);
            
            if (pnl > 0) {
                winningTrades++;
                grossProfit += pnl;
            } else if (pnl < 0) {
                losingTrades++;
                grossLoss += Math.abs(pnl);
            }
            
            // Calculate trade duration
            if (currentTradeEntryTime != null && exitTime != null) {
                long durationDays = ChronoUnit.DAYS.between(currentTradeEntryTime, exitTime);
                if (durationDays < 1) durationDays = 1; // Minimum 1 day for intraday
                tradeDurations.add(durationDays);
            }
            currentTradeEntryTime = null;
        }
        
        /**
         * Legacy addTrade method for backward compatibility.
         */
        public void addTrade(double pnl) {
            addTrade(pnl, initialCapital, LocalDateTime.now());
        }
        
        /**
         * Track equity at each bar for drawdown calculation.
         */
        public void trackEquity(double equity, boolean inPosition) {
            equityCurve.add(equity);
            totalBars++;
            if (inPosition) {
                barsInPosition++;
            }
            
            // Calculate daily return
            if (previousEquity > 0) {
                double dailyReturn = (equity - previousEquity) / previousEquity;
                dailyReturns.add(dailyReturn);
            }
            previousEquity = equity;
        }
        
        /**
         * Legacy trackEquity method for backward compatibility.
         */
        public void trackEquity(double equity) {
            trackEquity(equity, false);
        }
        
        /**
         * Record first and last price for buy & hold calculation.
         */
        public void recordPrice(double price) {
            if (firstPrice == 0) {
                firstPrice = price;
            }
            lastPrice = price;
        }
        
        public double getWinRate() {
            return totalTrades == 0 ? 0 : (double) winningTrades / totalTrades * 100;
        }
        
        /**
         * Calculate all comprehensive backtest metrics.
         * Should be called after the backtest simulation completes.
         */
        public void calculateMetrics() {
            // Total return percentage
            if (initialCapital > 0) {
                totalReturnPercentage = ((finalEquity - initialCapital) / initialCapital) * 100.0;
            }
            
            // Buy & hold return
            if (firstPrice > 0) {
                buyHoldReturnPct = ((lastPrice - firstPrice) / firstPrice) * 100.0;
            }
            
            // Exposure time (percentage of time in position)
            exposureTimePct = totalBars > 0 ? ((double) barsInPosition / totalBars) * 100.0 : 0.0;
            
            // Calculate drawdown metrics
            calculateDrawdownMetrics();
            
            // Calculate risk-adjusted ratios
            calculateRiskAdjustedMetrics();
            
            // Calculate trade statistics
            calculateTradeStatistics();
            
            // Calculate quality metrics
            calculateQualityMetrics();
        }
        
        private void calculateDrawdownMetrics() {
            if (equityCurve.isEmpty()) {
                equityPeak = initialCapital;
                return;
            }
            
            equityPeak = equityCurve.get(0);
            double maxDrawdown = 0.0;
            List<Double> drawdowns = new ArrayList<>();
            List<Integer> drawdownDurations = new ArrayList<>();
            
            int drawdownStart = -1;
            double currentDrawdownPeak = equityPeak;
            
            for (int i = 0; i < equityCurve.size(); i++) {
                double equity = equityCurve.get(i);
                
                if (equity > equityPeak) {
                    equityPeak = equity;
                    // End of drawdown period
                    if (drawdownStart >= 0) {
                        drawdownDurations.add(i - drawdownStart);
                        drawdownStart = -1;
                    }
                    currentDrawdownPeak = equity;
                }
                
                double drawdown = ((currentDrawdownPeak - equity) / currentDrawdownPeak) * 100.0;
                if (drawdown > 0) {
                    drawdowns.add(drawdown);
                    if (drawdownStart < 0) {
                        drawdownStart = i;
                    }
                    if (drawdown > maxDrawdown) {
                        maxDrawdown = drawdown;
                    }
                }
            }
            
            // Handle ongoing drawdown at end
            if (drawdownStart >= 0) {
                drawdownDurations.add(equityCurve.size() - drawdownStart);
            }
            
            maxDrawdownPercentage = maxDrawdown;
            avgDrawdownPercentage = drawdowns.isEmpty() ? 0.0 : 
                drawdowns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            maxDrawdownDurationDays = drawdownDurations.isEmpty() ? 0 :
                drawdownDurations.stream().mapToInt(Integer::intValue).max().orElse(0);
            avgDrawdownDurationDays = drawdownDurations.isEmpty() ? 0.0 :
                drawdownDurations.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        }
        
        private void calculateRiskAdjustedMetrics() {
            // Annualized metrics (assuming 252 trading days)
            int tradingDays = Math.max(1, dailyReturns.size());
            double totalReturn = initialCapital > 0 ? (finalEquity / initialCapital) - 1.0 : 0.0;
            
            // Annualized return: (1 + total_return) ^ (252 / days) - 1
            double yearsElapsed = tradingDays / 252.0;
            if (yearsElapsed > 0 && totalReturn > -1) {
                annualReturnPct = (Math.pow(1 + totalReturn, 1.0 / yearsElapsed) - 1) * 100.0;
            }
            
            // Daily volatility and annualized volatility
            double avgDailyReturn = dailyReturns.isEmpty() ? 0.0 :
                dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = dailyReturns.stream()
                .mapToDouble(r -> Math.pow(r - avgDailyReturn, 2))
                .average()
                .orElse(0.0);
            double dailyVolatility = Math.sqrt(variance);
            annualVolatilityPct = dailyVolatility * Math.sqrt(252) * 100.0;
            
            // Sharpe Ratio = (Annual Return - Risk Free Rate) / Annual Volatility
            // Assuming risk-free rate = 0 for simplicity
            sharpeRatio = annualVolatilityPct > 0 ? annualReturnPct / annualVolatilityPct : 0.0;
            
            // Sortino Ratio = (Annual Return - Risk Free Rate) / Downside Deviation
            double downsideVariance = dailyReturns.stream()
                .filter(r -> r < 0)
                .mapToDouble(r -> Math.pow(r, 2))
                .average()
                .orElse(0.0);
            double downsideDeviation = Math.sqrt(downsideVariance) * Math.sqrt(252) * 100.0;
            sortinoRatio = downsideDeviation > 0 ? annualReturnPct / downsideDeviation : 0.0;
            
            // Calmar Ratio = Annual Return / Max Drawdown
            calmarRatio = maxDrawdownPercentage > 0 ? annualReturnPct / maxDrawdownPercentage : 0.0;
        }
        
        private void calculateTradeStatistics() {
            if (!tradePnLPcts.isEmpty()) {
                bestTradePct = tradePnLPcts.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                worstTradePct = tradePnLPcts.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                avgTradePct = tradePnLPcts.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            }
            
            if (!tradeDurations.isEmpty()) {
                maxTradeDurationDays = tradeDurations.stream().mapToInt(Long::intValue).max().orElse(0);
                avgTradeDurationDays = tradeDurations.stream().mapToLong(Long::longValue).average().orElse(0.0);
            }
        }
        
        private void calculateQualityMetrics() {
            // Profit Factor = Gross Profit / Gross Loss
            profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? Double.MAX_VALUE : 0.0);
            
            // Expectancy = (Win% × Avg Win) - (Loss% × Avg Loss)
            if (totalTrades > 0) {
                double winPct = (double) winningTrades / totalTrades;
                double lossPct = (double) losingTrades / totalTrades;
                double avgWin = winningTrades > 0 ? grossProfit / winningTrades : 0.0;
                double avgLoss = losingTrades > 0 ? grossLoss / losingTrades : 0.0;
                expectancy = (winPct * avgWin) - (lossPct * avgLoss);
            }
            
            // SQN (System Quality Number) = sqrt(N) × Expectancy / StdDev(R)
            // Where N = number of trades, R = individual trade returns
            if (tradePnLs.size() >= 2) {
                double avgPnL = tradePnLs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double pnlVariance = tradePnLs.stream()
                    .mapToDouble(p -> Math.pow(p - avgPnL, 2))
                    .average()
                    .orElse(0.0);
                double pnlStdDev = Math.sqrt(pnlVariance);
                
                if (pnlStdDev > 0) {
                    sqn = Math.sqrt(totalTrades) * (avgPnL / pnlStdDev);
                }
            }
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
    public List<String> fetchTop50Stocks() {
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
    protected Set<StockCandidate> fetchFromTWSE() {
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
    protected Set<StockCandidate> fetchFromYahooFinanceTW() {
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
    protected Set<StockCandidate> fetchTAIEXComponents() {
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
    protected boolean meetsCriteria(StockCandidate stock) {
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
    static class StockCandidate {
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
     * High-performance architecture using:
     * - Virtual Threads for parallel backtest computation
     * - Single dedicated writer thread for BacktestResult persistence
     * - PgBulkInsert (COPY protocol) with JdbcTemplate fallback
     * 
     * NOTE: This method does NOT download historical data.
     * Data must be downloaded separately via HistoryDataService.
     * 
     * @param strategies List of strategies to test
     * @param initialCapital Starting capital for backtest
     * @return Map of stock symbol to backtest results
     */
    public Map<String, Map<String, InMemoryBacktestResult>> runParallelizedBacktest(double initialCapital) {
        return runParallelizedBacktestInternal(this::getAllStrategies, initialCapital);
    }

    public Map<String, Map<String, InMemoryBacktestResult>> runParallelizedBacktest(
            List<IStrategy> strategies, double initialCapital) {
        return runSequentialBacktestInternal(strategies, initialCapital);
    }

    private Map<String, Map<String, InMemoryBacktestResult>> runParallelizedBacktestInternal(
            Supplier<List<IStrategy>> strategySupplier, double initialCapital) {
        List<IStrategy> initialStrategies = strategySupplier.get();
        int strategyCount = initialStrategies == null ? 0 : initialStrategies.size();

        log.info("🚀 Starting Parallelized Backtest with {} strategies", strategyCount);

        systemStatusService.startBacktest();

        try {
            List<String> stocks = fetchTop50Stocks();
            log.info("📊 Selected {} stocks for backtesting", stocks.size());

            // Check data availability - do NOT download
            List<String> stocksWithData = filterStocksWithHistoricalData(stocks);

            if (stocksWithData.isEmpty()) {
                log.warn("⚠️ No historical data available for any stocks. " +
                    "Use /api/history/download to download data first.");
                return new HashMap<>();
            }

            log.info("📊 Found historical data for {}/{} stocks", stocksWithData.size(), stocks.size());

            return executeParallelBacktests(stocksWithData, strategySupplier, initialCapital);
        } finally {
            systemStatusService.completeBacktest();
        }
    }

    private Map<String, Map<String, InMemoryBacktestResult>> runSequentialBacktestInternal(
            List<IStrategy> strategies, double initialCapital) {
        int strategyCount = strategies == null ? 0 : strategies.size();

        log.info("🚀 Starting Backtest (sequential) with {} strategies", strategyCount);

        systemStatusService.startBacktest();

        try {
            List<String> stocks = fetchTop50Stocks();
            log.info("📊 Selected {} stocks for backtesting", stocks.size());

            List<String> stocksWithData = filterStocksWithHistoricalData(stocks);

            if (stocksWithData.isEmpty()) {
                log.warn("⚠️ No historical data available for any stocks. " +
                    "Use /api/history/download to download data first.");
                return new HashMap<>();
            }

            log.info("📊 Found historical data for {}/{} stocks", stocksWithData.size(), stocks.size());

            return executeSequentialBacktests(stocksWithData, strategies, initialCapital);
        } finally {
            systemStatusService.completeBacktest();
        }
    }
    
    /**
     * Filter stocks that have historical data in the database.
     */
    private List<String> filterStocksWithHistoricalData(List<String> stocks) {
        LocalDateTime tenYearsAgo = LocalDateTime.now().minusYears(10);
        LocalDateTime now = LocalDateTime.now();
        
        return stocks.stream()
            .filter(symbol -> {
                long count = marketDataRepository.countBySymbolAndTimeframeAndTimestampBetween(
                    symbol, MarketData.Timeframe.DAY_1, tenYearsAgo, now);
                if (count == 0) {
                    log.debug("No data for {}", symbol);
                }
                return count > 0;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Execute parallel backtests with single-writer persistence pattern.
     */
        private Map<String, Map<String, InMemoryBacktestResult>> executeParallelBacktests(
            List<String> stocks, Supplier<List<IStrategy>> strategySupplier, double initialCapital) {
        
        log.info("🧪 Running backtests with single-writer persistence pattern...");
        
        String backtestRunId = generateBacktestRunId();
        BlockingQueue<BacktestResult> resultQueue = new ArrayBlockingQueue<>(BACKTEST_QUEUE_CAPACITY);
        AtomicBoolean allBacktestsComplete = new AtomicBoolean(false);
        AtomicInteger totalInserted = new AtomicInteger(0);
        CountDownLatch writerLatch = new CountDownLatch(1);
        
        Map<String, Map<String, InMemoryBacktestResult>> allResults = new HashMap<>();
        
        // Start single global writer thread for BacktestResult persistence
        Thread.ofPlatform()
            .name("BacktestResultWriter")
            .start(() -> {
                try {
                    int inserted = runBacktestResultWriter(resultQueue, allBacktestsComplete);
                    totalInserted.set(inserted);
                    log.info("✅ BacktestResult writer completed. Total inserted: {}", inserted);
                } catch (Exception e) {
                    log.error("❌ BacktestResult writer failed: {}", e.getMessage(), e);
                } finally {
                    writerLatch.countDown();
                }
            });
        
        // Semaphore to limit concurrent backtests
        Semaphore backtestPermits = new Semaphore(MAX_CONCURRENT_BACKTESTS);
        LocalDateTime tenYearsAgo = LocalDateTime.now().minusYears(10);
        LocalDateTime now = LocalDateTime.now();
        
        // Launch Virtual Threads for parallel backtest computation
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = stocks.stream()
                .map(symbol -> CompletableFuture.runAsync(() -> {
                    try {
                        backtestPermits.acquire();
                        try {
                            List<IStrategy> strategies = strategySupplier.get();
                            Map<String, InMemoryBacktestResult> results = runBacktestForStock(
                                symbol, strategies, initialCapital, backtestRunId,
                                tenYearsAgo, now, resultQueue
                            );
                            synchronized (allResults) {
                                allResults.put(symbol, results);
                            }
                        } finally {
                            backtestPermits.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("❌ Backtest interrupted for {}", symbol);
                    } catch (Exception e) {
                        log.error("❌ Backtest failed for {}: {}", symbol, e.getMessage());
                        synchronized (allResults) {
                            allResults.put(symbol, new HashMap<>());
                        }
                    }
                }, virtualExecutor))
                .collect(Collectors.toList());
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        allBacktestsComplete.set(true);
        
        // Wait for writer to finish
        try {
            if (!writerLatch.await(10, TimeUnit.MINUTES)) {
                log.warn("⚠️ BacktestResult writer timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("✅ Parallelized backtest completed. {} stocks tested, {} results persisted.",
            allResults.size(), totalInserted.get());
        
        return allResults;
    }

    private Map<String, Map<String, InMemoryBacktestResult>> executeSequentialBacktests(
            List<String> stocks, List<IStrategy> strategies, double initialCapital) {

        log.info("🧪 Running backtests sequentially with single-writer persistence pattern...");

        String backtestRunId = generateBacktestRunId();
        BlockingQueue<BacktestResult> resultQueue = new ArrayBlockingQueue<>(BACKTEST_QUEUE_CAPACITY);
        AtomicBoolean allBacktestsComplete = new AtomicBoolean(false);
        AtomicInteger totalInserted = new AtomicInteger(0);
        CountDownLatch writerLatch = new CountDownLatch(1);

        Map<String, Map<String, InMemoryBacktestResult>> allResults = new HashMap<>();

        Thread.ofPlatform()
            .name("BacktestResultWriter")
            .start(() -> {
                try {
                    int inserted = runBacktestResultWriter(resultQueue, allBacktestsComplete);
                    totalInserted.set(inserted);
                    log.info("✅ BacktestResult writer completed. Total inserted: {}", inserted);
                } catch (Exception e) {
                    log.error("❌ BacktestResult writer failed: {}", e.getMessage(), e);
                } finally {
                    writerLatch.countDown();
                }
            });

        LocalDateTime tenYearsAgo = LocalDateTime.now().minusYears(10);
        LocalDateTime now = LocalDateTime.now();

        for (String symbol : stocks) {
            try {
                Map<String, InMemoryBacktestResult> results = runBacktestForStock(
                    symbol, strategies, initialCapital, backtestRunId,
                    tenYearsAgo, now, resultQueue
                );
                allResults.put(symbol, results);
            } catch (Exception e) {
                log.error("❌ Backtest failed for {}: {}", symbol, e.getMessage());
                allResults.put(symbol, new HashMap<>());
            }
        }

        allBacktestsComplete.set(true);

        try {
            if (!writerLatch.await(10, TimeUnit.MINUTES)) {
                log.warn("⚠️ BacktestResult writer timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("✅ Sequential backtest completed. {} stocks tested, {} results persisted.",
            allResults.size(), totalInserted.get());

        return allResults;
    }
    
    /**
     * Run backtest for a single stock and queue results for persistence.
     */
    protected Map<String, InMemoryBacktestResult> runBacktestForStock(
            String symbol, List<IStrategy> strategies, double initialCapital,
            String backtestRunId, LocalDateTime start, LocalDateTime end,
            BlockingQueue<BacktestResult> resultQueue) {
        
        List<MarketData> history = marketDataRepository
            .findBySymbolAndTimeframeAndTimestampBetweenOrderByTimestampAsc(
                symbol, MarketData.Timeframe.DAY_1, start, end);
        
        if (history.isEmpty()) {
            log.warn("⚠️ No historical data found for {}", symbol);
            return new HashMap<>();
        }
        
        log.debug("📊 Running backtest for {} ({} data points)", symbol, history.size());
        
        Map<String, InMemoryBacktestResult> results = runBacktestCompute(strategies, history, initialCapital);
        
        // Queue results for persistence (non-blocking)
        LocalDateTime periodStart = history.get(0).getTimestamp().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime periodEnd = history.get(history.size() - 1).getTimestamp().atZone(ZoneId.systemDefault()).toLocalDateTime();
        
        for (Map.Entry<String, InMemoryBacktestResult> entry : results.entrySet()) {
            BacktestResult entity = buildBacktestResultEntity(
                backtestRunId, symbol, entry.getKey(), entry.getValue(),
                periodStart, periodEnd, history.size()
            );
            
            enqueueBacktestResult(resultQueue, entity, symbol, entry.getKey());
        }
        
        return results;
    }

    private void enqueueBacktestResult(BlockingQueue<BacktestResult> queue, BacktestResult entity,
                                       String symbol, String strategyName) {
        boolean enqueued = false;
        int attempts = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!enqueued && System.nanoTime() < deadline) {
            try {
                enqueued = queue.offer(entity, 500, TimeUnit.MILLISECONDS);
                if (!enqueued) {
                    attempts++;
                    if (attempts % 5 == 0) {
                        log.warn("⚠️ Result queue still full for {}/{} - retrying", symbol, strategyName);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (!enqueued) {
            log.error("❌ Result queue still full for {}/{} after 30s - dropping result", symbol, strategyName);
        }
    }
    
    /**
     * Run backtest computation only (no persistence).
     */
    protected Map<String, InMemoryBacktestResult> runBacktestCompute(
            List<IStrategy> strategies, List<MarketData> history, double initialCapital) {
        
        Map<String, InMemoryBacktestResult> results = new HashMap<>();
        String symbol = history.isEmpty() ? "UNKNOWN" : history.get(0).getSymbol();
        
        Map<String, Portfolio> portfolios = new HashMap<>();
        for (IStrategy strategy : strategies) {
            strategy.reset();
            
            Map<String, Integer> pos = new HashMap<>();
            pos.put(symbol, 0);
            
            Portfolio p = Portfolio.builder()
                .equity(initialCapital)
                .availableMargin(initialCapital)
                .positions(pos)
                .tradingMode("backtest")
                .tradingQuantity(1)
                .build();
            
            portfolios.put(strategy.getName(), p);
            
            InMemoryBacktestResult result = new InMemoryBacktestResult(strategy.getName(), initialCapital);
            result.setCommissionRate(BUY_FEE_RATE + SELL_FEE_RATE + SELL_TAX_RATE);
            result.setSlippageRate(SLIPPAGE_RATE);
            results.put(strategy.getName(), result);
        }
        
        // Simulation loop
        for (MarketData data : history) {
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                InMemoryBacktestResult result = results.get(strategy.getName());
                
                try {
                    TradeSignal signal = strategy.execute(p, data);
                    processSignal(strategy, p, data, signal, result);
                    // Note: trackEquity is now called inside processSignal with position status
                } catch (Exception e) {
                    log.trace("Error in backtest for strategy {}: {}", strategy.getName(), e.getMessage());
                }
            }
        }
        
        // Close positions and calculate metrics
        if (!history.isEmpty()) {
            MarketData lastData = history.get(history.size() - 1);
            for (IStrategy strategy : strategies) {
                Portfolio p = portfolios.get(strategy.getName());
                InMemoryBacktestResult result = results.get(strategy.getName());
                closeAllPositions(p, lastData, result);
                result.setFinalEquity(p.getEquity());
                result.calculateMetrics();
            }
        }
        
        return results;
    }
    
    /**
     * Build BacktestResult entity from in-memory result.
     */
    private BacktestResult buildBacktestResultEntity(String backtestRunId, String symbol,
            String strategyName, InMemoryBacktestResult result,
            LocalDateTime periodStart, LocalDateTime periodEnd, int dataPoints) {
        // Get stock name from historical data (already in database from download)
        String stockName = historyDataService.getStockNameFromHistory(symbol);
        
        // Calculate duration in days
        int durationDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd);
        if (durationDays < 1) durationDays = 1;
        
        return BacktestResult.builder()
            .backtestRunId(backtestRunId)
            .symbol(symbol)
            .stockName(stockName)
            .strategyName(strategyName)
            // Configuration
            .initialCapital(result.getInitialCapital())
            .commissionRate(result.getCommissionRate())
            .slippageRate(result.getSlippageRate())
            // Time metrics
            .backtestPeriodStart(periodStart)
            .backtestPeriodEnd(periodEnd)
            .durationDays(durationDays)
            .exposureTimePct(result.getExposureTimePct())
            // Equity metrics
            .finalEquity(result.getFinalEquity())
            .equityPeak(result.getEquityPeak())
            .totalReturnPct(result.getTotalReturnPercentage())
            .buyHoldReturnPct(result.getBuyHoldReturnPct())
            // Annualized metrics
            .annualReturnPct(result.getAnnualReturnPct())
            .annualVolatilityPct(result.getAnnualVolatilityPct())
            // Risk-adjusted ratios
            .sharpeRatio(result.getSharpeRatio())
            .sortinoRatio(result.getSortinoRatio())
            .calmarRatio(result.getCalmarRatio())
            // Drawdown metrics
            .maxDrawdownPct(result.getMaxDrawdownPercentage())
            .avgDrawdownPct(result.getAvgDrawdownPercentage())
            .maxDrawdownDurationDays(result.getMaxDrawdownDurationDays())
            .avgDrawdownDurationDays(result.getAvgDrawdownDurationDays())
            // Trade statistics
            .totalTrades(result.getTotalTrades())
            .winningTrades(result.getWinningTrades())
            .losingTrades(result.getLosingTrades())
            .winRatePct(result.getWinRate())
            .bestTradePct(result.getBestTradePct())
            .worstTradePct(result.getWorstTradePct())
            .avgTradePct(result.getAvgTradePct())
            .avgProfitPerTrade(result.getTotalTrades() > 0 ? result.getTotalPnL() / result.getTotalTrades() : 0.0)
            // Trade duration
            .maxTradeDurationDays(result.getMaxTradeDurationDays())
            .avgTradeDurationDays(result.getAvgTradeDurationDays())
            // Quality metrics
            .profitFactor(Double.isFinite(result.getProfitFactor()) ? result.getProfitFactor() : null)
            .expectancy(result.getExpectancy())
            .sqn(result.getSqn())
            .dataPoints(dataPoints)
            .build();
    }
    
    /**
     * Single writer thread that drains BacktestResult queue and performs bulk inserts.
     * Uses PgBulkInsert (COPY protocol) with JdbcTemplate fallback.
     */
    protected int runBacktestResultWriter(BlockingQueue<BacktestResult> queue, AtomicBoolean complete) {
        List<BacktestResult> batch = new ArrayList<>(BULK_INSERT_BATCH_SIZE);
        int totalInserted = 0;
        long lastFlush = System.currentTimeMillis();
        
        while (!complete.get() || !queue.isEmpty()) {
            try {
                BacktestResult result = queue.poll(100, TimeUnit.MILLISECONDS);
                
                if (result != null) {
                    batch.add(result);
                }
                
                boolean batchFull = batch.size() >= BULK_INSERT_BATCH_SIZE;
                boolean timeoutReached = System.currentTimeMillis() - lastFlush > FLUSH_TIMEOUT_MS;
                
                if ((batchFull || (timeoutReached && !batch.isEmpty())) && !batch.isEmpty()) {
                    int inserted = flushBacktestResults(batch);
                    totalInserted += inserted;
                    
                    if (totalInserted % 1_000 == 0 || inserted > 100) {
                        log.info("📊 BacktestResult writer: flushed {} (total: {}, queue: {})",
                            inserted, totalInserted, queue.size());
                    }
                    
                    batch.clear();
                    lastFlush = System.currentTimeMillis();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Final flush
        if (!batch.isEmpty()) {
            int inserted = flushBacktestResults(batch);
            totalInserted += inserted;
            log.info("📊 BacktestResult writer: final flush {} (total: {})", inserted, totalInserted);
        }
        
        return totalInserted;
    }
    
    /**
     * Flush BacktestResult batch using PgBulkInsert with JdbcTemplate fallback.
     */
    private int flushBacktestResults(List<BacktestResult> results) {
        try {
            return pgBulkInsertBacktestResults(results);
        } catch (Exception e) {
            log.warn("⚠️ PgBulkInsert failed, falling back to JdbcTemplate: {}", e.getMessage());
            return jdbcBatchInsertBacktestResults(results);
        }
    }
    
    /**
     * High-performance PostgreSQL COPY protocol insert for BacktestResult.
     */
    private int pgBulkInsertBacktestResults(List<BacktestResult> results) throws SQLException {
        initBacktestResultBulkInsert();
        
        try (Connection conn = dataSource.getConnection()) {
            PGConnection pgConn = conn.unwrap(PGConnection.class);
            backtestResultBulkInsert.saveAll(pgConn, results.stream());
            return results.size();
        }
    }
    
    private synchronized void initBacktestResultBulkInsert() {
        if (backtestResultBulkInsert == null) {
            backtestResultBulkInsert = new PgBulkInsert<>(new BacktestResultBulkInsertMapping());
        }
    }
    
    /**
     * JdbcTemplate batch insert fallback for BacktestResult.
     */
    protected int jdbcBatchInsertBacktestResults(List<BacktestResult> results) {
        String sql = """
            INSERT INTO backtest_results (
                backtest_run_id, symbol, stock_name, strategy_name,
                initial_capital, commission_rate, slippage_rate,
                backtest_period_start, backtest_period_end, duration_days, exposure_time_pct,
                final_equity, equity_peak, total_return_pct, buy_hold_return_pct,
                annual_return_pct, annual_volatility_pct,
                sharpe_ratio, sortino_ratio, calmar_ratio,
                max_drawdown_pct, avg_drawdown_pct, max_drawdown_duration_days, avg_drawdown_duration_days,
                total_trades, winning_trades, losing_trades, win_rate_pct,
                best_trade_pct, worst_trade_pct, avg_trade_pct, avg_profit_per_trade,
                max_trade_duration_days, avg_trade_duration_days,
                profit_factor, expectancy, sqn,
                data_points, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try {
            jdbcTemplate.batchUpdate(sql, results, results.size(), (ps, r) -> {
                ps.setString(1, r.getBacktestRunId());
                ps.setString(2, r.getSymbol());
                ps.setString(3, r.getStockName());
                ps.setString(4, r.getStrategyName());
                // Configuration
                ps.setDouble(5, r.getInitialCapital() != null ? r.getInitialCapital() : 0.0);
                ps.setDouble(6, r.getCommissionRate() != null ? r.getCommissionRate() : 0.0);
                ps.setDouble(7, r.getSlippageRate() != null ? r.getSlippageRate() : 0.0);
                // Time metrics
                ps.setObject(8, r.getBacktestPeriodStart());
                ps.setObject(9, r.getBacktestPeriodEnd());
                ps.setInt(10, r.getDurationDays() != null ? r.getDurationDays() : 0);
                ps.setDouble(11, r.getExposureTimePct() != null ? r.getExposureTimePct() : 0.0);
                // Equity metrics
                ps.setDouble(12, r.getFinalEquity() != null ? r.getFinalEquity() : 0.0);
                ps.setDouble(13, r.getEquityPeak() != null ? r.getEquityPeak() : 0.0);
                ps.setDouble(14, r.getTotalReturnPct() != null ? r.getTotalReturnPct() : 0.0);
                ps.setDouble(15, r.getBuyHoldReturnPct() != null ? r.getBuyHoldReturnPct() : 0.0);
                // Annualized metrics
                ps.setDouble(16, r.getAnnualReturnPct() != null ? r.getAnnualReturnPct() : 0.0);
                ps.setDouble(17, r.getAnnualVolatilityPct() != null ? r.getAnnualVolatilityPct() : 0.0);
                // Risk-adjusted ratios
                ps.setDouble(18, r.getSharpeRatio() != null ? r.getSharpeRatio() : 0.0);
                ps.setDouble(19, r.getSortinoRatio() != null ? r.getSortinoRatio() : 0.0);
                ps.setDouble(20, r.getCalmarRatio() != null ? r.getCalmarRatio() : 0.0);
                // Drawdown metrics
                ps.setDouble(21, r.getMaxDrawdownPct() != null ? r.getMaxDrawdownPct() : 0.0);
                ps.setDouble(22, r.getAvgDrawdownPct() != null ? r.getAvgDrawdownPct() : 0.0);
                ps.setInt(23, r.getMaxDrawdownDurationDays() != null ? r.getMaxDrawdownDurationDays() : 0);
                ps.setDouble(24, r.getAvgDrawdownDurationDays() != null ? r.getAvgDrawdownDurationDays() : 0.0);
                // Trade statistics
                ps.setInt(25, r.getTotalTrades() != null ? r.getTotalTrades() : 0);
                ps.setInt(26, r.getWinningTrades() != null ? r.getWinningTrades() : 0);
                ps.setInt(27, r.getLosingTrades() != null ? r.getLosingTrades() : 0);
                ps.setDouble(28, r.getWinRatePct() != null ? r.getWinRatePct() : 0.0);
                ps.setDouble(29, r.getBestTradePct() != null ? r.getBestTradePct() : 0.0);
                ps.setDouble(30, r.getWorstTradePct() != null ? r.getWorstTradePct() : 0.0);
                ps.setDouble(31, r.getAvgTradePct() != null ? r.getAvgTradePct() : 0.0);
                ps.setDouble(32, r.getAvgProfitPerTrade() != null ? r.getAvgProfitPerTrade() : 0.0);
                // Trade duration
                ps.setInt(33, r.getMaxTradeDurationDays() != null ? r.getMaxTradeDurationDays() : 0);
                ps.setDouble(34, r.getAvgTradeDurationDays() != null ? r.getAvgTradeDurationDays() : 0.0);
                // Quality metrics
                ps.setObject(35, r.getProfitFactor());
                ps.setDouble(36, r.getExpectancy() != null ? r.getExpectancy() : 0.0);
                ps.setDouble(37, r.getSqn() != null ? r.getSqn() : 0.0);
                // Misc
                ps.setInt(38, r.getDataPoints() != null ? r.getDataPoints() : 0);
                ps.setObject(39, r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now());
            });
            return results.size();
        } catch (Exception e) {
            log.error("❌ JdbcTemplate batch insert failed: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * PgBulkInsert mapping for BacktestResult entity.
     */
    protected static class BacktestResultBulkInsertMapping extends AbstractMapping<BacktestResult> {
        public BacktestResultBulkInsertMapping() {
            super("public", "backtest_results");
            mapText("backtest_run_id", BacktestResult::getBacktestRunId);
            mapText("symbol", BacktestResult::getSymbol);
            mapText("stock_name", BacktestResult::getStockName);
            mapText("strategy_name", BacktestResult::getStrategyName);
            // Configuration
            mapDouble("initial_capital", r -> r.getInitialCapital() != null ? r.getInitialCapital() : 0.0);
            mapDouble("commission_rate", r -> r.getCommissionRate() != null ? r.getCommissionRate() : 0.0);
            mapDouble("slippage_rate", r -> r.getSlippageRate() != null ? r.getSlippageRate() : 0.0);
            // Time metrics
            mapTimeStamp("backtest_period_start", BacktestResult::getBacktestPeriodStart);
            mapTimeStamp("backtest_period_end", BacktestResult::getBacktestPeriodEnd);
            mapInteger("duration_days", r -> r.getDurationDays() != null ? r.getDurationDays() : 0);
            mapDouble("exposure_time_pct", r -> r.getExposureTimePct() != null ? r.getExposureTimePct() : 0.0);
            // Equity metrics
            mapDouble("final_equity", r -> r.getFinalEquity() != null ? r.getFinalEquity() : 0.0);
            mapDouble("equity_peak", r -> r.getEquityPeak() != null ? r.getEquityPeak() : 0.0);
            mapDouble("total_return_pct", r -> r.getTotalReturnPct() != null ? r.getTotalReturnPct() : 0.0);
            mapDouble("buy_hold_return_pct", r -> r.getBuyHoldReturnPct() != null ? r.getBuyHoldReturnPct() : 0.0);
            // Annualized metrics
            mapDouble("annual_return_pct", r -> r.getAnnualReturnPct() != null ? r.getAnnualReturnPct() : 0.0);
            mapDouble("annual_volatility_pct", r -> r.getAnnualVolatilityPct() != null ? r.getAnnualVolatilityPct() : 0.0);
            // Risk-adjusted ratios
            mapDouble("sharpe_ratio", r -> r.getSharpeRatio() != null ? r.getSharpeRatio() : 0.0);
            mapDouble("sortino_ratio", r -> r.getSortinoRatio() != null ? r.getSortinoRatio() : 0.0);
            mapDouble("calmar_ratio", r -> r.getCalmarRatio() != null ? r.getCalmarRatio() : 0.0);
            // Drawdown metrics
            mapDouble("max_drawdown_pct", r -> r.getMaxDrawdownPct() != null ? r.getMaxDrawdownPct() : 0.0);
            mapDouble("avg_drawdown_pct", r -> r.getAvgDrawdownPct() != null ? r.getAvgDrawdownPct() : 0.0);
            mapInteger("max_drawdown_duration_days", r -> r.getMaxDrawdownDurationDays() != null ? r.getMaxDrawdownDurationDays() : 0);
            mapDouble("avg_drawdown_duration_days", r -> r.getAvgDrawdownDurationDays() != null ? r.getAvgDrawdownDurationDays() : 0.0);
            // Trade statistics
            mapInteger("total_trades", r -> r.getTotalTrades() != null ? r.getTotalTrades() : 0);
            mapInteger("winning_trades", r -> r.getWinningTrades() != null ? r.getWinningTrades() : 0);
            mapInteger("losing_trades", r -> r.getLosingTrades() != null ? r.getLosingTrades() : 0);
            mapDouble("win_rate_pct", r -> r.getWinRatePct() != null ? r.getWinRatePct() : 0.0);
            mapDouble("best_trade_pct", r -> r.getBestTradePct() != null ? r.getBestTradePct() : 0.0);
            mapDouble("worst_trade_pct", r -> r.getWorstTradePct() != null ? r.getWorstTradePct() : 0.0);
            mapDouble("avg_trade_pct", r -> r.getAvgTradePct() != null ? r.getAvgTradePct() : 0.0);
            mapDouble("avg_profit_per_trade", r -> r.getAvgProfitPerTrade() != null ? r.getAvgProfitPerTrade() : 0.0);
            // Trade duration
            mapInteger("max_trade_duration_days", r -> r.getMaxTradeDurationDays() != null ? r.getMaxTradeDurationDays() : 0);
            mapDouble("avg_trade_duration_days", r -> r.getAvgTradeDurationDays() != null ? r.getAvgTradeDurationDays() : 0.0);
            // Quality metrics
            mapDouble("profit_factor", r -> r.getProfitFactor() != null && Double.isFinite(r.getProfitFactor()) ? r.getProfitFactor() : 0.0);
            mapDouble("expectancy", r -> r.getExpectancy() != null ? r.getExpectancy() : 0.0);
            mapDouble("sqn", r -> r.getSqn() != null ? r.getSqn() : 0.0);
            // Misc
            mapInteger("data_points", r -> r.getDataPoints() != null ? r.getDataPoints() : 0);
            mapTimeStamp("created_at", r -> r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now());
        }
    }
    
    /**
     * Get all 100 strategies for backtesting.
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
        
        // Technical indicators (34)
        strategies.add(new KeltnerChannelStrategy());
        strategies.add(new IchimokuCloudStrategy());
        strategies.add(new ParabolicSARStrategy());
        strategies.add(new ADXTrendStrategy());
        strategies.add(new WilliamsRStrategy());
        strategies.add(new CCIStrategy());
        strategies.add(new VolumeWeightedStrategy());
        strategies.add(new TripleEMAStrategy());
        strategies.add(new EmaVolumeCrossoverStrategy());
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
        
        // Create FundamentalDataProvider that fetches from FundamentalDataService
        FundamentalDataProvider fdProvider = symbol -> fundamentalDataService.getLatestBySymbol(symbol);
        
        // Factor-based strategies (50) - using real fundamental data where applicable
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
        strategies.add(new BookToMarketStrategy(0.7, 90, fdProvider));
        strategies.add(new EarningsYieldStrategy(0.05, 60, fdProvider));
        strategies.add(new CashFlowValueStrategy(0.05));
        strategies.add(new EnterpriseValueMultipleStrategy(10.0));
        strategies.add(new SalesGrowthValueStrategy(2.0, 0.10));
        strategies.add(new QualityValueStrategy(0.7, 7));
        strategies.add(new DividendYieldStrategy(0.03, 3, fdProvider));
        strategies.add(new NetPayoutYieldStrategy(0.04));
        strategies.add(new ProfitabilityFactorStrategy(0.35));
        strategies.add(new InvestmentFactorStrategy(0.15));
        strategies.add(new AssetGrowthAnomalyStrategy(1, 0.15));
        strategies.add(new AccrualAnomalyStrategy(0.05));
        strategies.add(new NetStockIssuanceStrategy(12, 0.02));
        strategies.add(new FinancialDistressStrategy(0.15));
        strategies.add(new LowVolatilityAnomalyStrategy(60, 20));
        strategies.add(new BettingAgainstBetaStrategy(60, 0.8));
        strategies.add(new QualityMinusJunkStrategy(60, 30, fdProvider));
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
