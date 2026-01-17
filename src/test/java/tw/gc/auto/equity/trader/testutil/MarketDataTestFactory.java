package tw.gc.auto.equity.trader.testutil;

import tw.gc.auto.equity.trader.entities.Bar;
import tw.gc.auto.equity.trader.entities.MarketData;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Test factory for creating sample market data and bars for testing.
 * Provides various scenarios including normal data, gaps, and extreme movements.
 */
public class MarketDataTestFactory {

    private static final double DEFAULT_BASE_PRICE = 100.0;
    private static final long DEFAULT_VOLUME = 1000L;

    /**
     * Create a list of sample historical market data with consecutive days.
     *
     * @param symbol the stock symbol
     * @param days number of days of data to generate
     * @return list of MarketData with daily timestamps
     */
    public static List<MarketData> createSampleHistory(String symbol, int days) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);
        
        for (int i = 0; i < days; i++) {
            LocalDateTime timestamp = baseTime.plusDays(i);
            double price = DEFAULT_BASE_PRICE + (i % 10); // Slight variation
            history.add(createMarketData(symbol, timestamp, price));
        }
        
        return history;
    }

    /**
     * Create market data with gaps on specified days.
     *
     * @param symbol the stock symbol
     * @param skipDays list of day indices to skip (0-based)
     * @return list of MarketData with missing days
     */
    public static List<MarketData> createHistoryWithGaps(String symbol, List<Integer> skipDays) {
        List<MarketData> history = new ArrayList<>();
        int totalDays = skipDays.stream().max(Integer::compareTo).orElse(30) + 10;
        LocalDateTime baseTime = LocalDateTime.now().minusDays(totalDays);
        
        for (int i = 0; i < totalDays; i++) {
            if (!skipDays.contains(i)) {
                LocalDateTime timestamp = baseTime.plusDays(i);
                double price = DEFAULT_BASE_PRICE + (i % 10);
                history.add(createMarketData(symbol, timestamp, price));
            }
        }
        
        return history;
    }

    /**
     * Create a single MarketData instance.
     *
     * @param symbol the stock symbol
     * @param timestamp the timestamp
     * @param price the close price
     * @return a MarketData instance
     */
    public static MarketData createMarketData(String symbol, LocalDateTime timestamp, double price) {
        return MarketData.builder()
            .symbol(symbol)
            .timestamp(timestamp)
            .open(price * 0.98)
            .high(price * 1.02)
            .low(price * 0.96)
            .close(price)
            .volume(DEFAULT_VOLUME)
            .timeframe(MarketData.Timeframe.DAY_1)
            .build();
    }

    /**
     * Create market data with extreme price movements.
     *
     * @param symbol the stock symbol
     * @param days number of days
     * @param volatilityPercent daily swing percentage (e.g., 50 for 50%)
     * @return list of MarketData with high volatility
     */
    public static List<MarketData> createVolatileHistory(String symbol, int days, double volatilityPercent) {
        List<MarketData> history = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);
        double price = DEFAULT_BASE_PRICE;
        
        for (int i = 0; i < days; i++) {
            LocalDateTime timestamp = baseTime.plusDays(i);
            // Alternate between gains and losses
            double change = (i % 2 == 0 ? 1 : -1) * (volatilityPercent / 100.0);
            price = price * (1 + change);
            
            history.add(MarketData.builder()
                .symbol(symbol)
                .timestamp(timestamp)
                .open(price * 0.95)
                .high(price * 1.05)
                .low(price * 0.90)
                .close(price)
                .volume(DEFAULT_VOLUME * 2)
                .timeframe(MarketData.Timeframe.DAY_1)
                .build());
        }
        
        return history;
    }

    /**
     * Create a single Bar instance.
     *
     * @param symbol the stock symbol
     * @param timestamp the timestamp
     * @param price the close price
     * @return a Bar instance
     */
    public static Bar createBar(String symbol, LocalDateTime timestamp, double price) {
        return Bar.builder()
            .symbol(symbol)
            .timestamp(timestamp)
            .name("Test Stock")
            .open(price * 0.98)
            .high(price * 1.02)
            .low(price * 0.96)
            .close(price)
            .volume(DEFAULT_VOLUME)
            .timeframe("1day")
            .build();
    }

    /**
     * Create a list of sample Bar data.
     *
     * @param symbol the stock symbol
     * @param days number of days
     * @return list of Bar instances
     */
    public static List<Bar> createSampleBars(String symbol, int days) {
        List<Bar> bars = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(days);
        
        for (int i = 0; i < days; i++) {
            LocalDateTime timestamp = baseTime.plusDays(i);
            double price = DEFAULT_BASE_PRICE + (i % 10);
            bars.add(createBar(symbol, timestamp, price));
        }
        
        return bars;
    }

    /**
     * Create market data with null or missing name field.
     *
     * @param symbol the stock symbol
     * @param timestamp the timestamp
     * @param price the close price
     * @return MarketData with null name
     */
    public static MarketData createMarketDataWithoutName(String symbol, LocalDateTime timestamp, double price) {
        return MarketData.builder()
            .symbol(symbol)
            .timestamp(timestamp)
            .name(null)
            .open(price * 0.98)
            .high(price * 1.02)
            .low(price * 0.96)
            .close(price)
            .volume(DEFAULT_VOLUME)
            .timeframe(MarketData.Timeframe.DAY_1)
            .build();
    }
}
