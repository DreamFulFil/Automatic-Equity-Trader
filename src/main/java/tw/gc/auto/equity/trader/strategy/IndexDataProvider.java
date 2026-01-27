package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.IndexData;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Functional interface for providing market index data to strategies.
 * 
 * <p>This interface allows strategies to access index data (TAIEX, etc.)
 * without direct dependency on IndexDataService.
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // In BacktestService (provider implementation)
 * IndexDataProvider provider = symbol -> indexDataService.getLatest(symbol);
 * 
 * // In strategy constructor
 * IndexArbitrageStrategy strategy = new IndexArbitrageStrategy("^TWII", 0.02, provider);
 * </pre>
 * 
 * <h3>Supported Indices:</h3>
 * <ul>
 *   <li>^TWII - TAIEX (Taiwan Stock Exchange)</li>
 *   <li>^TWOII - Taiwan OTC Index</li>
 *   <li>0050.TW - Taiwan Top 50 ETF</li>
 * </ul>
 * 
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@FunctionalInterface
public interface IndexDataProvider extends Function<String, Optional<IndexData>> {

    /**
     * Primary index symbol for Taiwan market.
     */
    String TAIEX = "^TWII";

    /**
     * Get latest index data for a symbol.
     * 
     * @param indexSymbol Index symbol (e.g., "^TWII")
     * @return Optional containing IndexData if available
     */
    Optional<IndexData> getLatestIndex(String indexSymbol);

    /**
     * Default implementation delegates to getLatestIndex.
     */
    @Override
    default Optional<IndexData> apply(String indexSymbol) {
        return getLatestIndex(indexSymbol);
    }

    /**
     * Get current index value (closing price).
     * 
     * @param indexSymbol Index symbol
     * @return Current value or null if unavailable
     */
    default Double getCurrentValue(String indexSymbol) {
        return getLatestIndex(indexSymbol)
                .map(IndexData::getCloseValue)
                .orElse(null);
    }

    /**
     * Get current TAIEX value.
     */
    default Double getTaiexValue() {
        return getCurrentValue(TAIEX);
    }

    /**
     * Get daily return for an index.
     */
    default Double getDailyReturn(String indexSymbol) {
        return getLatestIndex(indexSymbol)
                .map(IndexData::getDailyReturn)
                .orElse(null);
    }

    /**
     * Get daily change percentage.
     */
    default Double getChangePercent(String indexSymbol) {
        return getLatestIndex(indexSymbol)
                .map(IndexData::getChangePercent)
                .orElse(null);
    }

    /**
     * Check if market is bullish (above 50 and 200 MA).
     */
    default boolean isBullMarket() {
        return getLatestIndex(TAIEX)
                .map(IndexData::isBullMarket)
                .orElse(false);
    }

    /**
     * Check if market is bearish (below 200 MA).
     */
    default boolean isBearMarket() {
        return getLatestIndex(TAIEX)
                .map(data -> !data.isAboveMa200())
                .orElse(false);
    }

    /**
     * Get market trend direction.
     * @return 1 for bullish, -1 for bearish, 0 for neutral/unknown
     */
    default int getMarketTrend() {
        return getLatestIndex(TAIEX)
                .map(data -> {
                    if (data.isBullMarket()) return 1;
                    if (!data.isAboveMa200()) return -1;
                    return 0;
                })
                .orElse(0);
    }

    /**
     * Check if market had significant move today.
     * @param threshold Percentage threshold (e.g., 0.01 for 1%)
     */
    default boolean hasSignificantMove(double threshold) {
        return getLatestIndex(TAIEX)
                .map(data -> data.hasSignificantMove(threshold))
                .orElse(false);
    }

    /**
     * Get index position within 52-week range.
     * @return 0.0 at year low, 1.0 at year high
     */
    default Double getYearRangePosition() {
        return getLatestIndex(TAIEX)
                .map(IndexData::getYearRangePosition)
                .orElse(null);
    }

    /**
     * Check if index is near 52-week high.
     */
    default boolean isNearYearHigh() {
        return getLatestIndex(TAIEX)
                .map(data -> data.isNearYearHigh(0.02))
                .orElse(false);
    }

    /**
     * Check if index is near 52-week low.
     */
    default boolean isNearYearLow() {
        return getLatestIndex(TAIEX)
                .map(data -> data.isNearYearLow(0.02))
                .orElse(false);
    }

    // ========== Factory Methods ==========

    /**
     * Create a no-op provider that returns empty for all queries.
     * Useful for testing or when index data is not available.
     */
    static IndexDataProvider noOp() {
        return symbol -> Optional.empty();
    }

    /**
     * Create a provider with a fixed index value.
     * Useful for testing.
     */
    static IndexDataProvider withFixedValue(double value) {
        return symbol -> Optional.of(IndexData.builder()
                .indexSymbol(symbol)
                .closeValue(value)
                .tradeDate(LocalDate.now())
                .build());
    }

    /**
     * Create a provider with a fixed IndexData.
     */
    static IndexDataProvider withFixedData(IndexData data) {
        return symbol -> Optional.of(data);
    }
}
