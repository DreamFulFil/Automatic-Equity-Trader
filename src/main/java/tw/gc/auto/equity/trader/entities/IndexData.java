package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * IndexData Entity - Market index data for benchmark comparison and arbitrage strategies.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Index Arbitrage</b>: Compare stock prices against index fair value</li>
 *   <li><b>Beta Calculation</b>: Use index returns for market model beta</li>
 *   <li><b>Relative Performance</b>: Benchmark individual stock performance</li>
 *   <li><b>Market Timing</b>: Assess overall market direction</li>
 * </ul>
 * 
 * <h3>Supported Indices:</h3>
 * <ul>
 *   <li><b>^TWII</b>: Taiwan Capitalization Weighted Stock Index (TAIEX)</li>
 *   <li><b>^TWOII</b>: Taiwan OTC Index</li>
 *   <li><b>0050.TW</b>: Taiwan Top 50 ETF (as index proxy)</li>
 * </ul>
 * 
 * <h3>Data Source:</h3>
 * Fetched from Yahoo Finance (yfinance) via Python bridge, updated intraday.
 * 
 * @see IndexDataService for data retrieval and refresh
 * @since 2026-01-26 - Phase 3 Data Improvement Plan
 */
@Entity
@Table(name = "index_data", indexes = {
    @Index(name = "idx_index_symbol", columnList = "index_symbol"),
    @Index(name = "idx_index_symbol_date", columnList = "index_symbol, trade_date"),
    @Index(name = "idx_index_fetched_at", columnList = "fetched_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Index symbol (e.g., "^TWII", "^TWOII", "0050.TW")
     */
    @Column(name = "index_symbol", length = 20, nullable = false)
    private String indexSymbol;

    /**
     * Index display name (e.g., "TAIEX", "Taiwan OTC Index")
     */
    @Column(name = "index_name", length = 100)
    private String indexName;

    /**
     * Trading date for this data point
     */
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /**
     * When this data was fetched from the data source
     */
    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    // ========== Price Data ==========

    /**
     * Opening index value
     */
    @Column(name = "open_value")
    private Double openValue;

    /**
     * Highest index value during trading
     */
    @Column(name = "high_value")
    private Double highValue;

    /**
     * Lowest index value during trading
     */
    @Column(name = "low_value")
    private Double lowValue;

    /**
     * Closing/current index value
     */
    @Column(name = "close_value", nullable = false)
    private Double closeValue;

    /**
     * Previous day's closing value (for change calculation)
     */
    @Column(name = "previous_close")
    private Double previousClose;

    // ========== Change Metrics ==========

    /**
     * Absolute change from previous close
     */
    @Column(name = "change_points")
    private Double changePoints;

    /**
     * Percentage change from previous close
     */
    @Column(name = "change_percent")
    private Double changePercent;

    // ========== Volume & Trading Activity ==========

    /**
     * Total trading volume (shares traded)
     */
    @Column
    private Long volume;

    /**
     * Total trading value (in currency)
     */
    @Column(name = "trading_value")
    private Double tradingValue;

    // ========== Technical Indicators ==========

    /**
     * 52-week high
     */
    @Column(name = "year_high")
    private Double yearHigh;

    /**
     * 52-week low
     */
    @Column(name = "year_low")
    private Double yearLow;

    /**
     * 20-day moving average
     */
    @Column(name = "ma_20")
    private Double ma20;

    /**
     * 50-day moving average
     */
    @Column(name = "ma_50")
    private Double ma50;

    /**
     * 200-day moving average
     */
    @Column(name = "ma_200")
    private Double ma200;

    // ========== Volatility Metrics ==========

    /**
     * 20-day realized volatility (annualized)
     */
    @Column(name = "volatility_20d")
    private Double volatility20d;

    /**
     * 60-day realized volatility (annualized)
     */
    @Column(name = "volatility_60d")
    private Double volatility60d;

    // ========== Utility Methods ==========

    /**
     * Calculate daily return from previous close.
     * @return Daily return as decimal (e.g., 0.02 for 2%)
     */
    public Double getDailyReturn() {
        if (previousClose == null || previousClose == 0 || closeValue == null) {
            return null;
        }
        return (closeValue - previousClose) / previousClose;
    }

    /**
     * Check if index is above its 50-day moving average (bullish).
     */
    public boolean isAboveMa50() {
        return closeValue != null && ma50 != null && closeValue > ma50;
    }

    /**
     * Check if index is above its 200-day moving average (long-term bullish).
     */
    public boolean isAboveMa200() {
        return closeValue != null && ma200 != null && closeValue > ma200;
    }

    /**
     * Check if index is in a bull market (above both 50 and 200 MA).
     */
    public boolean isBullMarket() {
        return isAboveMa50() && isAboveMa200();
    }

    /**
     * Check if there's a golden cross (50 MA above 200 MA).
     */
    public boolean hasGoldenCross() {
        return ma50 != null && ma200 != null && ma50 > ma200;
    }

    /**
     * Check if there's a death cross (50 MA below 200 MA).
     */
    public boolean hasDeathCross() {
        return ma50 != null && ma200 != null && ma50 < ma200;
    }

    /**
     * Get distance from 52-week high as percentage.
     * @return Negative percentage (e.g., -0.05 for 5% below high)
     */
    public Double getDistanceFromYearHigh() {
        if (yearHigh == null || yearHigh == 0 || closeValue == null) {
            return null;
        }
        return (closeValue - yearHigh) / yearHigh;
    }

    /**
     * Get distance from 52-week low as percentage.
     * @return Positive percentage (e.g., 0.10 for 10% above low)
     */
    public Double getDistanceFromYearLow() {
        if (yearLow == null || yearLow == 0 || closeValue == null) {
            return null;
        }
        return (closeValue - yearLow) / yearLow;
    }

    /**
     * Check if index is near 52-week high (within threshold).
     * @param threshold Percentage threshold (e.g., 0.02 for 2%)
     */
    public boolean isNearYearHigh(double threshold) {
        Double distance = getDistanceFromYearHigh();
        return distance != null && Math.abs(distance) <= threshold;
    }

    /**
     * Check if index is near 52-week low (within threshold).
     * @param threshold Percentage threshold (e.g., 0.02 for 2%)
     */
    public boolean isNearYearLow(double threshold) {
        Double distance = getDistanceFromYearLow();
        return distance != null && distance <= threshold;
    }

    /**
     * Get position within 52-week range (0 = at low, 1 = at high).
     * @return Position in range [0, 1]
     */
    public Double getYearRangePosition() {
        if (yearHigh == null || yearLow == null || closeValue == null) {
            return null;
        }
        double range = yearHigh - yearLow;
        if (range == 0) {
            return 0.5;
        }
        return (closeValue - yearLow) / range;
    }

    /**
     * Check if today was a positive day.
     */
    public boolean isPositiveDay() {
        return changePercent != null && changePercent > 0;
    }

    /**
     * Check if today was a negative day.
     */
    public boolean isNegativeDay() {
        return changePercent != null && changePercent < 0;
    }

    /**
     * Check if today had significant movement (above threshold).
     * @param threshold Absolute percentage threshold (e.g., 0.01 for 1%)
     */
    public boolean hasSignificantMove(double threshold) {
        return changePercent != null && Math.abs(changePercent) >= threshold;
    }

    @Override
    public String toString() {
        return String.format("IndexData{symbol='%s', date=%s, close=%.2f, change=%.2f%%}", 
            indexSymbol, tradeDate, closeValue != null ? closeValue : 0.0, 
            changePercent != null ? changePercent * 100 : 0.0);
    }
}
