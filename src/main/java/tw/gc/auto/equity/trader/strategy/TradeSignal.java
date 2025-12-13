package tw.gc.auto.equity.trader.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trade signal returned by strategy execution
 * Contains direction, confidence, and reasoning
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignal {
    
    /**
     * Signal direction
     */
    private SignalDirection direction;
    
    /**
     * Confidence level (0.0 to 1.0)
     */
    private double confidence;
    
    /**
     * Human-readable reason for the signal
     */
    private String reason;
    
    /**
     * Whether this is an exit signal for existing position
     */
    private boolean exitSignal;
    
    /**
     * Signal direction enum
     */
    public enum SignalDirection {
        LONG,    // Buy signal
        SHORT,   // Sell signal  
        NEUTRAL  // Hold / no action
    }
    
    /**
     * Create a NEUTRAL signal (no action)
     */
    public static TradeSignal neutral(String reason) {
        return TradeSignal.builder()
                .direction(SignalDirection.NEUTRAL)
                .confidence(0.0)
                .reason(reason)
                .exitSignal(false)
                .build();
    }
    
    /**
     * Create a LONG signal (buy)
     */
    public static TradeSignal longSignal(double confidence, String reason) {
        return TradeSignal.builder()
                .direction(SignalDirection.LONG)
                .confidence(confidence)
                .reason(reason)
                .exitSignal(false)
                .build();
    }
    
    /**
     * Create a SHORT signal (sell)
     */
    public static TradeSignal shortSignal(double confidence, String reason) {
        return TradeSignal.builder()
                .direction(SignalDirection.SHORT)
                .confidence(confidence)
                .reason(reason)
                .exitSignal(false)
                .build();
    }
    
    /**
     * Create an EXIT signal
     */
    public static TradeSignal exitSignal(SignalDirection direction, double confidence, String reason) {
        return TradeSignal.builder()
                .direction(direction)
                .confidence(confidence)
                .reason(reason)
                .exitSignal(true)
                .build();
    }
}
