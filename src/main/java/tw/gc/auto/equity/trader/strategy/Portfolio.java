package tw.gc.auto.equity.trader.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Portfolio state snapshot for strategy execution
 * Immutable view of current positions, equity, and P&L
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {
    
    /**
     * Current positions by symbol (positive = long, negative = short, 0 = flat)
     */
    private Map<String, Integer> positions;
    
    /**
     * Entry prices for current positions by symbol
     */
    private Map<String, Double> entryPrices;
    
    /**
     * Entry times for current positions by symbol
     */
    private Map<String, LocalDateTime> entryTimes;
    
    /**
     * Total account equity (TWD)
     */
    private double equity;
    
    /**
     * Available margin/buying power (TWD)
     */
    private double availableMargin;
    
    /**
     * Today's realized P&L (TWD)
     */
    private double dailyPnL;
    
    /**
     * This week's cumulative P&L (TWD)
     */
    private double weeklyPnL;
    
    /**
     * Trading mode ("stock" or "futures")
     */
    private String tradingMode;
    
    /**
     * Current trading quantity (shares for stock, contracts for futures)
     */
    private int tradingQuantity;
    
    /**
     * Check if flat (no positions)
     */
    public boolean isFlat() {
        return positions == null || positions.isEmpty() || 
               positions.values().stream().allMatch(p -> p == 0);
    }
    
    /**
     * Get position for a specific symbol
     */
    public int getPosition(String symbol) {
        return positions != null ? positions.getOrDefault(symbol, 0) : 0;
    }
    
    /**
     * Get entry price for a specific symbol
     */
    public Double getEntryPrice(String symbol) {
        return entryPrices != null ? entryPrices.get(symbol) : null;
    }
    
    /**
     * Get entry time for a specific symbol
     */
    public LocalDateTime getEntryTime(String symbol) {
        return entryTimes != null ? entryTimes.get(symbol) : null;
    }
}
