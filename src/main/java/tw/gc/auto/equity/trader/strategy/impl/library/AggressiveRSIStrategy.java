package tw.gc.auto.equity.trader.strategy.impl.library;

import tw.gc.auto.equity.trader.strategy.impl.library.RSIStrategy;

/**
 * Aggressive RSI Strategy (Example Configuration)
 * 
 * Configuration:
 * - Period: 7 (Standard is 14)
 * - Overbought: 80 (Standard is 70)
 * - Oversold: 20 (Standard is 30)
 * 
 * Why this configuration?
 * 1. Shorter Period (7): Makes the RSI more sensitive to recent price changes. 
 *    It reacts faster to short-term momentum shifts, suitable for scalping or fast markets.
 * 
 * 2. Wider Bands (80/20): Since a 7-period RSI is more volatile, it will hit 70/30 very often.
 *    By widening the bands to 80/20, we filter out "noise" and only trade when the move is truly extreme.
 * 
 * Trade-off:
 * - Pros: Gets into trades earlier than standard RSI.
 * - Cons: More false signals if the market is just choppy.
 */
public class AggressiveRSIStrategy extends RSIStrategy {

    public AggressiveRSIStrategy() {
        super(7, 80, 20);
    }

    @Override
    public String getName() {
        return "Aggressive RSI (7, 80/20)";
    }
}
