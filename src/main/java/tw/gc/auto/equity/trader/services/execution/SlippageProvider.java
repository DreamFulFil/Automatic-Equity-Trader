package tw.gc.auto.equity.trader.services.execution;

import tw.gc.auto.equity.trader.entities.MarketData;

/**
 * SlippageProvider - Functional interface for pluggable slippage calculation.
 * 
 * <h3>Phase 4: Realistic Execution Modeling</h3>
 * <p>Allows BacktestService and other services to use different slippage models:
 * <ul>
 *   <li>{@link #fixed(double)} - Static percentage (legacy behavior)</li>
 *   <li>{@link #adaptive(AdaptiveSlippageModel)} - Dynamic model based on volume/time/size</li>
 *   <li>Custom implementations for specific use cases</li>
 * </ul>
 * 
 * @see AdaptiveSlippageModel for the full adaptive implementation
 */
@FunctionalInterface
public interface SlippageProvider {
    
    /**
     * Calculate slippage rate for a trade.
     * 
     * @param context the slippage context containing trade details
     * @return slippage as a rate (e.g., 0.001 = 0.1%)
     */
    double calculateSlippage(SlippageContext context);
    
    /**
     * Create a fixed slippage provider (legacy behavior).
     * 
     * @param rate fixed slippage rate (e.g., 0.0005 = 0.05%)
     * @return a provider that always returns the fixed rate
     */
    static SlippageProvider fixed(double rate) {
        return context -> rate;
    }
    
    /**
     * Create an adaptive slippage provider using the full model.
     * 
     * @param model the adaptive slippage model
     * @return a provider that delegates to the adaptive model
     */
    static SlippageProvider adaptive(AdaptiveSlippageModel model) {
        return context -> model.estimateSlippage(
            AdaptiveSlippageModel.SlippageContext.builder()
                .symbol(context.symbol())
                .orderSize(context.orderSize())
                .recentVolume(context.recentVolume())
                .executionTime(context.executionTime())
                .action(context.action())
                .build()
        );
    }
    
    /**
     * Create a volume-aware slippage provider (simplified adaptive).
     * 
     * @param baseRate minimum slippage rate
     * @param lowVolumeThreshold volume below which higher slippage applies
     * @param lowVolumeRate slippage rate for low-volume stocks
     * @return a provider that adjusts based on volume
     */
    static SlippageProvider volumeAware(double baseRate, long lowVolumeThreshold, double lowVolumeRate) {
        return context -> {
            if (context.recentVolume() < lowVolumeThreshold) {
                // Interpolate based on how illiquid
                double volumeRatio = (double) context.recentVolume() / lowVolumeThreshold;
                return baseRate + (lowVolumeRate - baseRate) * (1.0 - volumeRatio);
            }
            return baseRate;
        };
    }
    
    /**
     * Create a capped slippage provider that limits maximum slippage.
     * 
     * @param delegate the underlying provider
     * @param maxRate maximum slippage rate allowed
     * @return a provider that caps slippage at the maximum
     */
    default SlippageProvider capped(double maxRate) {
        return context -> Math.min(calculateSlippage(context), maxRate);
    }
    
    /**
     * Create a provider with a minimum floor.
     * 
     * @param minRate minimum slippage rate
     * @return a provider that ensures at least the minimum slippage
     */
    default SlippageProvider floored(double minRate) {
        return context -> Math.max(calculateSlippage(context), minRate);
    }
    
    // ==================== Context Class ====================
    
    /**
     * Context for slippage calculation.
     */
    record SlippageContext(
        String symbol,
        int orderSize,
        long recentVolume,
        java.time.LocalTime executionTime,
        String action,
        double price
    ) {
        /**
         * Create context from market data.
         */
        public static SlippageContext fromMarketData(MarketData data, int orderSize, String action) {
            return new SlippageContext(
                data.getSymbol(),
                orderSize,
                data.getVolume(),
                data.getTimestamp() != null ? data.getTimestamp().toLocalTime() : null,
                action,
                data.getClose()
            );
        }
        
        /**
         * Create minimal context (for backward compatibility).
         */
        public static SlippageContext minimal(String symbol, double price) {
            return new SlippageContext(symbol, 0, 0, null, null, price);
        }
    }
}
