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
 * CCI (Commodity Channel Index) Strategy
 * Type: Momentum Oscillator
 * 
 * Logic:
 * - Buy when CCI crosses above -100 from below
 * - Sell when CCI crosses below +100 from above
 */
@Slf4j
public class CCIStrategy implements IStrategy {
    
    private final int period;
    private final Map<String, Deque<Double>> typicalPriceHistory = new HashMap<>();
    private final Map<String, Double> previousCCI = new HashMap<>();
    
    public CCIStrategy() {
        this(20);
    }
    
    public CCIStrategy(int period) {
        this.period = period;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> tpHistory = typicalPriceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        double typicalPrice = (data.getHigh() + data.getLow() + data.getClose()) / 3.0;
        tpHistory.addLast(typicalPrice);
        
        if (tpHistory.size() > period) {
            tpHistory.removeFirst();
        }
        
        if (tpHistory.size() < period) {
            return TradeSignal.neutral("Warming up CCI");
        }
        
        double sma = tpHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanDeviation = tpHistory.stream()
            .mapToDouble(tp -> Math.abs(tp - sma))
            .average().orElse(0);
        
        double cci = (typicalPrice - sma) / (0.015 * meanDeviation);
        
        Double prevCCI = previousCCI.get(symbol);
        previousCCI.put(symbol, cci);
        
        if (prevCCI == null) return TradeSignal.neutral("Initializing CCI");
        
        int position = portfolio.getPosition(symbol);
        
        if (prevCCI < -100 && cci > -100 && position <= 0) {
            return TradeSignal.longSignal(0.75, String.format("CCI bullish crossover (%.1f)", cci));
        } else if (prevCCI > 100 && cci < 100 && position >= 0) {
            return TradeSignal.shortSignal(0.75, String.format("CCI bearish crossover (%.1f)", cci));
        }
        
        return TradeSignal.neutral(String.format("CCI=%.1f", cci));
    }

    @Override
    public String getName() {
        return "CCI Oscillator";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SHORT_TERM;
    }

    @Override
    public void reset() {
        typicalPriceHistory.clear();
        previousCCI.clear();
    }
}
