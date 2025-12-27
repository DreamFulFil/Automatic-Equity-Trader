package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MarketData Entity - OHLCV candlestick data for backtesting and technical analysis.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Backtesting</b>: Historical data source for strategy simulation</li>
 *   <li><b>Technical Analysis</b>: Pre-calculated indicators (SMA, EMA, RSI, MACD, Bollinger)</li>
 *   <li><b>Chart Generation</b>: OHLCV data for visualization</li>
 *   <li><b>Real-time Feed</b>: Stores tick-level to daily aggregations</li>
 * </ul>
 * 
 * <h3>Asset Type:</h3>
 * The {@code assetType} column defaults to {@code STOCK}. Supports both Taiwan stocks (TSE)
 * and Taiwan futures (TAIFEX) when futures trading is enabled.
 * 
 * @see BacktestService for historical simulation
 * @see HistoryDataService for data retrieval
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
    private String symbol; // e.g., "2454.TW", "AUTO_EQUITY_TRADER"

    @Column(length = 200)
    private String name; // e.g., "MediaTek", "Taiwan Semiconductor Manufacturing"

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

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    @Builder.Default
    private AssetType assetType = AssetType.STOCK;

    public enum Timeframe {
        TICK,       // Raw tick data (aggregated per second)
        MIN_1,      // 1-minute candles
        MIN_5,      // 5-minute candles
        MIN_15,     // 15-minute candles
        MIN_30,     // 30-minute candles
        HOUR_1,     // 1-hour candles
        DAY_1       // Daily candles
    }
    
    public enum AssetType {
        STOCK,
        FUTURE
    }
}
