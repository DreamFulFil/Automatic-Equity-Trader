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
 * TickTestStrategy
 * Type: Market Microstructure
 * 
 * Academic Foundation:
 * - Lee & Ready (1991) - 'Inferring Trade Direction from Intraday Data'
 * 
 * Logic:
 * Infer buy/sell pressure from price tick direction sequence.
 */
@Slf4j
public class TickTestStrategy implements IStrategy {
    
    private final int tickWindow;
    private final double directionThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public TickTestStrategy(int tickWindow, double directionThreshold) {
        this.tickWindow = tickWindow;
        this.directionThreshold = directionThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > tickWindow + 5) prices.removeFirst();
        
        if (prices.size() < tickWindow) {
            return TradeSignal.neutral("Warming up tick test");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        
        // Count upticks and downticks
        int upticks = 0, downticks = 0;
        for (int i = 1; i < priceArray.length; i++) {
            if (priceArray[i] > priceArray[i-1]) upticks++;
            else if (priceArray[i] < priceArray[i-1]) downticks++;
        }
        
        int totalTicks = upticks + downticks;
        double tickDirection = totalTicks > 0 ? (double)(upticks - downticks) / totalTicks : 0;
        
        int position = portfolio.getPosition(symbol);
        
        // Strong buying pressure
        if (tickDirection > directionThreshold && position <= 0) {
            return TradeSignal.longSignal(0.70,
                String.format("Tick test buy: direction=%.2f (up=%d, down=%d)", 
                    tickDirection, upticks, downticks));
        }
        
        // Strong selling pressure
        if (tickDirection < -directionThreshold && position > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.70,
                String.format("Tick test sell: direction=%.2f", tickDirection));
        }
        
        // Short on extreme selling
        if (tickDirection < -directionThreshold && position >= 0) {
            return TradeSignal.shortSignal(0.65,
                String.format("Tick test short: direction=%.2f", tickDirection));
        }
        
        return TradeSignal.neutral(String.format("Tick direction: %.2f", tickDirection));
    }

    @Override
    public String getName() {
        return String.format("Tick Test (%d, %.1f)", tickWindow, directionThreshold);
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        priceHistory.clear();
    }
}
