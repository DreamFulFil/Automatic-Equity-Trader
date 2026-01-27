package tw.gc.auto.equity.trader.strategy;

import tw.gc.auto.equity.trader.entities.OrderBookData;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Functional interface for providing order book (Level 2) data to strategies.
 * 
 * <p>This interface allows strategies to access bid/ask depth, spread, and
 * order flow imbalance without direct dependency on OrderBookService.
 * 
 * <h3>Usage:</h3>
 * <pre>
 * // In BacktestService (provider implementation)
 * OrderBookProvider provider = symbol -> orderBookService.getLatest(symbol);
 * 
 * // In strategy constructor
 * MarketMakingStrategy strategy = new MarketMakingStrategy(0.001, 10, provider);
 * </pre>
 * 
 * <h3>Supported Data:</h3>
 * <ul>
 *   <li>Best bid/ask prices and volumes</li>
 *   <li>Order book depth (5 levels)</li>
 *   <li>Bid-ask spread and spread in basis points</li>
 *   <li>Order flow imbalance (buy/sell pressure)</li>
 *   <li>Historical order book snapshots</li>
 * </ul>
 * 
 * @since 2026-01-27 - Phase 5 Data Improvement Plan
 */
@FunctionalInterface
public interface OrderBookProvider extends Function<String, Optional<OrderBookData>> {

    /**
     * Get the latest order book snapshot for a symbol.
     * 
     * @param symbol Stock/futures symbol (e.g., "2454.TW")
     * @return Optional containing OrderBookData if available
     */
    Optional<OrderBookData> getOrderBook(String symbol);

    /**
     * Default implementation delegates to getOrderBook.
     */
    @Override
    default Optional<OrderBookData> apply(String symbol) {
        return getOrderBook(symbol);
    }

    // ============ Convenience Methods ============

