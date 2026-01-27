package tw.gc.auto.equity.trader.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.gc.auto.equity.trader.entities.IndexData;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.repositories.IndexDataRepository;
import tw.gc.auto.equity.trader.repositories.MarketDataRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for calculating stock beta relative to market index.
 * 
 * <h3>Beta Calculation:</h3>
 * Beta measures a stock's volatility relative to the market.
 * β = Cov(stock returns, market returns) / Var(market returns)
 * 
 * <h3>Interpretation:</h3>
 * <ul>
 *   <li>β = 1: Stock moves with the market</li>
 *   <li>β &gt; 1: Stock is more volatile than market (amplified moves)</li>
 *   <li>β &lt; 1: Stock is less volatile than market (dampened moves)</li>
 *   <li>β &lt; 0: Stock moves opposite to market (rare)</li>
 * </ul>
 * 
 * <h3>Use Cases:</h3>
 * <ul>
 *   <li><b>BettingAgainstBetaStrategy</b>: Long low-beta, short high-beta</li>
 *   <li><b>Risk Management</b>: Position sizing based on beta</li>
 *   <li><b>Portfolio Construction</b>: Target portfolio beta</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BetaCalculationService {

    private static final int DEFAULT_BETA_WINDOW = 60; // 60 trading days (~3 months)
    private static final int MIN_DATA_POINTS = 20;     // Minimum for meaningful beta
    private static final double ANNUALIZATION_FACTOR = Math.sqrt(252); // Trading days per year

    private final IndexDataRepository indexDataRepository;
    private final MarketDataRepository marketDataRepository;

    /**
     * Cache for beta values to avoid repeated calculations.
     * Key: symbol, Value: BetaResult with timestamp
     */
    private final Map<String, BetaResult> betaCache = new ConcurrentHashMap<>();
    
    /**
     * Cache expiry time (refresh every 4 hours during trading).
     */
    private static final long CACHE_EXPIRY_MS = 4 * 60 * 60 * 1000;

    // ========== Public API ==========

    /**
     * Get beta for a stock using default window (60 days).
     * 
     * @param symbol Stock symbol
     * @return Beta value, or null if insufficient data
     */
    public Double getBeta(String symbol) {
        return getBeta(symbol, DEFAULT_BETA_WINDOW);
    }

    /**
     * Get beta for a stock with specified window.
     * 
     * @param symbol Stock symbol
     * @param days Number of trading days for calculation
     * @return Beta value, or null if insufficient data
     */
    public Double getBeta(String symbol, int days) {
        // Check cache first
        BetaResult cached = betaCache.get(symbol);
        if (cached != null && !cached.isExpired() && cached.window == days) {
            return cached.beta;
        }

        // Calculate fresh beta
        BetaResult result = calculateBeta(symbol, days);
        if (result != null) {
            betaCache.put(symbol, result);
            return result.beta;
        }

        return null;
    }

    /**
     * Get detailed beta result including R-squared and alpha.
     */
    public BetaResult getBetaResult(String symbol) {
        return getBetaResult(symbol, DEFAULT_BETA_WINDOW);
    }

    /**
     * Get detailed beta result with specified window.
     */
    public BetaResult getBetaResult(String symbol, int days) {
        // Check cache first
        BetaResult cached = betaCache.get(symbol);
        if (cached != null && !cached.isExpired() && cached.window == days) {
            return cached;
        }

        // Calculate fresh
        BetaResult result = calculateBeta(symbol, days);
        if (result != null) {
            betaCache.put(symbol, result);
        }

        return result;
    }

    /**
     * Get betas for multiple symbols.
     * 
     * @param symbols List of stock symbols
     * @return Map of symbol to beta value
     */
    public Map<String, Double> getBetas(List<String> symbols) {
        Map<String, Double> betas = new HashMap<>();
        for (String symbol : symbols) {
            Double beta = getBeta(symbol);
            if (beta != null) {
                betas.put(symbol, beta);
            }
        }
        return betas;
    }

    /**
     * Categorize stock by beta.
     * 
     * @param symbol Stock symbol
     * @return Category: LOW_BETA, NEUTRAL, HIGH_BETA, or UNKNOWN
     */
    public BetaCategory categorize(String symbol) {
        Double beta = getBeta(symbol);
        if (beta == null) {
            return BetaCategory.UNKNOWN;
        }
        if (beta < 0.8) {
            return BetaCategory.LOW_BETA;
        }
        if (beta > 1.2) {
            return BetaCategory.HIGH_BETA;
        }
        return BetaCategory.NEUTRAL;
    }

    /**
     * Find stocks with low beta (defensive stocks).
     * 
     * @param symbols Candidate symbols
     * @param maxBeta Maximum beta threshold (e.g., 0.8)
     * @return Symbols with beta below threshold
     */
    public List<String> findLowBetaStocks(List<String> symbols, double maxBeta) {
        List<String> lowBeta = new ArrayList<>();
        for (String symbol : symbols) {
            Double beta = getBeta(symbol);
            if (beta != null && beta < maxBeta) {
                lowBeta.add(symbol);
            }
        }
        return lowBeta;
    }

    /**
     * Find stocks with high beta (aggressive stocks).
     * 
     * @param symbols Candidate symbols
     * @param minBeta Minimum beta threshold (e.g., 1.2)
     * @return Symbols with beta above threshold
     */
    public List<String> findHighBetaStocks(List<String> symbols, double minBeta) {
        List<String> highBeta = new ArrayList<>();
        for (String symbol : symbols) {
            Double beta = getBeta(symbol);
            if (beta != null && beta > minBeta) {
                highBeta.add(symbol);
            }
        }
        return highBeta;
    }

    /**
     * Rank stocks by beta (ascending - lowest beta first).
     * 
     * @param symbols Candidate symbols
     * @return Symbols sorted by beta ascending
     */
    public List<String> rankByBeta(List<String> symbols) {
        Map<String, Double> betas = getBetas(symbols);
        return betas.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Calculate portfolio beta.
     * 
     * @param positions Map of symbol to position weight
     * @return Weighted average beta of portfolio
     */
    public Double calculatePortfolioBeta(Map<String, Double> positions) {
        double totalWeight = 0;
        double weightedBeta = 0;

        for (Map.Entry<String, Double> entry : positions.entrySet()) {
            Double beta = getBeta(entry.getKey());
            if (beta != null) {
                double weight = Math.abs(entry.getValue());
                weightedBeta += beta * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return null;
        }

        return weightedBeta / totalWeight;
    }

    /**
     * Invalidate cache for a symbol.
     */
    public void invalidateCache(String symbol) {
        betaCache.remove(symbol);
    }

    /**
     * Clear entire cache.
     */
    public void clearCache() {
        betaCache.clear();
    }

    // ========== Scheduled Tasks ==========

    /**
     * Refresh beta cache daily after market close.
     */
    @Scheduled(cron = "0 0 14 * * MON-FRI", zone = "Asia/Taipei")
    public void scheduledBetaRefresh() {
        log.info("📊 Starting scheduled beta calculation refresh...");
        clearCache();
        // Beta will be recalculated on demand
        log.info("✅ Beta cache cleared, values will refresh on access");
    }

    // ========== Private Methods ==========

    @Transactional(readOnly = true)
    private BetaResult calculateBeta(String symbol, int days) {
        try {
            // Get market (TAIEX) returns
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days * 2); // Fetch extra for weekends
            
            List<IndexData> indexHistory = indexDataRepository.findBySymbolAndDateRange(
                    IndexDataService.TAIEX, startDate, endDate);
            
            if (indexHistory.size() < MIN_DATA_POINTS) {
                log.debug("Insufficient index data for beta calculation: {} points", indexHistory.size());
                return null;
            }

            // Get stock returns
            List<MarketData> stockHistory = marketDataRepository.findBySymbolAndDateRange(
                    symbol, 
                    startDate.atStartOfDay(),
                    endDate.atTime(23, 59));

            if (stockHistory.size() < MIN_DATA_POINTS) {
                log.debug("Insufficient stock data for beta calculation: {} points", stockHistory.size());
                return null;
            }

            // Align dates and calculate returns
            Map<LocalDate, Double> indexReturns = calculateReturns(indexHistory);
            Map<LocalDate, Double> stockReturns = calculateStockReturns(stockHistory);

            // Find common dates
            List<LocalDate> commonDates = new ArrayList<>(indexReturns.keySet());
            commonDates.retainAll(stockReturns.keySet());
            Collections.sort(commonDates);

            // Limit to requested window
            if (commonDates.size() > days) {
                commonDates = commonDates.subList(commonDates.size() - days, commonDates.size());
            }

            if (commonDates.size() < MIN_DATA_POINTS) {
                log.debug("Insufficient common dates for beta: {} dates", commonDates.size());
                return null;
            }

            // Extract aligned returns
            double[] marketRets = new double[commonDates.size()];
            double[] stockRets = new double[commonDates.size()];
            
            for (int i = 0; i < commonDates.size(); i++) {
                LocalDate date = commonDates.get(i);
                marketRets[i] = indexReturns.get(date);
                stockRets[i] = stockReturns.get(date);
            }

            // Calculate statistics
            double marketMean = mean(marketRets);
            double stockMean = mean(stockRets);
            
            double covariance = 0;
            double marketVariance = 0;
            double stockVariance = 0;
            
            for (int i = 0; i < marketRets.length; i++) {
                double marketDev = marketRets[i] - marketMean;
                double stockDev = stockRets[i] - stockMean;
                covariance += marketDev * stockDev;
                marketVariance += marketDev * marketDev;
                stockVariance += stockDev * stockDev;
            }
            
            int n = marketRets.length;
            covariance /= (n - 1);
            marketVariance /= (n - 1);
            stockVariance /= (n - 1);

            if (marketVariance == 0) {
                log.debug("Market variance is zero, cannot calculate beta");
                return null;
            }

            // Beta = Cov(stock, market) / Var(market)
            double beta = covariance / marketVariance;

            // Calculate R-squared
            double correlation = covariance / (Math.sqrt(marketVariance) * Math.sqrt(stockVariance));
            double rSquared = correlation * correlation;

            // Calculate alpha (Jensen's alpha)
            // Alpha = stock_return - (risk_free + beta * (market_return - risk_free))
            // Simplified: assuming risk-free = 0
            double annualizedStockReturn = stockMean * 252;
            double annualizedMarketReturn = marketMean * 252;
            double alpha = annualizedStockReturn - (beta * annualizedMarketReturn);

            // Calculate tracking error (residual volatility)
            double residualVar = stockVariance - (beta * beta * marketVariance);
            double trackingError = Math.sqrt(Math.max(0, residualVar)) * ANNUALIZATION_FACTOR;

            return new BetaResult(
                    symbol,
                    beta,
                    rSquared,
                    alpha,
                    Math.sqrt(stockVariance) * ANNUALIZATION_FACTOR, // annualized volatility
                    trackingError,
                    days,
                    commonDates.size(),
                    System.currentTimeMillis()
            );

        } catch (Exception e) {
            log.error("Error calculating beta for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private Map<LocalDate, Double> calculateReturns(List<IndexData> history) {
        Map<LocalDate, Double> returns = new LinkedHashMap<>();
        
        for (int i = 1; i < history.size(); i++) {
            IndexData prev = history.get(i - 1);
            IndexData curr = history.get(i);
            
            if (prev.getCloseValue() != null && prev.getCloseValue() > 0 && curr.getCloseValue() != null) {
                double ret = (curr.getCloseValue() - prev.getCloseValue()) / prev.getCloseValue();
                returns.put(curr.getTradeDate(), ret);
            }
        }
        
        return returns;
    }

    private Map<LocalDate, Double> calculateStockReturns(List<MarketData> history) {
        Map<LocalDate, Double> returns = new LinkedHashMap<>();
        
        // Group by date (in case of multiple entries per day)
        Map<LocalDate, Double> dailyClose = new TreeMap<>();
        for (MarketData data : history) {
            LocalDate date = data.getTimestamp().toLocalDate();
            // close is a primitive double, so no null check needed - check for positive value
            if (data.getClose() > 0) {
                dailyClose.put(date, data.getClose());
            }
        }
        
        LocalDate prevDate = null;
        Double prevClose = null;
        
        for (Map.Entry<LocalDate, Double> entry : dailyClose.entrySet()) {
            if (prevClose != null && prevClose > 0) {
                double ret = (entry.getValue() - prevClose) / prevClose;
                returns.put(entry.getKey(), ret);
            }
            prevDate = entry.getKey();
            prevClose = entry.getValue();
        }
        
        return returns;
    }

    private double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    // ========== Inner Classes ==========

    /**
     * Detailed beta calculation result.
     */
    public record BetaResult(
            String symbol,
            double beta,
            double rSquared,
            double alpha,
            double volatility,
            double trackingError,
            int window,
            int dataPoints,
            long calculatedAt
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() - calculatedAt > CACHE_EXPIRY_MS;
        }

        public boolean isReliable() {
            return rSquared >= 0.3 && dataPoints >= MIN_DATA_POINTS;
        }

        public boolean isLowBeta() {
            return beta < 0.8;
        }

        public boolean isHighBeta() {
            return beta > 1.2;
        }

        @Override
        public String toString() {
            return String.format("Beta{%s: β=%.2f, R²=%.2f, α=%.2f%%, n=%d}", 
                    symbol, beta, rSquared, alpha * 100, dataPoints);
        }
    }

    /**
     * Beta category for strategy use.
     */
    public enum BetaCategory {
        LOW_BETA,   // β < 0.8 (defensive)
        NEUTRAL,    // 0.8 ≤ β ≤ 1.2
        HIGH_BETA,  // β > 1.2 (aggressive)
        UNKNOWN     // Insufficient data
    }
}
