package tw.gc.auto.equity.trader.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import tw.gc.auto.equity.trader.entities.MarketData;
import tw.gc.auto.equity.trader.strategy.IStrategy;
import tw.gc.auto.equity.trader.strategy.Portfolio;
import tw.gc.auto.equity.trader.strategy.StrategyType;
import tw.gc.auto.equity.trader.strategy.TradeSignal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * AcceleratingMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Stivers & Sun (2012) - 'Momentum and Acceleration'
 * 
 * Logic:
 * Second derivative of momentum (acceleration).
 * Buy when momentum is positive AND accelerating (positive acceleration).
 * Sell when momentum decelerates or turns negative.
 */
@Slf4j
public class AcceleratingMomentumStrategy implements IStrategy {
    
    private final int momentumPeriod;
    private final int accelerationPeriod;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    private final Map<String, Deque<Double>> momentumHistory = new HashMap<>();
    
    public AcceleratingMomentumStrategy(int momentumPeriod, int accelerationPeriod) {
        this.momentumPeriod = momentumPeriod;
        this.accelerationPeriod = accelerationPeriod;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> momentums = momentumHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > momentumPeriod + accelerationPeriod + 10) {
            prices.removeFirst();
        }
        
        if (prices.size() <= momentumPeriod) {
            return TradeSignal.neutral("Warming up - need " + momentumPeriod + " prices");
        }
        
        // Calculate momentum (rate of change)
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        double pastPrice = priceArray[priceArray.length - 1 - momentumPeriod];
        double momentum = (currentPrice - pastPrice) / pastPrice;
        
        momentums.addLast(momentum);
        if (momentums.size() > accelerationPeriod + 5) {
            momentums.removeFirst();
        }
        
        if (momentums.size() < accelerationPeriod) {
            return TradeSignal.neutral("Warming up momentum history");
        }
        
        // Calculate acceleration (change in momentum)
        Double[] momArray = momentums.toArray(new Double[0]);
        double currentMomentum = momArray[momArray.length - 1];
        double pastMomentum = momArray[momArray.length - accelerationPeriod];
        double acceleration = currentMomentum - pastMomentum;
        
        int position = portfolio.getPosition(symbol);
        
        // Entry: positive momentum AND positive acceleration
        if (currentMomentum > 0 && acceleration > 0 && position <= 0) {
            return TradeSignal.longSignal(0.75, 
                String.format("Accelerating momentum: mom=%.2f%%, accel=%.4f", 
                    currentMomentum * 100, acceleration));
        }
        
        // Exit long: momentum decelerating or turning negative
        if (position > 0 && (acceleration < 0 || currentMomentum < 0)) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.7,
                String.format("Momentum decelerating: mom=%.2f%%, accel=%.4f", 
                    currentMomentum * 100, acceleration));
        }
        
        // Short entry: negative momentum AND negative acceleration (accelerating down)
        if (currentMomentum < 0 && acceleration < 0 && position >= 0) {
            return TradeSignal.shortSignal(0.70,
                String.format("Accelerating downward: mom=%.2f%%, accel=%.4f", 
                    currentMomentum * 100, acceleration));
        }
        
        return TradeSignal.neutral(String.format("Mom=%.2f%%, Accel=%.4f", 
            currentMomentum * 100, acceleration));
    }

    @Override
    public String getName() {
        return String.format("Accelerating Momentum (%d, %d)", momentumPeriod, accelerationPeriod);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
        momentumHistory.clear();
    }
}
