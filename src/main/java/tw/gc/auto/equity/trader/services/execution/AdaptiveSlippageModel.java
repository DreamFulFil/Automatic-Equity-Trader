package tw.gc.auto.equity.trader.services.execution;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AdaptiveSlippageModel - Volume, time, and size-dependent slippage estimation.
 * 
 * <h3>Phase 4: Realistic Execution Modeling</h3>
 * <p>Replaces the fixed 0.05% slippage rate with an adaptive model that considers:
 * <ul>
 *   <li><b>Volume Impact</b>: Lower volume stocks have wider spreads</li>
 *   <li><b>Time-of-Day</b>: Market open/close have higher slippage</li>
 *   <li><b>Order Size</b>: Larger orders move the market more</li>
 *   <li><b>Historical Data</b>: Uses realized slippage from ExecutionAnalyticsService</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * <pre>
 * slippage = base_slippage + volume_factor + time_factor + size_factor
 * 
 * where:
 *   - base_slippage = 0.05% (5 bps) minimum
 *   - volume_factor = 0.15% * (1 - min(volume/ADV_threshold, 1))
 *   - time_factor = 0.10% at open/close, 0% otherwise
 *   - size_factor = 0.05% * (order_size/ADV) for orders > 1% of ADV
 * </pre>
 * 
 * @see ExecutionAnalyticsService for realized slippage tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdaptiveSlippageModel {

    private final ExecutionAnalyticsService executionAnalyticsService;
    
    // Base slippage components (in basis points)
    private static final double BASE_SLIPPAGE_BPS = 5.0;        // 0.05% minimum
    private static final double MAX_VOLUME_FACTOR_BPS = 15.0;   // 0.15% for illiquid stocks
    private static final double OPEN_CLOSE_FACTOR_BPS = 10.0;   // 0.10% at market open/close
    private static final double SIZE_IMPACT_FACTOR = 5.0;       // 0.05% per 1% of ADV
    
    // Volume thresholds
    private static final double HIGH_VOLUME_THRESHOLD = 1_000_000; // Shares considered "liquid"
    private static final double ADV_SIZE_IMPACT_THRESHOLD = 0.01;  // 1% of ADV triggers size impact
    
    // Time-of-day boundaries (Taiwan market)
    private static final LocalTime MARKET_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime MARKET_OPEN_END = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE_START = LocalTime.of(13, 0);
    private static final LocalTime MARKET_CLOSE_END = LocalTime.of(13, 30);
    
    // Cache for ADV values (symbol -> average daily volume)
    private final Map<String, Double> advCache = new ConcurrentHashMap<>();
    
    /**
     * Estimate slippage for a trade based on all factors.
     * 
     * @param context the slippage calculation context
     * @return estimated slippage as a rate (e.g., 0.001 = 0.1%)
     */
    public double estimateSlippage(SlippageContext context) {
        double totalBps = BASE_SLIPPAGE_BPS;
        
        // Factor 1: Volume-based slippage
        totalBps += calculateVolumeFactor(context.getRecentVolume(), context.getSymbol());
        
        // Factor 2: Time-of-day slippage
        totalBps += calculateTimeFactor(context.getExecutionTime());
        
        // Factor 3: Order size impact
        totalBps += calculateSizeImpact(context.getOrderSize(), context.getSymbol());
        
        // Factor 4: Historical slippage adjustment
        totalBps = adjustForHistoricalSlippage(totalBps, context.getSymbol());
        
        // Convert bps to rate
        double slippageRate = totalBps / 10000.0;
        
        if (log.isDebugEnabled()) {
            log.debug("📊 Slippage estimate for {} {} shares of {}: {:.2f} bps ({:.4f}%)",
                context.getOrderSize(), context.getSymbol(), totalBps, slippageRate * 100);
        }
        
        return slippageRate;
    }
    
    /**
     * Estimate slippage with minimal context (for backtest compatibility).
     * 
     * @param symbol the stock symbol
     * @param orderSize number of shares
     * @param recentVolume recent trading volume
     * @return estimated slippage rate
     */
    public double estimateSlippage(String symbol, int orderSize, long recentVolume) {
        return estimateSlippage(SlippageContext.builder()
            .symbol(symbol)
            .orderSize(orderSize)
            .recentVolume(recentVolume)
            .build());
    }
    
    /**
     * Get the base slippage rate (for stocks with no additional factors).
     * 
     * @return base slippage rate (0.0005 = 0.05%)
     */
    public double getBaseSlippageRate() {
        return BASE_SLIPPAGE_BPS / 10000.0;
    }
    
    /**
     * Update ADV cache for a symbol.
     * 
     * @param symbol the stock symbol
     * @param adv average daily volume
     */
    public void updateADV(String symbol, double adv) {
        if (symbol != null && adv > 0) {
            advCache.put(symbol, adv);
        }
    }
    
    /**
     * Get cached ADV for a symbol.
     * 
     * @param symbol the stock symbol
     * @return ADV if cached, empty otherwise
     */
    public Optional<Double> getADV(String symbol) {
        return Optional.ofNullable(advCache.get(symbol));
    }
    
    /**
     * Calculate the estimated transaction cost including slippage, fees, and taxes.
     * 
     * @param context the slippage context
     * @param price the trade price
     * @param isBuy true for buy, false for sell
     * @return total transaction cost as a rate
     */
    public double estimateTotalTransactionCost(SlippageContext context, double price, boolean isBuy) {
        double slippage = estimateSlippage(context);
        
        // Taiwan fees and taxes
        double feeRate = 0.001425;  // 0.1425% commission
        double taxRate = isBuy ? 0.0 : 0.003;  // 0.3% sell tax only
        
        return slippage + feeRate + taxRate;
    }
    
    /**
     * Check if a stock is considered illiquid based on volume.
     * 
     * @param symbol the stock symbol
     * @param recentVolume recent trading volume
     * @return true if the stock is illiquid
     */
    public boolean isIlliquid(String symbol, long recentVolume) {
        double adv = advCache.getOrDefault(symbol, (double) recentVolume);
        return adv < HIGH_VOLUME_THRESHOLD / 10;
    }
    
    /**
     * Get the slippage breakdown for analysis.
     * 
     * @param context the slippage context
     * @return breakdown of slippage components
     */
    public SlippageBreakdown getSlippageBreakdown(SlippageContext context) {
        double baseBps = BASE_SLIPPAGE_BPS;
        double volumeBps = calculateVolumeFactor(context.getRecentVolume(), context.getSymbol());
        double timeBps = calculateTimeFactor(context.getExecutionTime());
        double sizeBps = calculateSizeImpact(context.getOrderSize(), context.getSymbol());
        
        double totalBps = baseBps + volumeBps + timeBps + sizeBps;
        totalBps = adjustForHistoricalSlippage(totalBps, context.getSymbol());
        
        return SlippageBreakdown.builder()
            .symbol(context.getSymbol())
            .baseSlippageBps(baseBps)
            .volumeFactorBps(volumeBps)
            .timeFactorBps(timeBps)
            .sizeImpactBps(sizeBps)
            .totalSlippageBps(totalBps)
            .totalSlippageRate(totalBps / 10000.0)
            .build();
    }
    
    // ==================== Helper Methods ====================
    
    private double calculateVolumeFactor(long recentVolume, String symbol) {
        // Use ADV from cache if available, otherwise use recent volume
        double adv = advCache.getOrDefault(symbol, (double) Math.max(recentVolume, 1));
        
        // Calculate volume factor: higher for low-volume stocks
        // Factor is 0 for stocks with volume >= threshold, max for very low volume
        if (adv >= HIGH_VOLUME_THRESHOLD) {
            return 0.0;
        }
        
        double volumeRatio = adv / HIGH_VOLUME_THRESHOLD;
        return MAX_VOLUME_FACTOR_BPS * (1.0 - volumeRatio);
    }
    
    private double calculateTimeFactor(LocalTime executionTime) {
        if (executionTime == null) {
            return 0.0;
        }
        
        // Higher slippage at market open and close
        boolean isOpenPeriod = !executionTime.isBefore(MARKET_OPEN_START) && 
                               executionTime.isBefore(MARKET_OPEN_END);
        boolean isClosePeriod = !executionTime.isBefore(MARKET_CLOSE_START) && 
                                executionTime.isBefore(MARKET_CLOSE_END);
        
        return (isOpenPeriod || isClosePeriod) ? OPEN_CLOSE_FACTOR_BPS : 0.0;
    }
    
    private double calculateSizeImpact(int orderSize, String symbol) {
        if (orderSize <= 0) {
            return 0.0;
        }
        
        // Get ADV for the symbol
        Double adv = advCache.get(symbol);
        if (adv == null || adv <= 0) {
            // No ADV data - assume moderate impact for larger orders
            if (orderSize > 1000) {
                return SIZE_IMPACT_FACTOR;
            }
            return 0.0;
        }
        
        // Calculate order as percentage of ADV
        double sizeRatio = orderSize / adv;
        
        // Only apply impact if order is significant portion of ADV
        if (sizeRatio < ADV_SIZE_IMPACT_THRESHOLD) {
            return 0.0;
        }
        
        // Linear impact: 5 bps per 1% of ADV
        return SIZE_IMPACT_FACTOR * (sizeRatio / ADV_SIZE_IMPACT_THRESHOLD);
    }
    
    private double adjustForHistoricalSlippage(double estimatedBps, String symbol) {
        // Try to get historical slippage data
        OptionalDouble historicalAvg = executionAnalyticsService.getAverageSlippageForSymbol(symbol);
        
        if (historicalAvg.isEmpty()) {
            return estimatedBps;
        }
        
        double historical = historicalAvg.getAsDouble();
        
        // Blend estimated and historical (70% historical, 30% estimated when data available)
        return historical * 0.7 + estimatedBps * 0.3;
    }
    
    // ==================== Data Classes ====================
    
    /**
     * Context for slippage estimation.
     */
    @Data
    @Builder
    public static class SlippageContext {
        private final String symbol;
        private final int orderSize;
        private final long recentVolume;
        private final LocalTime executionTime;
        private final String action;  // BUY or SELL
    }
    
    /**
     * Detailed breakdown of slippage components.
     */
    @Data
    @Builder
    public static class SlippageBreakdown {
        private final String symbol;
        private final double baseSlippageBps;
        private final double volumeFactorBps;
        private final double timeFactorBps;
        private final double sizeImpactBps;
        private final double totalSlippageBps;
        private final double totalSlippageRate;
        
        /**
         * Get the primary contributor to slippage.
         */
        public String getPrimaryFactor() {
            double max = Math.max(Math.max(volumeFactorBps, timeFactorBps), sizeImpactBps);
            
            if (max == 0.0) {
                return "BASE";
            } else if (max == volumeFactorBps) {
                return "LOW_VOLUME";
            } else if (max == timeFactorBps) {
                return "MARKET_TIMING";
            } else {
                return "ORDER_SIZE";
            }
        }
        
        /**
         * Check if slippage is acceptable for trading.
         */
        public boolean isAcceptable(double thresholdBps) {
            return totalSlippageBps <= thresholdBps;
        }
    }
}
