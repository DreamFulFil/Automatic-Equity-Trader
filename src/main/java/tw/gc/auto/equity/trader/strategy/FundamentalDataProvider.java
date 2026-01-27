package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.FundamentalData;

import java.util.Optional;
import java.util.function.Function;

/**
 * Functional interface for providing fundamental data to strategies.
 * 
 * <p>This interface allows strategies to access fundamental financial data
 * (P/E ratios, book value, earnings yield, etc.) without direct dependency
 * on the FundamentalDataService.
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // In BacktestService (provider implementation)
 * FundamentalDataProvider provider = symbol -> fundamentalDataService.getLatestBySymbol(symbol);
 * 
 * // In strategy constructor
 * EarningsYieldStrategy strategy = new EarningsYieldStrategy(0.05, 60, provider);
 * </pre>
 * 
 * <h3>Fallback Behavior:</h3>
 * Strategies should gracefully degrade to price-based proxies when fundamental
 * data is unavailable (returns Optional.empty()).
 * 
 * @since 2026-01-26 - Phase 1 Data Improvement Plan
 * @see FundamentalData
 */
@FunctionalInterface
public interface FundamentalDataProvider extends Function<String, Optional<FundamentalData>> {
    
    /**
     * Get fundamental data for a symbol.
     * 
     * @param symbol Stock ticker symbol (e.g., "2330.TW")
     * @return Optional containing FundamentalData if available, empty otherwise
     */
    Optional<FundamentalData> getFundamentalData(String symbol);
    
    /**
     * Default implementation delegates to getFundamentalData.
     */
    @Override
    default Optional<FundamentalData> apply(String symbol) {
        return getFundamentalData(symbol);
    }
    
    /**
     * Create a no-op provider that always returns empty.
     * Useful for strategies that don't need fundamental data or for testing.
     */
    static FundamentalDataProvider noOp() {
        return symbol -> Optional.empty();
    }
}
