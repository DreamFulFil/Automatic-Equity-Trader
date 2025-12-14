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
 * Ichimoku Cloud Strategy
 * Type: Trend Following
 * 
 * Logic:
 * - Buy when price above cloud and Tenkan crosses above Kijun
 * - Sell when price below cloud and Tenkan crosses below Kijun
 */
@Slf4j
public class IchimokuCloudStrategy implements IStrategy {
    
    private final Map<String, Deque<Double>> highHistory = new HashMap<>();
    private final Map<String, Deque<Double>> lowHistory = new HashMap<>();
    private final Map<String, Deque<Double>> closeHistory = new HashMap<>();
    private final Map<String, Boolean> previousCross = new HashMap<>();
    
    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> highs = highHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> lows = lowHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        Deque<Double> closes = closeHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        highs.addLast(data.getHigh());
        lows.addLast(data.getLow());
        closes.addLast(data.getClose());
        
        if (highs.size() > 52) {
            highs.removeFirst();
            lows.removeFirst();
            closes.removeFirst();
        }
        
        if (highs.size() < 52) {
            return TradeSignal.neutral("Warming up Ichimoku");
        }
        
        double tenkan = (getMax(highs, 9) + getMin(lows, 9)) / 2;
        double kijun = (getMax(highs, 26) + getMin(lows, 26)) / 2;
        double senkou_a = (tenkan + kijun) / 2;
        double senkou_b = (getMax(highs, 52) + getMin(lows, 52)) / 2;
        
        double currentPrice = data.getClose();
        boolean aboveCloud = currentPrice > Math.max(senkou_a, senkou_b);
        boolean belowCloud = currentPrice < Math.min(senkou_a, senkou_b);
        boolean tenkanAboveKijun = tenkan > kijun;
        
        Boolean wasTenkanAbove = previousCross.get(symbol);
        previousCross.put(symbol, tenkanAboveKijun);
        
        if (wasTenkanAbove == null) return TradeSignal.neutral("Initializing");
        
        int position = portfolio.getPosition(symbol);
        
        if (!wasTenkanAbove && tenkanAboveKijun && aboveCloud && position <= 0) {
            return TradeSignal.longSignal(0.8, "Ichimoku bullish crossover above cloud");
        } else if (wasTenkanAbove && !tenkanAboveKijun && belowCloud && position >= 0) {
            return TradeSignal.shortSignal(0.8, "Ichimoku bearish crossover below cloud");
        }
        
        return TradeSignal.neutral("Ichimoku no signal");
    }
    
    private double getMax(Deque<Double> values, int period) {
        return values.stream().skip(Math.max(0, values.size() - period))
            .mapToDouble(Double::doubleValue).max().orElse(0);
    }
    
    private double getMin(Deque<Double> values, int period) {
        return values.stream().skip(Math.max(0, values.size() - period))
            .mapToDouble(Double::doubleValue).min().orElse(0);
    }

    @Override
    public String getName() {
        return "Ichimoku Cloud";
    }

    @Override
    public StrategyType getType() {
        return StrategyType.SWING;
    }

    @Override
    public void reset() {
        highHistory.clear();
        lowHistory.clear();
        closeHistory.clear();
        previousCross.clear();
    }
}
