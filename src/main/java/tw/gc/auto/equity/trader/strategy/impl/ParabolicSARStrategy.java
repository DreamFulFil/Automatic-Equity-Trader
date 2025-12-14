package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.HashMap;
import java.util.Map;

/**
 * Parabolic SAR (Stop and Reverse) Strategy
 * Type: Trend Following
 * 
 * Logic:
 * - Buy when price crosses above SAR (uptrend)
 * - Sell when price crosses below SAR (downtrend)
 */
@Slf4j
public class ParabolicSARStrategy implements IStrategy {
    
    private final double accelerationFactor = 0.02;
    private final double maxAcceleration = 0.2;
    private final Map<String, Double> sarValues = new HashMap<>();
    private final Map<String, Boolean> isUptrend = new HashMap<>();
    private final Map<String, Double> extremePoint = new HashMap<>();
    private final Map<String, Double> acceleration = new HashMap<>();
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        
        if (!sarValues.containsKey(symbol)) {
            sarValues.put(symbol, data.getLow());
            isUptrend.put(symbol, true);
            extremePoint.put(symbol, data.getHigh());
            acceleration.put(symbol, accelerationFactor);
            return TradeSignal.neutral("Initializing SAR");
        }
        
        double currentPrice = data.getClose();
        double sar = sarValues.get(symbol);
        boolean uptrend = isUptrend.get(symbol);
        double ep = extremePoint.get(symbol);
        double af = acceleration.get(symbol);
        
        boolean crossover = false;
        
        if (uptrend) {
            if (currentPrice < sar) {
                uptrend = false;
                sar = ep;
                ep = data.getLow();
                af = accelerationFactor;
                crossover = true;
            } else {
                if (data.getHigh() > ep) {
                    ep = data.getHigh();
                    af = Math.min(af + accelerationFactor, maxAcceleration);
                }
                sar = sar + af * (ep - sar);
            }
        } else {
            if (currentPrice > sar) {
                uptrend = true;
                sar = ep;
                ep = data.getHigh();
                af = accelerationFactor;
                crossover = true;
            } else {
                if (data.getLow() < ep) {
                    ep = data.getLow();
                    af = Math.min(af + accelerationFactor, maxAcceleration);
                }
                sar = sar + af * (ep - sar);
            }
        }
        
        sarValues.put(symbol, sar);
        isUptrend.put(symbol, uptrend);
        extremePoint.put(symbol, ep);
        acceleration.put(symbol, af);
        
        int position = portfolio.getPosition(symbol);
        
        if (crossover && uptrend && position <= 0) {
            return TradeSignal.longSignal(0.75, "SAR crossover bullish");
        } else if (crossover && !uptrend && position >= 0) {
            return TradeSignal.shortSignal(0.75, "SAR crossover bearish");
        }
        
        return TradeSignal.neutral("SAR no crossover");
    }

    @Override
    public String getName() {
        return "Parabolic SAR";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        sarValues.clear();
        isUptrend.clear();
        extremePoint.clear();
        acceleration.clear();
    }
}