    /**
     * Get the best bid price.
     * 
     * @param symbol Stock/futures symbol
     * @return Best bid price, or null if unavailable
     */
    default Double getBestBid(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getBidPrice1)
                .orElse(null);
    }

    /**
     * Get the best ask price.
     * 
     * @param symbol Stock/futures symbol
     * @return Best ask price, or null if unavailable
     */
    default Double getBestAsk(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getAskPrice1)
                .orElse(null);
    }

    /**
     * Get the mid-price (average of best bid and ask).
     * 
     * @param symbol Stock/futures symbol
     * @return Mid-price, or null if unavailable
     */
    default Double getMidPrice(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getMidPrice)
                .orElse(null);
    }

    /**
     * Get the bid-ask spread.
     * 
     * @param symbol Stock/futures symbol
     * @return Spread as absolute price difference, or null if unavailable
     */
    default Double getSpread(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getSpread)
                .orElse(null);
    }

    /**
     * Get the spread in basis points.
     * 
     * @param symbol Stock/futures symbol
     * @return Spread in bps, or null if unavailable
     */
    default Double getSpreadBps(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getSpreadBps)
                .orElse(null);
    }

    /**
     * Get the order flow imbalance.
     * 
     * @param symbol Stock/futures symbol
     * @return Imbalance ratio in [-1, 1], or null if unavailable
     */
    default Double getImbalance(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getImbalance)
                .orElse(null);
    }

    /**
     * Get total bid volume across all levels.
     * 
     * @param symbol Stock/futures symbol
     * @return Total bid volume, or null if unavailable
     */
    default Long getTotalBidVolume(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getTotalBidVolume)
                .orElse(null);
    }

    /**
     * Get total ask volume across all levels.
     * 
     * @param symbol Stock/futures symbol
     * @return Total ask volume, or null if unavailable
     */
    default Long getTotalAskVolume(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getTotalAskVolume)
                .orElse(null);
    }

    /**
     * Check if there's significant buy pressure.
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Imbalance threshold (e.g., 0.3)
     * @return true if buy pressure exceeds threshold
     */
    default boolean hasBuyPressure(String symbol, double threshold) {
        return getOrderBook(symbol)
                .map(ob -> ob.hasBuyPressure(threshold))
                .orElse(false);
    }

    /**
     * Check if there's significant sell pressure.
     * 
     * @param symbol Stock/futures symbol
     * @param threshold Imbalance threshold (e.g., 0.3)
     * @return true if sell pressure exceeds threshold
     */
    default boolean hasSellPressure(String symbol, double threshold) {
        return getOrderBook(symbol)
                .map(ob -> ob.hasSellPressure(threshold))
                .orElse(false);
    }

    /**
     * Check if the spread is wider than normal.
     * 
     * @param symbol Stock/futures symbol
     * @param normalSpreadBps Normal spread in basis points
     * @return true if current spread exceeds normal
     */
    default boolean isWideSpread(String symbol, double normalSpreadBps) {
        return getOrderBook(symbol)
                .map(ob -> ob.isWideSpread(normalSpreadBps))
                .orElse(false);
    }

    /**
     * Check if order book data is available and valid.
     * 
     * @param symbol Stock/futures symbol
     * @return true if valid order book data is available
     */
    default boolean hasValidData(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::isValid)
                .orElse(false);
    }

    /**
     * Get bid depth at a specific level.
     * 
     * @param symbol Stock/futures symbol
     * @param level Level 1-5
     * @return Volume at that level, or 0
     */
    default long getBidDepth(String symbol, int level) {
        return getOrderBook(symbol)
                .map(ob -> ob.getBidDepth(level))
                .orElse(0L);
    }

    /**
     * Get ask depth at a specific level.
     * 
     * @param symbol Stock/futures symbol
     * @param level Level 1-5
     * @return Volume at that level, or 0
     */
    default long getAskDepth(String symbol, int level) {
        return getOrderBook(symbol)
                .map(ob -> ob.getAskDepth(level))
                .orElse(0L);
    }

    /**
     * Get number of depth levels available.
     * 
     * @param symbol Stock/futures symbol
     * @return Number of levels (0-5)
     */
    default int getDepthLevels(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getDepthLevels)
                .orElse(0);
    }

    /**
     * Get volume-weighted average bid price.
     * 
     * @param symbol Stock/futures symbol
     * @return VWAB, or null if unavailable
     */
    default Double getVolumeWeightedBidPrice(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getVolumeWeightedBidPrice)
                .orElse(null);
    }

    /**
     * Get volume-weighted average ask price.
     * 
     * @param symbol Stock/futures symbol
     * @return VWAA, or null if unavailable
     */
    default Double getVolumeWeightedAskPrice(String symbol) {
        return getOrderBook(symbol)
                .map(OrderBookData::getVolumeWeightedAskPrice)
                .orElse(null);
    }

    // ============ Extended Interface ============

    /**
     * Get historical order book snapshots.
     * Default implementation returns empty list (override for full functionality).
     * 
     * @param symbol Stock/futures symbol
     * @param minutes Number of minutes of history
     * @return List of historical snapshots
     */
    default List<OrderBookData> getHistory(String symbol, int minutes) {
        return Collections.emptyList();
    }

    /**
     * Get average spread over recent history.
     * Default returns current spread (override for moving average).
     * 
     * @param symbol Stock/futures symbol
     * @param minutes Number of minutes for average
     * @return Average spread in bps, or null if unavailable
     */
    default Double getAverageSpreadBps(String symbol, int minutes) {
        return getSpreadBps(symbol);
    }

    /**
     * Get average imbalance over recent history.
     * Default returns current imbalance (override for moving average).
     * 
     * @param symbol Stock/futures symbol
     * @param minutes Number of minutes for average
     * @return Average imbalance, or null if unavailable
     */
    default Double getAverageImbalance(String symbol, int minutes) {
        return getImbalance(symbol);
    }

    // ============ Factory Methods ============

    /**
     * Create a no-op provider that returns empty for all queries.
     * Useful for testing or when order book data is unavailable.
     * 
     * @return No-op provider
     */
    static OrderBookProvider noOp() {
        return symbol -> Optional.empty();
    }

    /**
     * Create a provider with fixed order book data.
     * Useful for testing strategies.
     * 
     * @param bidPrice Best bid price
     * @param askPrice Best ask price
     * @param bidVolume Best bid volume
     * @param askVolume Best ask volume
     * @return Provider with fixed data
     */
    static OrderBookProvider withFixedData(double bidPrice, double askPrice, 
                                           long bidVolume, long askVolume) {
        OrderBookData data = OrderBookData.builder()
                .bidPrice1(bidPrice)
                .askPrice1(askPrice)
                .bidVolume1(bidVolume)
                .askVolume1(askVolume)
                .build();
        return symbol -> Optional.of(data);
    }

    /**
     * Create a provider with fixed spread.
     * 
     * @param midPrice Mid-price
     * @param spreadBps Spread in basis points
     * @return Provider with fixed spread
     */
    static OrderBookProvider withFixedSpread(double midPrice, double spreadBps) {
        double spread = midPrice * spreadBps / 10000.0;
        double bid = midPrice - spread / 2;
        double ask = midPrice + spread / 2;
        return withFixedData(bid, ask, 1000L, 1000L);
    }

    /**
     * Create a provider with fixed imbalance.
     * 
     * @param midPrice Mid-price
     * @param imbalance Imbalance ratio [-1, 1]
     * @return Provider with fixed imbalance
     */
    static OrderBookProvider withFixedImbalance(double midPrice, double imbalance) {
        long totalVol = 10000;
        long bidVol = (long)((1 + imbalance) / 2 * totalVol);
        long askVol = totalVol - bidVol;
        double bid = midPrice * 0.999;
        double ask = midPrice * 1.001;
        return withFixedData(bid, ask, bidVol, askVol);
    }

    /**
     * Create a provider with full order book depth.
     * 
     * @param data Full OrderBookData object
     * @return Provider with full data
     */
    static OrderBookProvider withFullData(OrderBookData data) {
        return symbol -> Optional.of(data);
    }

    /**
     * Create a provider that supplies different data per symbol.
     * 
     * @param dataSupplier Function to get data by symbol
     * @return Provider backed by supplier
     */
    static OrderBookProvider fromFunction(Function<String, Optional<OrderBookData>> dataSupplier) {
        return dataSupplier::apply;
    }
}
