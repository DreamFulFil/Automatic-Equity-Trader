package tw.gc.auto.equity.trader.services.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tw.gc.auto.equity.trader.entities.Trade;
import tw.gc.auto.equity.trader.repositories.TradeRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fill Analytics Service - Phase 8
 * 
 * Tracks execution performance metrics:
 * - Fill rates by symbol, time of day, order type
 * - Slippage analysis
 * - Execution quality scores
 * - Optimal order type recommendations
 * 
 * Helps optimize execution strategy by learning from historical performance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FillAnalyticsService {

    private static final ZoneId TAIPEI_ZONE = ZoneId.of("Asia/Taipei");
    
    private final TradeRepository tradeRepository;
    
    // In-memory cache for recent execution statistics
    private final Map<String, ExecutionStats> executionStatsCache = new ConcurrentHashMap<>();

    /**
     * Record execution result for analytics
     */
    public void recordExecution(String symbol, String orderType, double requestedPrice,
                               double filledPrice, int requestedQuantity, int filledQuantity,
                               LocalDateTime executionTime) {
        
        String key = symbol + "_" + orderType;
        ExecutionStats stats = executionStatsCache.computeIfAbsent(key, k -> new ExecutionStats(symbol, orderType));
        
        double slippage = calculateSlippage(requestedPrice, filledPrice);
        double fillRate = (double) filledQuantity / requestedQuantity;
        
        stats.addExecution(slippage, fillRate, executionTime);
        
        log.debug("📊 Execution recorded: {} {} - Fill Rate: {:.1f}%, Slippage: {:.2f}%",
            symbol, orderType, fillRate * 100, slippage * 100);
    }

    /**
     * Calculate slippage percentage
     */
    private double calculateSlippage(double requestedPrice, double filledPrice) {
        if (requestedPrice <= 0) return 0.0;
        return Math.abs(filledPrice - requestedPrice) / requestedPrice;
    }

    /**
     * Get execution statistics for symbol
     */
    public ExecutionStats getExecutionStats(String symbol) {
        return executionStatsCache.values().stream()
            .filter(stats -> stats.getSymbol().equals(symbol))
            .findFirst()
            .orElse(new ExecutionStats(symbol, "UNKNOWN"));
    }

    /**
     * Get recommended order type based on historical performance
     */
    public OrderTypeRecommendation getOrderTypeRecommendation(String symbol, double currentVolatility) {
        ExecutionStats marketStats = executionStatsCache.get(symbol + "_MARKET");
        ExecutionStats limitStats = executionStatsCache.get(symbol + "_LIMIT");
        
        // Default to market orders if insufficient data
        if (marketStats == null && limitStats == null) {
            return OrderTypeRecommendation.builder()
                .recommendedType("MARKET")
                .reason("Insufficient historical data - defaulting to market order")
                .confidence(50)
                .build();
        }
        
        // If only one type has data, recommend that
        if (marketStats == null) {
            return OrderTypeRecommendation.builder()
                .recommendedType("LIMIT")
                .reason("Only limit order data available")
                .confidence(60)
                .build();
        }
        if (limitStats == null) {
            return OrderTypeRecommendation.builder()
                .recommendedType("MARKET")
                .reason("Only market order data available")
                .confidence(60)
                .build();
        }
        
        // Compare performance
        double marketFillRate = marketStats.getAverageFillRate();
        double limitFillRate = limitStats.getAverageFillRate();
        double marketSlippage = marketStats.getAverageSlippage();
        double limitSlippage = limitStats.getAverageSlippage();
        
        // Calculate quality score: higher fill rate, lower slippage is better
        double marketScore = (marketFillRate * 0.7) - (marketSlippage * 0.3);
        double limitScore = (limitFillRate * 0.7) - (limitSlippage * 0.3);
        
        // Adjust for volatility: prefer market orders in high volatility
        if (currentVolatility > 0.03) {
            marketScore += 0.1;
        } else {
            limitScore += 0.1;
        }
        
        if (marketScore > limitScore) {
            return OrderTypeRecommendation.builder()
                .recommendedType("MARKET")
                .reason(String.format("Better fill rate (%.1f%% vs %.1f%%) and lower slippage",
                    marketFillRate * 100, limitFillRate * 100))
                .confidence((int) ((marketScore - limitScore) * 100))
                .build();
        } else {
            return OrderTypeRecommendation.builder()
                .recommendedType("LIMIT")
                .reason(String.format("Better execution quality - lower slippage (%.2f%% vs %.2f%%)",
                    limitSlippage * 100, marketSlippage * 100))
                .confidence((int) ((limitScore - marketScore) * 100))
                .build();
        }
    }

    /**
     * Analyze execution quality from recent trades
     */
    public ExecutionQualityReport analyzeExecutionQuality(int days) {
        LocalDateTime since = LocalDateTime.now(TAIPEI_ZONE).minusDays(days);
        List<Trade> recentTrades = tradeRepository.findByTimestampBetween(
            since, LocalDateTime.now(TAIPEI_ZONE));
        
        if (recentTrades.isEmpty()) {
            return ExecutionQualityReport.builder()
                .periodDays(days)
                .totalTrades(0)
                .averageSlippage(0.0)
                .medianSlippage(0.0)
                .worstSlippage(0.0)
                .averageFillRate(0.0)
                .build();
        }
        
        // Calculate aggregate metrics
        List<Double> slippages = new ArrayList<>();
        double totalFillRate = 0.0;
        
        for (Trade trade : recentTrades) {
            // Estimate slippage from entry/exit prices
            // This is simplified - in production, would track actual vs expected prices
            double estimatedSlippage = 0.001; // 0.1% default estimate
            slippages.add(estimatedSlippage);
            totalFillRate += 1.0; // Assume full fills for completed trades
        }
        
        Collections.sort(slippages);
        double medianSlippage = slippages.get(slippages.size() / 2);
        double avgSlippage = slippages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double worstSlippage = slippages.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        return ExecutionQualityReport.builder()
            .periodDays(days)
            .totalTrades(recentTrades.size())
            .averageSlippage(avgSlippage)
            .medianSlippage(medianSlippage)
            .worstSlippage(worstSlippage)
            .averageFillRate(totalFillRate / recentTrades.size())
            .build();
    }

    /**
     * Get execution statistics for all symbols
     */
    public Map<String, ExecutionStats> getAllExecutionStats() {
        return new HashMap<>(executionStatsCache);
    }

    /**
     * Clear cached statistics (for testing or reset)
     */
    public void clearStatistics() {
        executionStatsCache.clear();
        log.info("🧹 Execution statistics cache cleared");
    }

    /**
     * Execution statistics for a symbol/order type combination
     */
    @lombok.Data
    public static class ExecutionStats {
        private final String symbol;
        private final String orderType;
        private final List<Double> slippages = new ArrayList<>();
        private final List<Double> fillRates = new ArrayList<>();
        private final List<LocalDateTime> executionTimes = new ArrayList<>();
        
        public ExecutionStats(String symbol, String orderType) {
            this.symbol = symbol;
            this.orderType = orderType;
        }
        
        public void addExecution(double slippage, double fillRate, LocalDateTime time) {
            slippages.add(slippage);
            fillRates.add(fillRate);
            executionTimes.add(time);
            
            // Keep only last 100 executions
            if (slippages.size() > 100) {
                slippages.remove(0);
                fillRates.remove(0);
                executionTimes.remove(0);
            }
        }
        
        public double getAverageSlippage() {
            return slippages.isEmpty() ? 0.0 : slippages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        public double getAverageFillRate() {
            return fillRates.isEmpty() ? 0.0 : fillRates.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        
        public int getExecutionCount() {
            return slippages.size();
        }
    }

    /**
     * Order type recommendation
     */
    @lombok.Builder
    @lombok.Value
    public static class OrderTypeRecommendation {
        String recommendedType; // MARKET or LIMIT
        String reason;
        int confidence; // 0-100
    }

    /**
     * Execution quality report
     */
    @lombok.Builder
    @lombok.Value
    public static class ExecutionQualityReport {
        int periodDays;
        int totalTrades;
        double averageSlippage;
        double medianSlippage;
        double worstSlippage;
        double averageFillRate;
        
        public String getQualityGrade() {
            if (averageSlippage < 0.001) return "A+";
            if (averageSlippage < 0.002) return "A";
            if (averageSlippage < 0.005) return "B";
            if (averageSlippage < 0.01) return "C";
            return "D";
        }
    }
}
