package tw.gc.auto.equity.trader.services.execution;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ExecutionAnalyticsService - Track and analyze actual trade execution quality.
 * 
 * <h3>Phase 4: Realistic Execution Modeling</h3>
 * <p>This service tracks:
 * <ul>
 *   <li>Actual fill prices vs decision prices (realized slippage)</li>
 *   <li>Slippage patterns by stock, time-of-day, order size</li>
 *   <li>Fill rates and partial fill statistics</li>
 *   <li>High-slippage periods (open/close) identification</li>
 * </ul>
 * 
 * <h3>Expected Impact:</h3>
 * <ul>
 *   <li>More accurate backtest results</li>
 *   <li>Identify stocks to avoid due to execution costs</li>
 * </ul>
 * 
 * @see AdaptiveSlippageModel for slippage estimation
 * @see ExecutionQualityReport for weekly analysis reports
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionAnalyticsService {

    // Time-of-day buckets for analysis
    public enum TimeBucket {
        MARKET_OPEN,      // 09:00-09:30
        MORNING_EARLY,    // 09:30-10:30
        MORNING_LATE,     // 10:30-11:30
        MIDDAY,           // 11:30-12:30
        AFTERNOON_EARLY,  // 12:30-13:00
        MARKET_CLOSE      // 13:00-13:30
    }
    
    // In-memory storage for execution records (could be persisted to DB later)
    private final Map<String, List<ExecutionRecord>> executionsBySymbol = new ConcurrentHashMap<>();
    private final Map<TimeBucket, List<ExecutionRecord>> executionsByTimeBucket = new ConcurrentHashMap<>();
    
    // Configurable thresholds
    private static final double HIGH_SLIPPAGE_THRESHOLD_BPS = 30.0; // 0.30% = high slippage
    private static final int MIN_SAMPLES_FOR_ANALYSIS = 10;
    private static final int MAX_RECORDS_PER_SYMBOL = 1000;
    
    /**
     * Record an execution for analysis.
     * 
     * @param record the execution record containing decision and fill details
     */
    public void recordExecution(ExecutionRecord record) {
        if (record == null || record.getSymbol() == null) {
            return;
        }
        
        // Calculate realized slippage
        double slippageBps = calculateSlippageBps(record);
        ExecutionRecord enriched = record.toBuilder()
            .realizedSlippageBps(slippageBps)
            .timeBucket(classifyTimeBucket(record.getExecutionTime()))
            .build();
        
        // Store by symbol (with cap)
        executionsBySymbol.compute(record.getSymbol(), (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(enriched);
            // Keep only recent records
            if (v.size() > MAX_RECORDS_PER_SYMBOL) {
                v = new ArrayList<>(v.subList(v.size() - MAX_RECORDS_PER_SYMBOL, v.size()));
            }
            return v;
        });
        
        // Store by time bucket
        TimeBucket bucket = enriched.getTimeBucket();
        executionsByTimeBucket.compute(bucket, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(enriched);
            return v;
        });
        
        // Log high slippage events
        if (slippageBps > HIGH_SLIPPAGE_THRESHOLD_BPS) {
            log.warn("⚠️ HIGH SLIPPAGE: {} - {:.2f} bps ({} @ {} vs decision {})",
                record.getSymbol(), slippageBps, record.getAction(), 
                record.getFillPrice(), record.getDecisionPrice());
        }
    }
    
    /**
     * Get average slippage for a specific symbol.
     * 
     * @param symbol the stock symbol
     * @return average slippage in basis points, or empty if insufficient data
     */
    public OptionalDouble getAverageSlippageForSymbol(String symbol) {
        List<ExecutionRecord> records = executionsBySymbol.get(symbol);
        if (records == null || records.size() < MIN_SAMPLES_FOR_ANALYSIS) {
            return OptionalDouble.empty();
        }
        
        return records.stream()
            .mapToDouble(ExecutionRecord::getRealizedSlippageBps)
            .average();
    }
    
    /**
     * Get average slippage for a time bucket.
     * 
     * @param bucket the time-of-day bucket
     * @return average slippage in basis points, or empty if insufficient data
     */
    public OptionalDouble getAverageSlippageForTimeBucket(TimeBucket bucket) {
        List<ExecutionRecord> records = executionsByTimeBucket.get(bucket);
        if (records == null || records.size() < MIN_SAMPLES_FOR_ANALYSIS) {
            return OptionalDouble.empty();
        }
        
        return records.stream()
            .mapToDouble(ExecutionRecord::getRealizedSlippageBps)
            .average();
    }
    
    /**
     * Get symbols with consistently high slippage (problematic for trading).
     * 
     * @return list of symbols with average slippage above threshold
     */
    public List<SymbolSlippageStats> getHighSlippageSymbols() {
        return executionsBySymbol.entrySet().stream()
            .filter(e -> e.getValue().size() >= MIN_SAMPLES_FOR_ANALYSIS)
            .map(e -> calculateSymbolStats(e.getKey(), e.getValue()))
            .filter(stats -> stats.getAverageSlippageBps() > HIGH_SLIPPAGE_THRESHOLD_BPS)
            .sorted(Comparator.comparingDouble(SymbolSlippageStats::getAverageSlippageBps).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Get high-slippage time periods.
     * 
     * @return map of time buckets to their average slippage
     */
    public Map<TimeBucket, Double> getSlippageByTimeBucket() {
        Map<TimeBucket, Double> result = new EnumMap<>(TimeBucket.class);
        
        for (TimeBucket bucket : TimeBucket.values()) {
            OptionalDouble avg = getAverageSlippageForTimeBucket(bucket);
            if (avg.isPresent()) {
                result.put(bucket, avg.getAsDouble());
            }
        }
        
        return result;
    }
    
    /**
     * Calculate fill rate (percentage of orders fully filled).
     * 
     * @param symbol optional symbol filter (null for all)
     * @return fill rate as percentage (0-100)
     */
    public double calculateFillRate(String symbol) {
        List<ExecutionRecord> records;
        
        if (symbol != null) {
            records = executionsBySymbol.get(symbol);
        } else {
            records = executionsBySymbol.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        }
        
        if (records == null || records.isEmpty()) {
            return 100.0; // No data = assume full fill
        }
        
        long fullFills = records.stream()
            .filter(r -> r.getFilledQuantity() >= r.getRequestedQuantity())
            .count();
        
        return (fullFills * 100.0) / records.size();
    }
    
    /**
     * Get execution statistics for a specific symbol.
     * 
     * @param symbol the stock symbol
     * @return detailed statistics or null if insufficient data
     */
    public SymbolSlippageStats getStatsForSymbol(String symbol) {
        List<ExecutionRecord> records = executionsBySymbol.get(symbol);
        if (records == null || records.isEmpty()) {
            return null;
        }
        return calculateSymbolStats(symbol, records);
    }
    
    /**
     * Get execution statistics for a date range.
     * 
     * @param startDate start of range (inclusive)
     * @param endDate end of range (inclusive)
     * @return aggregated statistics for the period
     */
    public ExecutionSummary getSummaryForPeriod(LocalDate startDate, LocalDate endDate) {
        List<ExecutionRecord> allRecords = executionsBySymbol.values().stream()
            .flatMap(List::stream)
            .filter(r -> {
                LocalDate execDate = r.getExecutionTime().toLocalDate();
                return !execDate.isBefore(startDate) && !execDate.isAfter(endDate);
            })
            .collect(Collectors.toList());
        
        if (allRecords.isEmpty()) {
            return ExecutionSummary.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalExecutions(0)
                .averageSlippageBps(0.0)
                .maxSlippageBps(0.0)
                .fillRate(100.0)
                .highSlippageCount(0)
                .symbolCount(0)
                .build();
        }
        
        DoubleSummaryStatistics slippageStats = allRecords.stream()
            .mapToDouble(ExecutionRecord::getRealizedSlippageBps)
            .summaryStatistics();
        
        long highSlippageCount = allRecords.stream()
            .filter(r -> r.getRealizedSlippageBps() > HIGH_SLIPPAGE_THRESHOLD_BPS)
            .count();
        
        long fullFills = allRecords.stream()
            .filter(r -> r.getFilledQuantity() >= r.getRequestedQuantity())
            .count();
        
        long uniqueSymbols = allRecords.stream()
            .map(ExecutionRecord::getSymbol)
            .distinct()
            .count();
        
        return ExecutionSummary.builder()
            .startDate(startDate)
            .endDate(endDate)
            .totalExecutions(allRecords.size())
            .averageSlippageBps(slippageStats.getAverage())
            .maxSlippageBps(slippageStats.getMax())
            .minSlippageBps(slippageStats.getMin())
            .fillRate((fullFills * 100.0) / allRecords.size())
            .highSlippageCount((int) highSlippageCount)
            .symbolCount((int) uniqueSymbols)
            .build();
    }
    
    /**
     * Clear all execution records (for testing or reset).
     */
    public void clearAllRecords() {
        executionsBySymbol.clear();
        executionsByTimeBucket.clear();
        log.info("🗑️ Cleared all execution analytics records");
    }
    
    /**
     * Get total number of recorded executions.
     */
    public int getTotalRecordCount() {
        return executionsBySymbol.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    // ==================== Helper Methods ====================
    
    private double calculateSlippageBps(ExecutionRecord record) {
        if (record.getDecisionPrice() <= 0) {
            return 0.0;
        }
        
        double priceDiff = record.getFillPrice() - record.getDecisionPrice();
        
        // For BUY: positive diff = positive slippage (paid more = unfavorable)
        // For SELL: negative diff = positive slippage (received less = unfavorable)
        // So for SELL, we negate the diff so that lower fill = positive slippage
        if ("SELL".equalsIgnoreCase(record.getAction())) {
            priceDiff = -priceDiff;
        }
        
        // Convert to basis points (1 bps = 0.01%)
        // Positive result = unfavorable slippage (worse execution)
        // Negative result = favorable slippage (better execution)
        return (priceDiff / record.getDecisionPrice()) * 10000;
    }
    
    private TimeBucket classifyTimeBucket(LocalDateTime executionTime) {
        if (executionTime == null) {
            return TimeBucket.MIDDAY;
        }
        
        LocalTime time = executionTime.toLocalTime();
        
        if (time.isBefore(LocalTime.of(9, 30))) {
            return TimeBucket.MARKET_OPEN;
        } else if (time.isBefore(LocalTime.of(10, 30))) {
            return TimeBucket.MORNING_EARLY;
        } else if (time.isBefore(LocalTime.of(11, 30))) {
            return TimeBucket.MORNING_LATE;
        } else if (time.isBefore(LocalTime.of(12, 30))) {
            return TimeBucket.MIDDAY;
        } else if (time.isBefore(LocalTime.of(13, 0))) {
            return TimeBucket.AFTERNOON_EARLY;
        } else {
            return TimeBucket.MARKET_CLOSE;
        }
    }
    
    private SymbolSlippageStats calculateSymbolStats(String symbol, List<ExecutionRecord> records) {
        DoubleSummaryStatistics stats = records.stream()
            .mapToDouble(ExecutionRecord::getRealizedSlippageBps)
            .summaryStatistics();
        
        long fullFills = records.stream()
            .filter(r -> r.getFilledQuantity() >= r.getRequestedQuantity())
            .count();
        
        double stdDev = calculateStdDev(records.stream()
            .mapToDouble(ExecutionRecord::getRealizedSlippageBps)
            .toArray());
        
        return SymbolSlippageStats.builder()
            .symbol(symbol)
            .sampleCount(records.size())
            .averageSlippageBps(stats.getAverage())
            .maxSlippageBps(stats.getMax())
            .minSlippageBps(stats.getMin())
            .stdDevSlippageBps(stdDev)
            .fillRate((fullFills * 100.0) / records.size())
            .build();
    }
    
    private double calculateStdDev(double[] values) {
        if (values.length < 2) return 0.0;
        
        double mean = Arrays.stream(values).average().orElse(0.0);
        double sumSquaredDiff = Arrays.stream(values)
            .map(v -> (v - mean) * (v - mean))
            .sum();
        
        return Math.sqrt(sumSquaredDiff / (values.length - 1));
    }
    
    // ==================== Data Classes ====================
    
    /**
     * Record of a single trade execution for analytics.
     */
    @Data
    @Builder(toBuilder = true)
    public static class ExecutionRecord {
        private final String symbol;
        private final String action;           // BUY or SELL
        private final double decisionPrice;    // Price when signal was generated
        private final double fillPrice;        // Actual fill price
        private final int requestedQuantity;   // Quantity requested
        private final int filledQuantity;      // Quantity actually filled
        private final LocalDateTime executionTime;
        private final String strategyName;
        
        // Calculated fields
        private Double realizedSlippageBps;
        private TimeBucket timeBucket;
    }
    
    /**
     * Aggregated slippage statistics for a symbol.
     */
    @Data
    @Builder
    public static class SymbolSlippageStats {
        private final String symbol;
        private final int sampleCount;
        private final double averageSlippageBps;
        private final double maxSlippageBps;
        private final double minSlippageBps;
        private final double stdDevSlippageBps;
        private final double fillRate;
        
        /**
         * Check if this symbol has problematically high slippage.
         */
        public boolean isHighSlippage() {
            return averageSlippageBps > HIGH_SLIPPAGE_THRESHOLD_BPS;
        }
        
        /**
         * Check if slippage is consistent (low standard deviation).
         */
        public boolean isConsistent() {
            return stdDevSlippageBps < averageSlippageBps * 0.5;
        }
    }
    
    /**
     * Summary of execution quality for a time period.
     */
    @Data
    @Builder
    public static class ExecutionSummary {
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int totalExecutions;
        private final double averageSlippageBps;
        private final double maxSlippageBps;
        private final double minSlippageBps;
        private final double fillRate;
        private final int highSlippageCount;
        private final int symbolCount;
        
        /**
         * Calculate the total slippage cost as percentage of traded value.
         */
        public double getSlippageCostPercent() {
            return averageSlippageBps / 100.0;
        }
    }
}
