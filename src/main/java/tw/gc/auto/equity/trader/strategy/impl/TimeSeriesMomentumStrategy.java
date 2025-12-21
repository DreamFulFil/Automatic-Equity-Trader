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
 * TimeSeriesMomentumStrategy
 * Type: Quantitative
 * 
 * Academic Foundation:
 * - Moskowitz, Ooi & Pedersen (2012) - 'Time Series Momentum'
 * 
 * Logic:
 * Pure time-series momentum - go long if asset has positive return over lookback period.
 * Go short if negative return. Position size scaled by volatility.
 */
@Slf4j
public class TimeSeriesMomentumStrategy implements IStrategy {
    
    private final int lookback;
    private final double entryThreshold;
    private final Map<String, Deque<Double>> priceHistory = new HashMap<>();
    
    public TimeSeriesMomentumStrategy(int lookback, double entryThreshold) {
        this.lookback = lookback;
        this.entryThreshold = entryThreshold;
    }

    @Override
    public TradeSignal execute(Portfolio portfolio, MarketData data) {
        String symbol = data.getSymbol();
        Deque<Double> prices = priceHistory.computeIfAbsent(symbol, k -> new ArrayDeque<>());
        
        prices.addLast(data.getClose());
        if (prices.size() > lookback + 10) {
            prices.removeFirst();
        }
        
        if (prices.size() < lookback) {
            return TradeSignal.neutral("Warming up - need " + lookback + " prices");
        }
        
        Double[] priceArray = prices.toArray(new Double[0]);
        double currentPrice = priceArray[priceArray.length - 1];
        double pastPrice = priceArray[priceArray.length - 1 - lookback];
        
        // Time-series momentum: return over lookback period
        double momentum = (currentPrice - pastPrice) / pastPrice;
        
        // Calculate volatility for position sizing (standard deviation of returns)
        double sumReturns = 0;
        double sumSqReturns = 0;
        int n = Math.min(lookback, priceArray.length - 1);
        for (int i = priceArray.length - n; i < priceArray.length; i++) {
            double ret = (priceArray[i] - priceArray[i - 1]) / priceArray[i - 1];
            sumReturns += ret;
            sumSqReturns += ret * ret;
        }
        double avgReturn = sumReturns / n;
        double variance = (sumSqReturns / n) - (avgReturn * avgReturn);
        double volatility = Math.sqrt(variance);
        
        // Volatility-scaled confidence
        double confidence = Math.min(0.9, 0.5 + (Math.abs(momentum) / (volatility * 10)));
        
        int position = portfolio.getPosition(symbol);
        
        // Go long if positive momentum exceeds threshold
        if (momentum > entryThreshold && position <= 0) {
            return TradeSignal.longSignal(confidence,
                String.format("TSMOM Long: %.2f%% > %.2f%% (vol: %.2f%%)", 
                    momentum * 100, entryThreshold * 100, volatility * 100));
        }
        
        // Go short if negative momentum exceeds threshold
        if (momentum < -entryThreshold && position >= 0) {
            return TradeSignal.shortSignal(confidence,
                String.format("TSMOM Short: %.2f%% < -%.2f%% (vol: %.2f%%)", 
                    momentum * 100, entryThreshold * 100, volatility * 100));
        }
        
        // Exit long if momentum turns negative
        if (position > 0 && momentum < 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.SHORT, 0.7,
                String.format("TSMOM Exit Long: momentum=%.2f%%", momentum * 100));
        }
        
        // Exit short if momentum turns positive
        if (position < 0 && momentum > 0) {
            return TradeSignal.exitSignal(TradeSignal.SignalDirection.LONG, 0.7,
                String.format("TSMOM Exit Short: momentum=%.2f%%", momentum * 100));
        }
        
        return TradeSignal.neutral(String.format("TSMOM: %.2f%%", momentum * 100));
    }

    @Override
    public String getName() {
        return String.format("Time Series Momentum (%d)", lookback);
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
