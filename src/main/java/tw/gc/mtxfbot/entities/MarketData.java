package tw.gc.mtxfbot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MarketData entity for storing OHLCV (Open, High, Low, Close, Volume) candlestick data.
 * Essential for backtesting, technical analysis, and chart generation.
 * 
 * Data is aggregated at various timeframes (1min, 5min, 15min, 1h, 1d).
 */
@Entity
@Table(name = "market_data", indexes = {
    @Index(name = "idx_market_data_symbol_timestamp", columnList = "symbol, timestamp"),
    @Index(name = "idx_market_data_timeframe", columnList = "timeframe")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 20, nullable = false)
    private String symbol; // e.g., "2454.TW", "MTXF"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Timeframe timeframe;

    @Column(name = "open_price", nullable = false)
    private double open;

    @Column(name = "high_price", nullable = false)
    private double high;

    @Column(name = "low_price", nullable = false)
    private double low;

    @Column(name = "close_price", nullable = false)
    private double close;

    @Column(nullable = false)
    private long volume;

    @Column(name = "tick_count")
    private Integer tickCount; // Number of ticks in this candle

    // Technical indicators calculated at this candle
    @Column(name = "sma_20")
    private Double sma20; // 20-period Simple Moving Average

    @Column(name = "sma_50")
    private Double sma50; // 50-period Simple Moving Average

    @Column(name = "ema_12")
    private Double ema12; // 12-period Exponential Moving Average

    @Column(name = "ema_26")
    private Double ema26; // 26-period Exponential Moving Average

    @Column
    private Double rsi; // Relative Strength Index (14-period default)

    @Column(name = "macd_line")
    private Double macdLine; // MACD line (EMA12 - EMA26)

    @Column(name = "macd_signal")
    private Double macdSignal; // MACD signal line (9-period EMA of MACD)

    @Column(name = "macd_histogram")
    private Double macdHistogram; // MACD histogram (MACD - Signal)

    @Column(name = "bollinger_upper")
    private Double bollingerUpper; // Upper Bollinger Band

    @Column(name = "bollinger_lower")
    private Double bollingerLower; // Lower Bollinger Band

    @Column(name = "bollinger_middle")
    private Double bollingerMiddle; // Middle Bollinger Band (20-SMA)

    @Column
    private Double atr; // Average True Range (14-period)

    @Column
    private Double vwap; // Volume Weighted Average Price

    @Column(name = "volume_sma")
    private Double volumeSma; // Volume SMA for comparison

    @Column(name = "momentum_pct")
    private Double momentumPct; // Price momentum percentage

    public enum Timeframe {
        TICK,       // Raw tick data (aggregated per second)
        MIN_1,      // 1-minute candles
        MIN_5,      // 5-minute candles
        MIN_15,     // 15-minute candles
        MIN_30,     // 30-minute candles
        HOUR_1,     // 1-hour candles
        DAY_1       // Daily candles
    }
}
