package tw.gc.auto.equity.trader.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OrderBookData Entity - Level 2 order book snapshots with bid/ask depth.
 * 
 * <h3>Trading Lifecycle Role:</h3>
 * <ul>
 *   <li><b>Market Making</b>: Spread capture and inventory management</li>
 *   <li><b>Order Flow Analysis</b>: Buy/sell imbalance detection</li>
 *   <li><b>Liquidity Assessment</b>: Market depth and execution cost estimation</li>
 *   <li><b>Toxicity Detection</b>: Identify informed trading patterns</li>
 * </ul>
 * 
 * <h3>Data Source:</h3>
 * Retrieved from Shioaji L2 BidAsk subscription via Python bridge.
 * 
 * @since 2026-01-27 - Phase 5 Data Improvement Plan
 * @see OrderBookService for data retrieval
 */
@Entity
@Table(name = "order_book_data", indexes = {
    @Index(name = "idx_order_book_symbol_timestamp", columnList = "symbol, timestamp"),
    @Index(name = "idx_order_book_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 20, nullable = false)
    private String symbol;

    // Best bid (highest buy price)
    @Column(name = "bid_price_1")
    private Double bidPrice1;
    
    @Column(name = "bid_volume_1")
    private Long bidVolume1;

    // Second best bid
    @Column(name = "bid_price_2")
    private Double bidPrice2;
    
    @Column(name = "bid_volume_2")
    private Long bidVolume2;

    // Third best bid
    @Column(name = "bid_price_3")
    private Double bidPrice3;
    
    @Column(name = "bid_volume_3")
    private Long bidVolume3;

    // Fourth best bid
    @Column(name = "bid_price_4")
    private Double bidPrice4;
    
    @Column(name = "bid_volume_4")
    private Long bidVolume4;

    // Fifth best bid
    @Column(name = "bid_price_5")
    private Double bidPrice5;
    
    @Column(name = "bid_volume_5")
    private Long bidVolume5;

    // Best ask (lowest sell price)
    @Column(name = "ask_price_1")
    private Double askPrice1;
    
    @Column(name = "ask_volume_1")
    private Long askVolume1;

    // Second best ask
    @Column(name = "ask_price_2")
    private Double askPrice2;
    
    @Column(name = "ask_volume_2")
    private Long askVolume2;

    // Third best ask
    @Column(name = "ask_price_3")
    private Double askPrice3;
    
    @Column(name = "ask_volume_3")
    private Long askVolume3;

    // Fourth best ask
    @Column(name = "ask_price_4")
    private Double askPrice4;
    
    @Column(name = "ask_volume_4")
    private Long askVolume4;

    // Fifth best ask
    @Column(name = "ask_price_5")
    private Double askPrice5;
    
    @Column(name = "ask_volume_5")
    private Long askVolume5;

    // Calculated metrics
    @Column(name = "spread")
    private Double spread;

    @Column(name = "spread_bps")
    private Double spreadBps; // Spread in basis points

    @Column(name = "mid_price")
    private Double midPrice;

    @Column(name = "total_bid_volume")
    private Long totalBidVolume;

    @Column(name = "total_ask_volume")
    private Long totalAskVolume;

    @Column(name = "imbalance")
    private Double imbalance; // (bidVol - askVol) / (bidVol + askVol)

    /**
     * Get the bid-ask spread.
     * @return Spread as absolute price difference, or null if unavailable
     */
    public Double getSpread() {
        if (spread != null) {
            return spread;
        }
        if (askPrice1 != null && bidPrice1 != null && askPrice1 > 0 && bidPrice1 > 0) {
            return askPrice1 - bidPrice1;
        }
        return null;
    }

    /**
     * Get spread in basis points relative to mid-price.
     * @return Spread in bps, or null if unavailable
     */
    public Double getSpreadBps() {
        if (spreadBps != null) {
            return spreadBps;
        }
        Double s = getSpread();
        Double mid = getMidPrice();
        if (s != null && mid != null && mid > 0) {
            return (s / mid) * 10000;
        }
        return null;
    }

    /**
     * Get the mid-price (average of best bid and ask).
     * @return Mid-price, or null if unavailable
     */
    public Double getMidPrice() {
        if (midPrice != null) {
            return midPrice;
        }
        if (askPrice1 != null && bidPrice1 != null && askPrice1 > 0 && bidPrice1 > 0) {
            return (askPrice1 + bidPrice1) / 2.0;
        }
        return null;
    }

    /**
     * Get total bid volume across all levels.
     * @return Total bid volume
     */
    public Long getTotalBidVolume() {
        if (totalBidVolume != null) {
            return totalBidVolume;
        }
        long total = 0;
        if (bidVolume1 != null) total += bidVolume1;
        if (bidVolume2 != null) total += bidVolume2;
        if (bidVolume3 != null) total += bidVolume3;
        if (bidVolume4 != null) total += bidVolume4;
        if (bidVolume5 != null) total += bidVolume5;
        return total;
    }

    /**
     * Get total ask volume across all levels.
     * @return Total ask volume
     */
    public Long getTotalAskVolume() {
        if (totalAskVolume != null) {
            return totalAskVolume;
        }
        long total = 0;
        if (askVolume1 != null) total += askVolume1;
        if (askVolume2 != null) total += askVolume2;
        if (askVolume3 != null) total += askVolume3;
        if (askVolume4 != null) total += askVolume4;
        if (askVolume5 != null) total += askVolume5;
        return total;
    }

    /**
     * Get order flow imbalance ratio.
     * Positive = more buy pressure, Negative = more sell pressure.
     * @return Imbalance ratio in range [-1, 1], or 0 if volumes unavailable
     */
    public Double getImbalance() {
        if (imbalance != null) {
            return imbalance;
        }
        long bidVol = getTotalBidVolume();
        long askVol = getTotalAskVolume();
        long total = bidVol + askVol;
        return total > 0 ? (double)(bidVol - askVol) / total : 0.0;
    }

    /**
     * Check if the spread is wider than normal.
     * @param normalSpreadBps Normal spread in basis points
     * @return true if current spread exceeds normal
     */
    public boolean isWideSpread(double normalSpreadBps) {
        Double current = getSpreadBps();
        return current != null && current > normalSpreadBps;
    }

    /**
     * Check if there's significant buy pressure.
     * @param threshold Imbalance threshold (e.g., 0.3)
     * @return true if buy pressure exceeds threshold
     */
    public boolean hasBuyPressure(double threshold) {
        Double imb = getImbalance();
        return imb != null && imb > threshold;
    }

    /**
     * Check if there's significant sell pressure.
     * @param threshold Imbalance threshold (e.g., 0.3)
     * @return true if sell pressure exceeds threshold
     */
    public boolean hasSellPressure(double threshold) {
        Double imb = getImbalance();
        return imb != null && imb < -threshold;
    }

    /**
     * Get depth at a specific bid level.
     * @param level 1-5
     * @return Volume at that level, or 0
     */
    public long getBidDepth(int level) {
        return switch (level) {
            case 1 -> bidVolume1 != null ? bidVolume1 : 0;
            case 2 -> bidVolume2 != null ? bidVolume2 : 0;
            case 3 -> bidVolume3 != null ? bidVolume3 : 0;
            case 4 -> bidVolume4 != null ? bidVolume4 : 0;
            case 5 -> bidVolume5 != null ? bidVolume5 : 0;
            default -> 0;
        };
    }

    /**
     * Get depth at a specific ask level.
     * @param level 1-5
     * @return Volume at that level, or 0
     */
    public long getAskDepth(int level) {
        return switch (level) {
            case 1 -> askVolume1 != null ? askVolume1 : 0;
            case 2 -> askVolume2 != null ? askVolume2 : 0;
            case 3 -> askVolume3 != null ? askVolume3 : 0;
            case 4 -> askVolume4 != null ? askVolume4 : 0;
            case 5 -> askVolume5 != null ? askVolume5 : 0;
            default -> 0;
        };
    }

    /**
     * Get bid price at a specific level.
     * @param level 1-5
     * @return Price at that level, or null
     */
    public Double getBidPrice(int level) {
        return switch (level) {
            case 1 -> bidPrice1;
            case 2 -> bidPrice2;
            case 3 -> bidPrice3;
            case 4 -> bidPrice4;
            case 5 -> bidPrice5;
            default -> null;
        };
    }

    /**
     * Get ask price at a specific level.
     * @param level 1-5
     * @return Price at that level, or null
     */
    public Double getAskPrice(int level) {
        return switch (level) {
            case 1 -> askPrice1;
            case 2 -> askPrice2;
            case 3 -> askPrice3;
            case 4 -> askPrice4;
            case 5 -> askPrice5;
            default -> null;
        };
    }

    /**
     * Calculate volume-weighted average bid price (VWAB).
     * @return VWAB or best bid if calculation fails
     */
    public Double getVolumeWeightedBidPrice() {
        double totalVolPrice = 0;
        long totalVol = 0;
        
        for (int i = 1; i <= 5; i++) {
            Double price = getBidPrice(i);
            long vol = getBidDepth(i);
            if (price != null && vol > 0) {
                totalVolPrice += price * vol;
                totalVol += vol;
            }
        }
        
        if (totalVol > 0) {
            return totalVolPrice / totalVol;
        }
        return bidPrice1;
    }

    /**
     * Calculate volume-weighted average ask price (VWAA).
     * @return VWAA or best ask if calculation fails
     */
    public Double getVolumeWeightedAskPrice() {
        double totalVolPrice = 0;
        long totalVol = 0;
        
        for (int i = 1; i <= 5; i++) {
            Double price = getAskPrice(i);
            long vol = getAskDepth(i);
            if (price != null && vol > 0) {
                totalVolPrice += price * vol;
                totalVol += vol;
            }
        }
        
        if (totalVol > 0) {
            return totalVolPrice / totalVol;
        }
        return askPrice1;
    }

    /**
     * Check if order book has valid data.
     * @return true if both bid and ask prices are present
     */
    public boolean isValid() {
        return bidPrice1 != null && bidPrice1 > 0 
            && askPrice1 != null && askPrice1 > 0
            && askPrice1 > bidPrice1;
    }

    /**
     * Get market depth (number of price levels with volume).
     * @return Count of levels with data (0-5)
     */
    public int getDepthLevels() {
        int count = 0;
        if (bidPrice1 != null && bidVolume1 != null && bidVolume1 > 0) count++;
        if (bidPrice2 != null && bidVolume2 != null && bidVolume2 > 0) count++;
        if (bidPrice3 != null && bidVolume3 != null && bidVolume3 > 0) count++;
        if (bidPrice4 != null && bidVolume4 != null && bidVolume4 > 0) count++;
        if (bidPrice5 != null && bidVolume5 != null && bidVolume5 > 0) count++;
        return count;
    }

    @Override
    public String toString() {
        return String.format("OrderBook[%s @ %s: bid=%.2f(%d), ask=%.2f(%d), spread=%.2fbps, imb=%.2f]",
                symbol,
                timestamp,
                bidPrice1 != null ? bidPrice1 : 0.0,
                bidVolume1 != null ? bidVolume1 : 0,
                askPrice1 != null ? askPrice1 : 0.0,
                askVolume1 != null ? askVolume1 : 0,
                getSpreadBps() != null ? getSpreadBps() : 0.0,
                getImbalance() != null ? getImbalance() : 0.0);
    }
}
